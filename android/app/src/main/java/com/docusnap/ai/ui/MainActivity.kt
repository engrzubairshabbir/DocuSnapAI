package com.docusnap.ai.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.docusnap.ai.service.MediaIngestionWorker
import com.docusnap.ai.service.MediaObserverService
import com.docusnap.ai.ui.theme.DocuSnapTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DocuSnapViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // On Android 14+ (API 34/35/36), users can grant full or partial (selected photos) access
        val mediaGranted = if (Build.VERSION.SDK_INT >= 34) {
            permissions[Manifest.permission.READ_MEDIA_IMAGES] == true ||
            permissions[Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED] == true
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_IMAGES] == true
        } else {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }

        if (mediaGranted) {
            Log.d("DocuSnapAuth", "Media permission granted (full or selected). Starting service and initial scan.")
            MediaObserverService.start(this)
            MediaObserverService.scheduleContentUriTrigger(this)
            // Immediately scan gallery/selected photos & screenshots into Room database
            MediaIngestionWorker.enqueueScan(this, scanRecentLimit = 50)
        } else {
            Log.w("DocuSnapAuth", "Media permission denied by user.")
        }
    }

    private var screenCaptureCallback: Any? = null

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                val callback = Activity.ScreenCaptureCallback {
                    Log.d("DocuSnapAuth", "Android 14+ ScreenCaptureCallback fired. Queuing screenshot scan.")
                    Handler(Looper.getMainLooper()).postDelayed({
                        MediaIngestionWorker.enqueueScan(this@MainActivity, scanRecentLimit = 10)
                    }, 900L)
                }
                screenCaptureCallback = callback
                registerScreenCaptureCallback(mainExecutor, callback)
            } catch (e: Exception) {
                Log.w("DocuSnapAuth", "ScreenCaptureCallback registration skipped: ${e.message}")
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                (screenCaptureCallback as? Activity.ScreenCaptureCallback)?.let {
                    unregisterScreenCaptureCallback(it)
                }
            } catch (_: Exception) {
            }
        }
    }

    override fun onResume() {
        super.onResume()
        MediaObserverService.scheduleContentUriTrigger(this)
        if (hasAnyMediaPermission()) {
            MediaIngestionWorker.enqueueScan(this, scanRecentLimit = 25)
        }
    }

    private fun hasAnyMediaPermission(): Boolean {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        // Check and request runtime permissions
        checkAndRequestPermissions()

        setContent {
            DocuSnapTheme {
                DocuSnapNavHost(viewModel = viewModel)
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= 34) {
            // Android 14+ (API 34), Android 15 (API 35), Android 16 (API 36)
            val fullGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED

            val partialGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            ) == PackageManager.PERMISSION_GRANTED

            if (!fullGranted && !partialGranted) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            }

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            MediaObserverService.start(this)
            MediaObserverService.scheduleContentUriTrigger(this)
            // Permission already granted on subsequent launches: trigger recent scan
            MediaIngestionWorker.enqueueScan(this, scanRecentLimit = 50)
        }
    }
}

