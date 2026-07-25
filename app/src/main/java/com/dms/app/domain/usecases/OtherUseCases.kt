package com.dms.app.domain.usecases

import com.dms.app.domain.interfaces.*
import com.dms.app.domain.models.*
import com.dms.app.services.timer.TimerEngine
import java.io.File
import java.time.Instant

/**
 * EvaluateTimerUseCase inspects stored check-in state and calculates current status (including Grace Period).
 */
class EvaluateTimerUseCase(
    private val storage: ISecureStorage,
    private val timerEngine: TimerEngine = TimerEngine()
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

        return timerEngine.evaluateStatusWithGrace(
            lastCheckInEpochMillis = lastEpochMillis,
            intervalMinutes = config.timerIntervalMinutes,
            gracePeriodMinutes = config.gracePeriodMinutes,
            currentTimeEpochMillis = currentEpochMillis
        )
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
 * Respects user-configured emergencyBurstCount and emergencyPauseSeconds!
 * Performs thorough Auto-Delete of sensitive emergency message text, photos, and voice notes if enabled!
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

        val burstCount = config.emergencyBurstCount.coerceIn(1, 5)
        val pauseSeconds = config.emergencyPauseSeconds.coerceIn(0, 60)

        var lastResult = DispatchResult(
            success = false,
            summary = "No dispatches performed"
        )

        for (i in 1..burstCount) {
            lastResult = emergencyDispatcher.triggerEmergencyDispatch(config, message, contacts, smtp)

            if (i < burstCount && pauseSeconds > 0) {
                try {
                    Thread.sleep(pauseSeconds * 1000L)
                } catch (ignored: Exception) {}
            }
        }

        storage.addCheckInLog(
            CheckInLog(
                timestamp = Instant.now().toString(),
                method = "SYSTEM_AUTO",
                status = if (lastResult.success) "DISPATCH_TRIGGERED" else "DISPATCH_FAILED",
                details = "${lastResult.summary} (Burst: ${burstCount}x, Delay: ${pauseSeconds}s)"
            )
        )

        // AUTO-DELETE SENSITIVE DATA AFTER DISPATCH (if enabled)
        if (config.autoDeleteAfterDispatch && lastResult.success) {
            purgeSensitiveEmergencyData(config.language)
        }

        return lastResult
    }

    /**
     * Purges all photo attachments, voice audio notes, and sensitive message body text from local device storage.
     */
    fun purgeSensitiveEmergencyData(language: String = "DE") {
        try {
            val message = storage.getEmergencyMessage()

            // 1. Delete all photo attachment files on disk
            for (path in message.attachmentPaths) {
                try {
                    val file = File(path)
                    if (file.exists()) file.delete()
                } catch (ignored: Exception) {}
            }

            // 2. Delete voice audio note file on disk
            if (message.audioNotePath.isNotBlank()) {
                try {
                    val audioFile = File(message.audioNotePath)
                    if (audioFile.exists()) audioFile.delete()
                } catch (ignored: Exception) {}
            }

            // 3. Clear message body & file attachment paths in persistent encrypted storage
            val clearedMsg = EmergencyMessage(
                id = 1,
                bodyTemplate = if (language == "EN")
                    "[AUTOMATICALLY DELETED AFTER EMERGENCY DISPATCH]"
                else
                    "[NOTFALL-NACHRICHT NACH VERSAND AUTOMATISCH GELÖSCHT]",
                containsLocation = false,
                attachmentPaths = emptyList(),
                audioNotePath = ""
            )
            storage.saveEmergencyMessage(clearedMsg)
        } catch (ignored: Exception) {}
    }
}
