package com.dms.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.fragment.app.FragmentActivity
import com.dms.app.data.local.KeyStoreManager
import com.dms.app.data.local.SQLCipherHelper
import com.dms.app.domain.usecases.CheckInUseCase
import com.dms.app.domain.usecases.EvaluateTimerUseCase
import com.dms.app.services.notifications.NotificationScheduler
import com.dms.app.services.storage.SecureStorageService
import com.dms.app.services.timer.TimerEngine

/**
 * MainActivity serves as the primary Android FragmentActivity entry point for the application.
 * Initializes storage, timer engine, viewmodels, biometrics, GPS permissions, and renders Jetpack Compose screens.
 */
class MainActivity : FragmentActivity() {

    private val keyStoreManager by lazy { KeyStoreManager() }
    private val dbHelper by lazy { SQLCipherHelper(applicationContext) }
    private val secureStorage by lazy { SecureStorageService(keyStoreManager, dbHelper) }
    private val timerEngine by lazy { TimerEngine() }
    private val notificationScheduler by lazy { NotificationScheduler(applicationContext) }

    private val evaluateTimerUseCase by lazy { EvaluateTimerUseCase(secureStorage, timerEngine) }
    private val checkInUseCase by lazy { CheckInUseCase(secureStorage, timerEngine, notificationScheduler, context = applicationContext) }

    private val checkInViewModel by lazy { CheckInViewModel(checkInUseCase, evaluateTimerUseCase, secureStorage) }
    private val settingsViewModel by lazy { SettingsViewModel(secureStorage) }

    private val checkInScreen by lazy { CheckInScreen(checkInViewModel) }
    private val settingsScreen by lazy { SettingsScreen(settingsViewModel) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize notification channels & state
        notificationScheduler.createNotificationChannels()
        checkInViewModel.refreshStatus()

        // Request permissions on launch
        requestAppPermissions()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var currentScreen by remember { mutableStateOf("CHECK_IN") }

                if (currentScreen == "CHECK_IN") {
                    checkInScreen.Content(
                        onNavigateToSettings = { currentScreen = "SETTINGS" }
                    )
                } else {
                    settingsScreen.Content(
                        onBackToMain = {
                            checkInViewModel.refreshStatus()
                            currentScreen = "CHECK_IN"
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkInViewModel.refreshStatus()
    }

    private fun requestAppPermissions() {
        val permissions = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.SEND_SMS)
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissions.isNotEmpty()) {
            requestPermissions(permissions.toTypedArray(), 101)
        }
    }
}
