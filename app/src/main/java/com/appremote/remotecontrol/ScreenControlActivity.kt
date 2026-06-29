package com.appremote.remotecontrol

import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.appremote.remotecontrol.databinding.ActivityScreenControlBinding
import com.appremote.remotecontrol.network.CommandProtocol
import com.appremote.remotecontrol.network.MultiDeviceManager
import com.appremote.remotecontrol.util.AppPreferences
import com.appremote.remotecontrol.util.DeviceControlBridge
import com.appremote.remotecontrol.util.TapCalibration
import com.appremote.remotecontrol.util.TouchCoordinateMapper
import kotlin.math.roundToInt

class ScreenControlActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScreenControlBinding
    private lateinit var deviceManager: MultiDeviceManager
    private var deviceIndex = 0
    private var remoteWidth = 0
    private var remoteHeight = 0
    private var frameReceived = false
    private var calibration = TapCalibration()
    private val handler = Handler(Looper.getMainLooper())
    private val frameTimeoutRunnable = Runnable { onFrameTimeout() }
    private val hideMarkerRunnable = Runnable {
        binding.vTapMarker.visibility = View.GONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScreenControlBinding.inflate(layoutInflater)
        setContentView(binding.root)

        deviceIndex = intent.getIntExtra(EXTRA_DEVICE_INDEX, 0)
        val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: "Máy ${deviceIndex + 1}"
        supportActionBar?.title = deviceName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        calibration = AppPreferences.getTapCalibration(this, deviceIndex)
        updateCalibrationUi()

        val manager = DeviceControlBridge.deviceManager
        if (manager == null || !manager.isPaired(deviceIndex)) {
            Toast.makeText(this, R.string.device_not_connected, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        deviceManager = manager

        manager.setScreenFrameListener(deviceIndex) { base64, width, height ->
            runOnUiThread { showFrame(base64, width, height) }
        }

        manager.sendToDeviceAwaitingResponse(deviceIndex, CommandProtocol.screenStart()) { response ->
            runOnUiThread {
                when (response) {
                    "OK" -> handler.postDelayed(frameTimeoutRunnable, FRAME_TIMEOUT_MS)
                    null -> {
                        Toast.makeText(this, R.string.screen_start_failed, Toast.LENGTH_LONG).show()
                        finish()
                    }
                    else -> {
                        Toast.makeText(this, R.string.screen_permission_required, Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            }
        }

        binding.btnToggleCalibrate.setOnClickListener { toggleCalibrationPanel() }
        binding.btnOffsetXMinus.setOnClickListener { adjustOffset(-OFFSET_STEP, 0f) }
        binding.btnOffsetXPlus.setOnClickListener { adjustOffset(OFFSET_STEP, 0f) }
        binding.btnOffsetYMinus.setOnClickListener { adjustOffset(0f, -OFFSET_STEP) }
        binding.btnOffsetYPlus.setOnClickListener { adjustOffset(0f, OFFSET_STEP) }
        binding.btnResetCalibration.setOnClickListener { resetCalibration() }

        binding.ivRemoteScreen.setOnTouchListener { view, event ->
            if (remoteWidth <= 0 || remoteHeight <= 0) return@setOnTouchListener true
            if (event.action == MotionEvent.ACTION_UP) {
                handleTap(event.x, event.y, view.width.toFloat(), view.height.toFloat())
            }
            true
        }
    }

    private fun toggleCalibrationPanel() {
        val show = binding.panelCalibrate.visibility != View.VISIBLE
        binding.panelCalibrate.visibility = if (show) View.VISIBLE else View.GONE
        binding.tvScreenHint.visibility = if (show) View.GONE else View.VISIBLE
        binding.btnToggleCalibrate.text = getString(
            if (show) R.string.calibrate_hide else R.string.calibrate_click
        )
    }

    private fun adjustOffset(dx: Float, dy: Float) {
        calibration = calibration.copy(
            offsetX = (calibration.offsetX + dx).coerceIn(-MAX_OFFSET, MAX_OFFSET),
            offsetY = (calibration.offsetY + dy).coerceIn(-MAX_OFFSET, MAX_OFFSET)
        )
        saveCalibration()
    }

    private fun resetCalibration() {
        calibration = TapCalibration()
        saveCalibration()
        Toast.makeText(this, R.string.calibrate_reset, Toast.LENGTH_SHORT).show()
    }

    private fun saveCalibration() {
        AppPreferences.setTapCalibration(this, deviceIndex, calibration)
        updateCalibrationUi()
    }

    private fun updateCalibrationUi() {
        binding.tvOffsetX.text = calibration.offsetX.roundToInt().toString()
        binding.tvOffsetY.text = calibration.offsetY.roundToInt().toString()
    }

    private fun handleTap(touchX: Float, touchY: Float, viewWidth: Float, viewHeight: Float) {
        showTapMarker(touchX, touchY)

        val coords = TouchCoordinateMapper.mapSimple(
            touchX, touchY, viewWidth, viewHeight,
            remoteWidth, remoteHeight, calibration
        ) ?: return

        deviceManager.sendToDevice(
            deviceIndex,
            CommandProtocol.tap(coords.first, coords.second)
        ) {}
    }

    private fun showTapMarker(x: Float, y: Float) {
        val marker = binding.vTapMarker
        val size = (28 * resources.displayMetrics.density).toInt()
        val params = marker.layoutParams as FrameLayout.LayoutParams
        params.leftMargin = (x - size / 2f).toInt().coerceAtLeast(0)
        params.topMargin = (y - size / 2f).toInt().coerceAtLeast(0)
        marker.layoutParams = params
        marker.visibility = View.VISIBLE
        handler.removeCallbacks(hideMarkerRunnable)
        handler.postDelayed(hideMarkerRunnable, MARKER_VISIBLE_MS)
    }

    private fun showFrame(base64: String, width: Int, height: Int) {
        frameReceived = true
        handler.removeCallbacks(frameTimeoutRunnable)
        binding.progressScreen.visibility = View.GONE
        remoteWidth = width
        remoteHeight = height
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        binding.ivRemoteScreen.setImageBitmap(bitmap)
    }

    private fun onFrameTimeout() {
        if (frameReceived || isFinishing) return
        Toast.makeText(this, R.string.screen_no_frames, Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacks(frameTimeoutRunnable)
        handler.removeCallbacks(hideMarkerRunnable)
        deviceManager.setScreenFrameListener(deviceIndex, null)
        deviceManager.sendToDevice(deviceIndex, CommandProtocol.screenStop()) {}
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        const val EXTRA_DEVICE_INDEX = "device_index"
        const val EXTRA_DEVICE_NAME = "device_name"

        private const val FRAME_TIMEOUT_MS = 10_000L
        private const val MARKER_VISIBLE_MS = 1_000L
        private const val OFFSET_STEP = 5f
        private const val MAX_OFFSET = 150f
    }
}
