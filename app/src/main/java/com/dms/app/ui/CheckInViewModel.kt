package com.dms.app.ui

import com.dms.app.domain.interfaces.ISecureStorage
import com.dms.app.domain.models.DmsConfig
import com.dms.app.domain.models.TimerEvaluation
import com.dms.app.domain.models.TimerStatus
import com.dms.app.domain.usecases.CheckInUseCase
import com.dms.app.domain.usecases.EvaluateTimerUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

/**
 * CheckInViewModel manages countdown state, PIN verification, Panic PIN duress triggers, app lock state on startup, and user check-in confirmations.
 */
class CheckInViewModel(
    private val checkInUseCase: CheckInUseCase,
    private val evaluateTimerUseCase: EvaluateTimerUseCase,
    private val storage: ISecureStorage
) {

    private val _configState = MutableStateFlow<DmsConfig>(storage.getConfig())
    val configState: StateFlow<DmsConfig> = _configState.asStateFlow()

    // App is ONLY locked on startup if enableBiometricLock is explicitly turned ON!
    private val _isAppLocked = MutableStateFlow<Boolean>(
        storage.getConfig().enableBiometricLock && (storage.getConfig().appPin.isNotBlank() || storage.getConfig().panicPin.isNotBlank())
    )
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private val _timerState = MutableStateFlow<TimerEvaluation>(
        TimerEvaluation(
            status = TimerStatus.ACTIVE,
            remainingMinutes = 1440L,
            elapsedMinutes = 0L,
            expiryTimestampEpochMillis = Instant.now().toEpochMilli() + 86400000L,
            lastCheckInTimestampEpochMillis = Instant.now().toEpochMilli()
        )
    )
    val timerState: StateFlow<TimerEvaluation> = _timerState.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun refreshStatus() {
        val cfg = storage.getConfig()
        _configState.value = cfg

        // Strictest condition: Lock ONLY if enableBiometricLock is TRUE!
        val shouldLock = cfg.enableBiometricLock && (cfg.appPin.isNotBlank() || cfg.panicPin.isNotBlank())
        if (!cfg.enableBiometricLock) {
            _isAppLocked.value = false
        } else if (shouldLock && _isAppLocked.value != false) {
            _isAppLocked.value = true
        }

        val eval = evaluateTimerUseCase.evaluateCurrentStatus()
        _timerState.value = eval
    }

    fun verifyStartupPin(enteredPin: String): Boolean {
        val config = storage.getConfig()
        val cleanPin = enteredPin.trim()

        // 1. Check Panic PIN (Nötigungs-PIN) first
        if (config.panicPin.isNotBlank() && cleanPin == config.panicPin.trim()) {
            performPanicCheckIn()
            _isAppLocked.value = false
            return true
        }

        // 2. Check regular App PIN
        if (config.appPin.isNotBlank() && cleanPin == config.appPin.trim()) {
            _isAppLocked.value = false
            return true
        }

        // 3. Fallback if biometric enabled without specific PIN
        if (config.enableBiometricLock && config.appPin.isBlank() && config.panicPin.isBlank()) {
            _isAppLocked.value = false
            return true
        }

        return false
    }

    fun unlockAppDirectly() {
        _isAppLocked.value = false
    }

    fun performCheckIn(method: String = "MANUAL_APP") {
        val updatedEval = checkInUseCase.executeCheckIn(method = method)
        _timerState.value = updatedEval
        _userMessage.value = if (_configState.value.language == "EN") "Check-in confirmed successfully!" else "Check-in erfolgreich bestätigt!"
    }

    fun performPanicCheckIn() {
        // Feigns successful check-in to user, but SECRETLY launches emergency dispatch in background!
        val updatedEval = checkInUseCase.executePanicCheckIn()
        _timerState.value = updatedEval
        _userMessage.value = if (_configState.value.language == "EN") "Check-in confirmed successfully!" else "Check-in erfolgreich bestätigt!"
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }
}
