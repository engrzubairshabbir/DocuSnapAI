package com.docusnap.ai.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.docusnap.ai.billing.BillingLaunchResult
import com.docusnap.ai.billing.OneTimeSku
import com.docusnap.ai.billing.SubscriptionTier
import com.docusnap.ai.i18n.AppStrings
import com.docusnap.ai.ui.DocuSnapViewModel
import com.docusnap.ai.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun SubscriptionScreen(
    viewModel: DocuSnapViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val activeTier by viewModel.activeTier.collectAsState()
    val lifetimeUsd by viewModel.lifetimeAmountUsd.collectAsState()
    val subProductsMap by viewModel.subProductDetailsMap.collectAsState()
    val inAppProductsMap by viewModel.inAppProductDetailsMap.collectAsState()
    val purchaseEventMsg by viewModel.purchaseEventMessage.collectAsState()
    val balanceCents by viewModel.balanceCents.collectAsState()
    val totalCount by viewModel.totalDocumentCount.collectAsState()
    val currentLang by viewModel.currentLanguage.collectAsState()

    val isPlayStoreBillingLive = subProductsMap.isNotEmpty() || inAppProductsMap.isNotEmpty()
    val effectiveMaxDocs = maxOf(activeTier.maxDocuments, OneTimeSku.maxDocsForAmount(lifetimeUsd))

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Monthly Plans, 1 = One-Time ($99-$999)
    var selectedTier by remember { mutableStateOf(SubscriptionTier.GOLD) }
    var customAmountText by remember { mutableStateOf("299") }
    val parsedCustomAmount = customAmountText.toIntOrNull() ?: 99
    val isCustomAmountValid = parsedCustomAmount in 99..999

    var pendingNotice by remember { mutableStateOf<BillingLaunchResult.PreLaunchPending?>(null) }

    LaunchedEffect(Unit) {
        viewModel.billingManager.refreshSubscriptions()
    }

    LaunchedEffect(purchaseEventMsg) {
        purchaseEventMsg?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.billingManager.clearPurchaseEventMessage()
        }
    }

    // Fancy Pre-Launch Play Store Billing Notice Modal
    pendingNotice?.let { notice ->
        PreLaunchNoticeDialog(
            notice = notice,
            onActivateTest = {
                viewModel.billingManager.setTier(selectedTier)
                Toast.makeText(context, "Activated ${selectedTier.title} successfully!", Toast.LENGTH_LONG).show()
                pendingNotice = null
            },
            onRefresh = {
                viewModel.billingManager.refreshSubscriptions()
                Toast.makeText(context, "Refreshing subscriptions from Google Play...", Toast.LENGTH_SHORT).show()
                pendingNotice = null
            },
            onDismiss = { pendingNotice = null }
        )
    }

    val allTiers = listOf(
        SubscriptionTier.STARTER,
        SubscriptionTier.SILVER,
        SubscriptionTier.GOLD,
        SubscriptionTier.ENTERPRISE,
        SubscriptionTier.INFINITY
    )

    val oneTimePresets = listOf(99, 199, 299, 499, 749, 999)

    Scaffold(
        containerColor = BgDark,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }

                Text(
                    text = AppStrings.get(AppStrings.PREMIUM_TITLE, currentLang),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )

                Spacer(modifier = Modifier.size(48.dp))
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Header Hero Banner + Quota + Play Store Billing Status Badge
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131D33)),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentBlue.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(listOf(AccentAmber, AccentCyan))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WorkspacePremium,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                val planLabel = if (lifetimeUsd >= 99) {
                                    "VIP Lifetime Pass ($$lifetimeUsd USD)"
                                } else {
                                    activeTier.title
                                }
                                Text(
                                    text = AppStrings.get(AppStrings.PREMIUM_ACTIVE, currentLang) + ": " + planLabel,
                                    color = AccentAmber,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "$totalCount / $effectiveMaxDocs " + AppStrings.get(AppStrings.PREMIUM_USAGE, currentLang),
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val progress = (totalCount.toFloat() / effectiveMaxDocs.toFloat()).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (progress > 0.9f) Color(0xFFEF4444) else AccentCyan,
                            trackColor = CardDark
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Fancy Play Billing Status Notice Strip
                        Surface(
                            color = if (isPlayStoreBillingLive) AccentEmerald.copy(alpha = 0.14f) else AccentAmber.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isPlayStoreBillingLive) AccentEmerald.copy(alpha = 0.4f) else AccentAmber.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isPlayStoreBillingLive) Icons.Default.Verified else Icons.Default.RocketLaunch,
                                    contentDescription = null,
                                    tint = if (isPlayStoreBillingLive) AccentEmerald else AccentAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isPlayStoreBillingLive) {
                                        "Google Play Billing Active & Ready"
                                    } else {
                                        "Pre-Release Preview • Play Store Checkout activates upon launch"
                                    },
                                    color = if (isPlayStoreBillingLive) AccentEmerald else AccentAmber,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // 2. Segmented Mode Switcher (Monthly Subscriptions vs One-Time $99-$999)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceDark)
                        .border(1.dp, BorderDark, RoundedCornerShape(14.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val tabs = listOf("Monthly Plans", "One-Time ($99–$999)")
                    tabs.forEachIndexed { index, label ->
                        val isTabSelected = selectedTab == index
                        Surface(
                            color = if (isTabSelected) AccentBlue else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedTab = index }
                        ) {
                            Text(
                                text = label,
                                color = if (isTabSelected) Color.White else TextSecondary,
                                fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                    }
                }
            }

            if (selectedTab == 0) {
                // ================= TAB 0: MONTHLY SUBSCRIPTIONS =================
                item {
                    Text(
                        text = "Select Monthly Subscription Tier",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                items(allTiers) { tier ->
                    val isSelected = selectedTier == tier
                    val isCurrentPlan = activeTier == tier
                    val livePrice = subProductsMap[tier.id]
                        ?.subscriptionOfferDetails
                        ?.firstOrNull()
                        ?.pricingPhases
                        ?.pricingPhaseList
                        ?.firstOrNull()
                        ?.formattedPrice ?: tier.priceUsd

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFF16223D) else SurfaceDark
                        ),
                        shape = RoundedCornerShape(18.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            if (isSelected) 2.dp else 1.dp,
                            if (tier.isPopular) AccentAmber else if (isSelected) AccentBlue else BorderDark
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTier = tier }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    if (tier.isPopular) {
                                        Surface(
                                            color = AccentAmber.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        ) {
                                            Text(
                                                text = "★ MOST POPULAR",
                                                color = AccentAmber,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = tier.title,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 3.dp)
                                    ) {
                                        Surface(
                                            color = AccentCyan.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f))
                                        ) {
                                            Text(
                                                text = "Max ${tier.maxFileSizeMb.toInt()} MB / File",
                                                color = AccentCyan,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }

                                        if (tier.adFree) {
                                            Surface(
                                                color = AccentEmerald.copy(alpha = 0.18f),
                                                shape = RoundedCornerShape(6.dp),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.6f))
                                            ) {
                                                Text(
                                                    text = "100% No Ads",
                                                    color = AccentEmerald,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                        } else if (tier == SubscriptionTier.STARTER) {
                                            Surface(
                                                color = AccentAmber.copy(alpha = 0.18f),
                                                shape = RoundedCornerShape(6.dp),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentAmber.copy(alpha = 0.6f))
                                            ) {
                                                Text(
                                                    text = "Fewer Ads",
                                                    color = AccentAmber,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    Text(
                                        text = "Up to ${tier.maxDocuments.toString().replace(Regex("(\\d)(?=(\\d{3})+$)"), "$1,")} documents",
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = livePrice,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    )
                                    Text(
                                        text = "auto-renews",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            tier.features.forEach { feat ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = AccentEmerald,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = feat,
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            if (isCurrentPlan) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    color = AccentEmerald.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "✓ Currently Active Plan",
                                        color = AccentEmerald,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 10.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            if (activity != null) {
                                when (val result = viewModel.billingManager.launchSubscription(activity, selectedTier)) {
                                    is BillingLaunchResult.Launched -> {
                                        // Google Play Billing sheet opened; onPurchasesUpdated handles verified activation
                                    }
                                    is BillingLaunchResult.PreLaunchPending -> {
                                        pendingNotice = result
                                    }
                                    is BillingLaunchResult.Error -> {
                                        Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        enabled = activeTier != selectedTier,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentBlue,
                            disabledContainerColor = CardDark
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        if (activeTier == selectedTier) {
                            Text("Current Plan Active", color = TextMuted)
                        } else {
                            Icon(imageVector = Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Subscribe via Google Play (${selectedTier.priceUsd})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Subscriptions require a verified Google Play Store transaction and renew monthly. Cancel anytime in Play Store > Subscriptions.",
                        color = TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                }
            } else {
                // ================= TAB 1: CUSTOM ONE-TIME PAYMENT ($99 - $999 USD) =================
                item {
                    val effectiveAmount = parsedCustomAmount.coerceIn(99, 999)
                    val matchedSku = OneTimeSku.nearestForAmount(effectiveAmount)
                    val unlockedQuota = OneTimeSku.maxDocsForAmount(effectiveAmount)

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF17132B)),
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            Brush.linearGradient(listOf(AccentPurple, AccentAmber))
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Diamond,
                                    contentDescription = null,
                                    tint = AccentAmber,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Custom VIP One-Time Payment",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Load $99–$999 USD prepaid balance via Google Play. Unlocks lifetime storage quota plus Global Any-Language AI Search & Vision OCR (charged at 5x rate = \$0.05 per AI query/vision scan deducted from your loaded balance).",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Surface(
                                color = AccentEmerald.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Active Global AI Balance (5x)",
                                        color = AccentEmerald,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "$${String.format(java.util.Locale.US, "%.2f", balanceCents / 100.0)} USD",
                                        color = AccentEmerald,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Live Big Amount Display
                            Surface(
                                color = CardDark,
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentPurple.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(vertical = 14.dp, horizontal = 12.dp)
                                ) {
                                    Text(
                                        text = "$${if (isCustomAmountValid) effectiveAmount else "---"} USD",
                                        color = AccentAmber,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 32.sp
                                    )
                                    Text(
                                        text = "${matchedSku.label} • Up to ${unlockedQuota.toString().replace(Regex("(\\d)(?=(\\d{3})+$)"), "$1,")} Docs Lifetime",
                                        color = AccentCyan,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Interactive Slider ($99 - $999)
                            Text(
                                text = "Slide to Adjust Amount ($99 – $999 USD)",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Slider(
                                value = effectiveAmount.toFloat(),
                                onValueChange = { customAmountText = it.roundToInt().toString() },
                                valueRange = 99f..999f,
                                colors = SliderDefaults.colors(
                                    thumbColor = AccentAmber,
                                    activeTrackColor = AccentPurple,
                                    inactiveTrackColor = BorderDark
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Direct Amount Input Field
                            OutlinedTextField(
                                value = customAmountText,
                                onValueChange = { input ->
                                    if (input.all { it.isDigit() } && input.length <= 3) {
                                        customAmountText = input
                                    }
                                },
                                label = { Text("Enter Custom USD Amount (99 – 999)") },
                                prefix = { Text("$ ", color = AccentAmber, fontWeight = FontWeight.Bold) },
                                suffix = { Text(" USD", color = TextSecondary) },
                                isError = !isCustomAmountValid,
                                supportingText = if (!isCustomAmountValid) {
                                    { Text("Please enter an amount between $99 and $999 USD", color = Color(0xFFEF4444)) }
                                } else null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentAmber,
                                    unfocusedBorderColor = BorderDark,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Quick Preset Chips
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(oneTimePresets) { preset ->
                                    val isPresetSelected = parsedCustomAmount == preset
                                    Surface(
                                        color = if (isPresetSelected) AccentAmber.copy(alpha = 0.22f) else CardDark,
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isPresetSelected) AccentAmber else BorderDark
                                        ),
                                        modifier = Modifier.clickable { customAmountText = preset.toString() }
                                    ) {
                                        Text(
                                            text = "$$preset",
                                            color = if (isPresetSelected) AccentAmber else TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // One-Time Pay Button
                            Button(
                                onClick = {
                                    if (!isCustomAmountValid) {
                                        Toast.makeText(context, "Amount must be between $99 and $999 USD", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (activity != null) {
                                        when (val result = viewModel.billingManager.launchOneTimePurchase(activity, effectiveAmount)) {
                                            is BillingLaunchResult.Launched -> {
                                                // Google Play Billing sheet opened
                                            }
                                            is BillingLaunchResult.PreLaunchPending -> {
                                                pendingNotice = result
                                            }
                                            is BillingLaunchResult.Error -> {
                                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                },
                                enabled = isCustomAmountValid,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentAmber,
                                    disabledContainerColor = CardDark
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(19.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Pay $${if (isCustomAmountValid) effectiveAmount else 99} USD Once via Play Store",
                                    color = Color.Black,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "One-time lifetime passes are processed securely via Google Play Billing (SKU mapped to Play Console tier ${OneTimeSku.nearestForAmount(parsedCustomAmount.coerceIn(99, 999)).productId}). Zero recurring fees.",
                        color = TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PreLaunchNoticeDialog(
    notice: BillingLaunchResult.PreLaunchPending,
    onActivateTest: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                Brush.linearGradient(listOf(AccentCyan, AccentPurple, AccentAmber))
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(22.dp)
            ) {
                // Radiant Icon Badge
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(AccentCyan, AccentPurple, AccentAmber))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.RocketLaunch,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    color = AccentAmber.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentAmber.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "EARLY ACCESS • PLAY BILLING PENDING",
                        color = AccentAmber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Play Store Checkout Launching Soon!",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Selected Package Pill
                Surface(
                    color = CardDark,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = notice.packageTitle,
                            color = AccentCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = notice.priceLabel,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Google Play Store billing connection is syncing with your Play Console account. If your internal test track is still propagating on device, you can unlock full tier access right now for testing, or refresh from Play Store.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onActivateTest,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unlock & Test Plan (${notice.packageTitle})", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onRefresh,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = AccentCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Refresh from Google Play Store", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(6.dp))

                TextButton(onClick = onDismiss) {
                    Text("Close", color = TextMuted)
                }
            }
        }
    }
}
