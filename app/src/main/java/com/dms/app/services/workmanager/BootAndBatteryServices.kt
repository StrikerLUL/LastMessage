package com.dms.app.services.workmanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dms.app.domain.models.TimerStatus
import com.dms.app.domain.usecases.DispatchEmergencyUseCase
import com.dms.app.domain.usecases.EvaluateTimerUseCase
import com.dms.app.domain.usecases.ScheduleNotificationsUseCase

/**
 * BootReceiver reacts to RECEIVE_BOOT_COMPLETED, LOCKED_BOOT_COMPLETED, and QUICKBOOT_POWERON system intents.
 * Reschedules periodic WorkManager workers and exact notification alarms following device boot.
 * If the countdown timer expired while device was off, triggers emergency dispatch immediately.
 */
open class BootReceiver(
    private val scheduleNotificationsUseCase: ScheduleNotificationsUseCase? = null,
    private val evaluateTimerUseCase: EvaluateTimerUseCase? = null,
    private val dispatchEmergencyUseCase: DispatchEmergencyUseCase? = null
) : BroadcastReceiver() {

    constructor() : this(null, null, null)

    companion object {
        const val ACTION_BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED"
        const val ACTION_LOCKED_BOOT_COMPLETED = "android.intent.action.LOCKED_BOOT_COMPLETED"
        const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        onReceiveIntent(intent?.action)
    }

    fun onReceiveIntent(action: String?): BootResult {
        if (action == null) return BootResult.Ignored("Null action")

        return when (action) {
            ACTION_BOOT_COMPLETED,
            ACTION_LOCKED_BOOT_COMPLETED,
            ACTION_QUICKBOOT_POWERON -> {
                // 1. Reschedule notifications and workers
                scheduleNotificationsUseCase?.rescheduleAll()

                // 2. Reboot Recovery Missed Expiry Dispatch
                var extraMessage = ""
                if (evaluateTimerUseCase != null && dispatchEmergencyUseCase != null) {
                    val eval = evaluateTimerUseCase.evaluateCurrentStatus()
                    if (eval.status == TimerStatus.EXPIRED) {
                        val dispatchRes = dispatchEmergencyUseCase.executeEmergencyDispatch()
                        extraMessage = " [MISSED EXPIRY DISPATCH TRIGGERED: ${dispatchRes.summary}]"
                    }
                }

                BootResult.Handled("Rescheduled notifications and WorkManager tasks for boot action: $action$extraMessage")
            }
            else -> BootResult.Ignored("Action $action not handled by BootReceiver")
        }
    }

    sealed class BootResult {
        data class Handled(val details: String) : BootResult()
        data class Ignored(val reason: String) : BootResult()
    }
}

/**
 * BatteryOptimizationHelper utility to check and request ignoring battery optimizations.
 */
class BatteryOptimizationHelper {

    companion object {
        const val ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS = "android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"
        const val PACKAGE_PREFIX = "package:"
    }

    fun isIgnoringBatteryOptimizations(isIgnoring: Boolean): Boolean {
        return isIgnoring
    }

    fun buildIgnoreBatteryOptimizationIntentUri(packageName: String = "com.dms.app"): String {
        return "$PACKAGE_PREFIX$packageName"
    }
}
