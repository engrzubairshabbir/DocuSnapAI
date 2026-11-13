package com.docusnap.ai.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.io.IOException

class OcrEngine(private val context: Context) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Extracts raw text from an image URI 100% on-device using Google ML Kit.
     * Zero latency and zero cloud cost.
     */
    suspend fun recognizeText(imageUri: Uri): String {
        return try {
            val inputImage = InputImage.fromFilePath(context, imageUri)
            val result = recognizer.process(inputImage).await()
            result.text.trim()
        } catch (e: IOException) {
            e.printStackTrace()
            ""
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * Extracts raw text from an in-memory Bitmap on-device.
     */
    suspend fun recognizeText(bitmap: Bitmap, rotationDegrees: Int = 0): String {
        return try {
            val inputImage = InputImage.fromBitmap(bitmap, rotationDegrees)
            val result = recognizer.process(inputImage).await()
            result.text.trim()
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun close() {
        recognizer.close()
    }
}

