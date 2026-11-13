package com.docusnap.ai.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.docusnap.ai.billing.BillingManager
import com.docusnap.ai.billing.SubscriptionTier
import com.docusnap.ai.ui.theme.*
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import java.util.concurrent.atomic.AtomicBoolean

object AdMobManager {

    private const val TAG = "DocuSnapAdMob"

    // Official AdMob Unit IDs configured for DocuSnap AI
    const val APP_ID = "ca-app-pub-5217980438195636~2144327818"
    const val BANNER_AD_UNIT_ID = "ca-app-pub-5217980438195636/7675856099"
    const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-5217980438195636/5526324572"
    const val NATIVE_AD_UNIT_ID = "ca-app-pub-5217980438195636/2064436767"

    private val isInitialized = AtomicBoolean(false)
    private var interstitialAd: InterstitialAd? = null
    private var isInterstitialLoading = false
    private var lastInterstitialShownTime = 0L

    // Minimum cooldown in milliseconds between interstitial ads to comply with AdMob policy & great UX
    private const val INTERSTITIAL_COOLDOWN_MS = 60_000L // 60 seconds

    fun initialize(context: Context) {
        if (isInitialized.compareAndSet(false, true)) {
            try {
                MobileAds.initialize(context) { initializationStatus ->
                    Log.d(TAG, "AdMob SDK Initialized: $initializationStatus")
                    loadInterstitial(context.applicationContext)
                }
            } catch (e: Exception) {
                Log.w(TAG, "AdMob init deferred: ${e.message}")
            }
        }
    }

    /**
     * Check if ads should be shown.
     * Premium subscribers (Silver, Gold, Platinum, Enterprise) enjoy a 100% ad-free experience.
     */
    fun shouldShowAds(billingManager: BillingManager?): Boolean {
        if (billingManager == null) return true
        val tier = billingManager.activeTier.value
        val hasLifetime = billingManager.lifetimeAmountUsd.value >= 99
        return tier == SubscriptionTier.FREE && !hasLifetime
    }

    /**
     * Preloads Interstitial Ad for instant seamless presentation at natural pause points
     */
    fun loadInterstitial(context: Context) {
        if (interstitialAd != null || isInterstitialLoading) return
        isInterstitialLoading = true

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            INTERSTITIAL_AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isInterstitialLoading = false
                    Log.d(TAG, "AdMob Interstitial loaded successfully.")

                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            interstitialAd = null
                            Log.d(TAG, "Interstitial dismissed. Preloading next one...")
                            loadInterstitial(context)
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            interstitialAd = null
                            isInterstitialLoading = false
                            Log.w(TAG, "Interstitial failed to show: ${adError.message}")
                            loadInterstitial(context)
                        }

                        override fun onAdShowedFullScreenContent() {
                            lastInterstitialShownTime = System.currentTimeMillis()
                        }
                    }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                    isInterstitialLoading = false
                    Log.w(TAG, "Interstitial failed to load: ${loadAdError.message}")
                }
            }
        )
    }

    /**
     * Shows interstitial ad only if ready, user is on free tier, and cooldown has elapsed.
     */
    fun showInterstitial(
        activity: Activity,
        billingManager: BillingManager?,
        onDismissed: () -> Unit = {}
    ) {
        if (!shouldShowAds(billingManager)) {
            onDismissed()
            return
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastInterstitialShownTime < INTERSTITIAL_COOLDOWN_MS) {
            Log.d(TAG, "Interstitial skipped due to policy cooldown.")
            onDismissed()
            return
        }

        val ad = interstitialAd
        if (ad != null) {
            val oldCallback = ad.fullScreenContentCallback
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    oldCallback?.onAdDismissedFullScreenContent()
                    onDismissed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    oldCallback?.onAdFailedToShowFullScreenContent(adError)
                    onDismissed()
                }

                override fun onAdShowedFullScreenContent() {
                    oldCallback?.onAdShowedFullScreenContent()
                }
            }
            ad.show(activity)
        } else {
            loadInterstitial(activity.applicationContext)
            onDismissed()
        }
    }
}

/**
 * High-Visibility Glowing Neon AdMob Banner Composable
 */
@Composable
fun AdMobBanner(
    modifier: Modifier = Modifier,
    billingManager: BillingManager? = null
) {
    if (!AdMobManager.shouldShowAds(billingManager)) return

    val animatedBorder = rememberAnimatedNeonBorder(durationMillis = 4000)

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, animatedBorder),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            // Subtle compliant Sponsor / Ad disclosure
            Text(
                text = "SPONSORED PARTNER",
                color = AccentCyan.copy(alpha = 0.7f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 2.dp)
            )

            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { context ->
                    AdView(context).apply {
                        setAdSize(AdSize.BANNER)
                        adUnitId = AdMobManager.BANNER_AD_UNIT_ID
                        loadAd(AdRequest.Builder().build())
                    }
                }
            )
        }
    }
}

/**
 * Premium Glowing Native Advanced Ad Card
 */
@Composable
fun AdMobNativeCard(
    modifier: Modifier = Modifier,
    billingManager: BillingManager? = null
) {
    if (!AdMobManager.shouldShowAds(billingManager)) return

    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    val animatedBorder = rememberAnimatedNeonBorder(durationMillis = 3500)

    DisposableEffect(Unit) {
        val adLoader = AdLoader.Builder(context, AdMobManager.NATIVE_AD_UNIT_ID)
            .forNativeAd { ad ->
                nativeAd = ad
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w("DocuSnapAdMob", "Native Ad failed: ${error.message}")
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .build()
            )
            .build()

        adLoader.loadAd(AdRequest.Builder().build())

        onDispose {
            nativeAd?.destroy()
        }
    }

    val ad = nativeAd ?: return

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, animatedBorder),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            factory = { ctx ->
                NativeAdView(ctx).apply {
                    val rootLayout = android.widget.LinearLayout(ctx).apply {
                        orientation = android.widget.LinearLayout.VERTICAL
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }

                    // Top Bar: "Ad" badge + Advertiser
                    val topRow = android.widget.LinearLayout(ctx).apply {
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                    }

                    val adBadge = TextView(ctx).apply {
                        text = "AD"
                        setTextColor(android.graphics.Color.BLACK)
                        textSize = 10f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        setBackgroundColor(android.graphics.Color.parseColor("#00F5FF"))
                        setPadding(12, 4, 12, 4)
                    }

                    val headlineView = TextView(ctx).apply {
                        setTextColor(android.graphics.Color.WHITE)
                        textSize = 14f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        setPadding(16, 0, 0, 0)
                        maxLines = 1
                    }

                    topRow.addView(adBadge)
                    topRow.addView(headlineView)
                    rootLayout.addView(topRow)

                    // Body
                    val bodyView = TextView(ctx).apply {
                        setTextColor(android.graphics.Color.parseColor("#94A3B8"))
                        textSize = 12f
                        setPadding(0, 10, 0, 12)
                        maxLines = 2
                    }
                    rootLayout.addView(bodyView)

                    // CTA Button with Glowing Neon styling
                    val ctaButton = Button(ctx).apply {
                        setTextColor(android.graphics.Color.BLACK)
                        textSize = 12f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        setBackgroundColor(android.graphics.Color.parseColor("#00F5FF"))
                    }
                    rootLayout.addView(ctaButton)

                    this.headlineView = headlineView
                    this.bodyView = bodyView
                    this.callToActionView = ctaButton

                    addView(rootLayout)
                }
            },
            update = { view ->
                (view.headlineView as? TextView)?.text = ad.headline
                (view.bodyView as? TextView)?.text = ad.body ?: ""
                (view.callToActionView as? Button)?.text = ad.callToAction ?: "Learn More"
                view.setNativeAd(ad)
            }
        )
    }
}
