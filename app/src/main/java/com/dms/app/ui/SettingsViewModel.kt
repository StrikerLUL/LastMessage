package com.dms.app.ui

import android.content.Context
import android.net.Uri
import com.dms.app.domain.interfaces.ISecureStorage
import com.dms.app.domain.models.DmsConfig
import com.dms.app.domain.models.EmergencyContact
import com.dms.app.domain.models.EmergencyMessage
import com.dms.app.domain.models.SmtpCredentials
import com.dms.app.services.dispatch.SmsDispatcher
import com.dms.app.services.dispatch.SmtpMailer
import com.dms.app.services.watchdog.WatchdogService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * SettingsViewModel manages configuration settings, emergency contacts, SMTP credentials,
 * provider preset legends, image attachments, redundancy fail-safe settings (Boot-Recovery & Cloud Watchdog),
 * and real-time live SMTP / SMS / Watchdog testing.
 */
class SettingsViewModel(
    private val storage: ISecureStorage,
    private val smtpMailer: SmtpMailer = SmtpMailer(),
    private val smsDispatcher: SmsDispatcher = SmsDispatcher(),
    private val watchdogService: WatchdogService = WatchdogService()
) {

    private val _configState = MutableStateFlow<DmsConfig>(storage.getConfig())
    val configState: StateFlow<DmsConfig> = _configState.asStateFlow()

    private val _contactsState = MutableStateFlow<List<EmergencyContact>>(storage.getEmergencyContacts())
    val contactsState: StateFlow<List<EmergencyContact>> = _contactsState.asStateFlow()

    private val _smtpState = MutableStateFlow<SmtpCredentials?>(storage.getSmtpCredentials())
    val smtpState: StateFlow<SmtpCredentials?> = _smtpState.asStateFlow()

    private val _emergencyMessageState = MutableStateFlow<EmergencyMessage>(storage.getEmergencyMessage())
    val emergencyMessageState: StateFlow<EmergencyMessage> = _emergencyMessageState.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isTesting = MutableStateFlow<Boolean>(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _testResult = MutableStateFlow<TestResult?>(null)
    val testResult: StateFlow<TestResult?> = _testResult.asStateFlow()

    fun refreshAllState() {
        CoroutineScope(Dispatchers.IO).launch {
            _configState.value = storage.getConfig()
            _contactsState.value = storage.getEmergencyContacts()
            _smtpState.value = storage.getSmtpCredentials()
            _emergencyMessageState.value = storage.getEmergencyMessage()
        }
    }

    fun updateConfig(
        intervalMinutes: Long,
        dispatchMethod: String,
        retryCount: Int = 3,
        isActive: Boolean = true,
        enableBootRecovery: Boolean = true,
        enableBatteryWarnings: Boolean = true,
        enableCloudWatchdog: Boolean = false,
        watchdogPingUrl: String = ""
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val updated = DmsConfig(
                timerIntervalMinutes = intervalMinutes,
                primaryDispatchMethod = dispatchMethod,
                retryCount = retryCount,
                isActive = isActive,
                enableBootRecovery = enableBootRecovery,
                enableBatteryWarnings = enableBatteryWarnings,
                enableCloudWatchdog = enableCloudWatchdog,
                watchdogPingUrl = watchdogPingUrl
            )
            storage.saveConfig(updated)
            _configState.value = updated
            _statusMessage.value = "Konfiguration & Ausfallschutz erfolgreich gespeichert."
        }
    }

    fun addEmergencyContact(name: String, phone: String, email: String, priority: Int = 1) {
        CoroutineScope(Dispatchers.IO).launch {
            val contact = EmergencyContact(
                recipientName = name,
                phoneNumber = phone,
                emailAddress = email,
                priority = priority
            )
            storage.addEmergencyContact(contact)
            _contactsState.value = storage.getEmergencyContacts()
            _statusMessage.value = "Kontakt '$name' gespeichert."
        }
    }

    fun saveSmtpCredentials(host: String, port: Int, username: String, passwordPlain: String, enableTls: Boolean = true) {
        CoroutineScope(Dispatchers.IO).launch {
            val smtp = SmtpCredentials(
                host = host,
                port = port,
                username = username,
                passwordEncrypted = passwordPlain,
                enableTls = enableTls
            )
            storage.saveSmtpCredentials(smtp)
            _smtpState.value = storage.getSmtpCredentials()
            _statusMessage.value = "SMTP-Zugangsdaten dauerhaft gespeichert."
        }
    }

    fun saveEmergencyMessage(bodyTemplate: String, containsLocation: Boolean = false) {
        CoroutineScope(Dispatchers.IO).launch {
            val current = _emergencyMessageState.value
            val msg = current.copy(bodyTemplate = bodyTemplate, containsLocation = containsLocation)
            storage.saveEmergencyMessage(msg)
            _emergencyMessageState.value = storage.getEmergencyMessage()
            _statusMessage.value = "Notfall-Nachrichtentext gespeichert."
        }
    }

    fun addImageAttachmentFromUri(context: Context, uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val attachmentsDir = File(context.filesDir, "attachments")
                if (!attachmentsDir.exists()) {
                    attachmentsDir.mkdirs()
                }

                val targetFile = File(attachmentsDir, "img_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }

                if (targetFile.exists() && targetFile.length() > 0) {
                    val current = _emergencyMessageState.value
                    val newPaths = current.attachmentPaths + targetFile.absolutePath
                    val updatedMsg = current.copy(attachmentPaths = newPaths)
                    storage.saveEmergencyMessage(updatedMsg)
                    _emergencyMessageState.value = updatedMsg
                    _statusMessage.value = "Bild erfolgreich als Anhang hinzugefügt."
                }
            } catch (e: Exception) {
                _statusMessage.value = "Fehler beim Hinzufügen des Bildes: ${e.message}"
            }
        }
    }

    fun removeAttachment(path: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
                val current = _emergencyMessageState.value
                val newPaths = current.attachmentPaths.filter { it != path }
                val updatedMsg = current.copy(attachmentPaths = newPaths)
                storage.saveEmergencyMessage(updatedMsg)
                _emergencyMessageState.value = updatedMsg
                _statusMessage.value = "Bild-Anhang entfernt."
            } catch (e: Exception) {
                _statusMessage.value = "Fehler beim Entfernen des Bildes."
            }
        }
    }

    fun testWatchdogPing(url: String) {
        if (url.isBlank()) {
            _testResult.value = TestResult(
                success = false,
                message = "Bitte eine gültige Webhook/Ping-URL eingeben!"
            )
            return
        }

        _isTesting.value = true
        _testResult.value = null

        CoroutineScope(Dispatchers.IO).launch {
            val success = watchdogService.sendPing(url)
            _isTesting.value = false
            if (success) {
                _testResult.value = TestResult(
                    success = true,
                    message = "✅ CLOUD WATCHDOG PING ERFOLGREICH!\n\nIhr Server/Webhook hat den Ping empfangen."
                )
            } else {
                _testResult.value = TestResult(
                    success = false,
                    message = "❌ WATCHDOG PING FEHLGESCHLAGEN!\nPrüfen Sie die Internetverbindung und die URL."
                )
            }
        }
    }

    fun testSmtpConnection(host: String, port: Int, username: String, passwordPlain: String, recipientEmail: String) {
        _isTesting.value = true
        _testResult.value = null

        val currentAttachments = _emergencyMessageState.value.attachmentPaths

        CoroutineScope(Dispatchers.IO).launch {
            val credentials = SmtpCredentials(
                host = host,
                port = port,
                username = username,
                passwordEncrypted = passwordPlain,
                enableTls = true
            )
            storage.saveSmtpCredentials(credentials)
            _smtpState.value = storage.getSmtpCredentials()

            if (host.isBlank() || username.isBlank() || passwordPlain.isBlank() || recipientEmail.isBlank()) {
                _isTesting.value = false
                _testResult.value = TestResult(
                    success = false,
                    message = "Bitte Server, Absender-Email, Passwort und Notfall-Empfänger E-Mail ausfüllen!"
                )
                return@launch
            }

            val result = smtpMailer.sendSmtpEmailWithRetry(
                smtp = credentials,
                recipientEmail = recipientEmail,
                message = "TEST-NACHRICHT: Dies ist eine Test-E-Mail der Dead Man's Switch App. Ihre SMTP-Einstellungen sind korrekt!" +
                        if (currentAttachments.isNotEmpty()) " (${currentAttachments.size} Anhang/Anhänge mitgesendet)" else "",
                attachmentPaths = currentAttachments,
                maxRetries = 1
            )

            _isTesting.value = false
            if (result.success) {
                _testResult.value = TestResult(
                    success = true,
                    message = "✅ E-MAIL ERFOLGREICH GESENDET!\n\nPrüfen Sie das Postfach von $recipientEmail" +
                            if (currentAttachments.isNotEmpty()) " (inkl. ${currentAttachments.size} Bild-Anhang/Anhänge)" else "."
                )
            } else {
                val humanFriendlyAdvice = parseSmtpError(result.errorMessage)
                _testResult.value = TestResult(
                    success = false,
                    message = humanFriendlyAdvice
                )
            }
        }
    }

    fun testSmsDispatch(phoneNumber: String) {
        if (phoneNumber.isBlank()) {
            _testResult.value = TestResult(
                success = false,
                message = "Bitte eine Handynummer angeben!"
            )
            return
        }

        _isTesting.value = true
        _testResult.value = null

        CoroutineScope(Dispatchers.IO).launch {
            val result = smsDispatcher.sendMultipartSms(
                phoneNumber = phoneNumber,
                message = "TEST-NACHRICHT: Dead Man's Switch SMS-Test erfolgreich!"
            )

            _isTesting.value = false
            if (result.success) {
                _testResult.value = TestResult(
                    success = true,
                    message = "✅ TEST-SMS WURDE AN $phoneNumber GESENDET!"
                )
            } else {
                _testResult.value = TestResult(
                    success = false,
                    message = "❌ SMS-VERSAND FEHLGESCHLAGEN:\n${result.errorMessage}"
                )
            }
        }
    }

    private fun parseSmtpError(rawError: String?): String {
        if (rawError.isNullOrBlank()) return "Unbekannter Verbindungsfehler."

        val err = rawError.lowercase()

        return when {
            err.contains("534") || err.contains("application-specific password") || err.contains("invalidsecondfactor") -> {
                "🔑 GOOGLE APP-PASSWORT ERFORDERLICH!\n\n" +
                "Gmail erlaubt aus Sicherheitsgründen nicht Ihr normales Google-Passwort.\n\n" +
                "Einfache Lösung in 3 Schritten:\n" +
                "1. Öffnen Sie im Browser: myaccount.google.com/apppasswords\n" +
                "2. Erstellen Sie ein neues App-Passwort für 'E-Mail'.\n" +
                "3. Kopieren Sie den 16-stelligen Code und tragen Sie ihn unten als Passwort ein."
            }
            err.contains("535") || err.contains("authentication failed") || err.contains("bad credentials") || err.contains("auth") -> {
                "🔒 BENUTZERNAME ODER PASSWORT FALSCH!\n\n" +
                "Der E-Mail-Server hat Ihre Zugangsdaten abgelehnt.\n\n" +
                "Lösung:\n" +
                "• Prüfen Sie Absender-Adresse und Passwort.\n" +
                "• Bei Gmail/iCloud: Verwenden Sie ein App-Passwort statt dem normalen Passwort.\n" +
                "• Bei GMX/WEB.DE: Prüfen Sie das Passwort auf Tippfehler."
            }
            err.contains("554") || err.contains("550") || err.contains("disabled") || err.contains("pop3/smtp") -> {
                "⚙️ POP3/SMTP BEIM ANBIETER DEAKTIVIERT!\n\n" +
                "Anbieter wie GMX oder WEB.DE blockieren den externen E-Mail-Versand standardmäßig.\n\n" +
                "Lösung:\n" +
                "1. Loggen Sie sich bei GMX oder WEB.DE im Browser ein.\n" +
                "2. Gehen Sie zu 'Einstellungen' -> 'POP3/SMTP'.\n" +
                "3. Aktivieren Sie 'POP3 und SMTP-Zugriff erlauben'."
            }
            err.contains("unknownhost") || err.contains("connection refused") || err.contains("timeout") || err.contains("connect") -> {
                "🌐 KEINE VERBINDUNG ZUM E-MAIL-SERVER!\n\n" +
                "Der E-Mail-Server konnte nicht erreicht werden.\n\n" +
                "Lösung:\n" +
                "• Prüfen Sie Ihre Internetverbindung.\n" +
                "• Prüfen Sie den Servernamen (z.B. smtp.gmail.com oder mail.gmx.net).\n" +
                "• Prüfen Sie den Port (Standard: 587)."
            }
            else -> {
                "❌ FEHLER BEIM E-MAIL VERSAND:\n$rawError"
            }
        }
    }

    fun clearTestResult() {
        _testResult.value = null
    }

    data class TestResult(
        val success: Boolean,
        val message: String
    )
}
