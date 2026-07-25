package com.dms.app.ui

import android.content.Context
import android.net.Uri
import com.dms.app.domain.interfaces.ISecureStorage
import com.dms.app.domain.models.DmsConfig
import com.dms.app.domain.models.EmergencyContact
import com.dms.app.domain.models.EmergencyMessage
import com.dms.app.domain.models.SmtpCredentials
import com.dms.app.domain.usecases.DispatchEmergencyUseCase
import com.dms.app.services.dispatch.EmergencyDispatchEngine
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
 * language preferences (DE / EN), Grace Period settings, biometric & PIN security, Panic PIN,
 * Auto-Delete sensitive data, GPS location settings, voice audio notes, burst dispatch counts, pause delays, provider presets, image attachments, and live testing.
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
        language: String = "DE",
        enableBootRecovery: Boolean = true,
        enableBatteryWarnings: Boolean = true,
        enableCloudWatchdog: Boolean = false,
        watchdogPingUrl: String = "",
        gracePeriodMinutes: Long = 360L,
        enableBiometricLock: Boolean = false,
        appPin: String = "",
        panicPin: String = "",
        autoDeleteAfterDispatch: Boolean = false,
        enableGpsLocation: Boolean = false,
        lastKnownLocationUrl: String = "",
        emergencyBurstCount: Int = 1,
        emergencyPauseSeconds: Int = 0
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val updated = DmsConfig(
                timerIntervalMinutes = intervalMinutes,
                gracePeriodMinutes = gracePeriodMinutes,
                primaryDispatchMethod = dispatchMethod,
                retryCount = retryCount,
                isActive = isActive,
                language = language,
                enableBootRecovery = enableBootRecovery,
                enableBatteryWarnings = enableBatteryWarnings,
                enableCloudWatchdog = enableCloudWatchdog,
                watchdogPingUrl = watchdogPingUrl,
                enableBiometricLock = enableBiometricLock,
                appPin = appPin,
                panicPin = panicPin,
                autoDeleteAfterDispatch = autoDeleteAfterDispatch,
                enableGpsLocation = enableGpsLocation,
                lastKnownLocationUrl = lastKnownLocationUrl,
                emergencyBurstCount = emergencyBurstCount,
                emergencyPauseSeconds = emergencyPauseSeconds
            )
            storage.saveConfig(updated)
            _configState.value = updated
            _statusMessage.value = if (language == "EN") "Settings, burst count & delay saved." else "Einstellungen, Notfall-Wiederholungen & Pausenzeit gespeichert."
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
            _statusMessage.value = if (_configState.value.language == "EN") "Contact '$name' saved." else "Kontakt '$name' gespeichert."
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
            _statusMessage.value = if (_configState.value.language == "EN") "SMTP credentials saved permanently." else "SMTP-Zugangsdaten dauerhaft gespeichert."
        }
    }

    fun saveEmergencyMessage(bodyTemplate: String, containsLocation: Boolean = false) {
        CoroutineScope(Dispatchers.IO).launch {
            val current = _emergencyMessageState.value
            val msg = current.copy(bodyTemplate = bodyTemplate, containsLocation = containsLocation)
            storage.saveEmergencyMessage(msg)
            _emergencyMessageState.value = storage.getEmergencyMessage()
            _statusMessage.value = if (_configState.value.language == "EN") "Emergency message text saved." else "Notfall-Nachrichtentext gespeichert."
        }
    }

    fun addAudioNoteFromUri(context: Context, uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val attachmentsDir = File(context.filesDir, "audio_notes")
                if (!attachmentsDir.exists()) {
                    attachmentsDir.mkdirs()
                }

                val targetFile = File(attachmentsDir, "voice_note_${System.currentTimeMillis()}.m4a")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }

                if (targetFile.exists() && targetFile.length() > 0) {
                    val current = _emergencyMessageState.value
                    val updatedMsg = current.copy(audioNotePath = targetFile.absolutePath)
                    storage.saveEmergencyMessage(updatedMsg)
                    _emergencyMessageState.value = updatedMsg
                    _statusMessage.value = if (_configState.value.language == "EN") "Voice note added successfully." else "Sprachnachricht erfolgreich hinzugefügt."
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error adding audio note: ${e.message}"
            }
        }
    }

    fun removeAudioNote() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val current = _emergencyMessageState.value
                if (current.audioNotePath.isNotBlank()) {
                    val file = File(current.audioNotePath)
                    if (file.exists()) file.delete()
                }
                val updatedMsg = current.copy(audioNotePath = "")
                storage.saveEmergencyMessage(updatedMsg)
                _emergencyMessageState.value = updatedMsg
                _statusMessage.value = if (_configState.value.language == "EN") "Voice note removed." else "Sprachnachricht entfernt."
            } catch (e: Exception) {
                _statusMessage.value = "Error removing audio note."
            }
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
                    _statusMessage.value = if (_configState.value.language == "EN") "Photo attached successfully." else "Bild erfolgreich als Anhang hinzugefügt."
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error adding image: ${e.message}"
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
                _statusMessage.value = if (_configState.value.language == "EN") "Photo attachment removed." else "Bild-Anhang entfernt."
            } catch (e: Exception) {
                _statusMessage.value = "Error removing image."
            }
        }
    }

    fun testWatchdogPing(url: String) {
        val isEn = _configState.value.language == "EN"
        if (url.isBlank()) {
            _testResult.value = TestResult(
                success = false,
                message = if (isEn) "Please enter a valid webhook/ping URL!" else "Bitte eine gültige Webhook/Ping-URL eingeben!"
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
                    message = if (isEn) "✅ WATCHDOG PING SUCCESSFUL!\nYour server received the ping." else "✅ CLOUD WATCHDOG PING ERFOLGREICH!\n\nIhr Server/Webhook hat den Ping empfangen."
                )
            } else {
                _testResult.value = TestResult(
                    success = false,
                    message = if (isEn) "❌ WATCHDOG PING FAILED!\nCheck internet connection and server URL." else "❌ WATCHDOG PING FEHLGESCHLAGEN!\nPrüfen Sie die Internetverbindung und die URL."
                )
            }
        }
    }

    fun testSmtpConnection(host: String, port: Int, username: String, passwordPlain: String, recipientEmail: String) {
        val isEn = _configState.value.language == "EN"
        _isTesting.value = true
        _testResult.value = null

        val currentAttachments = _emergencyMessageState.value.attachmentPaths.toMutableList()
        val audioPath = _emergencyMessageState.value.audioNotePath
        if (audioPath.isNotBlank() && File(audioPath).exists()) {
            currentAttachments.add(audioPath)
        }

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
                    message = if (isEn) "Please fill in Server, Sender Email, Password, and Recipient Email!" else "Bitte Server, Absender-Email, Passwort und Notfall-Empfänger E-Mail ausfüllen!"
                )
                return@launch
            }

            val result = smtpMailer.sendSmtpEmailWithRetry(
                smtp = credentials,
                recipientEmail = recipientEmail,
                message = (if (isEn) "TEST MESSAGE: This is a test email from the LastMessage app. Your SMTP settings are working!" else "TEST-NACHRICHT: Dies ist eine Test-E-Mail der LastMessage App. Ihre SMTP-Einstellungen sind korrekt!") +
                        if (currentAttachments.isNotEmpty()) " (${currentAttachments.size} attachment(s) sent)" else "",
                attachmentPaths = currentAttachments,
                maxRetries = 1
            )

            _isTesting.value = false
            if (result.success) {
                // Auto-delete sensitive files if feature is enabled
                if (_configState.value.autoDeleteAfterDispatch) {
                    val emergencyDispatcher = EmergencyDispatchEngine()
                    val dispatchUseCase = DispatchEmergencyUseCase(storage, emergencyDispatcher)
                    dispatchUseCase.purgeSensitiveEmergencyData(if (isEn) "EN" else "DE")
                    refreshAllState()
                }

                _testResult.value = TestResult(
                    success = true,
                    message = if (isEn)
                        "✅ EMAIL SENT SUCCESSFULLY!\n\nCheck inbox of $recipientEmail" + if (_configState.value.autoDeleteAfterDispatch) "\n\n🔥 Auto-Delete: Emergency message & attachments wiped from device." else "."
                    else
                        "✅ E-MAIL ERFOLGREICH GESENDET!\n\nPrüfen Sie das Postfach von $recipientEmail" + if (_configState.value.autoDeleteAfterDispatch) "\n\n🔥 Auto-Delete: Notfall-Text & Anhänge wurden nach Versand lokal gelöscht." else "."
                )
            } else {
                val humanFriendlyAdvice = parseSmtpError(result.errorMessage, isEn)
                _testResult.value = TestResult(
                    success = false,
                    message = humanFriendlyAdvice
                )
            }
        }
    }

    fun testSmsDispatch(phoneNumber: String) {
        val isEn = _configState.value.language == "EN"
        if (phoneNumber.isBlank()) {
            _testResult.value = TestResult(
                success = false,
                message = if (isEn) "Please enter a phone number!" else "Bitte eine Handynummer angeben!"
            )
            return
        }

        _isTesting.value = true
        _testResult.value = null

        CoroutineScope(Dispatchers.IO).launch {
            val result = smsDispatcher.sendMultipartSms(
                phoneNumber = phoneNumber,
                message = if (isEn) "TEST MESSAGE: LastMessage SMS test successful!" else "TEST-NACHRICHT: LastMessage SMS-Test erfolgreich!"
            )

            _isTesting.value = false
            if (result.success) {
                if (_configState.value.autoDeleteAfterDispatch) {
                    val emergencyDispatcher = EmergencyDispatchEngine()
                    val dispatchUseCase = DispatchEmergencyUseCase(storage, emergencyDispatcher)
                    dispatchUseCase.purgeSensitiveEmergencyData(if (isEn) "EN" else "DE")
                    refreshAllState()
                }

                _testResult.value = TestResult(
                    success = true,
                    message = if (isEn) "✅ TEST SMS SENT TO $phoneNumber!" else "✅ TEST-SMS WURDE AN $phoneNumber GESENDET!"
                )
            } else {
                _testResult.value = TestResult(
                    success = false,
                    message = if (isEn) "❌ SMS DISPATCH FAILED:\n${result.errorMessage}" else "❌ SMS-VERSAND FEHLGESCHLAGEN:\n${result.errorMessage}"
                )
            }
        }
    }

    private fun parseSmtpError(rawError: String?, isEn: Boolean): String {
        if (rawError.isNullOrBlank()) return if (isEn) "Unknown connection error." else "Unbekannter Verbindungsfehler."

        val err = rawError.lowercase()

        if (isEn) {
            return when {
                err.contains("534") || err.contains("application-specific password") || err.contains("invalidsecondfactor") -> {
                    "🔑 GOOGLE APP PASSWORD REQUIRED!\n\n" +
                    "Gmail blocks your regular account password for third-party apps.\n\n" +
                    "Quick 3-step fix:\n" +
                    "1. Open in browser: myaccount.google.com/apppasswords\n" +
                    "2. Generate a new App Password for 'Mail'.\n" +
                    "3. Copy the 16-character code and paste it as password below."
                }
                err.contains("535") || err.contains("authentication failed") || err.contains("bad credentials") || err.contains("auth") -> {
                    "🔒 USERNAME OR PASSWORD INCORRECT!\n\n" +
                    "The email server rejected your credentials.\n\n" +
                    "Fix:\n" +
                    "• Check sender email address and password.\n" +
                    "• For Gmail/iCloud: Use an App Password instead of main password.\n" +
                    "• For GMX/WEB.DE: Verify password spelling."
                }
                err.contains("554") || err.contains("550") || err.contains("disabled") || err.contains("pop3/smtp") -> {
                    "⚙️ POP3/SMTP ACCESS DISABLED BY PROVIDER!\n\n" +
                    "Providers like GMX or WEB.DE block external mail tools by default.\n\n" +
                    "Fix:\n" +
                    "1. Log in to GMX or WEB.DE in your desktop browser.\n" +
                    "2. Go to 'Settings' -> 'POP3/SMTP'.\n" +
                    "3. Enable 'Allow POP3 and SMTP access'."
                }
                err.contains("unknownhost") || err.contains("connection refused") || err.contains("timeout") || err.contains("connect") -> {
                    "🌐 UNABLE TO CONNECT TO EMAIL SERVER!\n\n" +
                    "Fix:\n" +
                    "• Check your internet connection.\n" +
                    "• Verify server hostname (e.g. smtp.gmail.com or mail.gmx.net).\n" +
                    "• Verify port (Default: 587)."
                }
                else -> "❌ EMAIL DISPATCH ERROR:\n$rawError"
            }
        }

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
            else -> "❌ FEHLER BEIM E-MAIL VERSAND:\n$rawError"
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
