package com.docusnap.ai.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.docusnap.ai.data.model.DocumentEntity
import com.docusnap.ai.data.model.KeyEntities
import com.docusnap.ai.ui.DocuSnapViewModel
import com.docusnap.ai.ui.theme.*
import com.docusnap.ai.util.ShareHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DocumentInspectorScreen(
    documentId: String,
    viewModel: DocuSnapViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var document by remember { mutableStateOf<DocumentEntity?>(null) }
    var showRawOcr by remember { mutableStateOf(false) }
    var showAddTagDialog by remember { mutableStateOf(false) }

    // Fetch document from database
    LaunchedEffect(documentId) {
        val app = context.applicationContext as com.docusnap.ai.DocuSnapApplication
        document = app.database.documentDao().getDocumentById(documentId)
    }

    val doc = document
    if (doc == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDark),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = AccentCyan)
        }
        return
    }

    val keyEntities = remember(doc.keyEntitiesJson) {
        KeyEntities.fromJson(doc.keyEntitiesJson)
    }

    // Helper functions for tags
    fun parseCustomTags(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val item = arr.optString(i).trim()
                if (item.isNotEmpty()) list.add(item)
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    val customTags = remember(doc.searchableTagsJson) {
        parseCustomTags(doc.searchableTagsJson)
    }

    fun saveUpdatedTags(newTags: List<String>) {
        val jsonArr = JSONArray()
        newTags.distinct().forEach { jsonArr.put(it) }
        val newJson = jsonArr.toString()
        val updated = doc.copy(searchableTagsJson = newJson)
        document = updated
        coroutineScope.launch(Dispatchers.IO) {
            val app = context.applicationContext as com.docusnap.ai.DocuSnapApplication
            app.database.documentDao().updateDocument(updated)
        }
    }

    // Zoomable Image state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        if (scale > 1f) {
            offset += offsetChange
        } else {
            offset = Offset.Zero
        }
    }

    val animatedNeonBorder = rememberAnimatedNeonBorder(durationMillis = 3500)

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
                    text = doc.title.ifBlank { "Document Inspector" },
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        ShareHelper.shareFullResolutionDocument(context, doc)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Document",
                            tint = AccentCyan
                        )
                    }

                    IconButton(onClick = {
                        viewModel.deleteDocument(doc.id)
                        Toast.makeText(context, "Document deleted", Toast.LENGTH_SHORT).show()
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = Color(0xFFFF3366)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Deep-Zoomable Image Box with Animated Neon Glowing Border
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceDark)
                    .border(1.5.dp, animatedNeonBorder, RoundedCornerShape(18.dp))
                    .transformable(state = transformState),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = doc.localImageUri.ifBlank { doc.cloudStorageUrl },
                    contentDescription = "Document Image",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        ),
                    contentScale = ContentScale.Fit
                )

                // Zoom hint badge
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (scale > 1f) "${"%.1f".format(scale)}x" else "Pinch to zoom",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Glowing Share Full-Resolution Document + Viral App Link Button
            Button(
                onClick = { ShareHelper.shareFullResolutionDocument(context, doc) },
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue.copy(alpha = 0.22f)),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, animatedNeonBorder),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Share Full-Res Original & App Link",
                    color = AccentCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // AI Summary Card with Neon Edge
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonBorderCyan.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            color = AccentCyan.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = doc.documentType.uppercase(),
                                color = AccentCyan,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Text(
                            text = doc.dateDetected,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = doc.aiSummary,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 21.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Parsed Key Entities (Dosages, Total Amounts, Doctors)
            Text(
                text = "Key Extracted Entities (Tap to Copy)",
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Total Amount Chip
            if (keyEntities.totalAmount != null) {
                CopyableEntityRow(
                    label = "Total Amount",
                    value = "PKR ${keyEntities.totalAmount}",
                    color = AccentAmber,
                    context = context
                )
            }

            // Vendor / Hospital
            keyEntities.vendorOrHospital?.let { vendor ->
                CopyableEntityRow(
                    label = "Vendor / Hospital",
                    value = vendor,
                    color = AccentCyan,
                    context = context
                )
            }

            // Doctor Name
            keyEntities.doctorName?.let { doctor ->
                CopyableEntityRow(
                    label = "Doctor",
                    value = doctor,
                    color = AccentPurple,
                    context = context
                )
            }

            // Medicines & Dosages
            if (keyEntities.medicines.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Prescribed Medicines:",
                    color = AccentEmerald,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                keyEntities.medicines.forEach { medicine ->
                    CopyableEntityRow(
                        label = "Rx Medicine",
                        value = medicine,
                        color = AccentEmerald,
                        context = context
                    )
                }
            }

            // High-Paying Native Advanced Sponsored Card (Free Tier)
            com.docusnap.ai.ads.AdMobNativeCard(billingManager = viewModel.billingManager)

            Spacer(modifier = Modifier.height(16.dp))

            // CUSTOM SEARCH KEYWORDS & REMARKS SECTION (With Glowing Neon '+' Icon)
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonBorderPurple.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tag,
                                contentDescription = null,
                                tint = AccentPurple,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Custom Keywords & Search Tags",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        // Glowing Neon '+' Button to add custom search words
                        Surface(
                            onClick = { showAddTagDialog = true },
                            color = AccentPurple.copy(alpha = 0.25f),
                            shape = CircleShape,
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, AccentPurple),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Custom Keyword",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Add manual words (e.g. 'Shaheen Bill', 'SAFCO Payment') to find this document instantly in search.",
                        color = TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (customTags.isEmpty()) {
                        Surface(
                            onClick = { showAddTagDialog = true },
                            color = CardDark.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(vertical = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddCircleOutline,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Tap '+' to add your first custom search tag",
                                    color = AccentCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    } else {
                        // Display Added Custom Tags as Glowing Neon Chips
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            customTags.forEach { tag ->
                                Surface(
                                    color = AccentPurple.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentPurple.copy(alpha = 0.6f))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = tag,
                                            color = TextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove tag",
                                            tint = AccentPink,
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable {
                                                    saveUpdatedTags(customTags - tag)
                                                    Toast.makeText(context, "Removed tag: $tag", Toast.LENGTH_SHORT).show()
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Raw OCR Text Accordion with Glowing Neon '+' Button on opposite side
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonBorderCyan.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showRawOcr = !showRawOcr }
                        ) {
                            Icon(
                                imageVector = Icons.Default.DocumentScanner,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Raw On-Device OCR Text",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // High-Visibility Neon '+' Button against OCR Text
                            Surface(
                                onClick = { showAddTagDialog = true },
                                color = AccentCyan.copy(alpha = 0.2f),
                                shape = CircleShape,
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, AccentCyan),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add custom words",
                                        tint = AccentCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            IconButton(
                                onClick = { showRawOcr = !showRawOcr },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (showRawOcr) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Expand or collapse",
                                    tint = TextSecondary
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = showRawOcr) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = doc.rawOcrText.ifBlank { "No text recognized." },
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 19.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CardDark, RoundedCornerShape(10.dp))
                                    .border(1.dp, BorderDark, RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // DIALOG: ADD CUSTOM SEARCH TAG / KEYWORD
    if (showAddTagDialog) {
        var inputTag by remember { mutableStateOf("") }
        val presets = listOf("Shaheen Bill", "SAFCO Payment", "Dr Michael Rx", "Clinic Receipt", "Utility Bill", "Warranty Pass")

        AlertDialog(
            onDismissRequest = { showAddTagDialog = false },
            containerColor = SurfaceDark,
            titleContentColor = TextPrimary,
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tag,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add Search Keywords",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "Enter any word or custom phrase to quickly find this document later:",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = inputTag,
                        onValueChange = { inputTag = it },
                        placeholder = { Text("e.g. Shaheen Bill, SAFCO Payment...", color = TextMuted, fontSize = 13.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = AccentCyan
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Quick Suggestions (Tap to insert):",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        presets.forEach { preset ->
                            Surface(
                                onClick = { inputTag = preset },
                                color = CardDark,
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
                            ) {
                                Text(
                                    text = "+ $preset",
                                    color = AccentCyan,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = inputTag.trim()
                        if (clean.isNotEmpty()) {
                            saveUpdatedTags(customTags + clean)
                            Toast.makeText(context, "Added: '$clean' to search tags", Toast.LENGTH_SHORT).show()
                            showAddTagDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Add & Save", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTagDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
fun CopyableEntityRow(
    label: String,
    value: String,
    color: Color,
    context: Context
) {
    Surface(
        color = CardDark,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText(label, value)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Copied: $value", Toast.LENGTH_SHORT).show()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = label, color = TextMuted, fontSize = 11.sp)
                Text(text = value, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
