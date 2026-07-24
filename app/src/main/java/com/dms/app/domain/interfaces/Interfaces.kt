package com.dms.app.domain.interfaces

import com.dms.app.domain.models.*

/**
 * Interface contract for encrypted storage management.
 */
interface ISecureStorage {
    fun saveCheckInTimestamp(timestampIso: String)
    fun getLastCheckInTimestamp(): String?
    
    fun getConfig(): DmsConfig
    fun saveConfig(config: DmsConfig)
    
    fun getEmergencyContacts(): List<EmergencyContact>
    fun saveEmergencyContacts(contacts: List<EmergencyContact>)
    fun addEmergencyContact(contact: EmergencyContact): Long
    fun deleteEmergencyContact(contactId: Long)
    
    fun getSmtpCredentials(): SmtpCredentials?
    fun saveSmtpCredentials(credentials: SmtpCredentials)
    
    fun getEmergencyMessage(): EmergencyMessage
    fun saveEmergencyMessage(message: EmergencyMessage)
    
    fun encryptSecret(plainText: String): String
    fun decryptSecret(cipherText: String): String
    
    fun addCheckInLog(log: CheckInLog): Long
    fun getCheckInLogs(): List<CheckInLog>
}

/**
 * Interface contract for timer calculation engine.
 */
interface ITimerEngine {
    fun getRemainingDurationMinutes(lastCheckInEpochMillis: Long, intervalMinutes: Long, currentTimeEpochMillis: Long): Long
    fun evaluateStatus(lastCheckInEpochMillis: Long, intervalMinutes: Long, currentTimeEpochMillis: Long): TimerEvaluation
    fun calculateNotificationThresholds(lastCheckInEpochMillis: Long, intervalMinutes: Long): List<MilestoneThreshold>
}

/**
 * Interface contract for scheduling local push notifications.
 */
interface INotificationScheduler {
    fun createNotificationChannels()
    fun scheduleThresholdNotifications(milestones: List<MilestoneThreshold>)
    fun cancelAllNotifications()
    fun sendWarningNotification(title: String, body: String)
}

/**
 * Interface contract for emergency dispatch orchestration.
 */
interface IEmergencyDispatcher {
    fun triggerEmergencyDispatch(config: DmsConfig, message: EmergencyMessage, contacts: List<EmergencyContact>, smtp: SmtpCredentials?): DispatchResult
    fun sendMultipartSms(phoneNumber: String, message: String): SmsResult
    fun sendSmtpEmailWithRetry(smtp: SmtpCredentials, recipientEmail: String, message: String, maxRetries: Int = 3): EmailResult
}
