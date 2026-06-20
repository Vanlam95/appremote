package com.appremote.remotecontrol.model

data class DeviceConfig(
    val index: Int,
    var name: String,
    var roomCode: String,
    var lanIp: String = "",
    var selected: Boolean = true
)

enum class DeviceState {
    DISCONNECTED,
    CONNECTING,
    WAITING,
    PAIRED,
    ERROR
}
