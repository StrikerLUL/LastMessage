package com.dms.app.services.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dms.app.services.notifications.NotificationScheduler
import com.dms.app.services.storage.SecureStorageService

/**
 * BatteryReceiver alerts the user when the phone battery is critically low (under 15%),
 * warning them to plug in or check in before the phone shuts down.
 */
class BatteryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        if (intent.action == Intent.ACTION_BATTERY_LOW) {
            val storage = SecureStorageService(dbHelper = com.dms.app.data.local.SQLCipherHelper(context))
            val config = storage.getConfig()

            if (!config.enableBatteryWarnings || !config.isActive) return

            val notificationScheduler = NotificationScheduler(context)
            notificationScheduler.sendWarningNotification(
                title = "⚡ AKKU FAST LEER (Unter 15%)",
                body = "Bitte Handy laden oder jetzt einchecken, um den Notfall-Countdown aktiv zu halten!"
            )
        }
    }
}
