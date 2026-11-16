package com.docusnap.ai.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docusnap.ai.billing.OneTimeSku
import com.docusnap.ai.billing.SubscriptionTier
import com.docusnap.ai.i18n.AppLanguage
import com.docusnap.ai.i18n.AppStrings
import com.docusnap.ai.ui.DocuSnapViewModel
import com.docusnap.ai.ui.theme.*
import com.docusnap.ai.util.ShareHelper

@Composable
fun SettingsScreen(
    viewModel: DocuSnapViewModel,
    onNavigateToSubscription: () -> Unit = {}
) {
    val context = LocalContext.current
    val deepReasoning by viewModel.deepReasoningEnabled.collectAsState()
    val currentUser by viewModel.syncRepo.currentUserFlow.collectAsState()
    val activeTier by viewModel.activeTier.collectAsState()
    val lifetimeUsd by viewModel.lifetimeAmountUsd.collectAsState()
    val effectiveMaxDocs = maxOf(activeTier.maxDocuments, OneTimeSku.maxDocsForAmount(lifetimeUsd))
    val totalCount by viewModel.totalDocumentCount.collectAsState()
    val currentLang by viewModel.currentLanguage.collectAsState()

    var showLanguageDialog by remember { mutableStateOf(false) }

    var isBatteryIgnored by remember {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName))
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (!idToken.isNullOrBlank()) {
                    viewModel.signInWithGoogleToken(idToken) { success, errorMsg ->
                        if (success) {
                            Toast.makeText(context, "Welcome ${account.displayName ?: account.email}! Web sync active.", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Sign-in notice: $errorMsg", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(context, "Signed in as ${account?.email}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Sign-In: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = {
                Text(
                    text = "Select Language / زبان منتخب کریں",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        val isSelected = currentLang == lang
                        Surface(
                            color = if (isSelected) AccentBlue.copy(alpha = 0.2f) else CardDark,
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) AccentBlue else BorderDark
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setLanguage(lang)
                                    showLanguageDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = lang.flag, fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = lang.nativeName,
                                        color = TextPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 15.sp
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = AccentCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("Close", color = AccentBlue)
                }
            },
            containerColor = SurfaceDark
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Settings & Privacy",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Configure AI engine, background ingestion & cloud sync",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(20.dp))

        // TOP HERO: GOOGLE ACCOUNT & WEB LINK
        Text(
            text = "GOOGLE ACCOUNT & DESKTOP SYNC",
            color = AccentCyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp, 
                if (currentUser != null && !currentUser!!.isAnonymous) AccentEmerald.copy(alpha = 0.8f) else AccentCyan.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (currentUser != null && !currentUser!!.isAnonymous) AccentEmerald.copy(alpha = 0.2f) else AccentBlue.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (currentUser != null && !currentUser!!.isAnonymous) Icons.Default.CheckCircle else Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = if (currentUser != null && !currentUser!!.isAnonymous) AccentEmerald else AccentBlue,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (currentUser != null && !currentUser!!.isAnonymous) "Google Account Connected" else "Link Google Account",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (currentUser != null && !currentUser!!.isAnonymous) AccentEmerald else AccentAmber)
                            )
                        }
                        Text(
                            text = if (currentUser != null && !currentUser!!.isAnonymous) {
                                currentUser!!.email ?: "Active Google Session"
                            } else {
                                "Connect to sync with docusnap.consultways.com"
                            },
                            color = if (currentUser != null && !currentUser!!.isAnonymous) AccentEmerald else TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Explanatory Sync Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardDark)
                        .padding(12.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SyncAlt,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "WhatsApp Web-Style Desktop Search",
                                color = AccentCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Search all phone OCR text, bills & prescriptions from your laptop at docusnap.consultways.com. Your login persists permanently.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (currentUser == null || currentUser!!.isAnonymous) {
                    Button(
                        onClick = {
                            val gsoBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .requestEmail()
                            val webClientId = try {
                                val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                                if (resId != 0) context.getString(resId) else "638866510492-ufh87nu6ams6jfhjgkarm4il2hj6943q.apps.googleusercontent.com"
                            } catch (_: Exception) {
                                "638866510492-ufh87nu6ams6jfhjgkarm4il2hj6943q.apps.googleusercontent.com"
                            }
                            gsoBuilder.requestIdToken(webClientId)
                            val client = GoogleSignIn.getClient(context, gsoBuilder.build())
                            googleSignInLauncher.launch(client.signInIntent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.AccountCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign in with Google", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                viewModel.signOut()
                                Toast.makeText(context, "Signed out of Google account", Toast.LENGTH_SHORT).show()
                            },
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Sign Out", color = TextSecondary, fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                viewModel.syncAllDocumentsToWeb {
                                    Toast.makeText(context, "All gallery documents pushed to Web Vault!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.5f)
                        ) {
                            Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Push All Docs", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://docusnap.consultways.com"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open browser: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Web Portal (docusnap.consultways.com)", color = AccentCyan, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SUBSCRIPTION TIER & LIMITS
        Text(
            text = AppStrings.get(AppStrings.SETTINGS_SUBSCRIPTION, currentLang).uppercase(),
            color = AccentAmber,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (activeTier.isPopular) AccentAmber else BorderDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (lifetimeUsd >= 99) "VIP Lifetime Pass ($$lifetimeUsd USD)" else activeTier.title,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "$totalCount / $effectiveMaxDocs documents used (${if (lifetimeUsd >= 99) "Lifetime" else activeTier.priceUsd})",
                            color = AccentCyan,
                            fontSize = 12.sp
                        )
                    }

                    Button(
                        onClick = { onNavigateToSubscription() },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("Upgrade", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // APP LANGUAGE
        Text(
            text = AppStrings.get(AppStrings.SETTINGS_LANGUAGE, currentLang).uppercase(),
            color = AccentCyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showLanguageDialog = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = currentLang.flag, fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = currentLang.nativeName,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Tap to switch to Urdu, Arabic, Spanish, French, etc.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // AI REASONING PREFERENCE
        Text(
            text = "AI ENGINE & PRIVACY",
            color = AccentBlue,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (deepReasoning) "AI-Powered Deep Reasoning" else "On-Device OCR Only",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = if (deepReasoning) {
                                "Uses Gemini Flash for structured entities, summaries, and conversational search."
                            } else {
                                "Runs 100% on-device via ML Kit. Zero cloud latency and absolute local privacy."
                            },
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }

                    Switch(
                        checked = deepReasoning,
                        onCheckedChange = { viewModel.toggleDeepReasoning(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AccentBlue,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = CardDark
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // BACKGROUND ZERO-CLICK INGESTION & BATTERY
        Text(
            text = "ZERO-CLICK BACKGROUND INGESTION",
            color = AccentEmerald,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isBatteryIgnored) AccentEmerald.copy(alpha = 0.2f) else AccentAmber.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isBatteryIgnored) Icons.Default.BatteryChargingFull else Icons.Default.BatteryAlert,
                            contentDescription = null,
                            tint = if (isBatteryIgnored) AccentEmerald else AccentAmber,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Battery Optimization",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (isBatteryIgnored) "Unrestricted (Instant camera ingestion active)" else "Optimized (OS may delay background scans)",
                            color = if (isBatteryIgnored) AccentEmerald else AccentAmber,
                            fontSize = 12.sp
                        )
                    }
                }

                if (!isBatteryIgnored) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Disable Battery Optimization for Zero-Click Ingestion")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // INVITE FRIENDS & SHARE APP
        Text(
            text = "SHARE & INVITE",
            color = AccentEmerald,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Love DocuSnap AI?",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Share DocuSnap AI with friends & family so they can organize medical prescriptions, utility bills & receipts automatically.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { ShareHelper.shareAppInvite(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share Play Store Link", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        Text(
            text = "DocuSnap AI v1.0.0 (Build 2026.11)\nDual-Ecosystem Bill & Rx Finder Engine",
            color = TextMuted,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
    }
}

