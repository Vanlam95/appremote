package com.appremote.remotecontrol.network

import com.appremote.remotecontrol.model.DeviceConfig
import com.appremote.remotecontrol.model.DeviceState

class MultiDeviceManager(
    private val useInternet: Boolean,
    private val relayUrl: String,
    private val lanPort: Int,
    private val listener: Listener
) {
    interface Listener {
        fun onDeviceStateChanged(index: Int, state: DeviceState)
        fun onSummaryChanged(pairedCount: Int, totalConfigured: Int)
    }

    private data class Session(
        val index: Int,
        var config: DeviceConfig? = null,
        var state: DeviceState = DeviceState.DISCONNECTED,
        var relay: RelayConnection? = null,
        var lan: RemoteWebSocketClient? = null
    )

    private val sessions = mutableMapOf<Int, Session>()
    private val screenFrameListeners = mutableMapOf<Int, (String, Int, Int) -> Unit>()

    fun setScreenFrameListener(index: Int, listener: ((String, Int, Int) -> Unit)?) {
        if (listener == null) screenFrameListeners.remove(index)
        else screenFrameListeners[index] = listener
    }

    fun connectDevice(config: DeviceConfig) {
        disconnectDevice(config.index)

        if (useInternet) {
            if (config.roomCode.length != 6) return
            val session = Session(config.index, config, DeviceState.CONNECTING)
            sessions[config.index] = session
            listener.onDeviceStateChanged(config.index, DeviceState.CONNECTING)

            session.relay = RelayConnection(
                role = RelayProtocol.ROLE_CLIENT,
                relayUrl = relayUrl,
                roomCode = config.roomCode,
                autoReconnect = true,
                listener = createRelayListener(config.index, session)
            ).also { it.connect() }
        } else {
            if (config.lanIp.isBlank()) return
            val session = Session(config.index, config, DeviceState.CONNECTING)
            sessions[config.index] = session
            listener.onDeviceStateChanged(config.index, DeviceState.CONNECTING)

            session.lan = RemoteWebSocketClient(
                onConnected = {
                    session.state = DeviceState.PAIRED
                    listener.onDeviceStateChanged(config.index, DeviceState.PAIRED)
                    notifySummary()
                },
                onDisconnected = {
                    session.state = DeviceState.DISCONNECTED
                    listener.onDeviceStateChanged(config.index, DeviceState.DISCONNECTED)
                    notifySummary()
                },
                onError = {
                    session.state = DeviceState.ERROR
                    listener.onDeviceStateChanged(config.index, DeviceState.ERROR)
                    notifySummary()
                }
            ).also { it.connect(config.lanIp, lanPort) }
        }
    }

    fun connectAll(configs: List<DeviceConfig>) {
        configs.forEach { connectDevice(it) }
    }

    fun disconnectDevice(index: Int) {
        sessions[index]?.let { session ->
            session.relay?.disconnect()
            session.lan?.disconnect()
            session.config = null
            sessions.remove(index)
            listener.onDeviceStateChanged(index, DeviceState.DISCONNECTED)
        }
        notifySummary()
    }

    fun disconnectAll() {
        sessions.keys.toList().forEach { disconnectDevice(it) }
    }

    fun sendToAll(command: String, callback: (Int, Int) -> Unit) {
        val indices = sessions.values
            .filter { it.state == DeviceState.PAIRED }
            .map { it.index }
        sendToIndices(indices, command, callback)
    }

    fun sendToIndices(indices: List<Int>, command: String, callback: (Int, Int) -> Unit) {
        val paired = indices.mapNotNull { sessions[it] }
            .filter { it.state == DeviceState.PAIRED }
        if (paired.isEmpty()) {
            callback(0, 0)
            return
        }
        var sent = 0
        var done = 0
        val total = paired.size
        paired.forEach { session ->
            sendToSession(session, command) { success ->
                if (success) sent++
                done++
                if (done == total) callback(sent, total)
            }
        }
    }

    fun isPaired(index: Int): Boolean =
        sessions[index]?.state == DeviceState.PAIRED

    fun sendToDevice(index: Int, command: String, callback: (Boolean) -> Unit) {
        val session = sessions[index] ?: run {
            callback(false)
            return
        }
        if (session.state != DeviceState.PAIRED) {
            callback(false)
            return
        }
        sendToSession(session, command, callback)
    }

    fun sendToDeviceAwaitingResponse(index: Int, command: String, callback: (String?) -> Unit) {
        val session = sessions[index] ?: run {
            callback(null)
            return
        }
        if (session.state != DeviceState.PAIRED) {
            callback(null)
            return
        }
        session.relay?.sendCommandAwaitingResponse(command, callback) ?: callback(null)
    }

    fun getPairedCount(): Int =
        sessions.values.count { it.state == DeviceState.PAIRED }

    fun getState(index: Int): DeviceState =
        sessions[index]?.state ?: DeviceState.DISCONNECTED

    private fun sendToSession(session: Session, command: String, callback: (Boolean) -> Unit) {
        session.relay?.sendCommand(command, callback)
            ?: session.lan?.send(command, callback)
            ?: callback(false)
    }

    private fun createRelayListener(index: Int, session: Session) =
        object : RelayConnection.Listener {
            override fun onRegistered() {
                session.state = DeviceState.WAITING
                listener.onDeviceStateChanged(index, DeviceState.WAITING)
                notifySummary()
            }

            override fun onPaired() {
                session.state = DeviceState.PAIRED
                listener.onDeviceStateChanged(index, DeviceState.PAIRED)
                notifySummary()
            }

            override fun onPeerDisconnected() {
                session.state = DeviceState.WAITING
                listener.onDeviceStateChanged(index, DeviceState.WAITING)
                notifySummary()
            }

            override fun onCommandReceived(command: String) = "ERROR"

            override fun onCommandResponse(response: String) {}

            override fun onScreenFrame(data: String, width: Int, height: Int) {
                screenFrameListeners[index]?.invoke(data, width, height)
            }

            override fun onError(message: String) {
                session.state = DeviceState.ERROR
                listener.onDeviceStateChanged(index, DeviceState.ERROR)
                notifySummary()
            }

            override fun onDisconnected() {
                if (session.config == null) {
                    session.state = DeviceState.DISCONNECTED
                    listener.onDeviceStateChanged(index, DeviceState.DISCONNECTED)
                    notifySummary()
                } else {
                    session.state = DeviceState.CONNECTING
                    listener.onDeviceStateChanged(index, DeviceState.CONNECTING)
                    notifySummary()
                }
            }

            override fun onReconnecting(attempt: Int) {
                session.state = DeviceState.CONNECTING
                listener.onDeviceStateChanged(index, DeviceState.CONNECTING)
            }
        }

    private fun notifySummary() {
        listener.onSummaryChanged(getPairedCount(), sessions.size)
    }
}
