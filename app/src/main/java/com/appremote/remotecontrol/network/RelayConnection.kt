package com.appremote.remotecontrol.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class RelayConnection(
    private val role: String,
    private val relayUrl: String,
    private val roomCode: String,
    private val listener: Listener
) {
    interface Listener {
        fun onRegistered()
        fun onPaired()
        fun onPeerDisconnected()
        fun onCommandReceived(command: String): String
        fun onCommandResponse(response: String)
        fun onError(message: String)
        fun onDisconnected()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    @Volatile
    private var paired = false

    fun connect() {
        disconnect()
        paired = false
        val url = RelayProtocol.normalizeRelayUrl(relayUrl)
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(RelayProtocol.register(role, roomCode))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                paired = false
                listener.onDisconnected()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                paired = false
                listener.onError(t.message ?: "Connection failed")
                listener.onDisconnected()
            }
        })
    }

    private fun handleMessage(text: String) {
        when (RelayProtocol.parseType(text)) {
            RelayProtocol.TYPE_REGISTERED -> listener.onRegistered()
            RelayProtocol.TYPE_PAIRED -> {
                paired = true
                listener.onPaired()
            }
            RelayProtocol.TYPE_COMMAND -> {
                if (role != RelayProtocol.ROLE_HOST) return
                val command = RelayProtocol.parseData(text) ?: return
                val result = listener.onCommandReceived(command)
                webSocket?.send(RelayProtocol.response(result))
            }
            RelayProtocol.TYPE_RESPONSE -> {
                if (role != RelayProtocol.ROLE_CLIENT) return
                RelayProtocol.parseData(text)?.let { listener.onCommandResponse(it) }
            }
            RelayProtocol.TYPE_PEER_DISCONNECTED -> {
                paired = false
                listener.onPeerDisconnected()
            }
            RelayProtocol.TYPE_ERROR -> {
                val message = RelayProtocol.parseError(text) ?: "Relay error"
                listener.onError(message)
            }
        }
    }

    fun sendCommand(command: String, callback: ((Boolean) -> Unit)? = null) {
        val ws = webSocket
        if (ws == null || !paired) {
            callback?.invoke(false)
            return
        }
        val sent = ws.send(RelayProtocol.command(command))
        callback?.invoke(sent)
    }

    fun isPaired(): Boolean = paired

    fun disconnect() {
        webSocket?.close(1000, "Disconnect")
        webSocket = null
        paired = false
    }
}
