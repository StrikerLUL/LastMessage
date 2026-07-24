package com.dms.app.domain.usecases

import com.dms.app.domain.interfaces.*
import com.dms.app.domain.models.*
import com.dms.app.services.watchdog.WatchdogService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * CheckInUseCase handles user check-in triggers ("I am alive"),
 * records audit log entries, updates persistent encrypted storage,
 * dispatches cloud watchdog Web-Pings, and reschedules notification thresholds.
 */
class CheckInUseCase(
    private val storage: ISecureStorage,
    private val timerEngine: ITimerEngine,
    private val notificationScheduler: INotificationScheduler,
    private val watchdogService: WatchdogService = WatchdogService()
) {

    fun executeCheckIn(method: String = "MANUAL_APP", currentTimeIso: String = Instant.now().toString()): TimerEvaluation {
        // 1. Save new timestamp in encrypted storage
        storage.saveCheckInTimestamp(currentTimeIso)

        // 2. Add audit log
        storage.addCheckInLog(
            CheckInLog(
                timestamp = currentTimeIso,
                method = method,
                status = "SUCCESS",
                details = "User check-in confirmation received"
            )
        )

        // 3. Fetch config interval
        val config = storage.getConfig()

        // 4. Send Cloud Watchdog Ping if enabled
        if (config.enableCloudWatchdog && config.watchdogPingUrl.isNotBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                watchdogService.sendPing(config.watchdogPingUrl)
            }
        }

        // 5. Calculate milestone thresholds & reschedule notifications
        val currentEpochMillis = Instant.parse(currentTimeIso).toEpochMilli()
        val milestones = timerEngine.calculateNotificationThresholds(currentEpochMillis, config.timerIntervalMinutes)
        notificationScheduler.scheduleThresholdNotifications(milestones)

        // 6. Return updated evaluation
        return timerEngine.evaluateStatus(currentEpochMillis, config.timerIntervalMinutes, currentEpochMillis)
    }
}
