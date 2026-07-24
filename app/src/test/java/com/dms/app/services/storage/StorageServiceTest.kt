package com.dms.app.services.storage

import com.dms.app.data.local.KeyStoreManager
import com.dms.app.data.local.SQLCipherHelper
import com.dms.app.domain.models.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StorageServiceTest {

    private lateinit var keyStoreManager: KeyStoreManager
    private lateinit var dbHelper: SQLCipherHelper
    private lateinit var storageService: SecureStorageService

    @BeforeEach
    fun setUp() {
        keyStoreManager = KeyStoreManager("test_master_key")
        dbHelper = SQLCipherHelper(dbPath = "jdbc:sqlite::memory:")
        storageService = SecureStorageService(keyStoreManager, dbHelper)
    }

    @Test
    fun testEncryptionConfigurationAndEnvelopeDecryption() {
        val originalSecret = "SuperSecretSMTPPassword123!"
        val encrypted = storageService.encryptSecret(originalSecret)

        assertNotNull(encrypted)
        assertTrue(encrypted.isNotBlank())
        assertNotEquals(originalSecret, encrypted)

        val decrypted = storageService.decryptSecret(encrypted)
        assertEquals(originalSecret, decrypted)
    }

    @Test
    fun testLastCheckInTimestampStorageAndRetrieval() {
        val timestampIso = "2026-07-24T12:00:00Z"
        storageService.saveCheckInTimestamp(timestampIso)

        val retrievedIso = storageService.getLastCheckInTimestamp()
        assertNotNull(retrievedIso)
        assertEquals(timestampIso, retrievedIso)
    }

    @Test
    fun testAppConfigAndEmergencyContactsStorage() {
        // 1. Config saving and retrieval
        val customConfig = DmsConfig(
            timerIntervalMinutes = 2880L, // 48 hours
            primaryDispatchMethod = "BOTH",
            retryCount = 5,
            isActive = true
        )
        storageService.saveConfig(customConfig)
        val retrievedConfig = storageService.getConfig()
        assertEquals(2880L, retrievedConfig.timerIntervalMinutes)
        assertEquals("BOTH", retrievedConfig.primaryDispatchMethod)
        assertEquals(5, retrievedConfig.retryCount)

        // 2. Emergency Contacts CRUD
        val contact1 = EmergencyContact(recipientName = "Alice", phoneNumber = "+15550101", emailAddress = "alice@example.com", priority = 1)
        val contact2 = EmergencyContact(recipientName = "Bob", phoneNumber = "+15550102", emailAddress = "bob@example.com", priority = 2)

        storageService.saveEmergencyContacts(listOf(contact1, contact2))
        val contactsList = storageService.getEmergencyContacts()
        assertEquals(2, contactsList.size)
        assertEquals("Alice", contactsList[0].recipientName)
        assertEquals("Bob", contactsList[1].recipientName)
    }

    @Test
    fun testSmtpCredentialsEnvelopeEncryption() {
        val smtp = SmtpCredentials(
            host = "smtp.securemail.org",
            port = 587,
            username = "admin@securemail.org",
            passwordEncrypted = "PlainPassword456!",
            enableTls = true
        )

        storageService.saveSmtpCredentials(smtp)
        val savedSmtp = storageService.getSmtpCredentials()

        assertNotNull(savedSmtp)
        assertEquals("smtp.securemail.org", savedSmtp!!.host)
        assertEquals("admin@securemail.org", savedSmtp.username)

        assertNotEquals("PlainPassword456!", savedSmtp.passwordEncrypted)

        val decryptedPassword = storageService.decryptSecret(savedSmtp.passwordEncrypted)
        assertEquals("PlainPassword456!", decryptedPassword)
    }

    @Test
    fun testCheckInLogAuditTrail() {
        val log = CheckInLog(
            timestamp = "2026-07-24T14:30:00Z",
            method = "MANUAL_APP",
            status = "SUCCESS",
            details = "Manual check-in completed"
        )
        storageService.addCheckInLog(log)

        val logs = storageService.getCheckInLogs()
        assertTrue(logs.isNotEmpty())
        assertEquals("MANUAL_APP", logs[0].method)
        assertEquals("SUCCESS", logs[0].status)
    }
}
