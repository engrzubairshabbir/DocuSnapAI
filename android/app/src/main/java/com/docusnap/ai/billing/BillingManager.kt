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
    val isPopular: Boolean = false,
    val features: List<String>
) {
    FREE(
        id = "docusnap_tier_free",
        title = "Free Starter",
        priceUsd = "$0.00",
        maxDocuments = 500,
        features = listOf(
            "Up to 500 offline documents",
            "100% on-device Google ML Kit OCR",
            "SQLite FTS5 instant search",
            "Standard categorization"
        )
    ),
    SILVER(
        id = "docusnap_sub_silver_299",
        title = "Silver Vault (Pro Organizer)",
        priceUsd = "$2.99 / mo",
        maxDocuments = 2000,
        features = listOf(
            "Up to 2,000 documents",
            "Unlimited Gemini Deep Reasoning",
            "Automatic Firebase Cloud Backup",
            "Auto-extract medicine dosages & totals",
            "Bilingual Roman Urdu / English queries"
        )
    ),
    GOLD(
        id = "docusnap_sub_gold_999",
        title = "Gold Vault (Clinic & Business)",
        priceUsd = "$9.99 / mo",
        maxDocuments = 5000,
        isPopular = true,
        features = listOf(
            "Up to 5,000 documents",
            "Fastest priority Gemini Flash reasoning",
            "Real-time sync to iPhone Safari & Desktop PWA",
            "Deep-zoom inspector & full OCR exporter",
            "Priority customer & billing support"
        )
    ),
    PLATINUM(
        id = "docusnap_sub_platinum_2999",
        title = "Platinum Master (Enterprise VIP)",
        priceUsd = "$29.99 / mo",
        maxDocuments = 10000,
        features = listOf(
            "Up to 10,000 documents",
            "High-capacity bulk gallery ingestion",
            "Multi-device cloud collaboration",
            "Dedicated Firestore storage vault",
            "Early access to new AI models"
        )
    ),
    ENTERPRISE(
        id = "docusnap_sub_enterprise_4999",
        title = "Infinity Diamond (Corporate Suite)",
        priceUsd = "$49.99 / mo",
        maxDocuments = 15000,
        features = listOf(
            "Up to 15,000 documents",
            "Maximum capacity for hospitals & offices",
            "Zero throttling on Gemini queries",
            "Full data export in JSON / CSV",
            "VIP 24/7 dedicated account manager"
        )
    );

    companion object {
        fun fromId(id: String): SubscriptionTier {
            return entries.firstOrNull { it.id == id } ?: FREE
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
            SubscriptionTier.SILVER.id,
            SubscriptionTier.GOLD.id,
            SubscriptionTier.PLATINUM.id,
            SubscriptionTier.ENTERPRISE.id
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

    private fun setTier(tier: SubscriptionTier) {
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
    }
}
