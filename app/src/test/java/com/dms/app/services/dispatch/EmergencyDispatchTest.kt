package com.dms.app.services.dispatch

import com.dms.app.domain.models.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EmergencyDispatchTest {

    private lateinit var smsDispatcher: SmsDispatcher
    private lateinit var smtpMailer: SmtpMailer
    private lateinit var dispatchEngine: EmergencyDispatchEngine

    @BeforeEach
    fun setUp() {
        smsDispatcher = SmsDispatcher()
        smtpMailer = SmtpMailer()
        dispatchEngine = EmergencyDispatchEngine(smsDispatcher, smtpMailer)
    }

    @Test
    fun testSmsMultipartMessageSplitting() {
        // 1. Single-part SMS (<= 160 chars)
        val shortMessage = "EMERGENCY: User check-in missing."
        val shortParts = smsDispatcher.divideMessageText(shortMessage)
        assertEquals(1, shortParts.size)
        assertEquals(shortMessage, shortParts[0])

        // 2. Multi-part GSM SMS (> 160 chars, 153 chars per part)
        val longMessage = "A".repeat(350)
        val longParts = smsDispatcher.divideMessageText(longMessage)
        assertEquals(3, longParts.size)
        assertEquals(153, longParts[0].length)
        assertEquals(153, longParts[1].length)
        assertEquals(44, longParts[2].length)

        // 3. Dispatch execution result
        val smsResult = smsDispatcher.sendMultipartSms("+15550199", longMessage)
        assertTrue(smsResult.success)
        assertEquals(3, smsResult.messagePartsCount)
        assertEquals("+15550199", smsResult.recipient)
        assertNull(smsResult.errorMessage)
    }

    @Test
    fun testSmtp3xRetryLoopBehavior() {
        val smtp = SmtpCredentials(
            host = "smtp.example.com",
            port = 587,
            username = "user@example.com",
            passwordEncrypted = "secret_pass",
            enableTls = true
        )

        val delayedMsList = mutableListOf<Long>()
        smtpMailer.delayProvider = { delayMs -> delayedMsList.add(delayMs) }

        // Simulate 2 transient failures before succeeding on attempt 3
        val result = smtpMailer.sendSmtpEmailWithRetry(
            smtp = smtp,
            recipientEmail = "contact@example.com",
            message = "Emergency Alert",
            maxRetries = 3,
            simulateFailuresBeforeSuccess = 2
        )

        assertTrue(result.success)
        assertEquals(3, result.attemptCount)
        assertEquals(2, delayedMsList.size)
        assertEquals(5000L, delayedMsList[0])
        assertEquals(15000L, delayedMsList[1])
        assertNull(result.errorMessage)
    }

    @Test
    fun testSmtpPreflightValidationFailure() {
        val invalidSmtp = SmtpCredentials(
            host = "",
            port = 0,
            username = "user@example.com",
            passwordEncrypted = "pass",
            enableTls = true
        )

        val result = smtpMailer.sendSmtpEmailWithRetry(
            smtp = invalidSmtp,
            recipientEmail = "contact@example.com",
            message = "Emergency Alert"
        )

        assertFalse(result.success)
        assertEquals(0, result.attemptCount)
        assertEquals("Invalid SMTP host or port configuration", result.errorMessage)
    }

    @Test
    fun testSmtpRetryExhaustion() {
        val smtp = SmtpCredentials(
            host = "smtp.example.com",
            port = 587,
            username = "user@example.com",
            passwordEncrypted = "secret_pass",
            enableTls = true
        )

        smtpMailer.delayProvider = { } // No-op delay for fast test execution

        // Simulate 3 failures (all retries exhausted)
        val result = smtpMailer.sendSmtpEmailWithRetry(
            smtp = smtp,
            recipientEmail = "contact@example.com",
            message = "Emergency Alert",
            maxRetries = 3,
            simulateFailuresBeforeSuccess = 3
        )

        assertFalse(result.success)
        assertEquals(3, result.attemptCount)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun testEmergencyDispatchSmsThenEmailSuccessDoesNotSendEmail() {
        val config = DmsConfig(primaryDispatchMethod = "SMS_THEN_EMAIL", retryCount = 3)
        val message = EmergencyMessage(bodyTemplate = "Emergency Alert Test")
        val contacts = listOf(
            EmergencyContact(id = 1L, recipientName = "Contact 1", phoneNumber = "+15550100", emailAddress = "contact1@example.com", priority = 1)
        )
        val smtp = SmtpCredentials(host = "smtp.example.com", port = 587, username = "alert@example.com", passwordEncrypted = "enc_pass")

        smtpMailer.delayProvider = { }

        val dispatchResult = dispatchEngine.triggerEmergencyDispatch(config, message, contacts, smtp)

        assertTrue(dispatchResult.success)
        assertEquals(1, dispatchResult.smsResults.size)
        assertTrue(dispatchResult.smsResults[0].success)
        // In SMS_THEN_EMAIL mode, since SMS succeeded, Email must NOT be dispatched!
        assertTrue(dispatchResult.emailResults.isEmpty())
    }
}
