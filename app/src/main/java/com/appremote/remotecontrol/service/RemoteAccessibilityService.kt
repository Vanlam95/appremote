package com.appremote.remotecontrol.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.appremote.remotecontrol.network.CommandProtocol
import com.appremote.remotecontrol.network.RemoteCommand
import com.appremote.remotecontrol.util.DeviceUtils

class RemoteAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        @Volatile
        var instance: RemoteAccessibilityService? = null
            private set

        private const val TAP_DURATION_MS = 100L
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    fun executeCommand(json: String): Boolean {
        val command = CommandProtocol.parseCommand(json) ?: return false
        return when (command) {
            is RemoteCommand.OpenBrowser -> {
                mainHandler.post { DeviceUtils.openBrowser(this, command.url) }
                true
            }
            is RemoteCommand.GlobalAction -> performGlobalAction(command.action)
            is RemoteCommand.Scroll -> {
                mainHandler.post { performScroll(command.direction) }
                true
            }
            is RemoteCommand.Tap -> {
                mainHandler.post { performTap(command.x, command.y) }
                true
            }
            is RemoteCommand.ScreenStart, is RemoteCommand.ScreenStop -> false
        }
    }

    private fun performGlobalAction(action: String): Boolean {
        val globalAction = when (action) {
            CommandProtocol.ACTION_HOME -> GLOBAL_ACTION_HOME
            CommandProtocol.ACTION_BACK -> GLOBAL_ACTION_BACK
            CommandProtocol.ACTION_RECENTS -> GLOBAL_ACTION_RECENTS
            else -> return false
        }
        return performGlobalAction(globalAction)
    }

    private fun performScroll(direction: String): Boolean {
        val displayMetrics = resources.displayMetrics
        val centerX = displayMetrics.widthPixels / 2f
        val startY: Float
        val endY: Float

        when (direction) {
            CommandProtocol.SCROLL_DOWN -> {
                startY = displayMetrics.heightPixels * 0.7f
                endY = displayMetrics.heightPixels * 0.3f
            }
            CommandProtocol.SCROLL_UP -> {
                startY = displayMetrics.heightPixels * 0.3f
                endY = displayMetrics.heightPixels * 0.7f
            }
            else -> return false
        }

        return dispatchSwipe(centerX, startY, centerX, endY)
    }

    private fun performTap(x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x + 1f, y + 1f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS))
            .build()
        return dispatchGesture(gesture, null, mainHandler)
    }

    private fun dispatchSwipe(x1: Float, y1: Float, x2: Float, y2: Float): Boolean {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()
        return dispatchGesture(gesture, null, mainHandler)
    }
}
