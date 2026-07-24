package com.dms.app.domain.usecases

import com.dms.app.domain.interfaces.*
import com.dms.app.domain.models.*
import java.time.Instant

/**
 * EvaluateTimerUseCase inspects stored check-in state and calculates current status.
 */
class EvaluateTimerUseCase(
    private val storage: ISecureStorage,
    private val timerEngine: ITimerEngine
) {
    fun evaluateCurrentStatus(currentTimeIso: String = Instant.now().toString()): TimerEvaluation {
        val config = storage.getConfig()
        val lastCheckInIso = storage.getLastCheckInTimestamp() ?: currentTimeIso

        val lastEpochMillis = try {
            Instant.parse(lastCheckInIso).toEpochMilli()
        } catch (e: Exception) {
            Instant.now().toEpochMilli()
        }
        val currentEpochMillis = Instant.parse(currentTimeIso).toEpochMilli()

        return timerEngine.evaluateStatus(lastEpochMillis, config.timerIntervalMinutes, currentEpochMillis)
    }
}

/**
 * ScheduleNotificationsUseCase calculates notification thresholds and schedules alarms.
 */
class ScheduleNotificationsUseCase(
    private val storage: ISecureStorage,
    private val timerEngine: ITimerEngine,
    private val notificationScheduler: INotificationScheduler
) {
    fun rescheduleAll(currentTimeIso: String = Instant.now().toString()) {
        val config = storage.getConfig()
        if (!config.isActive) {
            notificationScheduler.cancelAllNotifications()
            return
        }

        val lastCheckInIso = storage.getLastCheckInTimestamp() ?: currentTimeIso
        val lastEpochMillis = try {
            Instant.parse(lastCheckInIso).toEpochMilli()
        } catch (e: Exception) {
            Instant.now().toEpochMilli()
        }

        val milestones = timerEngine.calculateNotificationThresholds(lastEpochMillis, config.timerIntervalMinutes)
        notificationScheduler.scheduleThresholdNotifications(milestones)
    }
}

/**
 * DispatchEmergencyUseCase triggers autonomous emergency SMS/Email dispatches.
 */
class DispatchEmergencyUseCase(
    private val storage: ISecureStorage,
    private val emergencyDispatcher: IEmergencyDispatcher
) {
    fun executeEmergencyDispatch(): DispatchResult {
        val config = storage.getConfig()
        val contacts = storage.getEmergencyContacts().filter { it.isEnabled }.sortedBy { it.priority }
        val smtp = storage.getSmtpCredentials()
        val message = storage.getEmergencyMessage()

        val result = emergencyDispatcher.triggerEmergencyDispatch(config, message, contacts, smtp)

        storage.addCheckInLog(
            CheckInLog(
                timestamp = Instant.now().toString(),
                method = "SYSTEM_AUTO",
                status = if (result.success) "DISPATCH_TRIGGERED" else "DISPATCH_FAILED",
                details = result.summary
            )
        )

        return result
    }
}
