package com.dms.app.domain.models

import java.time.Instant

/**
 * Global application timer intervals, dispatch preferences, language settings,
 * grace period settings, biometric/PIN security settings, Panic PIN, auto-delete,
 * GPS location settings, burst dispatch counts, pause delays, and fail-safe redundancy settings.
 */
data class DmsConfig(
    val id: Int = 1,
    val timerIntervalMinutes: Long = 1440L, // Default 24 hours (1440 mins)
    val gracePeriodMinutes: Long = 360L,   // Default 6 hours grace period (360 mins)
    val primaryDispatchMethod: String = "SMS", // Options: "SMS", "EMAIL", "BOTH", "SMS_THEN_EMAIL"
    val retryCount: Int = 3,
    val isActive: Boolean = true,
    val language: String = "DE", // Options: "DE" (German), "EN" (English)
    val enableBootRecovery: Boolean = true,
    val enableBatteryWarnings: Boolean = true,
    val enableCloudWatchdog: Boolean = false,
    val watchdogPingUrl: String = "",
    val enableBiometricLock: Boolean = false,
    val appPin: String = "",
    val panicPin: String = "", // Panic/Duress PIN: Feigns check-in success, secretly triggers emergency dispatch immediately!
    val autoDeleteAfterDispatch: Boolean = false, // Auto-delete sensitive message body & image attachments after dispatch!
    val enableGpsLocation: Boolean = false, // Optional GPS location appending in emergency SMS/Email!
    val lastKnownLocationUrl: String = "", // Saved Google Maps location URL recorded at last check-in!
    val emergencyBurstCount: Int = 1, // Configurable number of times emergency dispatch repeats (1..5)!
    val emergencyPauseSeconds: Int = 0, // Configurable delay in seconds between burst dispatches (0..60s)!
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = Instant.now().toString()
) {
    companion object {
        val VALID_INTERVALS_MINUTES = listOf(
            720L,   // 12 hours
            1440L,  // 24 hours (default)
            2880L,  // 48 hours
            4320L,  // 72 hours
            10080L  // 7 days
        )

        val VALID_GRACE_PERIODS_MINUTES = listOf(
            0L,     // 0 hours (Immediate dispatch)
            60L,    // 1 hour
            180L,   // 3 hours
            360L,   // 6 hours (default)
            720L,   // 12 hours
            1440L   // 24 hours
        )
    }
}

/**
 * Emergency contact recipient information.
 */
data class EmergencyContact(
    val id: Long = 0L,
    val recipientName: String,
    val phoneNumber: String,
    val emailAddress: String,
    val priority: Int = 1,
    val isEnabled: Boolean = true,
    val createdAt: String = Instant.now().toString()
)

/**
 * Outbound SMTP server configuration with encrypted password.
 */
data class SmtpCredentials(
    val id: Int = 1,
    val host: String,
    val port: Int = 587,
    val username: String,
    val passwordEncrypted: String,
    val enableTls: Boolean = true,
    val updatedAt: String = Instant.now().toString()
)

/**
 * Audit log recording check-ins, warnings, and dispatches.
 */
data class CheckInLog(
    val id: Long = 0L,
    val timestamp: String = Instant.now().toString(),
    val method: String, // "MANUAL_APP", "PANIC_PIN_DURESS", "NOTIFICATION_ACTION", "WIDGET", "SYSTEM_AUTO"
    val status: String, // "SUCCESS", "WARNING_ISSUED", "GRACE_PERIOD", "EXPIRED", "DISPATCH_TRIGGERED", "DISPATCH_FAILED"
    val details: String? = null
)

/**
 * Emergency message template, attached local image files, and optional voice audio note dispatched to emergency contacts.
 */
data class EmergencyMessage(
    val id: Int = 1,
    val bodyTemplate: String,
    val containsLocation: Boolean = false,
    val attachmentPaths: List<String> = emptyList(),
    val audioNotePath: String = "", // Path to recorded .m4a audio voice note!
    val lastUpdated: String = Instant.now().toString()
)

/**
 * Status of the Dead Man's Switch countdown timer.
 */
enum class TimerStatus {
    ACTIVE,
    WARNING,
    GRACE_PERIOD,
    EXPIRED
}

/**
 * Complete evaluation snapshot of the timer state.
 */
data class TimerEvaluation(
    val status: TimerStatus,
    val remainingMinutes: Long,
    val elapsedMinutes: Long,
    val remainingGraceMinutes: Long = 0L,
    val expiryTimestampEpochMillis: Long,
    val lastCheckInTimestampEpochMillis: Long
)

/**
 * Scheduled notification milestone threshold.
 */
data class MilestoneThreshold(
    val milestoneName: String,
    val percentageRemaining: Double,
    val triggerTimeEpochMillis: Long,
    val remainingMinutes: Long
)

/**
 * Individual SMS dispatch outcome for a recipient.
 */
data class SmsResult(
    val recipient: String,
    val success: Boolean,
    val messagePartsCount: Int,
    val errorMessage: String? = null
)

/**
 * Individual Email dispatch outcome for a recipient.
 */
data class EmailResult(
    val recipient: String,
    val success: Boolean,
    val attemptCount: Int,
    val errorMessage: String? = null
)

/**
 * Overall emergency dispatch result summary.
 */
data class DispatchResult(
    val success: Boolean,
    val smsResults: List<SmsResult> = emptyList(),
    val emailResults: List<EmailResult> = emptyList(),
    val summary: String
)
