package com.dms.app.services.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dms.app.domain.models.TimerStatus
import com.dms.app.services.dispatch.EmergencyDispatchEngine
import com.dms.app.services.storage.SecureStorageService
import com.dms.app.services.timer.TimerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * BootReceiver executes immediately on device boot or power connection.
 * If the countdown timer expired while the phone was powered off (or battery died),
 * it performs emergency SMS/Email dispatch right after boot!
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_POWER_CONNECTED
        ) {
            val storage = SecureStorageService(dbHelper = com.dms.app.data.local.SQLCipherHelper(context))
            val config = storage.getConfig()

            if (!config.enableBootRecovery || !config.isActive) return

            val lastCheckInIso = storage.getLastCheckInTimestamp() ?: return
            val lastEpoch = try { Instant.parse(lastCheckInIso).toEpochMilli() } catch (e: Exception) { return }

            val timerEngine = TimerEngine()
            val eval = timerEngine.evaluateStatus(
                lastCheckInEpochMillis = lastEpoch,
                intervalMinutes = config.timerIntervalMinutes,
                currentTimeEpochMillis = Instant.now().toEpochMilli()
            )

            if (eval.status == TimerStatus.EXPIRED) {
                val dispatchEngine = EmergencyDispatchEngine()
                val message = storage.getEmergencyMessage()
                val contacts = storage.getEmergencyContacts()
                val smtp = storage.getSmtpCredentials()

                CoroutineScope(Dispatchers.IO).launch {
                    dispatchEngine.triggerEmergencyDispatch(config, message, contacts, smtp)
                }
            }
        }
    }
}
