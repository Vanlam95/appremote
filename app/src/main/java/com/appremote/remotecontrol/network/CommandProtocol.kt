package com.appremote.remotecontrol.network

import org.json.JSONObject

object CommandProtocol {
    const val DEFAULT_PORT = 8765

    const val CMD_OPEN_BROWSER = "OPEN_BROWSER"
    const val CMD_GLOBAL_ACTION = "GLOBAL_ACTION"
    const val CMD_SCROLL = "SCROLL"
    const val CMD_TAP = "TAP"
    const val CMD_SCREEN_START = "SCREEN_START"
    const val CMD_SCREEN_STOP = "SCREEN_STOP"

    const val ACTION_HOME = "HOME"
    const val ACTION_BACK = "BACK"
    const val ACTION_RECENTS = "RECENTS"

    const val SCROLL_UP = "UP"
    const val SCROLL_DOWN = "DOWN"

    fun openBrowser(url: String): String =
        JSONObject().apply {
            put("cmd", CMD_OPEN_BROWSER)
            put("url", url)
        }.toString()

    fun globalAction(action: String): String =
        JSONObject().apply {
            put("cmd", CMD_GLOBAL_ACTION)
            put("action", action)
        }.toString()

    fun scroll(direction: String): String =
        JSONObject().apply {
            put("cmd", CMD_SCROLL)
            put("direction", direction)
        }.toString()

    fun tap(x: Float, y: Float): String =
        JSONObject().apply {
            put("cmd", CMD_TAP)
            put("x", x.toDouble())
            put("y", y.toDouble())
        }.toString()

    fun screenStart(): String =
        JSONObject().apply { put("cmd", CMD_SCREEN_START) }.toString()

    fun screenStop(): String =
        JSONObject().apply { put("cmd", CMD_SCREEN_STOP) }.toString()

    fun parseCommand(json: String): RemoteCommand? {
        return try {
            val obj = JSONObject(json)
            when (obj.getString("cmd")) {
                CMD_OPEN_BROWSER -> RemoteCommand.OpenBrowser(obj.getString("url"))
                CMD_GLOBAL_ACTION -> RemoteCommand.GlobalAction(obj.getString("action"))
                CMD_SCROLL -> RemoteCommand.Scroll(obj.getString("direction"))
                CMD_TAP -> RemoteCommand.Tap(
                    obj.getDouble("x").toFloat(),
                    obj.getDouble("y").toFloat()
                )
                CMD_SCREEN_START -> RemoteCommand.ScreenStart
                CMD_SCREEN_STOP -> RemoteCommand.ScreenStop
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}

sealed class RemoteCommand {
    data class OpenBrowser(val url: String) : RemoteCommand()
    data class GlobalAction(val action: String) : RemoteCommand()
    data class Scroll(val direction: String) : RemoteCommand()
    data class Tap(val x: Float, val y: Float) : RemoteCommand()
    data object ScreenStart : RemoteCommand()
    data object ScreenStop : RemoteCommand()
}

object ShoppingSites {
    const val SHOPEE = "https://shopee.vn"
    const val LAZADA = "https://www.lazada.vn"
    const val TIKI = "https://tiki.vn"
    const val SENDO = "https://www.sendo.vn"
}
