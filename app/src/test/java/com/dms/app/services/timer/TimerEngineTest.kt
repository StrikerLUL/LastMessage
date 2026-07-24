package com.dms.app.services.timer

import com.dms.app.domain.models.TimerStatus
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TimerEngineTest {

    private lateinit var timerEngine: TimerEngine

    @BeforeEach
    fun setUp() {
        timerEngine = TimerEngine()
    }

    @Test
    fun testRemainingDurationCalculation() {
        val baseTimeEpochMillis = 1700000000000L
        val intervalMinutes = 1440L // 24 hours

        // 1. Immediately after check-in (0 mins elapsed) -> 1440 mins remaining
        val rem0 = timerEngine.getRemainingDurationMinutes(baseTimeEpochMillis, intervalMinutes, baseTimeEpochMillis)
        assertEquals(1440L, rem0)

        // 2. 12 hours (720 mins) elapsed -> 720 mins remaining
        val current12h = baseTimeEpochMillis + (720 * 60 * 1000L)
        val rem12h = timerEngine.getRemainingDurationMinutes(baseTimeEpochMillis, intervalMinutes, current12h)
        assertEquals(720L, rem12h)

        // 3. 23 hours (1380 mins) elapsed -> 60 mins remaining
        val current23h = baseTimeEpochMillis + (1380 * 60 * 1000L)
        val rem23h = timerEngine.getRemainingDurationMinutes(baseTimeEpochMillis, intervalMinutes, current23h)
        assertEquals(60L, rem23h)

        // 4. 24 hours (1440 mins) elapsed -> 0 mins remaining
        val current24h = baseTimeEpochMillis + (1440 * 60 * 1000L)
        val rem24h = timerEngine.getRemainingDurationMinutes(baseTimeEpochMillis, intervalMinutes, current24h)
        assertEquals(0L, rem24h)

        // 5. Past expiry -> 0 mins remaining (clamped)
        val current25h = baseTimeEpochMillis + (1500 * 60 * 1000L)
        val rem25h = timerEngine.getRemainingDurationMinutes(baseTimeEpochMillis, intervalMinutes, current25h)
        assertEquals(0L, rem25h)
    }

    @Test
    fun testThresholdMilestoneCalculation() {
        val baseTimeEpochMillis = 1700000000000L
        val intervalMinutes = 1440L // 24 hours = 86,400,000 ms

        val thresholds = timerEngine.calculateNotificationThresholds(baseTimeEpochMillis, intervalMinutes)
        assertEquals(5, thresholds.size)

        assertTrue(thresholds[0].triggerTimeEpochMillis <= thresholds[1].triggerTimeEpochMillis)

        val t75 = thresholds.first { it.milestoneName == "75_PERCENT" }
        val t50 = thresholds.first { it.milestoneName == "50_PERCENT" }
        val t25 = thresholds.first { it.milestoneName == "25_PERCENT" }
        val t10 = thresholds.first { it.milestoneName == "10_PERCENT" }
        val t1h = thresholds.first { it.milestoneName == "1_HOUR" }

        // 75% remaining: triggers at base + 6 hours
        assertEquals(baseTimeEpochMillis + (6 * 3600 * 1000L), t75.triggerTimeEpochMillis)
        assertEquals(1080L, t75.remainingMinutes)

        // 50% remaining: triggers at base + 12 hours
        assertEquals(baseTimeEpochMillis + (12 * 3600 * 1000L), t50.triggerTimeEpochMillis)
        assertEquals(720L, t50.remainingMinutes)

        // 25% remaining: triggers at base + 18 hours
        assertEquals(baseTimeEpochMillis + (18 * 3600 * 1000L), t25.triggerTimeEpochMillis)
        assertEquals(360L, t25.remainingMinutes)

        // 10% remaining: triggers at base + 21.6 hours
        assertEquals(baseTimeEpochMillis + (77760 * 1000L), t10.triggerTimeEpochMillis)
        assertEquals(144L, t10.remainingMinutes)

        // 1 hour remaining: triggers at base + 23 hours
        assertEquals(baseTimeEpochMillis + (23 * 3600 * 1000L), t1h.triggerTimeEpochMillis)
        assertEquals(60L, t1h.remainingMinutes)
    }

    @Test
    fun testShortIntervalMilestonesAndWarningThreshold() {
        val baseTimeEpochMillis = 1700000000000L

        // Short interval (30 mins <= 60 mins): should NOT include 1_HOUR milestone
        val thresholds30 = timerEngine.calculateNotificationThresholds(baseTimeEpochMillis, 30L)
        assertEquals(4, thresholds30.size)
        assertFalse(thresholds30.any { it.milestoneName == "1_HOUR" })

        // Short interval (30 mins) status at start: remaining = 30, warning threshold = min(60, 7.5) = 7.5 mins.
        // Status must be ACTIVE!
        val evalActive30 = timerEngine.evaluateStatus(baseTimeEpochMillis, 30L, baseTimeEpochMillis)
        assertEquals(TimerStatus.ACTIVE, evalActive30.status)
    }

    @Test
    fun testStatusEvaluation() {
        val baseTimeEpochMillis = 1700000000000L
        val intervalMinutes = 1440L

        // Active state at start
        val evalActive = timerEngine.evaluateStatus(baseTimeEpochMillis, intervalMinutes, baseTimeEpochMillis)
        assertEquals(TimerStatus.ACTIVE, evalActive.status)
        assertEquals(1440L, evalActive.remainingMinutes)

        // Warning state at 23 hours elapsed (60m remaining <= 60)
        val current23h = baseTimeEpochMillis + (23 * 3600 * 1000L)
        val evalWarning = timerEngine.evaluateStatus(baseTimeEpochMillis, intervalMinutes, current23h)
        assertEquals(TimerStatus.WARNING, evalWarning.status)

        // Expired state at 24 hours elapsed
        val current24h = baseTimeEpochMillis + (24 * 3600 * 1000L)
        val evalExpired = timerEngine.evaluateStatus(baseTimeEpochMillis, intervalMinutes, current24h)
        assertEquals(TimerStatus.EXPIRED, evalExpired.status)
    }

    @Test
    fun testCustomIntervalsAndOverflowProtection() {
        val baseTimeEpochMillis = 1700000000000L

        // 12 hours (720 mins)
        val rem12h = timerEngine.getRemainingDurationMinutes(baseTimeEpochMillis, 720L, baseTimeEpochMillis)
        assertEquals(720L, rem12h)

        // 7 days (10080 mins)
        val rem7d = timerEngine.getRemainingDurationMinutes(baseTimeEpochMillis, 10080L, baseTimeEpochMillis)
        assertEquals(10080L, rem7d)

        // Extremely large interval clamped to MAX_INTERVAL_MINUTES (10080L) without Long overflow
        val remOverflow = timerEngine.getRemainingDurationMinutes(baseTimeEpochMillis, Long.MAX_VALUE, baseTimeEpochMillis)
        assertEquals(10080L, remOverflow)
    }
}
