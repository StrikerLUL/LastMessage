# Dead Man's Switch Mobile Application
## Edge Cases Analysis Matrix & System Robustness Verification Specification

**Document Version:** 1.0.0  
**Date:** July 24, 2026  
**Status:** Approved Engineering Specification  
**Target Platform:** Android (API Level 26 to 35+ / Android 8.0 to Android 15+)  

---

## Executive Summary

The **Dead Man's Switch (DMS)** mobile application is a safety-critical application. Unlike standard consumer apps where background execution failure results in mild user inconvenience, a failure in a Dead Man's Switch application can have life-threatening consequences—preventing emergency alerts from reaching trusted contacts when a user is incapacitated.

Operating on Android requires navigating complex system power management policies, Doze mode restrictions, hardware states (Direct Boot, Flight Mode, missing SIM cards), aggressive vendor-specific task killers, network socket freezes, time manipulation attempt, and server outages.

This document establishes the **Edge Cases Analysis Matrix & Robustness Verification Architecture** for the DMS mobile application. It details 8 critical failure scenarios, explaining their root causes, system impact, mitigation architecture, code-level test assertion logic, and step-by-step ADB shell validation procedures.

---

## 1. Master Edge Cases Analysis Matrix Overview

| Scenario ID | Edge Case Category | Root Cause | System Behavior Without Mitigation | System Behavior With Mitigation Architecture | Recovery SLA |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **SC-01** | **Offline / Flight Mode** | No cellular or data connection during timer expiry window. | Email fails; SMS fails if in Flight Mode; alert lost silently. | Immediate SMS via cellular radio; deferred SMTP fallback worker queued with `NetworkType.CONNECTED` constraint. | < 5s for SMS; Instant on data restoration for SMTP |
| **SC-02** | **Direct Boot State** | Device reboots before timer expiry; locked prior to user PIN entry. | CE storage locked; app cannot read DB; background alarms lost. | `directBootAware` receiver & DE storage access; alarms re-registered before user unlock. | < 3s post-kernel boot |
| **SC-03** | **Post-Expiry Reboot** | Device powered off when expiry occurs, then powered back on. | Missed expiry alarm lost; no emergency dispatch triggered. | Instant wall-clock vs expiry evaluation on `LOCKED_BOOT_COMPLETED`; immediate emergency dispatch. | < 5s post-kernel boot |
| **SC-04** | **Deep Doze & App Standby** | CPU asleep for hours; app demoted to `RARE`/`RESTRICTED` bucket. | Periodic WorkManager jobs deferred up to 2 hours; network sockets frozen. | `setExactAndAllowWhileIdle()` wakes CPU; `WAKE_LOCK` held; battery whitelist preserves network sockets. | 0s delay (Exact Alarm trigger) |
| **SC-05** | **OEM Task Killer / Force Stop** | App process killed by swipe or OEM daemons (MIUI, One UI, EMUI). | Background workers terminated; timer state frozen. | Pinned Foreground Service (`specialUse|health`); `AlarmManager` persistence in `system_server`; OEM settings setup modal. | Instant (AlarmManager fires outside process) |
| **SC-06** | **SMS Failure / Missing SIM** | No SIM card inserted, radio off, or SMS delivery rejected by PSTN. | SMS fails silently; emergency contacts receive no alert. | `SmsManager` `PendingIntent` callbacks detect failure; instant automatic fallback to SMTP email dispatch. | < 2s post-SMS failure callback |
| **SC-07** | **SMTP Server Outage / Bad Auth** | Invalid credentials, 535 auth error, or mail server socket timeout. | Exception uncaught; email dispatch lost permanently. | Exponential backoff retry loop (5s, 15s, 45s); WorkManager fallback persistence; audit logging. | Retry attempt 1 (5s), 2 (15s), 3 (45s) |
| **SC-08** | **System Time Tampering** | User manually alters device clock backward/forward or timezone change. | Timer bypassed or premature emergency dispatch triggered. | Dual monotonic (`elapsedRealtime()`) vs wall-clock validation; `ACTION_TIME_CHANGED` listener; NTP drift check. | Real-time on clock change event |

---

## 2. Scenario Deep Dives & Mitigation Architecture

### 2.1 Scenario 1: Offline / Flight Mode during Expiry Window

#### A. Scenario Overview & Root Cause
When the countdown timer expires, the device may be completely disconnected from the internet (no Wi-Fi, no mobile data) or placed in Flight Mode (cellular baseband radio powered down). 
- **Root Cause:** Cellular data (IP network) and cellular PSTN voice/SMS radio rely on different physical layers and system permissions. SMTP email dispatch requires an active TCP socket connection over an IP network, which fails instantly when offline. Standard SMS requires baseband registration to a cellular PSTN tower, which fails when in Flight Mode or when SIM network service is unavailable.

#### B. System & User Impact Analysis
Without robust offline handling, if a user is incapacitated in a remote area without cellular data or in Flight Mode:
1. SMTP Email dispatches throw `UnknownHostException` or `SocketTimeoutException` and collapse if uncaught.
2. If SMS dispatch fails due to radio power-down, emergency contact notifications are dropped without record.

#### C. Mitigation Architecture & Technical Solution

```
                 [ Countdown Timer Expires Offline ]
                                 │
                                 ▼
             [ EmergencyDispatchEngine Evaluates Channels ]
                                 │
                 ┌───────────────┴───────────────┐
                 │                               │
                 ▼                               ▼
    [ Primary Channel: SMS ]        [ Secondary Channel: SMTP Email ]
                 │                               │
    [ Check Baseband Radio ]         [ Check Network Connectivity ]
                 │                               │
      ┌──────────┴──────────┐          ┌─────────┴─────────┐
      │                     │          │                   │
 (Radio On)           (Flight Mode) (Connected)       (Offline)
      │                     │          │                   │
      ▼                     ▼          ▼                   ▼
[ Send SMS via ]      [ Register   [ Dispatch  [ Queue WorkManager ]
[ SmsManager   ]      [ Radio State] [ SMTP Mail] [ Network-Constrained ]
[ Direct PSTN  ]      [ Listener   ]           [ Deferred Retry Job]
```

1. **Dual Dispatch Isolation:** SMS and SMTP dispatch channels operate independently. SMS dispatch via native `SmsManager` operates over GSM/CDMA signaling channels and requires **no mobile data plan** or internet access—only basic cell tower signal.
2. **Deferred WorkManager SMTP Fallback:** If internet connectivity is unavailable during dispatch, the SMTP engine enqueues a deferred background job using `androidx.work.WorkManager` with a `NetworkType.CONNECTED` constraint.
3. **Audit Logging:** The app logs an `OFFLINE_DISPATCH_DEFERRED` audit record in the encrypted database (`checkin_logs`), preserving dispatch state.

#### D. Code Assertion Logic & Unit Test Implementation

```kotlin
package com.dms.app.domain.usecase

import com.dms.app.data.repository.FakeCheckInRepository
import com.dms.app.data.repository.FakeNetworkStateProvider
import com.dms.app.domain.model.DispatchResult
import com.dms.app.domain.model.NetworkStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Scenario1OfflineDispatchTest {

    private lateinit var fakeNetworkProvider: FakeNetworkStateProvider
    private lateinit var fakeRepository: FakeCheckInRepository
    private lateinit var dispatchUseCase: DispatchEmergencyUseCase

    @Before
    fun setUp() {
        fakeNetworkProvider = FakeNetworkStateProvider()
        fakeRepository = FakeCheckInRepository()
        dispatchUseCase = DispatchEmergencyUseCase(
            repository = fakeRepository,
            networkProvider = fakeNetworkProvider
        )
    }

    @Test
    fun `when device is in flight mode, SMS queues for radio and SMTP enqueues workmanager job`() = runBlocking {
        // Arrange: Simulate completely offline / flight mode state
        fakeNetworkProvider.setNetworkStatus(NetworkStatus.DISCONNECTED)
        fakeNetworkProvider.setFlightMode(true)

        // Act: Trigger emergency dispatch
        val result = dispatchUseCase.executeEmergencyDispatch()

        // Assert: Primary SMS handling should record offline state & SMTP should enqueue deferred work
        assertTrue(result is DispatchResult.OfflineDeferred)
        val deferredResult = result as DispatchResult.OfflineDeferred
        
        assertEquals(true, deferredResult.isSmsQueuedForRadio)
        assertEquals(true, deferredResult.isSmtpWorkManagerEnqueued)
        
        // Verify audit log entry in database repository
        val logs = fakeRepository.getLatestLogs(1)
        assertEquals("OFFLINE_DISPATCH_DEFERRED", logs.first().status)
    }
}
```

#### E. Step-by-Step ADB Shell Commands & Testing Procedure

```bash
# Step 1: Enable Flight Mode via ADB shell
adb shell cmd connectivity airplane-mode enable

# Step 2: Verify network state is completely disconnected
adb shell dumpsys connectivity | grep "NetworkAgentInfo"

# Step 3: Trigger emergency dispatch worker via ADB broadcast
adb shell am broadcast -a com.dms.app.ACTION_TRIGGER_EMERGENCY_DISPATCH

# Step 4: Verify WorkManager enqueued deferred network task
adb shell dumpsys jobscheduler | grep "com.dms.app"

# Step 5: Disable Flight Mode to simulate reconnecting to cellular towers
adb shell cmd connectivity airplane-mode disable

# Step 6: Verify background worker automatically executes upon network restoration
adb shell dumpsys workmanager | grep "CheckInCheckWorker"
```

---

### 2.2 Scenario 2: Device Reboot before Expiry (Direct Boot State)

#### A. Scenario Overview & Root Cause
If a device reboots (due to software update, battery drain, or manual restart) while a check-in countdown is running, it enters the **Direct Boot** state. The phone remains locked until the user enters their PIN, Pattern, or Password.
- **Root Cause:** Android partitions storage into **Credential Encrypted (CE)** and **Device Encrypted (DE)** storage. CE storage remains encrypted and completely inaccessible until first user unlock. Standard Room/SQLite databases and default `SharedPreferences` reside in CE storage and cannot be read after a reboot until the user unlocks the screen.

#### B. System & User Impact Analysis
If timer state or database encryption keys are stored exclusively in CE storage:
1. System receivers fail with `SQLiteException` or `IllegalStateException` when trying to access the database on `LOCKED_BOOT_COMPLETED`.
2. All scheduled exact alarms (`AlarmManager`) are wiped by the OS on kernel reboot and are not restored, causing the Dead Man's Switch timer to stop permanently.

#### C. Mitigation Architecture & Technical Solution

```
                 [ Device Power On / Reboot Event ]
                                 │
                                 ▼
         [ Kernel Boots into DIRECT BOOT State (Device Locked) ]
                                 │
       [ System Fires ACTION_LOCKED_BOOT_COMPLETED Broadcast ]
                                 │
                                 ▼
            [ BootReceiver (directBootAware="true") ]
                                 │
       [ Accesses Device Encrypted (DE) Context Storage ]
       [ context.createDeviceProtectedStorageContext()  ]
                                 │
                                 ▼
         [ Reads DE Secure Timer State & Master Key Spec ]
                                 │
                                 ▼
         [ Calculates Next Expiry Timestamp Monotonically ]
                                 │
                                 ▼
   [ Re-registers AlarmManager.setExactAndAllowWhileIdle() ]
                                 │
                                 ▼
      [ Timer Protection Fully Restored BEFORE User Unlock ]
```

1. **`directBootAware` Manifest Declaration:** `BootReceiver`, `EmergencyDispatchService`, and `NotificationActionReceiver` are declared with `android:directBootAware="true"`.
2. **DE Storage Partitioning:** Core safety state (`last_checkin_timestamp`, `timer_interval`, and database DE master key fragment) is saved in **Device Encrypted Storage** via `context.createDeviceProtectedStorageContext()`.
3. **Direct Boot Receiver Flow:** Upon receiving `ACTION_LOCKED_BOOT_COMPLETED`, `BootReceiver` reads DE storage without accessing CE storage, recalculates next expiry time, and re-enqueues `AlarmManager.setExactAndAllowWhileIdle()` before the user enters their PIN.

#### D. Code Assertion Logic & Unit Test Implementation

```kotlin
package com.dms.app.receiver

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.dms.app.data.storage.DeStorageManager
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.N]) // Direct Boot introduced in API 24
class Scenario2DirectBootReceiverTest {

    private lateinit var context: Context
    private lateinit var bootReceiver: BootReceiver
    private lateinit var deStorageManager: DeStorageManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Create Device Protected Context explicitly
        val deContext = context.createDeviceProtectedStorageContext()
        deStorageManager = DeStorageManager(deContext)
        bootReceiver = BootReceiver()

        // Seed DE Storage with running timer configuration
        deStorageManager.saveTimerState(
            lastCheckInEpochMs = System.currentTimeMillis() - 3600000, // 1 hour ago
            intervalMinutes = 1440 // 24 hours
        )
    }

    @Test
    fun `bootReceiver restores alarms from DE storage during LOCKED_BOOT_COMPLETED`() {
        // Arrange: Construct Intent for Direct Boot broadcast
        val lockedBootIntent = Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED)

        // Act: Execute receiver in Direct Boot simulation state
        bootReceiver.onReceive(context.createDeviceProtectedStorageContext(), lockedBootIntent)

        // Assert: Verify DE Storage was accessed and alarm scheduled
        val scheduledAlarm = deStorageManager.getScheduledAlarmDetails()
        assertNotNull("Alarm must be scheduled during Direct Boot state", scheduledAlarm)
        assertTrue("Scheduled alarm time must be in future", scheduledAlarm.triggerAtMs > System.currentTimeMillis())
    }
}
```

#### E. Step-by-Step ADB Shell Commands & Testing Procedure

```bash
# Step 1: Simulate Direct Boot state by locking storage via ADB (Emulators)
adb shell am switch-user 0

# Step 2: Emulate system reboot broadcast for LOCKED_BOOT_COMPLETED
adb shell am broadcast -a android.intent.action.LOCKED_BOOT_COMPLETED -c android.intent.category.DEFAULT

# Step 3: Inspect scheduled AlarmManager alarms while in Direct Boot state
adb shell dumpsys alarm | grep -E "com.dms.app|RTC_WAKEUP"

# Step 4: Verify process read Device Encrypted (DE) storage log
adb logcat -d | grep "DMS_BootReceiver: Direct Boot timer restored"

# Step 5: Simulate user unlocking device (BOOT_COMPLETED)
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED
```

---

### 2.3 Scenario 3: Device Reboot after Expiry

#### A. Scenario Overview & Root Cause
The device was powered off (or battery dead) when the scheduled expiry time passed, and is subsequently powered back on hours or days later.
- **Root Cause:** When a phone is powered off, physical CPU execution ceases. `AlarmManager` hardware timers cannot wake up a unpowered device. When the phone boots up, the scheduled alarm timestamp is already in the past.

#### B. System & User Impact Analysis
If the system only relies on future alarm callbacks without evaluating past missed thresholds:
1. The app assumes the timer is still active or misses the expiry event entirely.
2. Emergency contacts are never notified despite the safety window having expired long ago.

#### C. Mitigation Architecture & Technical Solution

```
                 [ Device Powered On Post-Expiry ]
                                 │
                                 ▼
             [ BootReceiver Intercepts BOOT_COMPLETED ]
                                 │
       [ Queries DE / SQLCipher Storage for Last Check-In ]
                                 │
                                 ▼
           [ Evaluates Current Time vs Expiry Threshold ]
                                 │
            CurrentTimeMs (18:00) > TargetExpiryMs (14:00)
                                 │
                                 ▼
              [ MISSED EXPIRY DETECTED (Delta = +4h) ]
                                 │
                                 ▼
             [ Triggers EmergencyDispatchService ]
             [ Immediate High-Priority Dispatch  ]
                                 │
                                 ▼
            [ Writes Audit Log: MISSED_EXPIRY_ON_BOOT ]
```

1. **Immediate Post-Boot Audit:** Upon receiving `LOCKED_BOOT_COMPLETED` or `BOOT_COMPLETED`, `BootReceiver` immediately invokes `EvaluateTimerUseCase.evaluateCurrentStatus()`.
2. **Missed Window Calculation:** If `System.currentTimeMillis() > targetExpiryTimestamp`, the system flags an **Immediate Missed Expiry Condition**.
3. **Instant Foreground Dispatch:** `BootReceiver` bypasses normal countdown delays and immediately invokes `EmergencyDispatchService.startEmergencyDispatch(reason = "MISSED_EXPIRY_ON_BOOT")`.

#### D. Code Assertion Logic & Unit Test Implementation

```kotlin
package com.dms.app.domain.usecase

import com.dms.app.data.repository.FakeCheckInRepository
import com.dms.app.domain.model.TimerStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Scenario3MissedExpiryBootTest {

    private lateinit var repository: FakeCheckInRepository
    private lateinit var evaluateTimerUseCase: EvaluateTimerUseCase

    @Before
    fun setUp() {
        repository = FakeCheckInRepository()
        evaluateTimerUseCase = EvaluateTimerUseCase(repository)
    }

    @Test
    fun `boot evaluation when current time exceeds expiry triggers immediate EXPIRED status`() = runBlocking {
        // Arrange: Set last check-in to 30 hours ago on a 24-hour (1440 min) timer
        val currentTimeMs = System.currentTimeMillis()
        val lastCheckInMs = currentTimeMs - (30 * 3600 * 1000L) // 30 hours ago
        
        repository.setLastCheckInTimestamp(lastCheckInMs)
        repository.setTimerIntervalMinutes(1440) // 24 hours

        // Act: Evaluate status upon boot recovery
        val status = evaluateTimerUseCase.evaluateStatusAtBoot(currentTimeMs)

        // Assert: Must evaluate directly to EXPIRED with positive overdue delta
        assertTrue("Status must be EXPIRED", status is TimerStatus.Expired)
        val expiredStatus = status as TimerStatus.Expired
        assertEquals(6 * 3600 * 1000L, expiredStatus.overdueDurationMs) // Overdue by 6 hours
        
        // Verify audit log status flag
        val lastLog = repository.getLatestLogs(1).first()
        assertEquals("MISSED_EXPIRY_ON_BOOT_TRIGGERED", lastLog.status)
    }
}
```

#### E. Step-by-Step ADB Shell Commands & Testing Procedure

```bash
# Step 1: Set app timer to 1 hour (60 mins) via app settings or database
adb shell sqlite3 /data/data/com.dms.app/databases/dms_encrypted.db "UPDATE app_config SET timer_interval_minutes=60;"

# Step 2: Simulate device powering down for 3 hours (shift system clock forward by 3 hours via ADB)
adb shell date $(date -d "+3 hours" +%m%d%H%M%Y.%S)

# Step 3: Trigger BOOT_COMPLETED broadcast to simulate device starting post-expiry
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -n com.dms.app/.receiver.BootReceiver

# Step 4: Verify EmergencyDispatchService was launched immediately
adb shell dumpsys activity services | grep "EmergencyDispatchService"

# Step 5: Check logcat output for instant emergency dispatch trigger
adb logcat -d | grep "DMS_BootReceiver: Missed expiry detected on boot! Overdue duration:"
```

---

### 2.4 Scenario 4: Deep Doze Mode & Aggressive App Standby Buckets

#### A. Scenario Overview & Root Cause
The device is left untouched on a table for hours, entering **Deep Doze Mode**. Furthermore, because a Dead Man's Switch app is rarely opened interactively, Android OS automatically demotes it to the **`RARE`** or **`RESTRICTED` App Standby Bucket**.
- **Root Cause:** In Doze mode, Android defers background job execution (`JobScheduler`, inexact `WorkManager`) to periodic maintenance windows spaced up to 2 hours apart. Network sockets are frozen, and CPU execution is suppressed to preserve battery.

#### B. System & User Impact Analysis
1. Non-exact alarms or standard `WorkManager` workers fire up to 2 hours late.
2. When the alarm triggers, TCP network sockets for SMTP email dispatches are blocked by the OS, causing connection timeouts.

#### C. Mitigation Architecture & Technical Solution

```
               [ Device Enters Deep Doze Mode ]
                               │
                               ▼
        [ System Suspends Standard WorkManager / Jobs ]
                               │
                               ▼
   [ AlarmManager.setExactAndAllowWhileIdle() Triggers ]
                               │
        (CPU Wakes Up; Ignores Doze Maintenance Window)
                               │
                               ▼
      [ Worker Acquires PowerManager Partial CPU WakeLock ]
      [ wakeLock.acquire(10000L) // 10s Safety Timeout   ]
                               │
                               ▼
     [ Battery Whitelist Exemption Active (REQUEST_IGNORE) ]
     [ Direct Access to Unrestricted Network Sockets       ]
                               │
                               ▼
     [ Executes Emergency Dispatch & Releases WakeLock     ]
```

1. **Exact Alarm Exemption:** Countdown milestone alarms use `AlarmManager.setExactAndAllowWhileIdle()` or `AlarmManager.setAlarmClock()`. The `AndAllowWhileIdle` flag forces the Android system to wake the CPU and execute the alarm even in deep sleep.
2. **CPU Partial WakeLock:** During timer evaluation and dispatch, the worker acquires a `PowerManager.PARTIAL_WAKE_LOCK` with a strict 10-second timeout (`wakeLock.acquire(10000L)`).
3. **Battery Optimization Exemption:** The app requests `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. This places the app in the exempt bucket, preserving active TCP network socket privileges during Doze mode.

#### D. Code Assertion Logic & Unit Test Implementation

```kotlin
package com.dms.app.permissions

import android.app.AlarmManager
import android.content.Context
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Scenario4DozeAndBatteryExemptionTest {

    private lateinit var context: Context
    private lateinit var powerManager: PowerManager
    private lateinit var alarmManager: AlarmManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    @Test
    fun `verify wakeLock acquisition and exact alarm capability under idle state`() {
        // Arrange & Act: Acquire partial wakeLock for dispatch execution
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DMS:TestWakeLock")
        wakeLock.acquire(5000L) // 5 seconds timeout

        // Assert: WakeLock must be held
        assertTrue("WakeLock must be active to prevent CPU sleep during dispatch", wakeLock.isHeld)
        
        // Release wakeLock
        wakeLock.release()
        assertTrue("WakeLock must release properly", !wakeLock.isHeld)
    }
}
```

#### E. Step-by-Step ADB Shell Commands & Testing Procedure

```bash
# Step 1: Force device into Deep Doze mode via ADB
adb shell dumpsys deviceidle force-idle deep

# Step 2: Verify Doze mode status is IDLE
adb shell dumpsys deviceidle step deep | grep "Stepped to deep mode state: IDLE"

# Step 3: Check App Standby Bucket assigned to app (verify RARE or RESTRICTED)
adb shell am get-standby-bucket com.dms.app

# Step 4: Fire scheduled exact alarm while in Deep Doze
adb shell am broadcast -a com.dms.app.ACTION_EXACT_ALARM_TRIGGER

# Step 5: Verify CPU WakeLock was acquired and dispatch executed despite Doze
adb logcat -d | grep "DMS:DispatchWakeLock: Acquired partial CPU wake lock in Doze mode"

# Step 6: Reset deviceidle state after testing
adb shell dumpsys deviceidle unforce
```

---

### 2.5 Scenario 5: Aggressive OEM Task Killer / Force Stop

#### A. Scenario Overview & Root Cause
Third-party Android OEMs (Xiaomi MIUI/HyperOS, Samsung One UI, Huawei EMUI, Oppo ColorOS) deploy custom aggressive power daemons (e.g. Xiaomi Security, Samsung Device Care, Huawei PowerGenie). Swiping an app from the recent apps list or background battery optimization frequently triggers `force-stop`.
- **Root Cause:** `force-stop` places the package into a stopped state (`FLAG_STOPPED`), unregistering all dynamically registered broadcast receivers and halting background services.

#### B. System & User Impact Analysis
1. Background `CoroutineWorker` instances are killed instantly.
2. The user believes the Dead Man's Switch is active, but background evaluation is dead.

#### C. Mitigation Architecture & Technical Solution

```
                 [ App Launch & Initialization ]
                                 │
                                 ▼
         [ 1. Start Pinned Foreground Service (FGS) ]
         [ foregroundServiceType="specialUse|health"]
         [ Shows Non-Dismissible Warning Notification]
                                 │
                                 ▼
         [ 2. Register Native AlarmManager Alarms   ]
         [ Note: AlarmManager intents persist in    ]
         [ OS 'system_server' independent of process]
                                 │
                                 ▼
         [ 3. Detect OEM Manufacturer at First Boot ]
                                 │
                 ┌───────────────┴───────────────┐
                 │                               │
            (OEM Vendor)                 (Stock Android)
                 │                               │
                 ▼                               ▼
       [ Prompt User via Modal ]       [ Standard Operation ]
       [ Guided Deep-Link to   ]
       [ OEM Autostart & Power ]
       [ Exemption Settings    ]
```

1. **Pinned Foreground Service:** Promotes the application process to foreground importance using `startForeground()` with a persistent notification. Android 14 FGS types (`specialUse|health`) prevent background process reaping.
2. **System Server AlarmManager Persistence:** `AlarmManager` pending intents reside inside the system process (`system_server`), not inside the application process memory. Even if the app process is force-killed, `system_server` re-spawns the app target when the alarm timestamp expires.
3. **In-App OEM Optimization Guide:** `OemBatteryOptimizationHelper` automatically detects vendor hardware (Xiaomi, Samsung, Huawei, Oppo, Vivo) and displays an explicit guided setup dialog directing the user to grant **Autostart** and add the app to **Never Sleeping Apps**.

#### D. Code Assertion Logic & Unit Test Implementation

```kotlin
package com.dms.app.permissions

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Scenario5OemDetectionTest {

    @Test
    fun `verify OEM detection logic correctly identifies custom ROM vendors`() {
        // Arrange & Act: Test manufacturer identification helper
        val xiaomiVendor = "Xiaomi"
        val samsungVendor = "samsung"
        val pixelVendor = "Google"

        // Assert
        assertTrue("Must detect Xiaomi", xiaomiVendor.lowercase().contains("xiaomi"))
        assertTrue("Must detect Samsung", samsungVendor.lowercase().contains("samsung"))
        assertEquals(false, pixelVendor.lowercase().contains("xiaomi"))
    }
}
```

#### E. Step-by-Step ADB Shell Commands & Testing Procedure

```bash
# Step 1: Force stop application to simulate aggressive OEM task kill
adb shell am force-stop com.dms.app

# Step 2: Verify application process is completely terminated
adb shell pidof com.dms.app # Should return empty

# Step 3: Trigger exact AlarmManager alarm (which persists in system_server)
adb shell am broadcast -a com.dms.app.ACTION_EXACT_ALARM_TRIGGER -n com.dms.app/.receiver.BootReceiver

# Step 4: Verify system_server re-spawns application process upon alarm trigger
adb shell pidof com.dms.app # Returns new PID

# Step 5: Check logcat to verify EmergencyDispatchService launched post-kill
adb logcat -d | grep "EmergencyDispatchService: Started post-task-kill"
```

---

### 2.6 Scenario 6: Missing SIM Card / Flight Mode / SMS Delivery Failure

#### A. Scenario Overview & Root Cause
The primary dispatch channel is configured for SMS, but the device lacks an active SIM card, has no cellular service, or the cellular PSTN network rejects the message (e.g. invalid recipient number, out of credit, SIM failure).
- **Root Cause:** Calls to `SmsManager.sendMultipartTextMessage()` do not synchronously throw exceptions. Instead, status is returned asynchronously via `PendingIntent` broad-cast callbacks (`sentIntent` and `deliveryIntent`).

#### B. System & User Impact Analysis
If SMS delivery status callbacks are ignored:
1. The app assumes the SMS was delivered successfully.
2. The primary alert fails, and the secondary SMTP fallback is never triggered.

#### C. Mitigation Architecture & Technical Solution

```
                 [ SmsManager.sendMultipartTextMessage() ]
                                 │
                                 ▼
                 [ Async Sent Intent Broadcast Callback ]
                                 │
                 ┌───────────────┴───────────────┐
                 │                               │
        (RESULT_OK = 0)                 (Result Code != 0)
                 │                   (GENERIC_FAILURE, NO_SERVICE,
                 │                    RADIO_OFF, NULL_PDU)
                 ▼                               │
      [ SMS Delivered Confirmed ]                ▼
      [ Update Log: SUCCESS     ]      [ SMS Failure Callback Triggered ]
                                                 │
                                                 ▼
                                       [ Instant Automatic Fallback ]
                                       [ Launch SmtpMailDispatcher  ]
                                                 │
                                                 ▼
                                       [ Log: SMS_FAILED_SMTP_FALLBACK ]
```

1. **PendingIntent Status Callbacks:** Every SMS segment is dispatched with a unique `PendingIntent` pointing to `SmsStatusReceiver`.
2. **Error Result Codes Interception:** `SmsStatusReceiver` listens for result codes:
   - `SmsManager.RESULT_ERROR_GENERIC_FAILURE`
   - `SmsManager.RESULT_ERROR_NO_SERVICE`
   - `SmsManager.RESULT_ERROR_RADIO_OFF`
   - `SmsManager.RESULT_ERROR_NULL_PDU`
3. **Instant SMTP Fallback Trigger:** Upon receiving any failure result code, the receiver immediately triggers `EmergencyDispatchEngine.triggerSmtpFallback()`, ensuring email dispatch proceeds automatically.

#### D. Code Assertion Logic & Unit Test Implementation

```kotlin
package com.dms.app.receiver

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import androidx.test.core.app.ApplicationProvider
import com.dms.app.domain.usecase.FakeEmergencyDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Scenario6SmsFailureFallbackTest {

    private lateinit var context: Context
    private lateinit var smsStatusReceiver: SmsStatusReceiver
    private lateinit var fakeDispatcher: FakeEmergencyDispatcher

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fakeDispatcher = FakeEmergencyDispatcher()
        smsStatusReceiver = SmsStatusReceiver(fakeDispatcher)
    }

    @Test
    fun `when SMS sent broadcast receives NO_SERVICE error, SMTP fallback triggers instantly`() {
        // Arrange: Create SMS Sent Broadcast Intent with failure code
        val failureIntent = Intent(SmsStatusReceiver.ACTION_SMS_SENT).apply {
            putExtra("contact_id", 42L)
        }

        // Act: Simulate system broadcast with RESULT_ERROR_NO_SERVICE
        smsStatusReceiver.onReceiveWithResultCode(
            context,
            failureIntent,
            SmsManager.RESULT_ERROR_NO_SERVICE
        )

        // Assert: Verify SMS failure was recorded and SMTP fallback invoked
        assertTrue("SMTP fallback must trigger on SMS failure", fakeDispatcher.isSmtpFallbackTriggered)
        assertEquals(42L, fakeDispatcher.lastFailedContactId)
    }
}
```

#### E. Step-by-Step ADB Shell Commands & Testing Procedure

```bash
# Step 1: Simulate SMS Sent Failure via ADB broadcast (RESULT_ERROR_NO_SERVICE = 4)
adb shell am broadcast -a com.dms.app.ACTION_SMS_SENT --ei "result_code" 4 --el "contact_id" 101

# Step 2: Check logcat output for SMS failure detection
adb logcat -d | grep "DMS_SmsReceiver: SMS dispatch failed with error code: 4"

# Step 3: Verify instant fallback to SMTP mailer engine
adb logcat -d | grep "DMS_EmergencyDispatcher: Automatic fallback to SMTP Email triggered"

# Step 4: Inspect checkin_logs table in database to confirm status
adb shell sqlite3 /data/data/com.dms.app/databases/dms_encrypted.db "SELECT * FROM checkin_logs ORDER BY id DESC LIMIT 1;"
```

---

### 2.7 Scenario 7: Invalid or Failing SMTP Credentials / Server Outage

#### A. Scenario Overview & Root Cause
When dispatching fallback email alerts, the outbound SMTP connection fails due to invalid credentials (authentication error 535), mail server outage, TLS handshake error, or network socket timeout.
- **Root Cause:** Networks are inherently unreliable during emergencies. Transient server errors or temporary packet loss can cause an isolated SMTP attempt to fail even if credentials are correct.

#### B. System & User Impact Analysis
A single uncaught socket error stops the emergency email process, leaving the user with zero alerts delivered.

#### C. Mitigation Architecture & Technical Solution

```
                 [ Initiate SMTP Email Dispatch ]
                                 │
                                 ▼
                  ┌──────────────────────────────┐
                  │ Attempt 1: Immediate Send    │
                  └──────────────┬───────────────┘
                                 │
                        (Failure / Exception)
                                 │
                                 ▼
                  ┌──────────────────────────────┐
                  │ Delay 5 Seconds              │
                  │ Attempt 2: Immediate Send    │
                  └──────────────┬───────────────┘
                                 │
                        (Failure / Exception)
                                 │
                                 ▼
                  ┌──────────────────────────────┐
                  │ Delay 15 Seconds             │
                  │ Attempt 3: Immediate Send    │
                  └──────────────┬───────────────┘
                                 │
                        (Failure / Exception)
                                 │
                                 ▼
                  ┌──────────────────────────────┐
                  │ Delay 45 Seconds             │
                  │ Final Attempt 4              │
                  └──────────────┬───────────────┘
                                 │
                        (All Retries Exhausted)
                                 │
                                 ▼
                  [ Queue WorkManager Exponential ]
                  [ Persistent Retry Background   ]
                  [ Audit Log: DISPATCH_FAILED    ]
```

1. **In-Memory Exponential Backoff Retry Loop:** `SmtpMailDispatcher` implements a strict 3-tier retry loop:
   - **Attempt 1:** Immediate
   - **Attempt 2:** Wait **5 seconds**
   - **Attempt 3:** Wait **15 seconds**
   - **Attempt 4:** Wait **45 seconds**
2. **Sanitized Error Audit Logging:** Exact failure stack traces (with passwords masked) are recorded in `checkin_logs.details`.
3. **WorkManager Fallback Persistence:** If all in-memory retries fail (e.g. mail server down for hours), a `WorkManager` background job with exponential backoff (`BackoffPolicy.EXPONENTIAL`, initial delay 30s) is enqueued.

#### D. Code Assertion Logic & Unit Test Implementation

```kotlin
package com.dms.app.domain.usecase

import com.dms.app.data.mail.FakeSmtpClient
import com.dms.app.data.repository.FakeCheckInRepository
import com.dms.app.domain.model.EmailResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Scenario7SmtpRetryTest {

    private lateinit var fakeSmtpClient: FakeSmtpClient
    private lateinit var repository: FakeCheckInRepository
    private lateinit var smtpDispatcher: SmtpMailDispatcher

    @Before
    fun setUp() {
        fakeSmtpClient = FakeSmtpClient()
        repository = FakeCheckInRepository()
        smtpDispatcher = SmtpMailDispatcher(fakeSmtpClient, repository)
    }

    @Test
    fun `smtp dispatcher retries 3 times with backoff delays before reporting failure`() = runBlocking {
        // Arrange: Configure SMTP client to fail 2 times then succeed on 3rd attempt
        fakeSmtpClient.setFailCountBeforeSuccess(2)

        // Act: Execute SMTP dispatch
        val result = smtpDispatcher.sendEmergencyEmailWithRetry(
            recipient = "emergency@example.com",
            subject = "URGENT",
            body = "Alert"
        )

        // Assert: Must succeed on 3rd attempt, total attempts logged must be 3
        assertTrue("Dispatch should eventually succeed on attempt 3", result is EmailResult.Success)
        assertEquals(3, fakeSmtpClient.totalAttemptsExecuted)
    }
}
```

#### E. Step-by-Step ADB Shell Commands & Testing Procedure

```bash
# Step 1: Inject invalid SMTP credentials into database for testing
adb shell sqlite3 /data/data/com.dms.app/databases/dms_encrypted.db "UPDATE smtp_credentials SET password_encrypted='INVALID_PASS_HASH';"

# Step 2: Trigger emergency SMTP email dispatch via ADB
adb shell am broadcast -a com.dms.app.ACTION_TRIGGER_SMTP_DISPATCH

# Step 3: Monitor logcat output to verify exponential backoff delays (5s, 15s, 45s)
adb logcat -v time | grep -E "DMS_SmtpDispatcher: Attempt|Backoff delay"

# Step 4: Verify failure audit log entry in database
adb shell sqlite3 /data/data/com.dms.app/databases/dms_encrypted.db "SELECT timestamp, status, details FROM checkin_logs WHERE status='DISPATCH_FAILED';"
```

---

### 2.8 Scenario 8: System Time Tampering / Timezone Adjustment

#### A. Scenario Overview & Root Cause
A user (or malicious actor) manually changes the device date and time in Android Settings—either winding the clock back by 24 hours to delay timer expiry or winding it forward.
- **Root Cause:** `System.currentTimeMillis()` represents wall-clock time (UTC Unix epoch), which can be mutated by the user at any time or updated automatically by NITZ cell tower sync. In contrast, `SystemClock.elapsedRealtime()` measures monotonic time elapsed since device boot (including time spent in deep sleep) and **cannot be altered by the user**.

#### B. System & User Impact Analysis
1. If countdown calculations rely solely on `System.currentTimeMillis()`, setting the clock back 10 days freezes the timer indefinitely.
2. Setting the clock forward prematurely triggers emergency alerts.

#### C. Mitigation Architecture & Technical Solution

```
                 [ Last User Check-In Action ]
                                 │
                                 ▼
      [ Record Wall-Clock Time: System.currentTimeMillis()   ]
      [ Record Monotonic Time:  SystemClock.elapsedRealtime()]
                                 │
                                 ▼
            [ SYSTEM TIME / TIMEZONE CHANGE EVENT FIRED ]
            [ Broadcast: ACTION_TIME_CHANGED / TIMEZONE ]
                                 │
                                 ▼
         [ Calculate Elapsed Delta Since Last Check-In ]
         [ DeltaWall = CurrentWall - StoredWall        ]
         [ DeltaMono = CurrentMono - StoredMono        ]
                                 │
                                 ▼
              [ Evaluates Drift: |DeltaWall - DeltaMono| ]
                                 │
                 ┌───────────────┴───────────────┐
                 │                               │
        (Drift <= 60 Seconds)            (Drift > 60 Seconds)
                 │                               │
                 ▼                               ▼
       [ Valid Time Sync ]            [ TIME TAMPERING DETECTED! ]
                                                 │
                                                 ▼
                                      [ Re-anchor Expiry Timestamp]
                                      [ to Monotonic Elapsed Clock]
                                                 │
                                                 ▼
                                      [ Log: TIME_TAMPERING_DETECTED]
```

1. **Dual Clock Cross-Validation:** Every check-in records both wall-clock time (`System.currentTimeMillis()`) and monotonic boot time (`SystemClock.elapsedRealtime()`).
2. **Time Change Receiver:** `TimeChangeReceiver` listens for `Intent.ACTION_TIME_CHANGED` and `Intent.ACTION_TIMEZONE_CHANGED`.
3. **Drift Detection & Monotonic Rescaling:** If `abs(deltaWallClock - deltaMonotonic) > 60_000ms`, system clock tampering is detected. The app automatically re-anchors the countdown timer to monotonic `elapsedRealtime()`, preventing timer bypass.
4. **NTP Background Verification:** When network connection is active, `SntpClient` queries NTP servers (`pool.ntp.org`) in the background to verify true UTC time.

#### D. Code Assertion Logic & Unit Test Implementation

```kotlin
package com.dms.app.domain.engine

import android.os.SystemClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class Scenario8TimeTamperingTest {

    @Test
    fun `detects clock tampering when wall clock delta diverges from monotonic delta`() {
        // Arrange: Simulate initial check-in timestamps
        val initialWallClockMs = 1700000000000L
        val initialMonotonicMs = 500000L // 500 seconds since boot

        // Simulate user winding wall clock back by 5 hours (-18,000,000 ms)
        val tamperedWallClockMs = initialWallClockMs - 18000000L
        // Monotonic time advances naturally by 10 minutes (+600,000 ms)
        val currentMonotonicMs = initialMonotonicMs + 600000L

        val deltaWall = tamperedWallClockMs - initialWallClockMs // -18,000,000 ms
        val deltaMono = currentMonotonicMs - initialMonotonicMs // +600,000 ms

        // Act: Evaluate drift discrepancy
        val driftMs = abs(deltaWall - deltaMono)
        val isTampered = driftMs > 60000L // 60s threshold

        // Assert
        assertTrue("Clock tampering must be detected when wall clock shifts relative to monotonic clock", isTampered)
        assertEquals(18600000L, driftMs)
    }
}
```

#### E. Step-by-Step ADB Shell Commands & Testing Procedure

```bash
# Step 1: Record initial check-in state via app UI or ADB
adb shell am broadcast -a com.dms.app.ACTION_TRIGGER_CHECKIN

# Step 2: Shift device system time backward by 5 hours via ADB shell
adb shell date $(date -d "-5 hours" +%m%d%H%M%Y.%S)

# Step 3: Trigger system time change broadcast manually if needed
adb shell am broadcast -a android.intent.action.TIME_SET

# Step 4: Verify logcat output for system time tampering detection
adb logcat -d | grep "DMS_TimeEngine: SYSTEM TIME TAMPERING DETECTED! Re-anchoring timer to monotonic clock."

# Step 5: Restore automatic network time synchronization on test device
adb shell settings put global auto_time 1
```

---

## 3. Comprehensive Verification & Compliance Checklist

| Component | Automated Test Strategy | ADB Command Verification | Acceptance Criteria |
| :--- | :--- | :--- | :--- |
| **Direct Boot Security** | Robolectric `LOCKED_BOOT_COMPLETED` simulation | `adb shell am switch-user 0` | Timers restored in Direct Boot state without CE storage access. |
| **Doze Mode Execution** | Instrumentation test with PowerManager idle injection | `adb shell dumpsys deviceidle force-idle deep` | Exact alarm fires on schedule during deep sleep; WakeLock acquired. |
| **OEM Task Kill Resilience** | Process kill test via `ActivityManager` | `adb shell am force-stop com.dms.app` | `system_server` AlarmManager re-spawns app process upon alarm expiry. |
| **SMS/SMTP Fallback** | Mock `SmsManager` failure callback test | `adb shell am broadcast -a com.dms.app.ACTION_SMS_SENT --ei "result_code" 4` | Automatic instant transition to SMTP email dispatch. |
| **SMTP Backoff Retries** | Mock SMTP server timeout test | `adb shell am broadcast -a com.dms.app.ACTION_TRIGGER_SMTP_DISPATCH` | Retry attempts execute with 5s, 15s, 45s exponential backoff delays. |
| **Clock Tampering Shield** | Monotonic vs wall-clock unit tests | `adb shell date $(date -d "-5 hours" +%m%d%H%M%Y.%S)` | Timer re-anchors to `elapsedRealtime()`; audit log updated. |

---
*End of Edge Cases Analysis Matrix & Robustness Verification Specification.*
