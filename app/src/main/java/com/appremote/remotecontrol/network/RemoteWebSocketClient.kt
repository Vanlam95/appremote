package com.appremote.remotecontrol.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class RemoteWebSocketClient(
    private val onConnected: () -> Unit,
    private val onDisconnected: () -> Unit,
    private val onError: (String) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null

    fun connect(ip: String, port: Int) {
        disconnect()
        val request = Request.Builder()
            .url("ws://$ip:$port")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onConnected()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onDisconnected()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onError(t.message ?: "Connection failed")
                onDisconnected()
            }
        })
    }

    fun send(command: String, callback: ((Boolean) -> Unit)? = null) {
        val ws = webSocket
        if (ws == null) {
            callback?.invoke(false)
            return
        }
        ws.send(command)
        callback?.invoke(true)
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
    }

    fun isConnected(): Boolean = webSocket != null
}
