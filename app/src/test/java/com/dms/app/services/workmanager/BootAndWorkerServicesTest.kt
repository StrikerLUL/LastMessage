package com.dms.app.services.workmanager

import com.dms.app.data.local.KeyStoreManager
import com.dms.app.data.local.SQLCipherHelper
import com.dms.app.domain.usecases.DispatchEmergencyUseCase
import com.dms.app.domain.usecases.EvaluateTimerUseCase
import com.dms.app.domain.usecases.ScheduleNotificationsUseCase
import com.dms.app.services.dispatch.EmergencyDispatchEngine
import com.dms.app.services.notifications.NotificationScheduler
import com.dms.app.services.storage.SecureStorageService
import com.dms.app.services.timer.TimerEngine
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BootAndWorkerServicesTest {

    private lateinit var storage: SecureStorageService
    private lateinit var timerEngine: TimerEngine
    private lateinit var notificationScheduler: NotificationScheduler
    private lateinit var scheduleNotificationsUseCase: ScheduleNotificationsUseCase
    private lateinit var evaluateTimerUseCase: EvaluateTimerUseCase
    private lateinit var dispatchEmergencyUseCase: DispatchEmergencyUseCase
    private lateinit var bootReceiver: BootReceiver
    private lateinit var worker: CheckInCheckWorker

    @BeforeEach
    fun setUp() {
        val dbHelper = SQLCipherHelper(dbPath = "jdbc:sqlite::memory:")
        storage = SecureStorageService(KeyStoreManager("test_boot_key"), dbHelper)
        timerEngine = TimerEngine()
        notificationScheduler = NotificationScheduler()

        scheduleNotificationsUseCase = ScheduleNotificationsUseCase(storage, timerEngine, notificationScheduler)
        evaluateTimerUseCase = EvaluateTimerUseCase(storage, timerEngine)
        dispatchEmergencyUseCase = DispatchEmergencyUseCase(storage, EmergencyDispatchEngine())

        bootReceiver = BootReceiver(scheduleNotificationsUseCase, evaluateTimerUseCase, dispatchEmergencyUseCase)
        worker = CheckInCheckWorker(evaluateTimerUseCase, dispatchEmergencyUseCase)
    }

    @Test
    fun testBootReceiverOnReceiveIntentHandled() {
        val result = bootReceiver.onReceiveIntent(BootReceiver.ACTION_BOOT_COMPLETED)
        assertTrue(result is BootReceiver.BootResult.Handled)
        val details = (result as BootReceiver.BootResult.Handled).details
        assertTrue(details.contains(BootReceiver.ACTION_BOOT_COMPLETED))
    }

    @Test
    fun testBootReceiverIgnoredAction() {
        val result = bootReceiver.onReceiveIntent("com.unknown.ACTION")
        assertTrue(result is BootReceiver.BootResult.Ignored)
    }

    @Test
    fun testCheckInCheckWorkerActiveExecution() = runBlocking {
        // Active status
        storage.saveCheckInTimestamp(java.time.Instant.now().toString())
        val result = worker.doWork()
        assertTrue(result is CheckInCheckWorker.WorkerResult.Success)
        val msg = (result as CheckInCheckWorker.WorkerResult.Success).message
        assertTrue(msg.contains("Timer active"))
    }

    @Test
    fun testBatteryOptimizationHelper() {
        val helper = BatteryOptimizationHelper()
        assertTrue(helper.isIgnoringBatteryOptimizations(true))
        assertFalse(helper.isIgnoringBatteryOptimizations(false))
        assertEquals("package:com.dms.app", helper.buildIgnoreBatteryOptimizationIntentUri("com.dms.app"))
    }

    @Test
    fun testBootReceiverInheritanceAndDefaultConstructor() {
        val defaultBootReceiver = BootReceiver()
        assertTrue(defaultBootReceiver is android.content.BroadcastReceiver)
        val result = defaultBootReceiver.onReceiveIntent(null)
        assertTrue(result is BootReceiver.BootResult.Ignored)

        defaultBootReceiver.onReceive(null, null)
    }

    @Test
    fun testCheckInCheckWorkerInheritanceAndConstructors() = runBlocking {
        val defaultWorker = CheckInCheckWorker()
        assertTrue(defaultWorker is androidx.work.CoroutineWorker)
        assertTrue(defaultWorker is androidx.work.ListenableWorker)
        val res = defaultWorker.doWork()
        assertTrue(res is CheckInCheckWorker.WorkerResult.Failure)
    }

    @Test
    fun testMainActivityInheritance() {
        val activity = com.dms.app.ui.MainActivity()
        assertTrue(activity is androidx.activity.ComponentActivity)
        assertTrue(activity is android.app.Activity)
        activity.onCreate()
    }
}
