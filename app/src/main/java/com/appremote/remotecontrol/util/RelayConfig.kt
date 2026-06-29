package com.appremote.remotecontrol.util

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Relay URL không hiển thị trong UI. Giá trị mặc định được mã hóa XOR trong APK.
 * Có thể cập nhật URL từ server qua /relay-config mà không cần sửa app.
 */
object RelayConfig {

    private const val XOR_KEY = 0x5A

    // wss://appremote-07ys.onrender.com (XOR 0x5A) — không lưu plain text
    private val EMBEDDED = intArrayOf(
        45, 41, 41, 96, 117, 117, 59, 42, 42, 40, 63, 55, 53, 46, 63, 119,
        106, 109, 35, 41, 116, 53, 52, 40, 63, 52, 62, 63, 40, 116, 57, 53, 55
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var resolvedUrl: String? = null

    fun getRelayUrl(context: Context): String {
        resolvedUrl?.let { return it }
        val embedded = decodeEmbedded()
        resolvedUrl = embedded
        refreshFromServerAsync(embedded)
        return embedded
    }

    private fun decodeEmbedded(): String =
        EMBEDDED.map { (it xor XOR_KEY).toChar() }.joinToString("")

    private fun refreshFromServerAsync(fallbackWss: String) {
        scope.launch {
            runCatching {
                val bootstrap = toHttpsBootstrap(fallbackWss)
                val request = Request.Builder()
                    .url("$bootstrap/relay-config")
                    .get()
                    .build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching
                    val body = response.body?.string() ?: return@runCatching
                    val relay = JSONObject(body).optString("relay").trim()
                    if (relay.startsWith("ws://") || relay.startsWith("wss://")) {
                        resolvedUrl = relay
                    }
                }
            }
        }
    }

    private fun toHttpsBootstrap(wssUrl: String): String {
        var url = wssUrl.trim().removeSuffix("/")
        url = when {
            url.startsWith("wss://") -> "https://" + url.removePrefix("wss://")
            url.startsWith("ws://") -> "http://" + url.removePrefix("ws://")
            url.startsWith("https://") -> url
            else -> "https://$url"
        }
        return url
    }
}
