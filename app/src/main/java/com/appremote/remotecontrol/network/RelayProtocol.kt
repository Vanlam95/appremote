package com.appremote.remotecontrol.network

import org.json.JSONObject

object RelayProtocol {
    const val TYPE_REGISTER = "register"
    const val TYPE_REGISTERED = "registered"
    const val TYPE_PAIRED = "paired"
    const val TYPE_COMMAND = "command"
    const val TYPE_RESPONSE = "response"
    const val TYPE_PEER_DISCONNECTED = "peer_disconnected"
    const val TYPE_ERROR = "error"
    const val TYPE_SCREEN_FRAME = "screen_frame"

    const val ROLE_HOST = "host"
    const val ROLE_CLIENT = "client"

    fun register(role: String, room: String): String =
        JSONObject().apply {
            put("type", TYPE_REGISTER)
            put("role", role)
            put("room", room)
        }.toString()

    fun command(data: String): String =
        JSONObject().apply {
            put("type", TYPE_COMMAND)
            put("data", data)
        }.toString()

    fun response(data: String): String =
        JSONObject().apply {
            put("type", TYPE_RESPONSE)
            put("data", data)
        }.toString()

    fun screenFrame(data: String, width: Int, height: Int): String =
        JSONObject().apply {
            put("type", TYPE_SCREEN_FRAME)
            put("data", data)
            put("width", width)
            put("height", height)
        }.toString()

    fun parseWidth(json: String): Int? =
        runCatching { JSONObject(json).getInt("width") }.getOrNull()

    fun parseHeight(json: String): Int? =
        runCatching { JSONObject(json).getInt("height") }.getOrNull()

    fun parseType(json: String): String? =
        runCatching { JSONObject(json).getString("type") }.getOrNull()

    fun parseData(json: String): String? =
        runCatching { JSONObject(json).getString("data") }.getOrNull()

    fun parseError(json: String): String? =
        runCatching { JSONObject(json).getString("message") }.getOrNull()

    fun normalizeRelayUrl(url: String): String {
        var normalized = url.trim()
        if (!normalized.startsWith("ws://") && !normalized.startsWith("wss://")) {
            normalized = "ws://$normalized"
        }
        return normalized.removeSuffix("/")
    }

    fun generateRoomCode(): String = (100000..999999).random().toString()
}
