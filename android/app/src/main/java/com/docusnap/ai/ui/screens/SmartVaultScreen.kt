package com.docusnap.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.docusnap.ai.R
import com.docusnap.ai.billing.OneTimeSku
import com.docusnap.ai.data.model.DocumentEntity
import com.docusnap.ai.data.model.KeyEntities
import com.docusnap.ai.i18n.AppStrings
import com.docusnap.ai.service.MediaIngestionWorker
import com.docusnap.ai.service.MediaObserverService
import com.docusnap.ai.ui.DocuSnapViewModel
import com.docusnap.ai.ui.theme.*
import java.io.File

@Composable
fun SmartVaultScreen(
    viewModel: DocuSnapViewModel,
    onDocumentClick: (String) -> Unit,
    onNavigateToSubscription: () -> Unit = {}
) {
    val context = LocalContext.current
    val documents by viewModel.allDocuments.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val activeTier by viewModel.activeTier.collectAsState()
    val lifetimeUsd by viewModel.lifetimeAmountUsd.collectAsState()
    val effectiveMaxDocs = maxOf(activeTier.maxDocuments, OneTimeSku.maxDocsForAmount(lifetimeUsd))
    val currentLang by viewModel.currentLanguage.collectAsState()

    var showAddSourceDialog by remember { mutableStateOf(false) }

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
            Toast.makeText(context, "Camera error: ${e.message}", Toast.LENGTH_SHORT).show()
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

    // Choice Dialog: Camera or Gallery
    if (showAddSourceDialog) {
        AlertDialog(
            onDismissRequest = { showAddSourceDialog = false },
            title = {
                Text("Add Bill or Prescription", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            showAddSourceDialog = false
                            launchCamera()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Take Camera Photo", fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = {
                            showAddSourceDialog = false
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pick from Gallery / Photos", color = TextPrimary)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddSourceDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = SurfaceDark
        )
    }

    val categories = listOf("All", "Medical", "Receipts", "Utility", "Bank")

    Scaffold(
        containerColor = BgDark,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddSourceDialog = true },
                containerColor = AccentBlue,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Document")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Photo", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Screen Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = AppStrings.get(AppStrings.VAULT_TITLE, currentLang),
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${documents.size} " + AppStrings.get(AppStrings.VAULT_COUNT_SUBTITLE, currentLang),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }

                // Storage Tier Status Pill
                Surface(
                    color = CardDark,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (activeTier.isPopular) AccentAmber else BorderDark),
                    modifier = Modifier.clickable { onNavigateToSubscription() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = "Tier",
                            tint = if (activeTier.isPopular) AccentAmber else AccentCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "${documents.size}/$effectiveMaxDocs",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!hasFullMediaAccess) {
                Surface(
                    color = AccentCyan.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
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

            // Category Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { cat ->
                    val isSelected = selectedCategory == cat
                    Surface(
                        color = if (isSelected) AccentBlue else SurfaceDark,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) AccentBlue else BorderDark
                        ),
                        modifier = Modifier.clickable {
                            viewModel.setCategory(cat)
                        }
                    ) {
                        Text(
                            text = cat,
                            color = if (isSelected) Color.White else TextSecondary,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Documents Grid
            if (documents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No documents found",
                            color = TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Take a photo of a bill or prescription to auto-index",
                            color = TextMuted,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pick Photos", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.scanGallery()
                                    Toast.makeText(context, "Scanning gallery...", Toast.LENGTH_SHORT).show()
                                },
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Sync, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Scan Gallery", fontSize = 12.sp, color = TextPrimary)
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(documents, key = { it.id }) { doc ->
                        VaultGridItem(doc = doc, onClick = { onDocumentClick(doc.id) })
                    }
                }
            }

            // High-Revenue AdMob Banner for Free Users
            com.docusnap.ai.ads.AdMobBanner(billingManager = viewModel.billingManager)
        }
    }
}

@Composable
fun VaultGridItem(
    doc: DocumentEntity,
    onClick: () -> Unit
) {
    val keyEntities = remember(doc.keyEntitiesJson) {
        KeyEntities.fromJson(doc.keyEntitiesJson)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column {
            // Document Image Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(CardDark),
                contentAlignment = Alignment.Center
            ) {
                if (doc.localImageUri.isNotBlank() || !doc.cloudStorageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = doc.cloudStorageUrl ?: doc.localImageUri,
                        contentDescription = doc.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = painterResource(id = R.drawable.app_logo),
                        placeholder = painterResource(id = R.drawable.app_logo)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Sync badge on top right of image
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = if (doc.syncStatus == DocumentEntity.SYNC_STATUS_SYNCED) {
                            Icons.Default.CloudDone
                        } else {
                            Icons.Default.CloudQueue
                        },
                        contentDescription = null,
                        tint = if (doc.syncStatus == DocumentEntity.SYNC_STATUS_SYNCED) AccentEmerald else TextSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            // Info Section
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = doc.documentType,
                    color = when (doc.documentType) {
                        DocumentEntity.TYPE_MEDICAL -> AccentEmerald
                        DocumentEntity.TYPE_RECEIPT -> AccentAmber
                        DocumentEntity.TYPE_UTILITY -> AccentBlue
                        else -> TextMuted
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = doc.title.ifBlank { "Scanned Document" },
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Highlighted entities (Medicine names or Total amount)
                if (keyEntities.totalAmount != null) {
                    Text(
                        text = "Total: PKR ${keyEntities.totalAmount}",
                        color = AccentAmber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else if (keyEntities.medicines.isNotEmpty()) {
                    Text(
                        text = "Rx: ${keyEntities.medicines.first()}",
                        color = AccentEmerald,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = doc.dateDetected,
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

