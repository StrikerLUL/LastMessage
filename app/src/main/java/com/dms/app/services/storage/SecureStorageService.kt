package com.dms.app.services.storage

import com.dms.app.data.local.KeyStoreManager
import com.dms.app.data.local.SQLCipherHelper
import com.dms.app.domain.interfaces.ISecureStorage
import com.dms.app.domain.models.*

/**
 * SecureStorageService provides encrypted storage operations for the DMS app.
 * Manages MasterKey AES-256 envelope encryption for sensitive credentials and state,
 * backed by EncryptedSharedPreferences semantics and SQLCipher database storage.
 */
class SecureStorageService(
    private val keyStoreManager: KeyStoreManager = KeyStoreManager(),
    private val dbHelper: SQLCipherHelper = SQLCipherHelper()
) : ISecureStorage {

    override fun saveCheckInTimestamp(timestampIso: String) {
        val encryptedTimestamp = keyStoreManager.encrypt(timestampIso)
        dbHelper.setLastCheckInTimestamp(encryptedTimestamp)
    }

    override fun getLastCheckInTimestamp(): String? {
        val encryptedTimestamp = dbHelper.getLastCheckInTimestamp() ?: return null
        return keyStoreManager.decryptOrOriginal(encryptedTimestamp)
    }

    override fun getConfig(): DmsConfig {
        return dbHelper.getAppConfig()
    }

    override fun saveConfig(config: DmsConfig) {
        dbHelper.saveAppConfig(config)
    }

    override fun getEmergencyContacts(): List<EmergencyContact> {
        return dbHelper.getEmergencyContacts()
    }

    override fun saveEmergencyContacts(contacts: List<EmergencyContact>) {
        dbHelper.saveEmergencyContacts(contacts)
    }

    override fun addEmergencyContact(contact: EmergencyContact): Long {
        return dbHelper.addEmergencyContact(contact)
    }

    override fun deleteEmergencyContact(contactId: Long) {
        dbHelper.deleteEmergencyContact(contactId)
    }

    override fun getSmtpCredentials(): SmtpCredentials? {
        val creds = dbHelper.getSmtpCredentials() ?: return null
        val decryptedPassword = keyStoreManager.decryptOrOriginal(creds.passwordEncrypted)
        return creds.copy(passwordEncrypted = decryptedPassword)
    }

    override fun saveSmtpCredentials(credentials: SmtpCredentials) {
        val encryptedPassword = keyStoreManager.encrypt(credentials.passwordEncrypted)
        val secureCredentials = credentials.copy(passwordEncrypted = encryptedPassword)
        dbHelper.saveSmtpCredentials(secureCredentials)
    }

    override fun getEmergencyMessage(): EmergencyMessage {
        return dbHelper.getEmergencyMessage()
    }

    override fun saveEmergencyMessage(message: EmergencyMessage) {
        dbHelper.saveEmergencyMessage(message)
    }

    override fun encryptSecret(plainText: String): String {
        return keyStoreManager.encrypt(plainText)
    }

    override fun decryptSecret(cipherText: String): String {
        return keyStoreManager.decryptOrOriginal(cipherText)
    }

    override fun addCheckInLog(log: CheckInLog): Long {
        return dbHelper.addCheckInLog(log)
    }

    override fun getCheckInLogs(): List<CheckInLog> {
        return dbHelper.getCheckInLogs()
    }
}
