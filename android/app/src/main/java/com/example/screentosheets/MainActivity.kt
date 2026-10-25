package com.example.screentosheets

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private val requestCapture = 4101
    private lateinit var status: TextView
    private lateinit var projectionManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }
        status = TextView(this).apply {
            textSize = 16f
            text = "Setup:\\n1. Grant overlay permission.\\n2. Start screen sharing.\\n3. Switch to the screen with the ID and tap the floating button."
        }
        val overlayButton = Button(this).apply {
            text = "Grant floating-button permission"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")))
                } else status.text = "Overlay permission is already granted."
            }
        }
        val startButton = Button(this).apply {
            text = "Enable capture & floating button"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    status.text = "Grant overlay permission first."
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")))
                    return@setOnClickListener
                }
                status.text = "Approve Android's screen-capture prompt."
                startActivityForResult(projectionManager.createScreenCaptureIntent(), requestCapture)
            }
        }
        val stopButton = Button(this).apply {
            text = "Stop capture"
            setOnClickListener { stopService(Intent(this@MainActivity, CaptureService::class.java)); status.text = "Capture stopped." }
        }
        layout.addView(status)
        layout.addView(overlayButton)
        layout.addView(startButton)
        layout.addView(stopButton)
        setContentView(layout)
    }

    @Deprecated("Deprecated API retained for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != requestCapture) return
        if (resultCode != RESULT_OK || data == null) {
            status.text = "Screen capture permission was not granted."
            return
        }
        val service = Intent(this, CaptureService::class.java).apply {
            action = CaptureService.ACTION_START
            putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(CaptureService.EXTRA_RESULT_DATA, data)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service) else startService(service)
        status.text = "Capture active. Switch to the app with the ID and tap the floating button."
    }
}
