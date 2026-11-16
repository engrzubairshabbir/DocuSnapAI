package com.docusnap.ai.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.docusnap.ai.DocuSnapApplication
import com.docusnap.ai.ai.AiBackendClient
import com.docusnap.ai.ai.GeminiReasoningEngine
import com.docusnap.ai.billing.BillingManager
import com.docusnap.ai.billing.OneTimeSku
import com.docusnap.ai.billing.SubscriptionTier
import com.docusnap.ai.data.model.DocumentEntity
import com.docusnap.ai.i18n.AppLanguage
import com.docusnap.ai.i18n.AppStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ConversationalSearchState(
    val query: String = "",
    val isSearching: Boolean = false,
    val aiAnswer: String? = null,
    val matchedDocuments: List<DocumentEntity> = emptyList()
)

data class GalleryScanProgress(
    val isScanning: Boolean = false,
    val isCoolingDown: Boolean = false,
    val cooldownRemainingSec: Int = 0,
    val totalGalleryCount: Int = 0,
    val indexedCount: Int = 0,
    val percent: Int = 0,
    val currentBatchProcessed: Int = 0,
    val isLimitedAccess: Boolean = false,
    val statusMessage: String = "Idle"
)

class DocuSnapViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DocuSnapApplication
    private val docDao = app.database.documentDao()
    private val geminiEngine = GeminiReasoningEngine()
    val syncRepo = app.syncRepository

    // Continuous Gallery Scanner Progress
    private val _scanProgress = MutableStateFlow(GalleryScanProgress())
    val scanProgress: StateFlow<GalleryScanProgress> = _scanProgress.asStateFlow()
    private var scanJob: Job? = null
    private val skipCooldownTrigger = MutableStateFlow(0)

    // Billing Manager & Tier
    val billingManager = BillingManager.getInstance(application)
    val activeTier: StateFlow<SubscriptionTier> = billingManager.activeTier
    val lifetimeAmountUsd: StateFlow<Int> = billingManager.lifetimeAmountUsd
    val balanceCents: StateFlow<Int> = billingManager.balanceCents
    val subProductDetailsMap = billingManager.subProductDetailsMap
    val inAppProductDetailsMap = billingManager.inAppProductDetailsMap
    val purchaseEventMessage = billingManager.purchaseEventMessage

    // Total Document Count Flow
    val totalDocumentCount = docDao.getTotalCountFlow().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        0
    )

    // Language Preference
    private val prefs = application.getSharedPreferences("docusnap_ui_prefs", Context.MODE_PRIVATE)
    private val _currentLanguage = MutableStateFlow(
        AppLanguage.fromCode(prefs.getString("selected_lang", AppLanguage.ENGLISH.code) ?: "en")
    )
    val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

    fun setLanguage(lang: AppLanguage) {
        prefs.edit().putString("selected_lang", lang.code).apply()
        _currentLanguage.value = lang
    }

    // Search state
    private val _searchState = MutableStateFlow(ConversationalSearchState())
    val searchState: StateFlow<ConversationalSearchState> = _searchState.asStateFlow()

    // Filter chip state for Smart Vault
    val selectedCategory = MutableStateFlow("All") // "All", "Medical", "Receipts", "Utility", "Bank"

    // Settings preferences
    val deepReasoningEnabled = MutableStateFlow(true)

    // All documents flow
    val allDocuments = selectedCategory.flatMapLatest { cat ->
        when (cat) {
            "Medical" -> docDao.getDocumentsByType(DocumentEntity.TYPE_MEDICAL)
            "Receipts" -> docDao.getDocumentsByType(DocumentEntity.TYPE_RECEIPT)
            "Utility" -> docDao.getDocumentsByType(DocumentEntity.TYPE_UTILITY)
            "Bank" -> docDao.getDocumentsByType(DocumentEntity.TYPE_BANK)
            else -> docDao.getAllDocuments()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChanged(newQuery: String) {
        _searchState.value = _searchState.value.copy(query = newQuery)
    }

    /**
     * Executes conversational search: Combines local FTS5 keyword hits with Gemini reasoning.
     */
    fun performConversationalSearch(query: String) {
        if (query.isBlank()) {
            _searchState.value = ConversationalSearchState()
            return
        }

        viewModelScope.launch {
            _searchState.value = _searchState.value.copy(
                query = query,
                isSearching = true,
                aiAnswer = null
            )

            // Step 1: Run instant offline SQLite FTS5 + UTF-8 LIKE token search via suspend DAO queries
            val cleanTerm = query.trim().replace("'", "").replace("\"", "")
            val tokens = cleanTerm.split("\\s+".toRegex()).filter { it.isNotBlank() }
            val ftsQuery = tokens.joinToString(" OR ") { "$it*" }

            var candidateDocs: List<DocumentEntity> = if (ftsQuery.isNotBlank()) {
                try {
                    docDao.searchFtsOnce(ftsQuery).take(50)
                } catch (_: Exception) {
                    emptyList()
                }
            } else emptyList()

            if (candidateDocs.isEmpty() && cleanTerm.isNotBlank()) {
                val likeMatches = LinkedHashMap<String, DocumentEntity>()
                try {
                    docDao.searchLikeOnce(cleanTerm).forEach { likeMatches[it.id] = it }
                    for (token in tokens.take(5)) {
                        if (token.length >= 2) {
                            docDao.searchLikeOnce(token).forEach { likeMatches[it.id] = it }
                        }
                    }
                } catch (_: Exception) {
                }
                candidateDocs = likeMatches.values.take(50).toList()
            }

            // For Global Any-Language Search (e.g. Japanese query matching cross-script receipts/summaries),
            // pull recent documents from vault when user has active 5x prepaid wallet balance.
            val has5xBalance = billingManager.hasSufficient5xBalance()
            if (candidateDocs.isEmpty() && has5xBalance) {
                candidateDocs = try {
                    docDao.getRecentDocumentsOnce(40)
                } catch (_: Exception) {
                    emptyList()
                }
            }

            // Step 2: Secure Backend 5x Multilingual Search (deducts $0.05 from One-Time Balance) or offline fallback
            var finalMatchedDocs = candidateDocs
            val answer = if (deepReasoningEnabled.value && has5xBalance && candidateDocs.isNotEmpty()) {
                val backendRes = AiBackendClient.multilingualSearch5x(
                    context = app,
                    query = query,
                    documents = candidateDocs,
                    uiLanguage = currentLanguage.value
                )
                if (!backendRes.answer.isNullOrBlank()) {
                    if (backendRes.matchedIds.isNotEmpty()) {
                        val docMap = candidateDocs.associateBy { it.id }
                        val prioritized = backendRes.matchedIds.mapNotNull { docMap[it] }
                        if (prioritized.isNotEmpty()) {
                            finalMatchedDocs = prioritized
                        }
                    }
                    backendRes.answer
                } else {
                    geminiEngine.answerConversationalQuery(query, candidateDocs, currentLanguage.value)
                }
            } else if (deepReasoningEnabled.value) {
                geminiEngine.answerConversationalQuery(query, candidateDocs, currentLanguage.value)
            } else {
                if (candidateDocs.isNotEmpty()) {
                    "Found ${candidateDocs.size} matching document(s). Tap any card below to inspect."
                } else {
                    "No matching documents found in your Smart Vault."
                }
            }

            _searchState.value = _searchState.value.copy(
                isSearching = false,
                aiAnswer = answer,
                matchedDocuments = finalMatchedDocs
            )
        }
    }

    fun setCategory(category: String) {
        selectedCategory.value = category
    }

    fun toggleDeepReasoning(enabled: Boolean) {
        deepReasoningEnabled.value = enabled
    }

    fun triggerManualSync() {
        viewModelScope.launch {
            val pending = docDao.getDocumentsBySyncStatus(DocumentEntity.SYNC_STATUS_PENDING_SYNC)
            for (doc in pending) {
                syncRepo.syncDocument(doc)
            }
        }
    }

    fun syncAllDocumentsToWeb(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            syncRepo.syncAllLocalDocuments()
            onComplete()
        }
    }

    fun signInWithGoogleToken(idToken: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = syncRepo.signInOrLinkWithGoogle(idToken)
            if (result.isSuccess) {
                onResult(true, null)
            } else {
                onResult(false, result.exceptionOrNull()?.localizedMessage ?: "Sign-in failed")
            }
        }
    }

    fun signOut() {
        syncRepo.signOut()
    }

    fun refreshWalletBalance() {
        viewModelScope.launch {
            AiBackendClient.syncWalletStatus(app)
        }
    }

    fun ingestSelectedUris(uris: List<android.net.Uri>, isCameraCapture: Boolean = false) {
        com.docusnap.ai.service.MediaIngestionWorker.enqueueScan(
            context = getApplication(),
            uris = uris,
            isCameraCapture = isCameraCapture
        )
    }

    fun checkPermissionsAndRefreshCounts() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<DocuSnapApplication>()
            val isLimited = com.docusnap.ai.service.MediaIngestionWorker.isPartialPhotoAccess(context)
            val totalInGallery = com.docusnap.ai.service.MediaIngestionWorker.queryTotalGalleryCount(context)
            val indexed = docDao.getTotalCount()
            val p = if (totalInGallery > 0) (indexed * 100 / totalInGallery).coerceIn(0, 100) else 0

            _scanProgress.value = _scanProgress.value.copy(
                totalGalleryCount = totalInGallery,
                indexedCount = indexed,
                percent = p,
                isLimitedAccess = isLimited
            )
        }
    }

    /**
     * Continuous automated gallery scanner:
     * Indexes batches of 50 images with on-device OCR -> Pushes OCR text to Web -> Pauses 2 mins -> repeats.
     */
    fun startContinuousGalleryScan() {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<DocuSnapApplication>()
            val isLimited = com.docusnap.ai.service.MediaIngestionWorker.isPartialPhotoAccess(context)
            val totalInGallery = com.docusnap.ai.service.MediaIngestionWorker.queryTotalGalleryCount(context)
            var indexed = docDao.getTotalCount()
            val maxTierDocs = maxOf(activeTier.value.maxDocuments, OneTimeSku.maxDocsForAmount(lifetimeAmountUsd.value))

            _scanProgress.value = GalleryScanProgress(
                isScanning = true,
                totalGalleryCount = totalInGallery,
                indexedCount = indexed,
                percent = if (totalInGallery > 0) (indexed * 100 / totalInGallery).coerceIn(0, 100) else 0,
                isLimitedAccess = isLimited,
                statusMessage = "Starting automated gallery indexing..."
            )

            var offset = 0
            while (coroutineContext.isActive && indexed < totalInGallery && indexed < maxTierDocs) {
                _scanProgress.value = _scanProgress.value.copy(
                    isScanning = true,
                    isCoolingDown = false,
                    statusMessage = "Indexing batch of 50 images with on-device OCR..."
                )

                val newDocs = com.docusnap.ai.service.MediaIngestionWorker.processBatch(context, batchSize = 50, offset = offset)
                indexed = docDao.getTotalCount()
                offset += 50

                if (newDocs.isNotEmpty()) {
                    _scanProgress.value = _scanProgress.value.copy(
                        statusMessage = "Pushing ${newDocs.size} OCR texts to website vault..."
                    )
                    syncRepo.syncBatchDocuments(newDocs)
                }

                val currentPercent = if (totalInGallery > 0) (indexed * 100 / totalInGallery).coerceIn(0, 100) else 100
                _scanProgress.value = _scanProgress.value.copy(
                    indexedCount = indexed,
                    percent = currentPercent,
                    currentBatchProcessed = newDocs.size
                )

                if (newDocs.isEmpty() && offset >= totalInGallery) {
                    break
                }

                // 2-minute battery-saving cooldown with skip capability
                if (indexed < totalInGallery && indexed < maxTierDocs) {
                    val initialTrigger = skipCooldownTrigger.value
                    for (sec in 120 downTo 1) {
                        if (!coroutineContext.isActive || skipCooldownTrigger.value != initialTrigger) break
                        _scanProgress.value = _scanProgress.value.copy(
                            isCoolingDown = true,
                            cooldownRemainingSec = sec,
                            statusMessage = "Batch of 50 synced! Pausing 2 mins to preserve battery (${sec}s remaining)..."
                        )
                        delay(1000)
                    }
                }
            }

            _scanProgress.value = _scanProgress.value.copy(
                isScanning = false,
                isCoolingDown = false,
                statusMessage = "Gallery scan complete! $indexed documents indexed & synced."
            )
        }
    }

    fun skipCooldownAndContinue() {
        skipCooldownTrigger.value += 1
    }

    fun stopContinuousGalleryScan() {
        scanJob?.cancel()
        _scanProgress.value = _scanProgress.value.copy(
            isScanning = false,
            isCoolingDown = false,
            statusMessage = "Gallery scan paused."
        )
    }

    fun scanGallery() {
        startContinuousGalleryScan()
    }

    fun scanFullGallery() {
        startContinuousGalleryScan()
    }

    fun deleteDocument(id: String) {
        viewModelScope.launch {
            docDao.deleteDocument(id)
        }
    }
}

