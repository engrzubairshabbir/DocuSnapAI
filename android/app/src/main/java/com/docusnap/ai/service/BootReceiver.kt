package com.docusnap.ai.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_REBOOT ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            MediaObserverService.start(context)
            MediaObserverService.scheduleContentUriTrigger(context)
            MediaObserverService.schedulePeriodicScan(context)
            MediaIngestionWorker.enqueueScan(context, scanRecentLimit = 30)
        }
    }
}
