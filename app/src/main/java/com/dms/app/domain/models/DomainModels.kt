package com.dms.app.domain.models

import java.time.Instant

/**
 * Global application timer intervals, dispatch preferences, and fail-safe redundancy settings.
 */
data class DmsConfig(
    val id: Int = 1,
    val timerIntervalMinutes: Long = 1440L, // Default 24 hours (1440 mins)
    val primaryDispatchMethod: String = "SMS", // Options: "SMS", "EMAIL", "BOTH", "SMS_THEN_EMAIL"
    val retryCount: Int = 3,
    val isActive: Boolean = true,
    val enableBootRecovery: Boolean = true,
    val enableBatteryWarnings: Boolean = true,
    val enableCloudWatchdog: Boolean = false,
    val watchdogPingUrl: String = "",
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
    val method: String, // "MANUAL_APP", "NOTIFICATION_ACTION", "WIDGET", "SYSTEM_AUTO"
    val status: String, // "SUCCESS", "WARNING_ISSUED", "EXPIRED", "DISPATCH_TRIGGERED", "DISPATCH_FAILED"
    val details: String? = null
)

/**
 * Emergency message template and attached local image files dispatched to emergency contacts.
 */
data class EmergencyMessage(
    val id: Int = 1,
    val bodyTemplate: String,
    val containsLocation: Boolean = false,
    val attachmentPaths: List<String> = emptyList(),
    val lastUpdated: String = Instant.now().toString()
)

/**
 * Status of the Dead Man's Switch countdown timer.
 */
enum class TimerStatus {
    ACTIVE,
    WARNING,
    EXPIRED
}

/**
 * Complete evaluation snapshot of the timer state.
 */
data class TimerEvaluation(
    val status: TimerStatus,
    val remainingMinutes: Long,
    val elapsedMinutes: Long,
    val expiryTimestampEpochMillis: Long,
    val lastCheckInTimestampEpochMillis: Long
)

/**
 * Scheduled notification milestone threshold (75%, 50%, 25%, 10%, 1h remaining).
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
