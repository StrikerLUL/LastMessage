package com.dms.app.domain.usecases

import android.content.Context
import com.dms.app.domain.interfaces.*
import com.dms.app.domain.models.*
import com.dms.app.services.dispatch.EmergencyDispatchEngine
import com.dms.app.services.location.GpsLocationProvider
import com.dms.app.services.watchdog.WatchdogService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * CheckInUseCase handles user check-in triggers ("I am alive"), Panic PIN duress dispatches,
 * automatic GPS location capture, audit log recording, persistent encrypted storage updates, cloud watchdog Web-Pings, and notification rescheduling.
 */
class CheckInUseCase(
    private val storage: ISecureStorage,
    private val timerEngine: ITimerEngine,
    private val notificationScheduler: INotificationScheduler,
    private val watchdogService: WatchdogService = WatchdogService(),
    private val context: Context? = null
) {

    fun executeCheckIn(method: String = "MANUAL_APP", currentTimeIso: String = Instant.now().toString()): TimerEvaluation {
        // 1. Save new timestamp in encrypted storage
        storage.saveCheckInTimestamp(currentTimeIso)

        // 2. Fetch config
        val config = storage.getConfig()

        // 3. Automatically capture live GPS location if GPS feature is enabled
        if (config.enableGpsLocation && context != null) {
            try {
                val liveGpsUrl = GpsLocationProvider(context).getCurrentOrLastKnownLocationUrl()
                if (!liveGpsUrl.isNullOrBlank()) {
                    val updatedConfig = config.copy(lastKnownLocationUrl = liveGpsUrl)
                    storage.saveConfig(updatedConfig)
                }
            } catch (ignored: Exception) {}
        }

        // 4. Add audit log
        storage.addCheckInLog(
            CheckInLog(
                timestamp = currentTimeIso,
                method = method,
                status = "SUCCESS",
                details = "User check-in confirmation received"
            )
        )

        // 5. Send Cloud Watchdog Ping if enabled
        if (config.enableCloudWatchdog && config.watchdogPingUrl.isNotBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                watchdogService.sendPing(config.watchdogPingUrl)
            }
        }

        // 6. Calculate milestone thresholds & reschedule notifications
        val currentEpochMillis = Instant.parse(currentTimeIso).toEpochMilli()
        val milestones = timerEngine.calculateNotificationThresholds(currentEpochMillis, config.timerIntervalMinutes)
        notificationScheduler.scheduleThresholdNotifications(milestones)

        // 7. Return updated evaluation
        return timerEngine.evaluateStatus(currentEpochMillis, config.timerIntervalMinutes, currentEpochMillis)
    }

    /**
     * PANIC PIN / DURESS TRIGGER:
     * Feigns a successful check-in to the user, but SECRETLY launches immediate emergency SMS & Email dispatch!
     */
    fun executePanicCheckIn(currentTimeIso: String = Instant.now().toString()): TimerEvaluation {
        // 1. Secretly trigger emergency dispatch in background coroutine
        CoroutineScope(Dispatchers.IO).launch {
            val emergencyDispatcher = EmergencyDispatchEngine(context = context)
            val dispatchUseCase = DispatchEmergencyUseCase(storage, emergencyDispatcher)
            dispatchUseCase.executeEmergencyDispatch()
        }

        // 2. Add audit log
        storage.addCheckInLog(
            CheckInLog(
                timestamp = currentTimeIso,
                method = "PANIC_PIN_DURESS",
                status = "PANIC_DISPATCH_TRIGGERED",
                details = "Panic PIN entered. Secret emergency dispatch launched!"
            )
        )

        // 3. Visually reset timer so app feigns success
        storage.saveCheckInTimestamp(currentTimeIso)
        val config = storage.getConfig()
        val currentEpochMillis = Instant.parse(currentTimeIso).toEpochMilli()

        return timerEngine.evaluateStatus(currentEpochMillis, config.timerIntervalMinutes, currentEpochMillis)
    }
}
