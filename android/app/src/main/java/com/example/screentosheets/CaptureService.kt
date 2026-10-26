package com.example.screentosheets

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import android.view.View

class CaptureService : Service() {
    companion object {
        const val ACTION_START = "com.example.screentosheets.START"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "screen_to_ctrl_f"
        private const val NOTIFICATION_ID = 101
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var overlay: Button? = null
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var width = 0
    private var height = 0
    private var density = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            startForeground(NOTIFICATION_ID, buildNotification())
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            @Suppress("DEPRECATION")
            val resultData = if (Build.VERSION.SDK_INT >= 33)
                intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            else intent.getParcelableExtra(EXTRA_RESULT_DATA) as? Intent
            if (resultData == null) {
                stopSelf()
                return START_NOT_STICKY
            }
            try {
                val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                projection = manager.getMediaProjection(resultCode, resultData)
                setupCapture()
                showOverlay()
            } catch (e: Exception) {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Screen capture", NotificationManager.IMPORTANCE_LOW))
        }
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder.setContentTitle("Screen to Ctrl+F")
            .setContentText("Screen capture is active. Use the floating button to send a screenshot.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }

    private fun setupCapture() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        width = metrics.widthPixels
        height = metrics.heightPixels
        density = metrics.densityDpi
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopSelf() }
        }, mainHandler)
        virtualDisplay = projection?.createVirtualDisplay(
            "ScreenToCtrlF", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader!!.surface, null, mainHandler
        )
    }

    private fun showOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val button = Button(this).apply {
            text = "Find ID"
            setOnClickListener { captureAndSend() }
        }
        overlay = button
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                   else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 180
        }
        windowManager.addView(button, params)
    }


private fun captureAndSend() {
    overlay?.isEnabled = false
    overlay?.visibility = View.INVISIBLE

    executor.execute {
        var image: android.media.Image? = null

        try {
            // Allow the screen to update after hiding the floating button.
            Thread.sleep(300)

            // Acquire the most recent screen frame.
            val deadline = System.currentTimeMillis() + 2500
            while (image == null && System.currentTimeMillis() < deadline) {
                image = imageReader?.acquireLatestImage()
                if (image == null) Thread.sleep(100)
            }

            if (image == null) {
                throw IllegalStateException("No screen frame available")
            }

            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width

            val padded = Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                Bitmap.Config.ARGB_8888
            )

            padded.copyPixelsFromBuffer(buffer)
            image.close()
            image = null

            val bitmap = Bitmap.createBitmap(padded, 0, 0, width, height)
            padded.recycle()

            val bytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                bitmap.recycle()
                out.toByteArray()
            }

            val prefs = getSharedPreferences(Config.PREFS_NAME, Context.MODE_PRIVATE)
            val host = prefs.getString(Config.KEY_BRIDGE_HOST, "").orEmpty()
            val port = prefs.getString(Config.KEY_BRIDGE_PORT, Config.DEFAULT_BRIDGE_PORT).orEmpty()
            val token = prefs.getString(Config.KEY_BRIDGE_TOKEN, "").orEmpty()

            val portNumber = port.toIntOrNull()
            if (host.isBlank() || token.isBlank() || portNumber == null || portNumber !in 1..65535) {
                throw IllegalStateException("Bridge settings are missing or invalid. Open the app and save them first.")
            }

            val boundary = "screenToCtrlFBoundary"
            val conn = URL("http://$host:$port/screenshot")
                .openConnection() as HttpURLConnection

            conn.requestMethod = "POST"
            conn.connectTimeout = 10000
            conn.readTimeout = 20000
            conn.doOutput = true
            conn.setRequestProperty(
                "Authorization", "Bearer " + token
            )
            conn.setRequestProperty(
                "Content-Type", "multipart/form-data; boundary=$boundary"
            )

            conn.outputStream.use { out ->
                out.write("--$boundary\r\n".toByteArray())
                out.write(
                    "Content-Disposition: form-data; name=\"image\"; filename=\"screen.png\"\r\n".toByteArray()
                )
                out.write("Content-Type: image/png\r\n\r\n".toByteArray())
                out.write(bytes)
                out.write("\r\n--$boundary--\r\n".toByteArray())
            }

            val responseCode = conn.responseCode
            val response = (
                if (responseCode in 200..299) conn.inputStream
                else conn.errorStream
            ).bufferedReader().use { it.readText() }

            android.util.Log.d(
                "ScreenToCtrlF",
                "Bridge response: $responseCode $response"
            )
            conn.disconnect()

            mainHandler.post {
                overlay?.visibility = View.VISIBLE
                overlay?.text = if (responseCode in 200..299) "Sent ✓" else "Retry"
                overlay?.isEnabled = true
                mainHandler.postDelayed({
                    overlay?.text = "Find ID"
                }, 1800)
            }
        } catch (e: Exception) {
            android.util.Log.e("ScreenToCtrlF", "Capture failed", e)

            mainHandler.post {
                overlay?.visibility = View.VISIBLE
                overlay?.text = "Retry"
                overlay?.isEnabled = true
            }
        } finally {
            try {
                image?.close()
            } catch (_: Exception) {
            }
        }
    }
}

    override fun onDestroy() {
        try { overlay?.let { windowManager.removeView(it) } } catch (_: Exception) {}
        overlay = null
        virtualDisplay?.release()
        imageReader?.close()
        projection?.stop()
        executor.shutdown()
        super.onDestroy()
    }
}
