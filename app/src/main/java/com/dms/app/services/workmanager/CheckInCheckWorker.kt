package com.dms.app.services.workmanager

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dms.app.data.local.KeyStoreManager
import com.dms.app.data.local.SQLCipherHelper
import com.dms.app.domain.models.TimerStatus
import com.dms.app.domain.usecases.DispatchEmergencyUseCase
import com.dms.app.domain.usecases.EvaluateTimerUseCase
import com.dms.app.services.dispatch.EmergencyDispatchEngine
import com.dms.app.services.storage.SecureStorageService
import com.dms.app.services.timer.TimerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * CheckInCheckWorker runs periodic background timer evaluations (every 15 minutes).
 * Independent of UI lifecycle; triggers EmergencyDispatchUseCase when timer expires.
 */
class CheckInCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val keyStoreManager = KeyStoreManager()
            val dbHelper = SQLCipherHelper()
            val secureStorage = SecureStorageService(keyStoreManager, dbHelper)
            val timerEngine = TimerEngine()
            val emergencyDispatcher = EmergencyDispatchEngine()

            val evaluateTimerUseCase = EvaluateTimerUseCase(secureStorage, timerEngine)
            val dispatchEmergencyUseCase = DispatchEmergencyUseCase(secureStorage, emergencyDispatcher)

            val evaluation = evaluateTimerUseCase.evaluateCurrentStatus()

            when (evaluation.status) {
                TimerStatus.ACTIVE, TimerStatus.WARNING -> {
                    Result.success()
                }
                TimerStatus.EXPIRED -> {
                    val dispatchResult = dispatchEmergencyUseCase.executeEmergencyDispatch()
                    if (dispatchResult.success) {
                        Result.success()
                    } else {
                        Result.retry()
                    }
                }
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
