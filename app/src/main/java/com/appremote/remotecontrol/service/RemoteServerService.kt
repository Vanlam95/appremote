package com.appremote.remotecontrol.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.media.projection.MediaProjection
import androidx.core.app.NotificationCompat
import com.appremote.remotecontrol.MainActivity
import com.appremote.remotecontrol.R
import com.appremote.remotecontrol.network.CommandProtocol
import com.appremote.remotecontrol.network.RelayConnection
import com.appremote.remotecontrol.network.RelayProtocol
import com.appremote.remotecontrol.network.RemoteCommand
import com.appremote.remotecontrol.service.ScreenCaptureManager
import com.appremote.remotecontrol.util.ScreenCaptureHolder
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress

class RemoteServerService : Service() {

    private var webSocketServer: RemoteWebSocketServer? = null
    private var relayConnection: RelayConnection? = null
    private var usingRelay = true

    interface ConnectionListener {
        fun onRegistered(roomCode: String)
        fun onClientConnected()
        fun onClientDisconnected()
        fun onError(message: String)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopServer()
                stopSelf()
            }
            else -> {
                usingRelay = intent?.getBooleanExtra(EXTRA_USE_RELAY, true) ?: true
                if (usingRelay) {
                    val relayUrl = intent?.getStringExtra(EXTRA_RELAY_URL).orEmpty()
                    val roomCode = intent?.getStringExtra(EXTRA_ROOM_CODE).orEmpty()
                    startRelay(relayUrl, roomCode)
                } else {
                    startLanServer()
                }
            }
        }
        return START_STICKY
    }

    private fun startRelay(relayUrl: String, roomCode: String) {
        if (relayConnection != null) return
        if (relayUrl.isBlank() || roomCode.length != 6) {
            connectionListener?.onError("Relay URL hoặc mã phòng không hợp lệ")
            stopSelf()
            return
        }

        createNotificationChannel()
        startAsForeground(
            NOTIFICATION_ID,
            buildNotification(getString(R.string.server_running) + " • $roomCode")
        )

        relayConnection = RelayConnection(
            role = RelayProtocol.ROLE_HOST,
            relayUrl = relayUrl,
            roomCode = roomCode,
            listener = object : RelayConnection.Listener {
                override fun onRegistered() {
                    connectionListener?.onRegistered(roomCode)
                }

                override fun onPaired() {
                    connectionListener?.onClientConnected()
                }

                override fun onPeerDisconnected() {
                    connectionListener?.onClientDisconnected()
                }

                override fun onCommandReceived(command: String): String {
                    return handleCommand(command)
                }

                override fun onCommandResponse(response: String) {}

                override fun onError(message: String) {
                    connectionListener?.onError(message)
                }

                override fun onDisconnected() {
                    connectionListener?.onClientDisconnected()
                }
            }
        ).also { it.connect() }
    }

    private fun startLanServer() {
        if (webSocketServer != null) return

        createNotificationChannel()
        startAsForeground(
            NOTIFICATION_ID,
            buildNotification(getString(R.string.server_running))
        )

        webSocketServer = RemoteWebSocketServer(
            InetSocketAddress(CommandProtocol.DEFAULT_PORT)
        ).also { it.start() }
    }

    private fun stopServer() {
        ScreenCaptureManager.stop()
        relayConnection?.disconnect()
        relayConnection = null
        webSocketServer?.stopSafely()
        webSocketServer = null
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun startAsForeground(notificationId: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(notificationId, notification)
        }
    }

    private fun ensureMediaProjection(): MediaProjection? {
        ScreenCaptureHolder.getProjection()?.let { return it }
        if (!ScreenCaptureHolder.hasPermission()) return null

        val projection = ScreenCaptureHolder.createProjection(this) ?: return null
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                ScreenCaptureManager.stop()
                ScreenCaptureHolder.release()
            }
        }, Handler(Looper.getMainLooper()))
        ScreenCaptureHolder.attachProjection(projection)
        return projection
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private inner class RemoteWebSocketServer(address: InetSocketAddress) : WebSocketServer(address) {
        override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
            connectionListener?.onClientConnected()
        }

        override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
            connectionListener?.onClientDisconnected()
        }

        override fun onMessage(conn: WebSocket, message: String) {
            conn.send(handleCommand(message))
        }

        override fun onError(conn: WebSocket?, ex: Exception) {
            ex.printStackTrace()
        }

        override fun onStart() {}

        fun stopSafely() {
            try { stop(1000) } catch (_: Exception) {}
        }
    }

    companion object {
        const val ACTION_STOP = "com.appremote.remotecontrol.STOP_SERVER"
        const val EXTRA_USE_RELAY = "use_relay"
        const val EXTRA_RELAY_URL = "relay_url"
        const val EXTRA_ROOM_CODE = "room_code"

        private const val CHANNEL_ID = "remote_server_channel"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        var connectionListener: ConnectionListener? = null
    }

    private fun handleCommand(command: String): String {
        when (CommandProtocol.parseCommand(command)) {
            is RemoteCommand.ScreenStart -> {
                val projection = ensureMediaProjection() ?: return "ERROR"
                val started = ScreenCaptureManager.start(applicationContext, projection) { base64, width, height ->
                    relayConnection?.sendScreenFrame(base64, width, height)
                }
                return if (started) "OK" else "ERROR"
            }
            is RemoteCommand.ScreenStop -> {
                ScreenCaptureManager.stop()
                return "OK"
            }
            else -> {
                val service = RemoteAccessibilityService.instance
                return if (service != null && service.executeCommand(command)) "OK" else "ERROR"
            }
        }
    }
}
