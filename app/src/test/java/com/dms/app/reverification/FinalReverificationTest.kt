package com.dms.app.reverification

import com.dms.app.data.local.KeyStoreManager
import com.dms.app.data.local.SQLCipherHelper
import com.dms.app.domain.models.*
import com.dms.app.domain.usecases.DispatchEmergencyUseCase
import com.dms.app.domain.usecases.EvaluateTimerUseCase
import com.dms.app.domain.usecases.ScheduleNotificationsUseCase
import com.dms.app.services.dispatch.EmergencyDispatchEngine
import com.dms.app.services.dispatch.SmsDispatcher
import com.dms.app.services.dispatch.SmtpMailer
import com.dms.app.services.notifications.NotificationScheduler
import com.dms.app.services.storage.SecureStorageService
import com.dms.app.services.timer.TimerEngine
import com.dms.app.services.workmanager.BootReceiver
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class FinalReverificationTest {

    private lateinit var smsDispatcher: SmsDispatcher
    private lateinit var smtpMailer: SmtpMailer
    private lateinit var dispatchEngine: EmergencyDispatchEngine
    private lateinit var timerEngine: TimerEngine
    private lateinit var storage: SecureStorageService
    private lateinit var evaluateTimerUseCase: EvaluateTimerUseCase
    private lateinit var scheduleNotificationsUseCase: ScheduleNotificationsUseCase
    private lateinit var dispatchEmergencyUseCase: DispatchEmergencyUseCase
    private lateinit var bootReceiver: BootReceiver

    @BeforeEach
    fun setUp() {
        smsDispatcher = SmsDispatcher()
        smtpMailer = SmtpMailer()
        dispatchEngine = EmergencyDispatchEngine(smsDispatcher, smtpMailer)
        timerEngine = TimerEngine()

        val dbHelper = SQLCipherHelper(dbPath = "jdbc:sqlite::memory:")
        storage = SecureStorageService(KeyStoreManager("test_reverif_key"), dbHelper)
        evaluateTimerUseCase = EvaluateTimerUseCase(storage, timerEngine)
        scheduleNotificationsUseCase = ScheduleNotificationsUseCase(storage, timerEngine, NotificationScheduler())
        dispatchEmergencyUseCase = DispatchEmergencyUseCase(storage, dispatchEngine)
        bootReceiver = BootReceiver(scheduleNotificationsUseCase, evaluateTimerUseCase, dispatchEmergencyUseCase)
    }

    // --- Task 1.1: DispatchServices.kt ---

    @Test
    fun testSmsThenEmailOnlyTriggersEmailOnSmsFailure() {
        val config = DmsConfig(primaryDispatchMethod = "SMS_THEN_EMAIL", retryCount = 3)
        val message = EmergencyMessage(bodyTemplate = "Emergency Alert Test")
        val contacts = listOf(
            EmergencyContact(id = 1L, recipientName = "Contact 1", phoneNumber = "+15550100", emailAddress = "contact1@example.com", priority = 1)
        )
        val smtp = SmtpCredentials(host = "smtp.example.com", port = 587, username = "alert@example.com", passwordEncrypted = "pass")

        smtpMailer.delayProvider = { }

        // Scenario A: SMS Succeeds -> Email MUST NOT be sent
        val dispatchResultSmsSuccess = dispatchEngine.triggerEmergencyDispatch(config, message, contacts, smtp)
        assertTrue(dispatchResultSmsSuccess.success)
        assertEquals(1, dispatchResultSmsSuccess.smsResults.size)
        assertTrue(dispatchResultSmsSuccess.smsResults[0].success)
        assertTrue(dispatchResultSmsSuccess.emailResults.isEmpty(), "Email should NOT be triggered when SMS succeeds in SMS_THEN_EMAIL mode")

        // Scenario B: SMS Fails (blank phone number) -> Email MUST be triggered
        val contactsFailingSms = listOf(
            EmergencyContact(id = 2L, recipientName = "Contact 2", phoneNumber = "", emailAddress = "contact2@example.com", priority = 1)
        )
        val dispatchResultSmsFailure = dispatchEngine.triggerEmergencyDispatch(config, message, contactsFailingSms, smtp)
        assertEquals(1, dispatchResultSmsFailure.smsResults.size)
        assertFalse(dispatchResultSmsFailure.smsResults[0].success)
        assertEquals(1, dispatchResultSmsFailure.emailResults.size, "Email MUST be triggered when SMS fails in SMS_THEN_EMAIL mode")
    }

    @Test
    fun testPreflightSmtpValidation() {
        // Blank host
        val invalidHostSmtp = SmtpCredentials(host = "", port = 587, username = "user@example.com", passwordEncrypted = "pass")
        val resultBlankHost = smtpMailer.sendSmtpEmailWithRetry(invalidHostSmtp, "recipient@example.com", "Msg")
        assertFalse(resultBlankHost.success)
        assertEquals(0, resultBlankHost.attemptCount)
        assertEquals("Invalid SMTP host or port configuration", resultBlankHost.errorMessage)

        // Invalid port <= 0
        val invalidPortSmtp = SmtpCredentials(host = "smtp.example.com", port = 0, username = "user@example.com", passwordEncrypted = "pass")
        val resultInvalidPort = smtpMailer.sendSmtpEmailWithRetry(invalidPortSmtp, "recipient@example.com", "Msg")
        assertFalse(resultInvalidPort.success)
        assertEquals(0, resultInvalidPort.attemptCount)
        assertEquals("Invalid SMTP host or port configuration", resultInvalidPort.errorMessage)

        // Invalid port > 65535
        val outOfBoundsPortSmtp = SmtpCredentials(host = "smtp.example.com", port = 70000, username = "user@example.com", passwordEncrypted = "pass")
        val resultOutOfBoundsPort = smtpMailer.sendSmtpEmailWithRetry(outOfBoundsPortSmtp, "recipient@example.com", "Msg")
        assertFalse(resultOutOfBoundsPort.success)
        assertEquals(0, resultOutOfBoundsPort.attemptCount)
        assertEquals("Invalid SMTP host or port configuration", resultOutOfBoundsPort.errorMessage)

        // Blank recipient email
        val validSmtp = SmtpCredentials(host = "smtp.example.com", port = 587, username = "user@example.com", passwordEncrypted = "pass")
        val resultBlankRecipient = smtpMailer.sendSmtpEmailWithRetry(validSmtp, "", "Msg")
        assertFalse(resultBlankRecipient.success)
        assertEquals(0, resultBlankRecipient.attemptCount)
        assertEquals("Empty recipient email", resultBlankRecipient.errorMessage)
    }

    @Test
    fun testNonBlockingBackoffSchedule() {
        val smtp = SmtpCredentials(host = "smtp.example.com", port = 587, username = "user@example.com", passwordEncrypted = "pass")
        val recordedDelays = mutableListOf<Long>()
        smtpMailer.delayProvider = { delayMs -> recordedDelays.add(delayMs) }

        // Simulate 2 transient failures, succeeding on attempt 3
        val result = smtpMailer.sendSmtpEmailWithRetry(smtp, "test@example.com", "Body", maxRetries = 3, simulateFailuresBeforeSuccess = 2)

        assertTrue(result.success)
        assertEquals(3, result.attemptCount)
        assertEquals(2, recordedDelays.size)
        assertEquals(5000L, recordedDelays[0], "Attempt 2 delay must be 5000ms")
        assertEquals(15000L, recordedDelays[1], "Attempt 3 delay must be 15000ms")
    }

    @Test
    fun testGsmUdh153CharSplitLogic() {
        // 1. Single part GSM-7 (<= 160 chars)
        val gsm160 = "A".repeat(160)
        val partsGsm160 = smsDispatcher.divideMessageText(gsm160)
        assertEquals(1, partsGsm160.size)
        assertEquals(160, partsGsm160[0].length)

        // 2. Multi part GSM-7 (161 chars) -> 153 chars + 8 chars
        val gsm161 = "A".repeat(161)
        val partsGsm161 = smsDispatcher.divideMessageText(gsm161)
        assertEquals(2, partsGsm161.size)
        assertEquals(153, partsGsm161[0].length)
        assertEquals(8, partsGsm161[1].length)

        // 3. Single part UCS-2 Unicode (<= 70 chars)
        val ucs70 = "€".repeat(70)
        val partsUcs70 = smsDispatcher.divideMessageText(ucs70)
        assertEquals(1, partsUcs70.size)
        assertEquals(70, partsUcs70[0].length)

        // 4. Multi part UCS-2 Unicode (71 chars) -> 67 chars + 4 chars
        val ucs71 = "€".repeat(71)
        val partsUcs71 = smsDispatcher.divideMessageText(ucs71)
        assertEquals(2, partsUcs71.size)
        assertEquals(67, partsUcs71[0].length)
        assertEquals(4, partsUcs71[1].length)
    }

    // --- Task 1.2: TimerEngine.kt ---

    @Test
    fun testShortIntervalsEvaluateToActiveUponCheckIn() {
        val now = System.currentTimeMillis()

        for (interval in listOf(15L, 30L, 45L, 60L)) {
            val eval = timerEngine.evaluateStatus(now, interval, now)
            assertEquals(TimerStatus.ACTIVE, eval.status, "Short interval ${interval}m at check-in must evaluate to ACTIVE")
            assertEquals(interval, eval.remainingMinutes)
            assertEquals(0L, eval.elapsedMinutes)
        }
    }

    @Test
    fun testZeroMinuteDurationGuards() {
        val now = System.currentTimeMillis()

        // 0-minute duration in getRemainingDurationMinutes (coerced to 1 min safe interval)
        val rem0 = timerEngine.getRemainingDurationMinutes(now, 0L, now)
        assertEquals(1L, rem0)

        // 0-minute duration in evaluateStatus
        val eval0 = timerEngine.evaluateStatus(now, 0L, now)
        assertEquals(TimerStatus.ACTIVE, eval0.status)
        assertEquals(1L, eval0.remainingMinutes)

        // 0-minute duration in calculateNotificationThresholds returns empty list safely
        val thresholds0 = timerEngine.calculateNotificationThresholds(now, 0L)
        assertTrue(thresholds0.isEmpty(), "0-minute interval should return empty milestone list")

        // Negative duration returns empty list safely
        val thresholdsNeg = timerEngine.calculateNotificationThresholds(now, -15L)
        assertTrue(thresholdsNeg.isEmpty(), "Negative interval should return empty milestone list")
    }

    @Test
    fun testOneHourMilestoneInclusionGuard() {
        val now = System.currentTimeMillis()

        // <= 60L minutes -> 1_HOUR milestone MUST NOT be included
        val milestones15 = timerEngine.calculateNotificationThresholds(now, 15L)
        assertFalse(milestones15.any { it.milestoneName == "1_HOUR" })

        val milestones30 = timerEngine.calculateNotificationThresholds(now, 30L)
        assertFalse(milestones30.any { it.milestoneName == "1_HOUR" })

        val milestones60 = timerEngine.calculateNotificationThresholds(now, 60L)
        assertFalse(milestones60.any { it.milestoneName == "1_HOUR" })

        // > 60L minutes -> 1_HOUR milestone MUST be included
        val milestones120 = timerEngine.calculateNotificationThresholds(now, 120L)
        val m1h = milestones120.find { it.milestoneName == "1_HOUR" }
        assertNotNull(m1h)
        assertEquals(now + (60 * 60 * 1000L), m1h!!.triggerTimeEpochMillis)
        assertEquals(60L, m1h.remainingMinutes)
        assertEquals(0.5, m1h.percentageRemaining, 0.001)
    }

    @Test
    fun testIntegerOverflowProtection() {
        val now = System.currentTimeMillis()

        // Long.MAX_VALUE clamped to MAX_INTERVAL_MINUTES (10080L)
        val remMax = timerEngine.getRemainingDurationMinutes(now, Long.MAX_VALUE, now)
        assertEquals(10080L, remMax)

        val evalMax = timerEngine.evaluateStatus(now, Long.MAX_VALUE, now)
        assertEquals(TimerStatus.ACTIVE, evalMax.status)
        assertEquals(10080L, evalMax.remainingMinutes)

        // Overflow interval (Long.MAX_VALUE / 60000L + 1L)
        val overflowVal = (Long.MAX_VALUE / 60000L) + 1L
        val remOverflow = timerEngine.getRemainingDurationMinutes(now, overflowVal, now)
        assertEquals(10080L, remOverflow)
    }

    // --- Task 1.3: BootReceiver.kt ---

    @Test
    fun testPostRebootCheckTriggersEmergencyDispatchWhenTimerExpiredWhilePoweredOff() {
        // Setup timer as expired while device was powered off:
        // last check-in 25 hours ago, interval 24 hours (1440m)
        val twentyFiveHoursAgo = Instant.now().minusSeconds(25 * 3600).toString()
        storage.saveCheckInTimestamp(twentyFiveHoursAgo)

        // Add 1 enabled contact to storage
        storage.addEmergencyContact(
            EmergencyContact(id = 1L, recipientName = "Bob", phoneNumber = "+15550199", emailAddress = "bob@example.com", priority = 1, isEnabled = true)
        )

        // Execute boot action
        val bootResult = bootReceiver.onReceiveIntent(BootReceiver.ACTION_BOOT_COMPLETED)

        assertTrue(bootResult is BootReceiver.BootResult.Handled)
        val details = (bootResult as BootReceiver.BootResult.Handled).details
        assertTrue(details.contains("MISSED EXPIRY DISPATCH TRIGGERED"), "BootResult details should report missed expiry dispatch")

        // Verify check-in log recorded emergency dispatch action
        val logs = storage.getCheckInLogs()
        assertTrue(logs.any { it.status == "DISPATCH_TRIGGERED" }, "Emergency dispatch should record log entry")
    }
}
