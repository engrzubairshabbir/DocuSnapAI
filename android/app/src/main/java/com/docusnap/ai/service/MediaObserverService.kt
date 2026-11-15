package com.docusnap.ai.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.docusnap.ai.DocuSnapApplication
import com.docusnap.ai.R
import com.docusnap.ai.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MediaObserverService : Service() {

    private var contentObserver: ContentObserver? = null
    private val debounceHandler = Handler(Looper.getMainLooper())
    private var pendingUri: Uri? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        registerMediaObserver()
        scheduleContentUriTrigger(applicationContext)
        schedulePeriodicScan(applicationContext)

        // Instant scan on start
        serviceScope.launch {
            try {
                MediaIngestionWorker.processDirectly(applicationContext, scanLimit = 25)
            } catch (e: Exception) {
                Log.e("MediaObserverService", "Initial scan error: ${e.message}")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scheduleContentUriTrigger(applicationContext)
        schedulePeriodicScan(applicationContext)
        serviceScope.launch {
            try {
                MediaIngestionWorker.processDirectly(applicationContext, scanLimit = 25)
            } catch (e: Exception) {
                Log.e("MediaObserverService", "Start command scan error: ${e.message}")
            }
        }
        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(
            this,
            DocuSnapApplication.CHANNEL_ID_MEDIA_OBSERVER
        )
            .setContentTitle("DocuSnap AI Guard")
            .setContentText("Monitoring camera and screenshots for bills & prescriptions")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun registerMediaObserver() {
        contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                pendingUri = uri
                debounceHandler.removeCallbacks(debounceRunnable)
                // Fast 350ms debounce: allows OS to flush file header, then instantly ingests
                debounceHandler.postDelayed(debounceRunnable, 350)
            }
        }

        try {
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                contentObserver!!
            )
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.INTERNAL_CONTENT_URI,
                true,
                contentObserver!!
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private val debounceRunnable = Runnable {
        val uri = pendingUri
        // 1. Instant direct in-process ingestion (< 300ms)
        serviceScope.launch {
            try {
                MediaIngestionWorker.processDirectly(
                    applicationContext,
                    uris = listOfNotNull(uri),
                    scanLimit = 15
                )
            } catch (e: Exception) {
                Log.e("MediaObserverService", "Direct ingestion error: ${e.message}")
            }
        }
        // 2. WorkManager as durable OS backup
        triggerIngestionWorker(uri)
    }

    private fun triggerIngestionWorker(triggeredUri: Uri?) {
        val workManager = WorkManager.getInstance(applicationContext)

        val dataBuilder = workDataOf(
            MediaIngestionWorker.KEY_IMAGE_URI to (triggeredUri?.toString() ?: ""),
            MediaIngestionWorker.KEY_SCAN_LIMIT to 25
        )

        val workRequest = OneTimeWorkRequestBuilder<MediaIngestionWorker>()
            .setInputData(dataBuilder)
            .build()

        workManager.enqueueUniqueWork(
            MediaIngestionWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        contentObserver?.let {
            contentResolver.unregisterContentObserver(it)
        }
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 4091
        private const val CONTENT_TRIGGER_WORK_NAME = "DocuSnapMediaContentUriTrigger"
        private const val PERIODIC_WORK_NAME = "DocuSnapPeriodicMediaScan"
        const val TAG_CONTENT_TRIGGER = "docusnap_content_uri_trigger"

        /**
         * Registers a WorkManager OS ContentUriTrigger so screenshots and camera photos
         * are caught across Android 7–16 even if OEMs (Xiaomi MIUI/HyperOS) kill the service.
         */
        fun scheduleContentUriTrigger(context: Context, rearmFromRunningTrigger: Boolean = false) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val constraints = Constraints.Builder()
                    .addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
                    .addContentUriTrigger(MediaStore.Images.Media.INTERNAL_CONTENT_URI, true)
                    .setTriggerContentUpdateDelay(400L, TimeUnit.MILLISECONDS)
                    .setTriggerContentMaxDelay(2000L, TimeUnit.MILLISECONDS)
                    .build()

                val request = OneTimeWorkRequestBuilder<MediaIngestionWorker>()
                    .addTag(TAG_CONTENT_TRIGGER)
                    .setConstraints(constraints)
                    .setInputData(workDataOf(MediaIngestionWorker.KEY_SCAN_LIMIT to 25))
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    CONTENT_TRIGGER_WORK_NAME,
                    if (rearmFromRunningTrigger) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP,
                    request
                )
            }
        }

        /**
         * 15-minute Periodic safety net that catches any missed images when app is closed.
         */
        fun schedulePeriodicScan(context: Context) {
            val periodicRequest = PeriodicWorkRequestBuilder<MediaIngestionWorker>(15, TimeUnit.MINUTES)
                .setInputData(workDataOf(MediaIngestionWorker.KEY_SCAN_LIMIT to 25))
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
        }

        fun start(context: Context) {
            try {
                val intent = Intent(context, MediaObserverService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e("MediaObserverService", "Failed to start service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MediaObserverService::class.java)
            context.stopService(intent)
        }
    }
}
