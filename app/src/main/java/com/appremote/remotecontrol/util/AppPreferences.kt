package com.appremote.remotecontrol.util

import android.content.Context
import com.appremote.remotecontrol.model.DeviceConfig

object AppPreferences {
    private const val PREFS_NAME = "appremote_prefs"
    private const val KEY_RELAY_URL = "relay_url"
    private const val KEY_CONNECTION_MODE = "connection_mode"

    const val MODE_INTERNET = "internet"
    const val MODE_LAN = "lan"
    const val MAX_DEVICES = 5

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getRelayUrl(context: Context): String =
        prefs(context).getString(KEY_RELAY_URL, "") ?: ""

    fun setRelayUrl(context: Context, url: String) {
        prefs(context).edit().putString(KEY_RELAY_URL, url.trim()).apply()
    }

    fun getConnectionMode(context: Context): String =
        prefs(context).getString(KEY_CONNECTION_MODE, MODE_INTERNET) ?: MODE_INTERNET

    fun setConnectionMode(context: Context, mode: String) {
        prefs(context).edit().putString(KEY_CONNECTION_MODE, mode).apply()
    }

    fun loadDevices(context: Context): List<DeviceConfig> {
        val p = prefs(context)
        return (0 until MAX_DEVICES).map { i ->
            DeviceConfig(
                index = i,
                name = p.getString("device_${i}_name", "Máy ${i + 1}") ?: "Máy ${i + 1}",
                roomCode = p.getString("device_${i}_room", "") ?: "",
                lanIp = p.getString("device_${i}_ip", "") ?: "",
                selected = p.getBoolean("device_${i}_selected", true)
            )
        }
    }

    fun saveDevices(context: Context, devices: List<DeviceConfig>) {
        val editor = prefs(context).edit()
        devices.forEach { d ->
            editor.putString("device_${d.index}_name", d.name)
            editor.putString("device_${d.index}_room", d.roomCode)
            editor.putString("device_${d.index}_ip", d.lanIp)
            editor.putBoolean("device_${d.index}_selected", d.selected)
        }
        editor.apply()
    }

    fun getDeviceCount(context: Context): Int =
        prefs(context).getInt("device_count", MAX_DEVICES).coerceIn(1, MAX_DEVICES)

    fun setDeviceCount(context: Context, count: Int) {
        prefs(context).edit().putInt("device_count", count.coerceIn(1, MAX_DEVICES)).apply()
    }
}
