package com.dms.app.services.timer

import com.dms.app.domain.interfaces.ITimerEngine
import com.dms.app.domain.models.*
import java.time.Instant

/**
 * TimerEngine provides pure mathematical calculation of countdown duration,
 * Grace Period (Gnadenfrist) evaluation, milestone thresholds, and status evaluation across app restarts/reboots.
 */
class TimerEngine : ITimerEngine {

    companion object {
        const val MILLIS_PER_MINUTE = 60_000L
        const val MILLIS_PER_HOUR = 3600_000L
        const val MAX_INTERVAL_MINUTES = 10080L // 7 days max
    }

    private fun safeIntervalMinutes(intervalMinutes: Long): Long {
        return intervalMinutes.coerceIn(1L, MAX_INTERVAL_MINUTES)
    }

    override fun getRemainingDurationMinutes(
        lastCheckInEpochMillis: Long,
        intervalMinutes: Long,
        currentTimeEpochMillis: Long
    ): Long {
        val validInterval = safeIntervalMinutes(intervalMinutes)
        val intervalMillis = Math.multiplyExact(validInterval, MILLIS_PER_MINUTE)
        val expiryEpochMillis = lastCheckInEpochMillis + intervalMillis
        val remainingMillis = expiryEpochMillis - currentTimeEpochMillis
        return if (remainingMillis <= 0) 0L else remainingMillis / MILLIS_PER_MINUTE
    }

    override fun evaluateStatus(
        lastCheckInEpochMillis: Long,
        intervalMinutes: Long,
        currentTimeEpochMillis: Long
    ): TimerEvaluation {
        return evaluateStatusWithGrace(lastCheckInEpochMillis, intervalMinutes, 360L, currentTimeEpochMillis)
    }

    fun evaluateStatusWithGrace(
        lastCheckInEpochMillis: Long,
        intervalMinutes: Long,
        gracePeriodMinutes: Long = 360L,
        currentTimeEpochMillis: Long
    ): TimerEvaluation {
        val validInterval = safeIntervalMinutes(intervalMinutes)
        val validGrace = gracePeriodMinutes.coerceAtLeast(0L)

        val intervalMillis = Math.multiplyExact(validInterval, MILLIS_PER_MINUTE)
        val graceMillis = Math.multiplyExact(validGrace, MILLIS_PER_MINUTE)

        val intervalExpiryEpochMillis = lastCheckInEpochMillis + intervalMillis
        val finalExpiryEpochMillis = intervalExpiryEpochMillis + graceMillis

        val remainingIntervalMillis = intervalExpiryEpochMillis - currentTimeEpochMillis
        val remainingGraceMillis = finalExpiryEpochMillis - currentTimeEpochMillis
        val elapsedMillis = currentTimeEpochMillis - lastCheckInEpochMillis

        val remainingMinutes = if (remainingIntervalMillis <= 0) 0L else remainingIntervalMillis / MILLIS_PER_MINUTE
        val remainingGraceMinutes = if (remainingGraceMillis <= 0) 0L else remainingGraceMillis / MILLIS_PER_MINUTE
        val elapsedMinutes = if (elapsedMillis <= 0) 0L else elapsedMillis / MILLIS_PER_MINUTE

        val warningThresholdMinutes = minOf(60L, (validInterval * 0.25).toLong())

        val status = when {
            currentTimeEpochMillis >= finalExpiryEpochMillis -> TimerStatus.EXPIRED
            currentTimeEpochMillis >= intervalExpiryEpochMillis -> TimerStatus.GRACE_PERIOD
            remainingMinutes <= warningThresholdMinutes -> TimerStatus.WARNING
            else -> TimerStatus.ACTIVE
        }

        return TimerEvaluation(
            status = status,
            remainingMinutes = remainingMinutes,
            elapsedMinutes = elapsedMinutes,
            remainingGraceMinutes = remainingGraceMinutes,
            expiryTimestampEpochMillis = finalExpiryEpochMillis,
            lastCheckInTimestampEpochMillis = lastCheckInEpochMillis
        )
    }

    override fun calculateNotificationThresholds(
        lastCheckInEpochMillis: Long,
        intervalMinutes: Long
    ): List<MilestoneThreshold> {
        if (intervalMinutes <= 0L) return emptyList()

        val validInterval = safeIntervalMinutes(intervalMinutes)
        val intervalMillis = Math.multiplyExact(validInterval, MILLIS_PER_MINUTE)
        val expiryEpochMillis = lastCheckInEpochMillis + intervalMillis

        val milestones = mutableListOf<MilestoneThreshold>()

        // 75% remaining
        val t75Millis = expiryEpochMillis - (intervalMillis * 0.75).toLong()
        milestones.add(MilestoneThreshold("75_PERCENT", 0.75, t75Millis, (validInterval * 0.75).toLong()))

        // 50% remaining
        val t50Millis = expiryEpochMillis - (intervalMillis * 0.50).toLong()
        milestones.add(MilestoneThreshold("50_PERCENT", 0.50, t50Millis, (validInterval * 0.50).toLong()))

        // 25% remaining
        val t25Millis = expiryEpochMillis - (intervalMillis * 0.25).toLong()
        milestones.add(MilestoneThreshold("25_PERCENT", 0.25, t25Millis, (validInterval * 0.25).toLong()))

        // 10% remaining
        val t10Millis = expiryEpochMillis - (intervalMillis * 0.10).toLong()
        milestones.add(MilestoneThreshold("10_PERCENT", 0.10, t10Millis, (validInterval * 0.10).toLong()))

        // 1 hour remaining
        if (validInterval > 60L) {
            milestones.add(MilestoneThreshold("1_HOUR", (60.0 / validInterval), expiryEpochMillis - MILLIS_PER_HOUR, 60L))
        }

        return milestones.sortedBy { it.triggerTimeEpochMillis }
    }
}
