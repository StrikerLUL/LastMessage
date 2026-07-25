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
import com.dms.app.services.notifications.NotificationScheduler
import com.dms.app.services.storage.SecureStorageService
import com.dms.app.services.timer.TimerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * CheckInCheckWorker runs periodic background timer evaluations (every 15 minutes).
 * Manages Grace Period (Gnadenfrist) warning push notifications and triggers emergency dispatch when expired.
 */
class CheckInCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val keyStoreManager = KeyStoreManager()
            val dbHelper = SQLCipherHelper(applicationContext)
            val secureStorage = SecureStorageService(keyStoreManager, dbHelper)
            val timerEngine = TimerEngine()
            val emergencyDispatcher = EmergencyDispatchEngine()
            val notificationScheduler = NotificationScheduler(applicationContext)

            val evaluateTimerUseCase = EvaluateTimerUseCase(secureStorage, timerEngine)
            val dispatchEmergencyUseCase = DispatchEmergencyUseCase(secureStorage, emergencyDispatcher)

            val evaluation = evaluateTimerUseCase.evaluateCurrentStatus()
            val config = secureStorage.getConfig()
            val isEn = config.language == "EN"

            when (evaluation.status) {
                TimerStatus.ACTIVE, TimerStatus.WARNING -> {
                    Result.success()
                }
                TimerStatus.GRACE_PERIOD -> {
                    val remainingHours = (evaluation.remainingGraceMinutes / 60) + 1
                    val title = if (isEn) "🚨 EMERGENCY GRACE PERIOD ACTIVE!" else "🚨 NOTFALL-GNADENFRIST AKTIV!"
                    val message = if (isEn)
                        "Countdown expired! You have $remainingHours hour(s) left to cancel before emergency alert dispatch."
                    else
                        "Notfall-Countdown abgelaufen! Noch ca. $remainingHours Std. Zeit zum Abbrechen vor Notfall-Versand."

                    notificationScheduler.sendWarningNotification(title, message)
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
