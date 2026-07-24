package com.dms.app.services.timer

import com.dms.app.domain.interfaces.ITimerEngine
import com.dms.app.domain.models.*
import java.time.Instant

/**
 * TimerEngine provides pure mathematical calculation of countdown duration,
 * notification milestone thresholds, and timer status evaluation across app restarts/reboots.
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
        val validInterval = safeIntervalMinutes(intervalMinutes)
        val intervalMillis = Math.multiplyExact(validInterval, MILLIS_PER_MINUTE)
        val expiryEpochMillis = lastCheckInEpochMillis + intervalMillis
        val remainingMillis = expiryEpochMillis - currentTimeEpochMillis
        val elapsedMillis = currentTimeEpochMillis - lastCheckInEpochMillis

        val remainingMinutes = if (remainingMillis <= 0) 0L else remainingMillis / MILLIS_PER_MINUTE
        val elapsedMinutes = if (elapsedMillis <= 0) 0L else elapsedMillis / MILLIS_PER_MINUTE

        val warningThresholdMinutes = minOf(60L, (validInterval * 0.25).toLong())

        val status = when {
            currentTimeEpochMillis >= expiryEpochMillis -> TimerStatus.EXPIRED
            remainingMinutes <= warningThresholdMinutes -> TimerStatus.WARNING
            else -> TimerStatus.ACTIVE
        }

        return TimerEvaluation(
            status = status,
            remainingMinutes = remainingMinutes,
            elapsedMinutes = elapsedMinutes,
            expiryTimestampEpochMillis = expiryEpochMillis,
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

        // 75% remaining: (Expiry - (interval * 0.75))
        val t75Millis = expiryEpochMillis - (intervalMillis * 0.75).toLong()
        val t75RemMins = (validInterval * 0.75).toLong()
        milestones.add(MilestoneThreshold("75_PERCENT", 0.75, t75Millis, t75RemMins))

        // 50% remaining: (Expiry - (interval * 0.50))
        val t50Millis = expiryEpochMillis - (intervalMillis * 0.50).toLong()
        val t50RemMins = (validInterval * 0.50).toLong()
        milestones.add(MilestoneThreshold("50_PERCENT", 0.50, t50Millis, t50RemMins))

        // 25% remaining: (Expiry - (interval * 0.25))
        val t25Millis = expiryEpochMillis - (intervalMillis * 0.25).toLong()
        val t25RemMins = (validInterval * 0.25).toLong()
        milestones.add(MilestoneThreshold("25_PERCENT", 0.25, t25Millis, t25RemMins))

        // 10% remaining: (Expiry - (interval * 0.10))
        val t10Millis = expiryEpochMillis - (intervalMillis * 0.10).toLong()
        val t10RemMins = (validInterval * 0.10).toLong()
        milestones.add(MilestoneThreshold("10_PERCENT", 0.10, t10Millis, t10RemMins))

        // 1 hour remaining: Only include if interval > 60 mins
        if (validInterval > 60L) {
            val t1hMillis = expiryEpochMillis - MILLIS_PER_HOUR
            val t1hRemMins = 60L
            milestones.add(MilestoneThreshold("1_HOUR", (60.0 / validInterval), t1hMillis, t1hRemMins))
        }

        return milestones.sortedBy { it.triggerTimeEpochMillis }
    }
}
