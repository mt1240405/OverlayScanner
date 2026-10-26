package com.example.screentosheets

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private val requestCapture = 4101
    private lateinit var status: TextView
    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var hostInput: EditText
    private lateinit var portInput: EditText
    private lateinit var tokenInput: EditText

    private val bridgePrefs by lazy {
        getSharedPreferences(Config.PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
        }

        content.addView(TextView(this).apply {
            text = "Bridge settings"
            textSize = 22f
        })

        content.addView(TextView(this).apply {
            text = "Enter the computer's LAN IP address, bridge port, and the same token configured as BRIDGE_TOKEN on the computer."
            textSize = 14f
        })

        hostInput = EditText(this).apply {
            hint = "Bridge IP address (e.g. 192.168.1.25)"
            maxLines = 1
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setText(bridgePrefs.getString(Config.KEY_BRIDGE_HOST, "") ?: "")
        }
        content.addView(hostInput, matchWidth())

        portInput = EditText(this).apply {
            hint = "Bridge port"
            maxLines = 1
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(bridgePrefs.getString(Config.KEY_BRIDGE_PORT, Config.DEFAULT_BRIDGE_PORT)
                ?: Config.DEFAULT_BRIDGE_PORT)
        }
        content.addView(portInput, matchWidth())

        tokenInput = EditText(this).apply {
            hint = "Bridge secret token"
            maxLines = 1
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(bridgePrefs.getString(Config.KEY_BRIDGE_TOKEN, "") ?: "")
        }
        content.addView(tokenInput, matchWidth())

        content.addView(Button(this).apply {
            text = "Save bridge settings"
            setOnClickListener { saveBridgeSettings() }
        }, matchWidth())

        status = TextView(this).apply {
            textSize = 14f
            text = "Save bridge settings before starting capture."
            setPadding(0, 12, 0, 12)
        }
        content.addView(status, matchWidth())

        content.addView(Button(this).apply {
            text = "Grant floating-button permission"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")))
                } else status.text = "Overlay permission is already granted."
            }
        }, matchWidth())

        content.addView(Button(this).apply {
            text = "Enable capture & floating button"
            setOnClickListener {
                if (!hasValidSavedSettings()) {
                    status.text = "Enter and save the bridge IP, port, and token first."
                    return@setOnClickListener
                }
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    status.text = "Grant overlay permission first."
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")))
                    return@setOnClickListener
                }
                status.text = "Approve Android's screen-capture prompt."
                startActivityForResult(projectionManager.createScreenCaptureIntent(), requestCapture)
            }
        }, matchWidth())

        content.addView(Button(this).apply {
            text = "Stop capture"
            setOnClickListener {
                stopService(Intent(this@MainActivity, CaptureService::class.java))
                status.text = "Capture stopped."
            }
        }, matchWidth())

        val scroll = ScrollView(this).apply {
            addView(content)
        }
        setContentView(scroll)
    }

    private fun matchWidth(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

    private fun saveBridgeSettings() {
        val host = hostInput.text.toString().trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')
        val portText = portInput.text.toString().trim()
        val token = tokenInput.text.toString()

        val port = portText.toIntOrNull()
        if (host.isBlank()) {
            status.text = "Enter the computer's LAN IP address."
            return
        }
        if (host.contains("/") || host.contains(" ") || host.contains(":")) {
            status.text = "Enter only the IP address or hostname, without scheme, path, or port."
            return
        }
        if (port == null || port !in 1..65535) {
            status.text = "Enter a valid port from 1 to 65535."
            return
        }
        if (token.isBlank()) {
            status.text = "Enter the bridge secret token."
            return
        }

        bridgePrefs.edit()
            .putString(Config.KEY_BRIDGE_HOST, host)
            .putString(Config.KEY_BRIDGE_PORT, port.toString())
            .putString(Config.KEY_BRIDGE_TOKEN, token)
            .apply()

        status.text = "Bridge settings saved on this device."
    }

    private fun hasValidSavedSettings(): Boolean {
        val host = bridgePrefs.getString(Config.KEY_BRIDGE_HOST, "").orEmpty()
        val port = bridgePrefs.getString(Config.KEY_BRIDGE_PORT, "").orEmpty().toIntOrNull()
        val token = bridgePrefs.getString(Config.KEY_BRIDGE_TOKEN, "").orEmpty()
        return host.isNotBlank() && port != null && port in 1..65535 && token.isNotBlank()
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
