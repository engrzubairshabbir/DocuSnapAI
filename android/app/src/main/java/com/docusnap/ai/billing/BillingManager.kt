package com.docusnap.ai.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.docusnap.ai.ai.AiBackendClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SubscriptionTier(
    val id: String,
    val title: String,
    val priceUsd: String,
    val maxDocuments: Int,
    val maxFileSizeMb: Float,
    val isPopular: Boolean = false,
    val adFree: Boolean = false,
    val features: List<String>
) {
    FREE(
        id = "docusnap_tier_free",
        title = "Free Starter",
        priceUsd = "$0.00",
        maxDocuments = 500,
        maxFileSizeMb = 1.0f,
        adFree = false,
        features = listOf(
            "Scan & index files up to 1.0 MB",
            "Up to 500 gallery documents indexed",
            "Zero storage duplication (lightweight)",
            "100% on-device Google ML Kit OCR",
            "SQLite FTS5 instant smart search"
        )
    ),
    STARTER(
        id = "docusnap_sub_starter_499",
        title = "Starter Pro (Essential Vault)",
        priceUsd = "$4.99 / mo",
        maxDocuments = 2000,
        maxFileSizeMb = 3.0f,
        adFree = false,
        features = listOf(
            "Scan & index files up to 3.0 MB",
            "Fewer Ads Experience",
            "Up to 2,000 indexed documents / photos",
            "Secure Private Cloud Backup (Zero Data Loss)",
            "Remote Web Search from Laptop / Browser",
            "Auto-extract medicine dosages & totals"
        )
    ),
    SILVER(
        id = "docusnap_sub_silver_999",
        title = "Silver Vault (Pro Organizer)",
        priceUsd = "$9.99 / mo",
        maxDocuments = 5000,
        maxFileSizeMb = 8.0f,
        adFree = true,
        features = listOf(
            "Scan & index files & PDFs up to 8.0 MB",
            "100% Ad-Free Experience",
            "Up to 5,000 indexed documents / PDFs",
            "24/7 Remote Access (docusnap.consultways.com)",
            "Priority Cloud Backup & Real-time Web Sync",
            "Fast Gemini Flash AI Medical & Bill Reasoning"
        )
    ),
    GOLD(
        id = "docusnap_sub_gold_1499",
        title = "Gold Vault (Clinic & Business)",
        priceUsd = "$14.99 / mo",
        maxDocuments = 10000,
        maxFileSizeMb = 15.0f,
        isPopular = true,
        adFree = true,
        features = listOf(
            "Scan & index files up to 15.0 MB",
            "100% Ad-Free Experience",
            "Up to 10,000 indexed documents / PDFs",
            "24/7 Remote Access (docusnap.consultways.com)",
            "Private & Secure Backup (Zero Data Loss)",
            "Instant multi-page PDF & entity extraction",
            "Hi-Res photo remote retrieval"
        )
    ),
    ENTERPRISE(
        id = "docusnap_sub_enterprise_2599",
        title = "Platinum Enterprise (Business Suite)",
        priceUsd = "$25.99 / mo",
        maxDocuments = 25000,
        maxFileSizeMb = 25.0f,
        adFree = true,
        features = listOf(
            "Scan & index files up to 25.0 MB",
            "100% Ad-Free Experience",
            "Up to 25,000 indexed documents / PDFs",
            "24/7 Seamless Remote Web Access",
            "Private & Secure Encrypted Cloud Backup",
            "Dedicated Customer Service Support",
            "Bulk document export in JSON / CSV"
        )
    ),
    INFINITY(
        id = "docusnap_sub_infinity_4999",
        title = "Infinity Diamond (Ultimate Suite)",
        priceUsd = "$49.99 / mo",
        maxDocuments = 50000,
        maxFileSizeMb = 50.0f,
        adFree = true,
        features = listOf(
            "Scan & index files up to 50.0 MB",
            "100% No Ads At All (Pure VIP)",
            "Up to 50,000 indexed documents / PDFs",
            "24/7 Seamless Remote Access",
            "Private & Secure Cloud Backup",
            "Dedicated Customer Service Support",
            "Dedicated High-Speed Sync Servers",
            "State-of-the-Art End-to-End Encryption"
        )
    );

    companion object {
        fun fromId(id: String): SubscriptionTier {
            return when (id) {
                STARTER.id -> STARTER
                SILVER.id, "docusnap_sub_silver_299" -> SILVER
                GOLD.id, "docusnap_sub_gold_999" -> GOLD
                ENTERPRISE.id, "docusnap_sub_platinum_2999" -> ENTERPRISE
                INFINITY.id, "docusnap_sub_enterprise_4999" -> INFINITY
                else -> entries.firstOrNull { it.id == id } ?: FREE
            }
        }
    }
}

enum class OneTimeSku(val productId: String, val amountUsd: Int, val maxDocuments: Int, val label: String) {
    VIP_99("docusnap_lifetime_99", 99, 10_000, "Patron Starter Lifetime"),
    VIP_199("docusnap_lifetime_199", 199, 25_000, "Business Lifetime Pass"),
    VIP_299("docusnap_lifetime_299", 299, 40_000, "Executive Lifetime Vault"),
    VIP_499("docusnap_lifetime_499", 499, 65_000, "Clinic Pro Lifetime Suite"),
    VIP_749("docusnap_lifetime_749", 749, 85_000, "Platinum Sponsor Lifetime"),
    VIP_999("docusnap_lifetime_999", 999, 150_000, "Diamond Founders Lifetime");

    companion object {
        fun fromProductId(id: String): OneTimeSku? = entries.firstOrNull { it.productId == id }

        fun nearestForAmount(amountUsd: Int): OneTimeSku {
            val clamped = amountUsd.coerceIn(99, 999)
            return entries.minByOrNull { kotlin.math.abs(it.amountUsd - clamped) } ?: VIP_99
        }

        fun maxDocsForAmount(amountUsd: Int): Int = when {
            amountUsd >= 749 -> 150_000
            amountUsd >= 499 -> 65_000
            amountUsd >= 299 -> 40_000
            amountUsd >= 199 -> 25_000
            amountUsd >= 99 -> 10_000
            else -> 500
        }
    }
}

sealed class BillingLaunchResult {
    data object Launched : BillingLaunchResult()
    data class PreLaunchPending(val packageTitle: String, val priceLabel: String, val reason: String) : BillingLaunchResult()
    data class Error(val message: String) : BillingLaunchResult()
}

class BillingManager(private val context: Context) : PurchasesUpdatedListener {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _activeTier = MutableStateFlow(loadSavedTier())
    val activeTier: StateFlow<SubscriptionTier> = _activeTier.asStateFlow()

    private val _lifetimeAmountUsd = MutableStateFlow(prefs.getInt(KEY_LIFETIME_USD, 0))
    val lifetimeAmountUsd: StateFlow<Int> = _lifetimeAmountUsd.asStateFlow()

    private val _balanceCents = MutableStateFlow(prefs.getInt(KEY_BALANCE_CENTS, 0))
    val balanceCents: StateFlow<Int> = _balanceCents.asStateFlow()

    private val _subProductDetailsMap = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val subProductDetailsMap: StateFlow<Map<String, ProductDetails>> = _subProductDetailsMap.asStateFlow()

    private val _inAppProductDetailsMap = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val inAppProductDetailsMap: StateFlow<Map<String, ProductDetails>> = _inAppProductDetailsMap.asStateFlow()

    private val _isPlayBillingConnected = MutableStateFlow(false)
    val isPlayBillingConnected: StateFlow<Boolean> = _isPlayBillingConnected.asStateFlow()

    private val _purchaseEventMessage = MutableStateFlow<String?>(null)
    val purchaseEventMessage: StateFlow<String?> = _purchaseEventMessage.asStateFlow()

    private var billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    init {
        startConnection()
        CoroutineScope(Dispatchers.IO).launch {
            AiBackendClient.syncWalletStatus(context)
        }
    }

    fun clearPurchaseEventMessage() {
        _purchaseEventMessage.value = null
    }

    fun refreshSubscriptions() {
        if (!billingClient.isReady) {
            startConnection()
        } else {
            queryAvailableSubscriptions()
            queryAvailableOneTimeProducts()
            queryActivePurchases()
        }
    }

    private fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _isPlayBillingConnected.value = true
                    Log.d("DocuSnapBilling", "Play Billing client connected successfully.")
                    queryAvailableSubscriptions()
                    queryAvailableOneTimeProducts()
                    queryActivePurchases()
                } else {
                    _isPlayBillingConnected.value = false
                    Log.w("DocuSnapBilling", "Play Billing setup code: ${billingResult.responseCode} - ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                _isPlayBillingConnected.value = false
                Log.w("DocuSnapBilling", "Play Billing disconnected.")
            }
        })
    }

    private fun queryAvailableSubscriptions() {
        val productList = listOf(
            SubscriptionTier.STARTER.id,
            SubscriptionTier.SILVER.id,
            SubscriptionTier.GOLD.id,
            SubscriptionTier.ENTERPRISE.id,
            SubscriptionTier.INFINITY.id
        ).map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val map = productDetailsResult.productDetailsList.associateBy { it.productId }
                _subProductDetailsMap.value = map
                Log.d("DocuSnapBilling", "Loaded ${map.size} Play Store SUBS products.")
            }
        }
    }

    private fun queryAvailableOneTimeProducts() {
        val productList = OneTimeSku.entries.map { sku ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(sku.productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val map = productDetailsResult.productDetailsList.associateBy { it.productId }
                _inAppProductDetailsMap.value = map
                Log.d("DocuSnapBilling", "Loaded ${map.size} Play Store INAPP products.")
            }
        }
    }

    fun queryActivePurchases() {
        if (!billingClient.isReady) return

        // Verify subscriptions with Google Play as single source of truth
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val validSubPurchases = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                if (validSubPurchases.isEmpty()) {
                    setTier(SubscriptionTier.FREE)
                } else {
                    handlePurchases(validSubPurchases)
                }
            }
        }

        // Verify one-time lifetime INAPP purchases with Google Play
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val validInApp = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                if (validInApp.isEmpty()) {
                    setLifetimeAmount(0)
                } else {
                    handlePurchases(validInApp)
                }
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases != null) {
                    handlePurchases(purchases)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _purchaseEventMessage.value = "Purchase cancelled by user."
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                queryActivePurchases()
                _purchaseEventMessage.value = "You already own this plan. Restored from Google Play!"
            }
            else -> {
                _purchaseEventMessage.value = "Google Play Billing error (${billingResult.responseCode}): ${billingResult.debugMessage}"
            }
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        for (purchase in purchases) {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                for (productId in purchase.products) {
                    val tier = SubscriptionTier.fromId(productId)
                    if (tier != SubscriptionTier.FREE) {
                        setTier(tier)
                        _purchaseEventMessage.value = "Activated ${tier.title} via Google Play!"
                    }
                    val oneTime = OneTimeSku.fromProductId(productId)
                    if (oneTime != null) {
                        setLifetimeAmount(oneTime.amountUsd)
                        val loadedCents = oneTime.amountUsd * 100
                        if (_balanceCents.value < loadedCents) {
                            updateBalanceFromBackend(_balanceCents.value + loadedCents)
                        }
                        CoroutineScope(Dispatchers.IO).launch {
                            AiBackendClient.creditOneTimePurchase(
                                context = context,
                                productId = productId,
                                purchaseToken = purchase.purchaseToken
                            )
                        }
                        _purchaseEventMessage.value = "Activated ${oneTime.label} ($${oneTime.amountUsd} USD prepaid balance)!"
                    }
                }

                if (!purchase.isAcknowledged) {
                    val ackParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    billingClient.acknowledgePurchase(ackParams) { result ->
                        Log.d("DocuSnapBilling", "Purchase acknowledged: ${result.responseCode}")
                    }
                }
            } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                _purchaseEventMessage.value = "Payment is pending confirmation from Google Play."
            }
        }
    }

    /**
     * Launches real Google Play Subscription Billing Flow.
     * NEVER auto-subscribes if app/SKUs are not yet published on Google Play Console.
     */
    fun launchSubscription(activity: Activity, tier: SubscriptionTier): BillingLaunchResult {
        if (!billingClient.isReady) {
            startConnection()
            return BillingLaunchResult.PreLaunchPending(
                packageTitle = tier.title,
                priceLabel = tier.priceUsd,
                reason = "Google Play Billing service is connecting or not yet live for this test APK."
            )
        }

        val details = _subProductDetailsMap.value[tier.id]
        if (details == null) {
            return BillingLaunchResult.PreLaunchPending(
                packageTitle = tier.title,
                priceLabel = tier.priceUsd,
                reason = "Subscription SKU (${tier.id}) is not published on Google Play Console yet."
            )
        }

        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (offerToken.isNullOrBlank()) {
            return BillingLaunchResult.PreLaunchPending(
                packageTitle = tier.title,
                priceLabel = tier.priceUsd,
                reason = "No active base plan offer found in Play Console for ${tier.id}."
            )
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .setOfferToken(offerToken)
                .build()
        )

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val result = billingClient.launchBillingFlow(activity, flowParams)
        return if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            BillingLaunchResult.Launched
        } else {
            BillingLaunchResult.Error("Play Billing failed (${result.responseCode}): ${result.debugMessage}")
        }
    }

    /**
     * Launches real Google Play One-Time Payment Flow ($99 - $999 USD).
     * NEVER grants access without a verified Google Play PURCHASED transaction.
     */
    fun launchOneTimePurchase(activity: Activity, requestedAmountUsd: Int): BillingLaunchResult {
        val clamped = requestedAmountUsd.coerceIn(99, 999)
        val sku = OneTimeSku.nearestForAmount(clamped)

        if (!billingClient.isReady) {
            startConnection()
            return BillingLaunchResult.PreLaunchPending(
                packageTitle = "${sku.label} ($$clamped USD One-Time)",
                priceLabel = "$$clamped.00 USD",
                reason = "Google Play Billing is awaiting Play Store release for one-time VIP packages."
            )
        }

        val details = _inAppProductDetailsMap.value[sku.productId]
        if (details == null) {
            return BillingLaunchResult.PreLaunchPending(
                packageTitle = "${sku.label} ($$clamped USD One-Time)",
                priceLabel = "$$clamped.00 USD",
                reason = "One-time INAPP SKU (${sku.productId}) is not live on Google Play Console yet."
            )
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .build()
        )

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val result = billingClient.launchBillingFlow(activity, flowParams)
        return if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            BillingLaunchResult.Launched
        } else {
            BillingLaunchResult.Error("Play Billing failed (${result.responseCode}): ${result.debugMessage}")
        }
    }

    fun setTier(tier: SubscriptionTier) {
        prefs.edit().putString(KEY_TIER, tier.id).apply()
        _activeTier.value = tier
    }

    private fun setLifetimeAmount(amountUsd: Int) {
        prefs.edit().putInt(KEY_LIFETIME_USD, amountUsd).apply()
        _lifetimeAmountUsd.value = amountUsd
    }

    fun updateBalanceFromBackend(cents: Int) {
        val safeCents = maxOf(0, cents)
        prefs.edit().putInt(KEY_BALANCE_CENTS, safeCents).apply()
        _balanceCents.value = safeCents
    }

    fun hasSufficient5xBalance(): Boolean {
        return _balanceCents.value >= COST_5X_CALL_CENTS
    }

    private fun loadSavedTier(): SubscriptionTier {
        val id = prefs.getString(KEY_TIER, SubscriptionTier.FREE.id) ?: SubscriptionTier.FREE.id
        return SubscriptionTier.fromId(id)
    }

    fun effectiveMaxDocuments(): Int {
        val subMax = _activeTier.value.maxDocuments
        val oneTimeMax = OneTimeSku.maxDocsForAmount(_lifetimeAmountUsd.value)
        return maxOf(subMax, oneTimeMax)
    }

    companion object {
        private const val PREFS_NAME = "docusnap_billing_prefs"
        private const val KEY_TIER = "key_active_tier"
        private const val KEY_LIFETIME_USD = "key_lifetime_usd"
        private const val KEY_BALANCE_CENTS = "key_balance_cents"
        const val COST_5X_CALL_CENTS = 5

        @Volatile
        private var instance: BillingManager? = null

        fun getInstance(context: Context): BillingManager {
            return instance ?: synchronized(this) {
                val b = BillingManager(context.applicationContext)
                instance = b
                b
            }
        }

        fun getMaxAllowedDocuments(context: Context): Int {
            return getInstance(context).effectiveMaxDocuments()
        }

        fun getMaxAllowedFileSizeMb(context: Context): Float {
            val bm = getInstance(context)
            val subMaxMb = bm.activeTier.value.maxFileSizeMb
            val lifetimeUsd = bm.lifetimeAmountUsd.value
            val oneTimeMaxMb = when {
                lifetimeUsd >= 499 -> 50.0f
                lifetimeUsd >= 299 -> 25.0f
                lifetimeUsd >= 199 -> 15.0f
                lifetimeUsd >= 99 -> 8.0f
                else -> 1.0f
            }
            return maxOf(subMaxMb, oneTimeMaxMb)
        }

        fun isAdFree(context: Context): Boolean {
            val bm = getInstance(context)
            return bm.activeTier.value.adFree || bm.lifetimeAmountUsd.value >= 99
        }
    }
}
