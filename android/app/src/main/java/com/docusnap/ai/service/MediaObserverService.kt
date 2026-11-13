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
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.docusnap.ai.DocuSnapApplication
import com.docusnap.ai.R
import com.docusnap.ai.ui.MainActivity
import java.util.concurrent.TimeUnit

class MediaObserverService : Service() {

    private var contentObserver: ContentObserver? = null
    private val debounceHandler = Handler(Looper.getMainLooper())
    private var pendingUri: Uri? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        registerMediaObserver()
        scheduleContentUriTrigger(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaIngestionWorker.enqueueScan(applicationContext, scanRecentLimit = 25)
        scheduleContentUriTrigger(applicationContext)
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
                // Debounce rapid gallery notifications
                pendingUri = uri
                debounceHandler.removeCallbacks(debounceRunnable)
                debounceHandler.postDelayed(debounceRunnable, 1200)
            }
        }

        try {
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                contentObserver!!
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private val debounceRunnable = Runnable {
        triggerIngestionWorker(pendingUri)
    }

    private fun triggerIngestionWorker(triggeredUri: Uri?) {
        val workManager = WorkManager.getInstance(applicationContext)

        val dataBuilder = workDataOf(
            MediaIngestionWorker.KEY_IMAGE_URI to (triggeredUri?.toString() ?: ""),
            MediaIngestionWorker.KEY_SCAN_LIMIT to 25
        )

        // Work constraint: runs immediate OCR on-device without network,
        // cloud sync worker will execute upon connectivity.
        val workRequest = OneTimeWorkRequestBuilder<MediaIngestionWorker>()
            .setInputData(dataBuilder)
            .build()

        // ExistingWorkPolicy.KEEP ensures no events are dropped while avoiding redundant jobs
        workManager.enqueueUniqueWork(
            MediaIngestionWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            workRequest
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        contentObserver?.let {
            contentResolver.unregisterContentObserver(it)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 4091
        private const val CONTENT_TRIGGER_WORK_NAME = "DocuSnapMediaContentUriTrigger"
        const val TAG_CONTENT_TRIGGER = "docusnap_content_uri_trigger"

        /**
         * Registers a WorkManager OS ContentUriTrigger so screenshots and camera photos
         * are caught across Android 8–16 even if OEMs (Xiaomi MIUI/HyperOS) kill the service.
         */
        fun scheduleContentUriTrigger(context: Context, rearmFromRunningTrigger: Boolean = false) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val constraints = Constraints.Builder()
                    .addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
                    .setTriggerContentUpdateDelay(800L, TimeUnit.MILLISECONDS)
                    .setTriggerContentMaxDelay(3500L, TimeUnit.MILLISECONDS)
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

        fun start(context: Context) {
            val intent = Intent(context, MediaObserverService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MediaObserverService::class.java)
            context.stopService(intent)
        }
    }
}

