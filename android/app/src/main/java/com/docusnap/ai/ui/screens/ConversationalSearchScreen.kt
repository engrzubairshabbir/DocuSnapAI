package com.docusnap.ai.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import androidx.compose.foundation.Image
import java.io.File
import java.util.Locale
import com.docusnap.ai.i18n.AppLanguage
import com.docusnap.ai.i18n.AppStrings
import com.docusnap.ai.R
import com.docusnap.ai.data.model.DocumentEntity
import com.docusnap.ai.service.MediaIngestionWorker
import com.docusnap.ai.service.MediaObserverService
import com.docusnap.ai.ui.DocuSnapViewModel
import com.docusnap.ai.ui.theme.*

@Composable
fun ConversationalSearchScreen(
    viewModel: DocuSnapViewModel,
    onDocumentClick: (String) -> Unit,
    onNavigateToSubscription: () -> Unit = {}
) {
    val context = LocalContext.current
    val searchState by viewModel.searchState.collectAsState()
    val currentLang by viewModel.currentLanguage.collectAsState()
    val balanceCents by viewModel.balanceCents.collectAsState()
    var inputQuery by remember { mutableStateOf("") }
    var showLanguageDialog by remember { mutableStateOf(false) }

    var hasFullMediaAccess by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val fullMediaPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasFullMediaAccess = granted
        if (granted) {
            MediaObserverService.start(context)
            MediaObserverService.scheduleContentUriTrigger(context)
            MediaIngestionWorker.enqueueScan(context, scanRecentLimit = 30)
            Toast.makeText(context, "Auto-Screenshot capture enabled!", Toast.LENGTH_SHORT).show()
        }
    }

    // Direct Camera Capture
    var cameraTempUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraTempUri != null) {
            viewModel.ingestSelectedUris(listOf(cameraTempUri!!), isCameraCapture = true)
            Toast.makeText(context, "Scanning camera document...", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchCamera() {
        try {
            val file = File(context.cacheDir, "camera_snap_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraTempUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(context, "Camera launch error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Photo Picker
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.ingestSelectedUris(uris)
            Toast.makeText(context, "Scanning ${uris.size} document(s)...", Toast.LENGTH_SHORT).show()
        }
    }

    // Language Selector Dialog
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = {
                Text(
                    text = AppStrings.get(AppStrings.LANGUAGE_TITLE, currentLang),
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

    val exampleQueries = remember(currentLang) {
        AppStrings.getExampleQueries(currentLang)
    }

    val animatedNeonBorder = rememberAnimatedNeonBorder(durationMillis = 3500)
    val animatedNeonBrush = rememberAnimatedNeonBrush(durationMillis = 5000)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header Title & Language / Upgrade Badges
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "DocuSnap AI Logo",
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = AppStrings.get(AppStrings.APP_TITLE, currentLang),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = AppStrings.get(AppStrings.APP_SUBTITLE, currentLang),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }
            }

            // Top Right Badges (Language Selector & Premium Button)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Language Pill
                Surface(
                    color = CardDark,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                    modifier = Modifier.clickable { showLanguageDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = currentLang.flag, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = currentLang.code.uppercase(),
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                // 5x Global AI Wallet Balance / Upgrade Pill
                Surface(
                    color = if (balanceCents >= 5) AccentEmerald.copy(alpha = 0.14f) else AccentAmber.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (balanceCents >= 5) AccentEmerald.copy(alpha = 0.45f) else AccentAmber.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.clickable { onNavigateToSubscription() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (balanceCents >= 5) Icons.Default.AccountBalanceWallet else Icons.Default.WorkspacePremium,
                            contentDescription = "AI Wallet / Upgrade",
                            tint = if (balanceCents >= 5) AccentEmerald else AccentAmber,
                            modifier = Modifier.size(15.dp)
                        )
                        if (balanceCents > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$${String.format(Locale.US, "%.2f", balanceCents / 100.0)}",
                                color = AccentEmerald,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        if (!hasFullMediaAccess) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                color = AccentCyan.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Manifest.permission.READ_MEDIA_IMAGES
                        } else {
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        }
                        fullMediaPermLauncher.launch(perm)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Screenshot,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = AppStrings.get(AppStrings.AUTO_SCREENSHOT_BANNER, currentLang),
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Conversational Search Input Box with Animated Neon Glow
        OutlinedTextField(
            value = inputQuery,
            onValueChange = { inputQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, animatedNeonBorder, RoundedCornerShape(16.dp)),
            placeholder = {
                Text(
                    text = AppStrings.get(AppStrings.SEARCH_PLACEHOLDER, currentLang),
                    color = TextMuted,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = AccentBlue
                )
            },
            trailingIcon = {
                if (inputQuery.isNotBlank()) {
                    IconButton(onClick = {
                        viewModel.performConversationalSearch(inputQuery)
                    }) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Run AI Search",
                            tint = AccentCyan
                        )
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceDark,
                unfocusedContainerColor = SurfaceDark,
                focusedBorderColor = AccentBlue,
                unfocusedBorderColor = BorderDark,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Example Prompts Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(exampleQueries) { example ->
                Surface(
                    color = CardDark,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                    modifier = Modifier.clickable {
                        inputQuery = example
                        viewModel.performConversationalSearch(example)
                    }
                ) {
                    Text(
                        text = example,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Quick 3-Way Action Bar (Camera, Photos, Gallery Scan)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Camera Scan Button
            Button(
                onClick = { launchCamera() },
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1.1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 9.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = AppStrings.get(AppStrings.BTN_CAMERA_SCAN, currentLang),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }

            // Pick Photos Button
            OutlinedButton(
                onClick = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 9.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = AppStrings.get(AppStrings.BTN_PICK_PHOTOS, currentLang),
                    fontSize = 12.sp,
                    color = TextPrimary,
                    maxLines = 1
                )
            }

            // Scan Recent Gallery Button
            OutlinedButton(
                onClick = {
                    viewModel.scanGallery()
                    Toast.makeText(context, "Scanning recent gallery photos...", Toast.LENGTH_SHORT).show()
                },
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Scan Gallery",
                    tint = AccentEmerald,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Results Section
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Loading Indicator
            if (searchState.isSearching) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = AccentCyan,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = "DocuSnap is reasoning over your documents...",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // Conversational Answer Box with Glowing Neon Border
            searchState.aiAnswer?.let { answer ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1A2F)),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, animatedNeonBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "AI Answer",
                                    color = AccentCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = answer,
                                color = TextPrimary,
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            )

                            if (searchState.matchedDocuments.isEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        photoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddPhotoAlternate,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pick Prescriptions / Bills to Index", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Matched Document Cards
            if (searchState.matchedDocuments.isNotEmpty()) {
                item {
                    Text(
                        text = "Source Documents (${searchState.matchedDocuments.size})",
                        color = TextSecondary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(searchState.matchedDocuments) { doc ->
                    DocumentCardItem(doc = doc, onClick = { onDocumentClick(doc.id) })
                }
            }

            // High-Revenue AdMob Banner for Free Users
            item {
                Spacer(modifier = Modifier.height(6.dp))
                com.docusnap.ai.ads.AdMobBanner(billingManager = viewModel.billingManager)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun DocumentCardItem(
    doc: DocumentEntity,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail image / icon
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CardDark),
                contentAlignment = Alignment.Center
            ) {
                if (doc.localImageUri.isNotBlank()) {
                    AsyncImage(
                        model = doc.cloudStorageUrl ?: doc.localImageUri,
                        contentDescription = doc.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Category pill
                    Surface(
                        color = when (doc.documentType) {
                            DocumentEntity.TYPE_MEDICAL -> AccentEmerald.copy(alpha = 0.15f)
                            DocumentEntity.TYPE_RECEIPT -> AccentAmber.copy(alpha = 0.15f)
                            DocumentEntity.TYPE_UTILITY -> AccentBlue.copy(alpha = 0.15f)
                            else -> CardDark
                        },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = doc.documentType,
                            color = when (doc.documentType) {
                                DocumentEntity.TYPE_MEDICAL -> AccentEmerald
                                DocumentEntity.TYPE_RECEIPT -> AccentAmber
                                DocumentEntity.TYPE_UTILITY -> AccentBlue
                                else -> TextSecondary
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Sync status icon
                    Icon(
                        imageVector = if (doc.syncStatus == DocumentEntity.SYNC_STATUS_SYNCED) {
                            Icons.Default.CloudDone
                        } else {
                            Icons.Default.CloudSync
                        },
                        contentDescription = doc.syncStatus,
                        tint = if (doc.syncStatus == DocumentEntity.SYNC_STATUS_SYNCED) AccentEmerald else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = doc.title.ifBlank { "Scanned Document" },
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1
                )

                Text(
                    text = doc.aiSummary.ifBlank { doc.rawOcrText.take(80) },
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = doc.dateDetected,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

