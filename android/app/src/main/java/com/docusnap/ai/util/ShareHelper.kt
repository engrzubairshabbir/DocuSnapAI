package com.docusnap.ai.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import com.docusnap.ai.data.model.DocumentEntity
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object ShareHelper {

    fun getPlayStoreUrl(context: Context): String {
        return "https://play.google.com/store/apps/details?id=${context.packageName}"
    }

    fun buildPromoMessage(context: Context, doc: DocumentEntity? = null): String {
        val playUrl = getPlayStoreUrl(context)
        val header = if (doc != null) {
            buildString {
                appendLine("📄 *${doc.title.ifBlank { "Document" }}*")
                if (doc.dateDetected.isNotBlank()) {
                    appendLine("📅 Date: ${doc.dateDetected}")
                }
                if (doc.aiSummary.isNotBlank()) {
                    appendLine("📝 ${doc.aiSummary}")
                }
                appendLine()
                appendLine("━━━━━━━━━━━━━━━━━━━━━━")
            }
        } else {
            ""
        }

        return buildString {
            append(header)
            appendLine("✨ *DocuSnap AI — Smart Bill & Rx Finder*")
            appendLine()
            appendLine("Tired of scrolling through thousands of photos to find an old doctor's prescription, electricity bill, bank slip, or receipt? 😩")
            appendLine("DocuSnap AI reads every document offline & lets you ask in plain language (English, Urdu, Arabic & more) to find anything in 1 second! ⚡🔍")
            appendLine()
            appendLine("📲 *Install & Test DocuSnap AI FREE on Google Play Store:*")
            append(playUrl)
        }
    }

    /**
     * Shares the document in 100% full original resolution and original file format,
     * paired with an engaging promotional invitation and Google Play Store link.
     */
    fun shareFullResolutionDocument(context: Context, doc: DocumentEntity) {
        try {
            val localUri = Uri.parse(doc.localImageUri)
            var shareFile: File? = null

            if (localUri.scheme == "file" && !localUri.path.isNullOrBlank()) {
                val candidate = File(localUri.path!!)
                if (candidate.exists() && candidate.length() > 0L) {
                    shareFile = candidate
                }
            }

            // Fallback: if stored as content:// URI, copy raw original bytes into cache for FileProvider sharing
            if (shareFile == null) {
                val mime = context.contentResolver.getType(localUri)
                val ext = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "jpg"
                val cacheTarget = File(context.cacheDir, "share_${doc.id}.$ext")
                context.contentResolver.openInputStream(localUri)?.use { input ->
                    FileOutputStream(cacheTarget).use { output ->
                        input.copyTo(output)
                    }
                }
                if (cacheTarget.exists() && cacheTarget.length() > 0L) {
                    shareFile = cacheTarget
                }
            }

            if (shareFile == null) {
                Toast.makeText(context, "Original file not found on device", Toast.LENGTH_SHORT).show()
                return
            }

            val ext = shareFile.extension.lowercase(Locale.ROOT)
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "image/*"
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                shareFile
            )

            val promoText = buildPromoMessage(context, doc)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, promoText)
                putExtra(Intent.EXTRA_SUBJECT, "${doc.title.ifBlank { "Document" }} — Shared via DocuSnap AI")
                clipData = ClipData.newUri(context.contentResolver, doc.title, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(
                Intent.createChooser(shareIntent, "Share Full-Res Document (${ext.uppercase()}) & App Link")
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to share document: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareAppInvite(context: Context) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, buildPromoMessage(context, null))
            putExtra(Intent.EXTRA_SUBJECT, "Try DocuSnap AI: Smart Bill & Rx Finder")
        }
        context.startActivity(Intent.createChooser(shareIntent, "Invite Friends to DocuSnap AI"))
    }
}

