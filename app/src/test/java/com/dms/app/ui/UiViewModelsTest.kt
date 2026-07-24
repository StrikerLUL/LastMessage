package com.dms.app.ui

import com.dms.app.data.local.KeyStoreManager
import com.dms.app.data.local.SQLCipherHelper
import com.dms.app.domain.usecases.CheckInUseCase
import com.dms.app.domain.usecases.EvaluateTimerUseCase
import com.dms.app.services.notifications.NotificationScheduler
import com.dms.app.services.storage.SecureStorageService
import com.dms.app.services.timer.TimerEngine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UiViewModelsTest {

    private lateinit var storage: SecureStorageService
    private lateinit var timerEngine: TimerEngine
    private lateinit var notificationScheduler: NotificationScheduler
    private lateinit var checkInUseCase: CheckInUseCase
    private lateinit var evaluateTimerUseCase: EvaluateTimerUseCase

    private lateinit var checkInViewModel: CheckInViewModel
    private lateinit var settingsViewModel: SettingsViewModel

    @BeforeEach
    fun setUp() {
        val keyStoreManager = KeyStoreManager("test_ui_key")
        val dbHelper = SQLCipherHelper(dbPath = "jdbc:sqlite::memory:")
        storage = SecureStorageService(keyStoreManager, dbHelper)
        timerEngine = TimerEngine()
        notificationScheduler = NotificationScheduler()

        evaluateTimerUseCase = EvaluateTimerUseCase(storage, timerEngine)
        checkInUseCase = CheckInUseCase(storage, timerEngine, notificationScheduler)

        checkInViewModel = CheckInViewModel(checkInUseCase, evaluateTimerUseCase)
        settingsViewModel = SettingsViewModel(storage)
    }

    @Test
    fun testCheckInViewModelStateAndAction() {
        checkInViewModel.refreshStatus()
        assertNotNull(checkInViewModel.timerState.value)

        checkInViewModel.performCheckIn("MANUAL_APP")
        assertEquals("Check-in confirmed successfully!", checkInViewModel.userMessage.value)

        checkInViewModel.clearUserMessage()
        assertNull(checkInViewModel.userMessage.value)
    }

    @Test
    fun testSettingsViewModelConfigAndContactOperations() {
        // 1. Update config
        settingsViewModel.updateConfig(2880L, "SMS_THEN_EMAIL")
        assertEquals(2880L, settingsViewModel.configState.value.timerIntervalMinutes)
        assertEquals("SMS_THEN_EMAIL", settingsViewModel.configState.value.primaryDispatchMethod)

        // 2. Add contact
        settingsViewModel.addEmergencyContact("Carol", "+15550199", "carol@example.com", 1)
        val contacts = settingsViewModel.contactsState.value
        assertTrue(contacts.any { it.recipientName == "Carol" })

        // 3. Save SMTP
        settingsViewModel.saveSmtpCredentials("smtp.test.com", 587, "user", "secretPass", true)
        val smtp = settingsViewModel.smtpState.value
        assertNotNull(smtp)
        assertEquals("smtp.test.com", smtp?.host)
    }
}
