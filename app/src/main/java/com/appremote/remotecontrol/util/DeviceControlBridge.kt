package com.appremote.remotecontrol.util

import com.appremote.remotecontrol.network.MultiDeviceManager

object DeviceControlBridge {
    @Volatile
    var deviceManager: MultiDeviceManager? = null
}
