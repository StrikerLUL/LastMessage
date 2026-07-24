package com.dms.app.ui

import com.dms.app.domain.models.TimerEvaluation
import com.dms.app.domain.models.TimerStatus
import com.dms.app.domain.usecases.CheckInUseCase
import com.dms.app.domain.usecases.EvaluateTimerUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

/**
 * CheckInViewModel manages countdown state and user check-in triggers for CheckInScreen.
 */
class CheckInViewModel(
    private val checkInUseCase: CheckInUseCase,
    private val evaluateTimerUseCase: EvaluateTimerUseCase
) {

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
        val eval = evaluateTimerUseCase.evaluateCurrentStatus()
        _timerState.value = eval
    }

    fun performCheckIn(method: String = "MANUAL_APP") {
        val updatedEval = checkInUseCase.executeCheckIn(method = method)
        _timerState.value = updatedEval
        _userMessage.value = "Check-in confirmed successfully!"
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }
}
