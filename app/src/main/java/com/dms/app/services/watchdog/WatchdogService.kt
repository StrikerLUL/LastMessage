package com.dms.app.services.watchdog

import java.net.HttpURLConnection
import java.net.URL

/**
 * WatchdogService handles sending free HTTP ping heartbeats to external monitoring webhooks
 * (such as Healthchecks.io or ntfy.sh) on check-in.
 */
class WatchdogService {

    fun sendPing(pingUrl: String): Boolean {
        if (pingUrl.isBlank() || (!pingUrl.startsWith("http://") && !pingUrl.startsWith("https://"))) {
            return false
        }
        return try {
            val url = URL(pingUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 7000
            connection.readTimeout = 7000
            connection.setRequestProperty("User-Agent", "DeadMansSwitch-AndroidApp/1.0")
            val responseCode = connection.responseCode
            connection.disconnect()
            responseCode in 200..399
        } catch (e: Exception) {
            false
        }
    }
}
