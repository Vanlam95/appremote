package com.appremote.remotecontrol.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream

object ScreenCaptureManager {

    private const val TAG = "ScreenCapture"
    private const val MIN_FRAME_INTERVAL_MS = 200L

    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private var running = false
    private var frameCallback: ((String, Int, Int) -> Unit)? = null
    private var fullWidth = 0
    private var fullHeight = 0
    private var lastFrameTime = 0L

    fun start(
        context: Context,
        projection: MediaProjection,
        onFrame: (base64: String, width: Int, height: Int) -> Unit
    ): Boolean {
        stop()
        running = true
        frameCallback = onFrame
        lastFrameTime = 0L

        val metrics = context.resources.displayMetrics
        fullWidth = metrics.widthPixels
        fullHeight = metrics.heightPixels
        val scale = 0.35f
        val width = (fullWidth * scale).toInt().coerceAtLeast(240)
        val height = (fullHeight * scale).toInt().coerceAtLeast(360)

        handlerThread = HandlerThread("ScreenCapture").also { it.start() }
        captureHandler = Handler(handlerThread!!.looper)

        return try {
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            virtualDisplay = projection.createVirtualDisplay(
                "AppRemoteCapture",
                width,
                height,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface,
                null,
                captureHandler
            )

            imageReader!!.setOnImageAvailableListener({ reader ->
                if (!running) return@setOnImageAvailableListener
                val now = System.currentTimeMillis()
                if (now - lastFrameTime < MIN_FRAME_INTERVAL_MS) {
                    reader.acquireLatestImage()?.close()
                    return@setOnImageAvailableListener
                }
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val base64 = imageToJpegBase64(image) ?: return@setOnImageAvailableListener
                    lastFrameTime = now
                    frameCallback?.invoke(base64, fullWidth, fullHeight)
                } finally {
                    image.close()
                }
            }, captureHandler)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start screen capture", e)
            stop()
            false
        }
    }

    fun stop() {
        running = false
        frameCallback = null
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        handlerThread?.quitSafely()
        handlerThread = null
        captureHandler = null
    }

    fun isRunning(): Boolean = running

    private fun imageToJpegBase64(image: Image, quality: Int = 50): String? {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        val cropped = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
        if (cropped !== bitmap) bitmap.recycle()

        val stream = ByteArrayOutputStream()
        cropped.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        cropped.recycle()
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}
