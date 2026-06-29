package com.appremote.remotecontrol.network

import android.os.Handler
import android.os.Looper
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
    private val listener: Listener,
    private val autoReconnect: Boolean = false
) {
    interface Listener {
        fun onRegistered()
        fun onPaired()
        fun onPeerDisconnected()
        fun onCommandReceived(command: String): String
        fun onCommandResponse(response: String)
        fun onScreenFrame(data: String, width: Int, height: Int) {}
        fun onError(message: String)
        fun onDisconnected()
        fun onReconnecting(attempt: Int) {}
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    @Volatile
    private var paired = false

    @Volatile
    private var intentionalDisconnect = false

    @Volatile
    private var reconnectAttempt = 0

    private var pendingResponseHandler: ((String) -> Unit)? = null
    private var reconnectRunnable: Runnable? = null

    fun connect() {
        intentionalDisconnect = false
        cancelReconnect()
        openSocket()
    }

    private fun openSocket() {
        webSocket?.close(1000, "Reconnect")
        webSocket = null
        paired = false

        val url = RelayProtocol.normalizeRelayUrl(relayUrl)
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempt = 0
                webSocket.send(RelayProtocol.register(role, roomCode))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                paired = false
                listener.onDisconnected()
                scheduleReconnectIfNeeded()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                paired = false
                if (!intentionalDisconnect) {
                    listener.onError(t.message ?: "Connection failed")
                }
                listener.onDisconnected()
                scheduleReconnectIfNeeded()
            }
        })
    }

    private fun scheduleReconnectIfNeeded() {
        if (!autoReconnect || intentionalDisconnect) return
        cancelReconnect()
        reconnectAttempt++
        listener.onReconnecting(reconnectAttempt)
        val delayMs = minOf(3_000L * reconnectAttempt, 30_000L)
        reconnectRunnable = Runnable { openSocket() }
        mainHandler.postDelayed(reconnectRunnable!!, delayMs)
    }

    private fun cancelReconnect() {
        reconnectRunnable?.let { mainHandler.removeCallbacks(it) }
        reconnectRunnable = null
    }

    private fun handleMessage(text: String) {
        when (RelayProtocol.parseType(text)) {
            RelayProtocol.TYPE_REGISTERED -> listener.onRegistered()
            RelayProtocol.TYPE_PAIRED -> {
                paired = true
                reconnectAttempt = 0
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
                val data = RelayProtocol.parseData(text) ?: return
                val pending = pendingResponseHandler
                if (pending != null) {
                    pendingResponseHandler = null
                    pending.invoke(data)
                } else {
                    listener.onCommandResponse(data)
                }
            }
            RelayProtocol.TYPE_PEER_DISCONNECTED -> {
                paired = false
                listener.onPeerDisconnected()
            }
            RelayProtocol.TYPE_ERROR -> {
                val message = RelayProtocol.parseError(text) ?: "Relay error"
                listener.onError(message)
            }
            RelayProtocol.TYPE_SCREEN_FRAME -> {
                if (role != RelayProtocol.ROLE_CLIENT) return
                val data = RelayProtocol.parseData(text) ?: return
                val width = RelayProtocol.parseWidth(text) ?: return
                val height = RelayProtocol.parseHeight(text) ?: return
                listener.onScreenFrame(data, width, height)
            }
        }
    }

    fun sendScreenFrame(data: String, width: Int, height: Int): Boolean {
        val ws = webSocket ?: return false
        return ws.send(RelayProtocol.screenFrame(data, width, height))
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

    fun sendCommandAwaitingResponse(command: String, callback: (String?) -> Unit) {
        val ws = webSocket
        if (ws == null || !paired) {
            callback(null)
            return
        }
        pendingResponseHandler = callback
        if (!ws.send(RelayProtocol.command(command))) {
            pendingResponseHandler = null
            callback(null)
        }
    }

    fun isPaired(): Boolean = paired

    fun disconnect() {
        intentionalDisconnect = true
        cancelReconnect()
        pendingResponseHandler = null
        webSocket?.close(1000, "Disconnect")
        webSocket = null
        paired = false
    }
}
