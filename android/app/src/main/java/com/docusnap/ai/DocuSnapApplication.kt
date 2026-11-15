package com.docusnap.ai

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.docusnap.ai.data.local.AppDatabase
import com.docusnap.ai.service.MediaIngestionWorker
import com.docusnap.ai.service.MediaObserverService
import com.docusnap.ai.sync.FirebaseSyncRepository

class DocuSnapApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var syncRepository: FirebaseSyncRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize local Room database
        database = AppDatabase.getInstance(this)

        // Initialize Firebase Sync Repository
        syncRepository = FirebaseSyncRepository(this, database.documentDao())

        // Initialize Google Mobile Ads (AdMob)
        com.docusnap.ai.ads.AdMobManager.initialize(this)

        // Create notification channels for background ingestion service
        createNotificationChannels()

        // Auto-start zero-click guard and background observers if storage permission is already granted
        if (hasMediaPermission()) {
            MediaObserverService.start(this)
            MediaObserverService.scheduleContentUriTrigger(this)
            MediaObserverService.schedulePeriodicScan(this)
            MediaIngestionWorker.enqueueScan(this, scanRecentLimit = 30)
        }
    }

    fun hasMediaPermission(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= 34 -> {
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            }
            else -> {
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_MEDIA_OBSERVER,
                getString(R.string.channel_media_observer),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_media_observer_desc)
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID_MEDIA_OBSERVER = "media_observer_channel"
        lateinit var instance: DocuSnapApplication
            private set
    }
}
