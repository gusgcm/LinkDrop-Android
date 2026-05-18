package com.linkdrop.prefs

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("linkdrop_prefs", Context.MODE_PRIVATE)

    var serverIp: String
        get()      = sp.getString("server_ip", "") ?: ""
        set(value) = sp.edit().putString("server_ip", value).apply()

    var serverPort: String
        get()      = sp.getString("server_port", "8765") ?: "8765"
        set(value) = sp.edit().putString("server_port", value).apply()

    var serverUrl: String
        get() {
            val ip = serverIp
            val port = serverPort
            return if (ip.isEmpty()) "" else "$ip:$port"
        }
        set(value) {
            if (value.contains(":")) {
                serverIp = value.substringBeforeLast(":")
                serverPort = value.substringAfterLast(":")
            } else {
                serverIp = value
                serverPort = "8765"
            }
        }

    var password: String
        get()      = sp.getString("password", "linkdrop123") ?: "linkdrop123"
        set(value) = sp.edit().putString("password", value).apply()

    var autoClipboard: Boolean
        get()      = sp.getBoolean("auto_clipboard", true)
        set(value) = sp.edit().putBoolean("auto_clipboard", value).apply()

    /** Normalize URL: ensure it starts with http:// and has no trailing slash */
    fun normalizedUrl(): String {
        var url = serverUrl.trim()
        if (url.isEmpty()) return ""
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        return url.trimEnd('/')
    }
}
