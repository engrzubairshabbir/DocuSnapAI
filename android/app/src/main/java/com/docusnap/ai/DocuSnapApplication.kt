package com.docusnap.ai

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.docusnap.ai.data.local.AppDatabase
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

