# Dead Man's Switch Mobile Application
## Android Manifest & Permissions Guide Specification

**Document Version:** 1.0.0  
**Date:** July 24, 2026  
**Status:** Approved Technical Specification  
**Target Platform:** Android (API Level 26 to 35+ / Android 8.0 to Android 15+)  

---

## 1. Executive Summary & Background Execution Paradigm

### 1.1 The Lifesaving Background Execution Challenge
A **Dead Man's Switch (DMS)** application is a high-reliability, zero-trust safety system designed to monitor human vitality through periodic manual check-ins. If a user fails to check in before a designated timer expires, the app must autonomously dispatch emergency SMS and SMTP email notifications to trusted contacts.

Standard consumer mobile applications operate under the assumption that background process termination by the operating system is acceptable. However, for a Dead Man's Switch:
- If Android's **Doze Mode** or **App Standby Buckets** throttle CPU execution, critical countdown alarms will be delayed.
- If aggressive **OEM Battery Savers** (e.g., Samsung One UI, Xiaomi HyperOS/MIUI, Huawei EMUI) terminate background tasks, the emergency dispatch engine will fail entirely when the user is incapacitated.
- If a device reboots and the app cannot execute prior to the user entering their PIN/Pattern (**Direct Boot Phase**), the safety countdown will freeze.

To guarantee high reliability, the Dead Man's Switch app combines **Direct Boot compatibility**, **exact `AlarmManager` wakeup triggers**, **Doze Mode exemptions (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)**, **pinned Foreground Services (`FOREGROUND_SERVICE_SPECIAL_USE` / `HEALTH`)**, and explicit **OEM kill-prevention strategies**.

---

### 1.2 Android System Evolution & Permission Timeline

Modern Android releases (API 23 through API 35) have progressively tightened background execution and privacy constraints. The table below outlines how each Android release impacts the Dead Man's Switch architecture:

```
+-----------------------------------------------------------------------------------------------+
| Android Version | Key Constraints & Changes                     | DMS Architecture Mitigation  |
+-----------------------------------------------------------------------------------------------+
| Android 6.0     | • Introduction of Runtime Permissions         | • Dynamic permissions flow   |
| (API 23)        | • Introduction of Doze Mode & App Standby     | • REQUEST_IGNORE_BATTERY     |
+-----------------------------------------------------------------------------------------------+
| Android 7.0/7.1 | • Introduction of Direct Boot (`LOCKED_BOOT`) | • `android:directBootAware`  |
| (API 24/25)     | • CE vs. DE storage encryption split          | • SQLCipher in DE Storage    |
+-----------------------------------------------------------------------------------------------+
| Android 9.0     | • Mandatory `FOREGROUND_SERVICE` permission  | • Declare FGS permission in  |
| (API 28)        |   declaration in AndroidManifest.xml          |   AndroidManifest.xml        |
+-----------------------------------------------------------------------------------------------+
| Android 12/12L  | • `SCHEDULE_EXACT_ALARM` restricted           | • `AlarmManager.canSchedule` |
| (API 31/32)     | • FGS launch restrictions from background     | • `setExactAndAllowWhileIdle`|
+-----------------------------------------------------------------------------------------------+
| Android 13      | • `POST_NOTIFICATIONS` runtime permission     | • Explicit runtime prompt    |
| (API 33)        | • `USE_EXACT_ALARM` for safety/alarm apps     | • Declare both Alarm Perms   |
+-----------------------------------------------------------------------------------------------+
| Android 14      | • Mandatory FGS Types & manifest properties   | • `specialUse|health` FGS    |
| (API 34)        | • Strict back-channel FGS validation          | • Subtype property meta-data |
+-----------------------------------------------------------------------------------------------+
| Android 15      | • 6-hour execution timeout for generic FGS    | • Keep FGS brief; rely on    |
| (API 35)        | • Enhanced power consumption monitoring       |   AlarmManager + WorkManager |
+-----------------------------------------------------------------------------------------------+
```

---

### 1.3 Direct Boot Architecture & Storage Partitioning

When an Android device restarts, it enters the **Direct Boot** state before the user enters their lock screen credential (PIN, Pattern, or Password). 

During Direct Boot:
- **Credential Encrypted (CE) Storage** remains locked and inaccessible.
- **Device Encrypted (DE) Storage** is unlocked immediately after the kernel boots.

```
Device Power On / Reboot
        │
        ▼
┌────────────────────────────────────────────────────────┐
│ DIRECT BOOT PHASE (Device Locked)                      │
│ - Android OS Kernel Boots                              │
│ - LOCKED_BOOT_COMPLETED Broadcast Fired                │
│ - BootReceiver (directBootAware="true") Executes       │
│ - Accesses Device Encrypted (DE) Storage               │
│ - Reschedules AlarmManager Exact Timers                │
└────────────────────────────────────────────────────────┘
        │
        │ User Unlocks Device (PIN / Pattern / Password)
        ▼
┌────────────────────────────────────────────────────────┐
│ NORMAL EXECUTION PHASE (Device Unlocked)               │
│ - BOOT_COMPLETED Broadcast Fired                       │
│ - Credential Encrypted (CE) Storage Unlocked           │
│ - Full App UI and CE Database Available                │
└────────────────────────────────────────────────────────┘
```

**DMS Direct Boot Strategy:**
1. The app's `BootReceiver`, `EmergencyDispatchService`, and `NotificationActionReceiver` are declared with `android:directBootAware="true"`.
2. The core timer configuration and database encryption passphrase are stored in **DE Storage** using `context.createDeviceProtectedStorageContext()`.
3. This guarantees that if the device reboots in the middle of the night, `BootReceiver` restores exact timers without requiring user unlock.

---

## 2. Production `AndroidManifest.xml` Specification

### 2.1 Complete Production Manifest Code

The following file is saved in the codebase at `app/src/main/AndroidManifest.xml` (and mirrored at `docs/AndroidManifest.xml`):

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    package="com.dms.app">

    <!-- ===================================================================== -->
    <!-- PERMISSIONS DECLARATION                                               -->
    <!-- ===================================================================== -->

    <!-- SMS Dispatch Permission for Emergency Alerts -->
    <uses-permission android:name="android.permission.SEND_SMS" />

    <!-- Boot Completed Receivers for Rescheduling Alarms & Workers after Reboot -->
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <!-- Direct Boot Permission to run receivers before user unlock post-reboot -->
    <uses-permission android:name="android.permission.LOCKED_BOOT_COMPLETED" />

    <!-- Doze Mode Exemption to prevent OS sleep during critical countdown -->
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

    <!-- Android 13+ (API 33+) Local Notification Permission -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <!-- Android 12+ (API 31+) Exact Alarm Scheduling Permission (User-configurable) -->
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
    <!-- Android 13+ (API 33+) Pre-granted Exact Alarm Permission for Safety/Alarm Apps -->
    <uses-permission android:name="android.permission.USE_EXACT_ALARM" />

    <!-- Foreground Service Base Permission (Android 9+) -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <!-- Android 14+ (API 34+) Specific Foreground Service Types -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_HEALTH" />

    <!-- Internet & Network State for SMTP Email Emergency Fallback Dispatch -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <!-- CPU Wake Lock to prevent sleep during immediate dispatch execution -->
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <!-- Hardware Feature Requirements -->
    <uses-feature
        android:name="android.hardware.telephony"
        android:required="false" />

    <!-- ===================================================================== -->
    <!-- APPLICATION CONFIGURATION                                             -->
    <!-- ===================================================================== -->
    <application
        android:name=".DmsApplication"
        android:allowBackup="false"
        android:directBootAware="true"
        android:fullBackupContent="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.DeadMansSwitch">

        <!-- ===================================================================== -->
        <!-- ACTIVITIES                                                            -->
        <!-- ===================================================================== -->
        <activity
            android:name=".ui.MainActivity"
            android:exported="true"
            android:launchMode="singleTop"
            android:theme="@style/Theme.DeadMansSwitch">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- ===================================================================== -->
        <!-- BROADCAST RECEIVERS                                                   -->
        <!-- ===================================================================== -->
        
        <!-- System Boot Receiver (Direct Boot Aware) -->
        <receiver
            android:name=".receiver.BootReceiver"
            android:directBootAware="true"
            android:enabled="true"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.LOCKED_BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
            </intent-filter>
        </receiver>

        <!-- Notification Action Receiver (e.g. "I'm Alive" button tap from notification) -->
        <receiver
            android:name=".receiver.NotificationActionReceiver"
            android:directBootAware="true"
            android:enabled="true"
            android:exported="false" />

        <!-- Alarm State Change Receiver (Android 12+ EXACT_ALARM privilege change) -->
        <receiver
            android:name=".receiver.AlarmPermissionReceiver"
            android:enabled="true"
            android:exported="true">
            <intent-filter>
                <action android:name="android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" />
            </intent-filter>
        </receiver>

        <!-- ===================================================================== -->
        <!-- SERVICES                                                              -->
        <!-- ===================================================================== -->

        <!-- Emergency Dispatch Foreground Service (Android 14+ FGS Type Compliant) -->
        <service
            android:name=".service.EmergencyDispatchService"
            android:directBootAware="true"
            android:enabled="true"
            android:exported="false"
            android:foregroundServiceType="specialUse|health">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="Dead mans switch safety monitoring and emergency dispatch system" />
        </service>

        <!-- ===================================================================== -->
        <!-- CONTENT PROVIDERS / WORKMANAGER INITIALIZATION                        -->
        <!-- ===================================================================== -->

        <!-- Disable default WorkManager auto-initialization to allow custom -->
        <!-- Direct-Boot aware Configuration.Provider in DmsApplication.      -->
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <meta-data
                android:name="androidx.work.WorkManagerInitializer"
                tools:node="remove" />
        </provider>

    </application>

</manifest>
```

---

### 2.2 Permission Breakdown & Policy Rationale Matrix

| Permission Name | Category / Type | Introduced / Min API | Purpose & Technical Rationale | Google Play Policy Considerations |
| :--- | :--- | :--- | :--- | :--- |
| `android.permission.SEND_SMS` | Dangerous (Runtime) | API 23 (6.0) | Dispatches multi-part emergency text messages directly to user emergency contacts via `SmsManager`. | Core functionality exception required under Google Play SMS/Call Log Policy. |
| `android.permission.RECEIVE_BOOT_COMPLETED` | Normal | API 1 | Triggers `BootReceiver` after full system boot to reschedule background workers and alarms. | Standard permission; automatically granted at install time. |
| `android.permission.LOCKED_BOOT_COMPLETED` | Normal | API 24 (7.0) | Triggers `BootReceiver` immediately after device reboot during Direct Boot (before PIN entry). | Required for `android:directBootAware="true"` broadcast receivers. |
| `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Special Permission | API 23 (6.0) | Exposes exemption from Doze Mode CPU frequency scaling and App Standby network freezes. | Allowed for safety and emergency alert applications; requires user intent launch. |
| `android.permission.POST_NOTIFICATIONS` | Dangerous (Runtime) | API 33 (13) | Displays countdown warnings and persistent Foreground Service notification channel alerts. | Standard runtime permission prompt required on Android 13+. |
| `android.permission.SCHEDULE_EXACT_ALARM` | Special Permission | API 31 (12) | Allows scheduling pinpoint precision wakeups via `AlarmManager.setExactAndAllowWhileIdle()`. | Can be granted/revoked by user under Special App Access settings. |
| `android.permission.USE_EXACT_ALARM` | Normal (Pre-granted) | API 33 (13) | Pre-granted permission for apps categorized as Alarms, Clocks, or Personal Safety apps. | Restricted by Google Play Console policies; requires safety app declaration. |
| `android.permission.FOREGROUND_SERVICE` | Normal | API 28 (9.0) | Base manifest permission allowing the app to invoke `startForegroundService()`. | Mandatory for all foreground service execution on Android 9+. |
| `android.permission.FOREGROUND_SERVICE_SPECIAL_USE` | Normal | API 34 (14) | FGS type declaration for unique safety monitoring business logic not covered by standard types. | Requires manifest `<property>` metadata tag defining subtype usage. |
| `android.permission.FOREGROUND_SERVICE_HEALTH` | Normal | API 34 (14) | FGS type declaration for user vitality and safety state tracking services. | Standard Android 14 FGS enforcement mechanism. |
| `android.permission.INTERNET` | Normal | API 1 | Opens TCP network sockets for outbound SMTP email emergency message dispatch. | Standard permission; automatically granted at install time. |
| `android.permission.ACCESS_NETWORK_STATE` | Normal | API 1 | Checks network connectivity status before attempting SMTP mail socket connection. | Used by `ConnectivityManager` pre-flight checks. |
| `android.permission.WAKE_LOCK` | Normal | API 1 | Keeps CPU active during multi-part SMS assembly and SMTP retry loops (max 10s). | Managed securely via `PowerManager.WakeLock` with strict timeouts. |

---

### 2.3 Application Components Deep Dive

#### 1. `MainActivity` (`.ui.MainActivity`)
- **Launch Mode:** `singleTop` prevents redundant Activity instances when launched from notification action intents.
- **Intent Filter:** Declares `MAIN` and `LAUNCHER` as the primary user interface entry point.

#### 2. `BootReceiver` (`.receiver.BootReceiver`)
- **Direct Boot Aware:** `android:directBootAware="true"` allows the system to instantiate this receiver before user unlock.
- **Intent Filters:**
  - `android.intent.action.LOCKED_BOOT_COMPLETED`: Fires during Direct Boot state.
  - `android.intent.action.BOOT_COMPLETED`: Fires after device unlock.
  - `android.intent.action.MY_PACKAGE_REPLACED`: Reschedules alarms after app update.

#### 3. `NotificationActionReceiver` (`.receiver.NotificationActionReceiver`)
- **Function:** Handles background tap events on notification action buttons (e.g. "I'M ALIVE" quick check-in).
- **Exported:** `android:exported="false"` to prevent unauthorized external app invocation.

#### 4. `EmergencyDispatchService` (`.service.EmergencyDispatchService`)
- **FGS Types:** `android:foregroundServiceType="specialUse|health"`.
- **Android 14 Compatibility Property:** Contains `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" ... />` to satisfy Android 14 Google Play Store manifest validation.

#### 5. Custom WorkManager Initialization
- **Problem:** Default `WorkManagerInitializer` attempts to initialize WorkManager automatically using CE storage during Direct Boot, causing crash exceptions.
- **Solution:** Disabled standard provider via `<provider android:name="androidx.startup.InitializationProvider" ... tools:node="remove"/>`. `DmsApplication` implements `Configuration.Provider` to lazily supply custom Direct-Boot-aware configuration.

---

## 3. Step-by-Step Runtime Permission Request Workflow (Android 6.0+)

### 3.1 Permission Architecture Flowchart

```
                 [ User Opens App / Triggers Action ]
                                  │
                                  ▼
                 [ Check ContextCompat.checkSelfPermission ]
                                  │
                 ┌────────────────┴────────────────┐
                 │                                 │
         (Already Granted)                  (Permission Denied)
                 │                                 │
                 ▼                                 ▼
      [ Execute Feature Logic ]       [ Check shouldShowRequestPermissionRationale ]
                                                   │
                                  ┌────────────────┴────────────────┐
                                  │                                 │
                           (Returns True)                    (Returns False)
                                  │                                 │
                                  ▼                                 ▼
                     [ Show Educational Dialog ]          [ Check First Time vs Permanent ]
                                  │                                 │
                     ┌────────────┴────────────┐            ┌───────┴───────┐
                     │                         │            │               │
                 (Accepted)                (Canceled)  (First Time)    (Permanently Denied)
                     │                         │            │               │
                     ▼                         ▼            ▼               ▼
          [ Launch Request Contract ]   [ Feature ]  [ Launch Contract ] [ Show Settings Dialog ]
                     │                  [ Degraded ]        │               │
           ┌─────────┴─────────┐                            │               ▼
           │                   │                            │   [ Open App Settings Intent ]
       (Granted)           (Denied)                         │
           │                   │                            │
           ▼                   ▼                            │
  [ Proceed Feature ]  [ Degraded Mode ] <──────────────────┘
```

---

### 3.2 Production Kotlin Implementation: `PermissionManager.kt`

```kotlin
package com.dms.app.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Production-ready Permission Manager handling Android 6.0+ dangerous permissions
 * (SEND_SMS, POST_NOTIFICATIONS) and special system intents.
 */
class PermissionManager(private val activity: ComponentActivity) {

    private lateinit var requestMultiplePermissionsLauncher: ActivityResultLauncher<Array<String>>
    private var onPermissionsResultCallback: ((Map<String, Boolean>) -> Unit)? = null

    init {
        registerContracts()
    }

    private fun registerContracts() {
        requestMultiplePermissionsLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissionsResultMap ->
            onPermissionsResultCallback?.invoke(permissionsResultMap)
        }
    }

    /**
     * Required dangerous runtime permissions depending on API level.
     */
    fun getRequiredRuntimePermissions(): Array<String> {
        val permissions = mutableListOf<String>()
        
        // SEND_SMS is mandatory for cellular dispatch
        permissions.add(Manifest.permission.SEND_SMS)

        // POST_NOTIFICATIONS is required on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        return permissions.toTypedArray()
    }

    /**
     * Checks if all required runtime permissions are granted.
     */
    fun hasAllRuntimePermissions(): Boolean {
        return getRequiredRuntimePermissions().all { permission ->
            ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Executes permission request flow with rationale checks.
     */
    fun requestRuntimePermissions(onResult: (Map<String, Boolean>) -> Unit) {
        this.onPermissionsResultCallback = onResult
        val permissionsToRequest = getRequiredRuntimePermissions()

        val deniedPermissions = permissionsToRequest.filter { permission ->
            ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (deniedPermissions.isEmpty()) {
            // All granted
            val grantedMap = permissionsToRequest.associateWith { true }
            onResult(grantedMap)
            return
        }

        // Check if any permission requires rationale display
        val showRationale = deniedPermissions.any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }

        if (showRationale) {
            showEducationalRationaleDialog {
                requestMultiplePermissionsLauncher.launch(deniedPermissions.toTypedArray())
            }
        } else {
            requestMultiplePermissionsLauncher.launch(deniedPermissions.toTypedArray())
        }
    }

    /**
     * Displays rationale modal dialog explaining safety impact.
     */
    private fun showEducationalRationaleDialog(onProceed: () -> Unit) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("Essential Permissions Required")
            .setMessage(
                "The Dead Man's Switch application requires SMS permission to send emergency alerts " +
                "to your trusted contacts if your timer expires, and Notification permission to alert you " +
                "before the timer runs out."
            )
            .setPositiveButton("Grant Permissions") { dialog, _ ->
                dialog.dismiss()
                onProceed()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Navigates user directly to App Settings if permissions are permanently denied.
     */
    fun showSettingsRedirectDialog() {
        MaterialAlertDialogBuilder(activity)
            .setTitle("Permissions Permanently Denied")
            .setMessage(
                "Essential permissions have been permanently denied. To ensure the safety switch functions " +
                "properly, please open App Settings and grant SMS and Notification permissions."
            )
            .setPositiveButton("Open Settings") { dialog, _ ->
                dialog.dismiss()
                openAppSettings()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
        }
        activity.startActivity(intent)
    }
}
```

---

## 4. Battery Optimization Whitelisting Guide (`Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)

### 4.1 Doze Mode & App Standby Buckets Mechanics

Android categorizes unlaunched applications into **App Standby Buckets**:
1. `ACTIVE`: App is currently in foreground.
2. `WORKING_SET`: App is opened frequently.
3. `FREQUENT`: App is opened every few days.
4. `RARE`: App is rarely opened (default for DMS background safety app).
5. `RESTRICTED`: System strictly limits CPU and network access.

```
       ┌─────────────────────────────────────────────────────────┐
       │             APP STANDBY BUCKET: RARE                    │
       │  (Network access deferred, Jobs limited to 2-hour window)│
       └────────────────────────────┬────────────────────────────┘
                                    │
                  User grants Battery Optimization Exemption
                                    │
                                    ▼
       ┌─────────────────────────────────────────────────────────┐
       │             EXEMPTED BUCKET (Whitelisted)               │
       │  • Unrestricted Network Sockets during Doze             │
       │  • AlarmManager Wakeups execute without delay           │
       │  • CPU WakeLocks honoured immediately                   │
       └─────────────────────────────────────────────────────────┘
```

---

### 4.2 Checking & Requesting Battery Exemption

To ensure the safety countdown fires without delay, the DMS app must request explicit battery optimization exemption using `PowerManager`.

```kotlin
package com.dms.app.permissions

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object BatteryOptimizationHelper {

    /**
     * Returns true if app is already exempt from battery optimization.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Requests Battery Optimization exemption. Shows modal UX explanation first.
     */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (isIgnoringBatteryOptimizations(context)) return

        MaterialAlertDialogBuilder(context)
            .setTitle("Disable Battery Optimization")
            .setMessage(
                "To ensure the Dead Man's Switch reliably monitors your safety while your phone is asleep, " +
                "you must exempt this application from Android Battery Saver restrictions.\n\n" +
                "Without this, the OS may suppress emergency alarms."
            )
            .setPositiveButton("Configure Exemption") { dialog, _ ->
                dialog.dismiss()
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // Fallback to generic battery saver settings if package URI launch is blocked
                    val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    context.startActivity(fallbackIntent)
                }
            }
            .setNegativeButton("Later") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
}
```

---

## 5. Exact Alarm Scheduling Permissions Guide (Android 12+ / 13+)

### 5.1 API Rules (`SCHEDULE_EXACT_ALARM` vs `USE_EXACT_ALARM`)

Starting in Android 12 (API 31), exact alarms scheduled via `AlarmManager.setExactAndAllowWhileIdle()` require special system privileges:
- **`SCHEDULE_EXACT_ALARM`**: User-configurable permission under Special App Access. Can be revoked by the user at any time.
- **`USE_EXACT_ALARM`**: Introduced in Android 13 (API 33). Pre-granted permission for Alarms, Clocks, and Personal Safety applications.

If an app attempts to schedule exact alarms without privilege, `AlarmManager` throws a runtime `SecurityException`.

---

### 5.2 Complete Kotlin `ExactAlarmPermissionHandler` Utility

```kotlin
package com.dms.app.permissions

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ExactAlarmPermissionHandler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Checks whether exact alarm scheduling is granted.
     */
    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * Prompts the user to grant Exact Alarm permission in System Settings.
     */
    fun requestExactAlarmPermission() {
        if (canScheduleExactAlarms()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MaterialAlertDialogBuilder(context)
                .setTitle("Exact Timing Permission Required")
                .setMessage(
                    "The Dead Man's Switch needs permission to schedule exact alarms. " +
                    "This guarantees warning notifications are delivered precisely on schedule."
                )
                .setPositiveButton("Open Settings") { dialog, _ ->
                    dialog.dismiss()
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }
}
```

---

## 6. Comprehensive OEM Background Kill Prevention Guide ("DontKillMyApp")

Aggressive OEM power management daemons often kill background services indiscriminately. Below is the step-by-step resolution guide for major OEM vendors, paired with the unified `OemBatteryOptimizationHelper` Kotlin utility.

---

### 6.1 Samsung (One UI 3 / 4 / 5 / 6)

#### Obstacle Overview
Samsung's **Device Care** daemon automatically moves infrequently opened applications into **Sleeping Apps** or **Deep Sleeping Apps** lists after 3 days. Deep Sleeping apps cannot launch broadcast receivers or scheduled alarms.

#### User Configuration Steps
1. Open **Settings** -> **Battery and device care** -> **Battery**.
2. Tap **Background usage limits**.
3. Tap **Never sleeping apps** -> Tap **+** icon.
4. Select **Dead Man's Switch** and tap **Add**.
5. Go to **Settings** -> **Apps** -> **Dead Man's Switch** -> **Battery** -> Select **Unrestricted**.

---

### 6.2 Xiaomi (MIUI 12 / 13 / 14 & HyperOS)

#### Obstacle Overview
MIUI / HyperOS disables **Auto-start** by default. Swiping an app from the Recent Apps list executes `force-stop`, disabling all broadcast receivers. Furthermore, MIUI Battery Saver defaults to "Smart Save", restricting background execution after 20 minutes.

#### User Configuration Steps
1. Open **Security** app -> **Permissions** -> **Autostart**.
2. Enable **Autostart** toggle for **Dead Man's Switch**.
3. Open **Settings** -> **Apps** -> **Manage Apps** -> **Dead Man's Switch**.
4. Tap **Battery Saver** -> Select **No restrictions**.
5. Enable **Display pop-up windows while running in background**.

---

### 6.3 Huawei (EMUI 10 / 11 / 12 & HarmonyOS)

#### Obstacle Overview
Huawei's **PowerGenie** daemon forcibly terminates background sockets and suppresses `BOOT_COMPLETED` signals.

#### User Configuration Steps
1. Open **Settings** -> **Battery** -> **App Launch**.
2. Locate **Dead Man's Switch** and toggle off **Manage automatically**.
3. In the explicit dialog, enable all three toggles:
   - **Auto-launch** (Enabled)
   - **Secondary launch** (Enabled)
   - **Run in background** (Enabled)
4. Tap **OK**.

---

### 6.4 Oppo & Vivo (ColorOS / Funtouch OS / OriginOS)

#### Obstacle Overview
Aggressive RAM cleaning daemons terminate background services upon screen lock.

#### User Configuration Steps (Oppo / ColorOS)
1. Open **Settings** -> **Battery** -> **App Battery Management** -> **Dead Man's Switch**.
2. Enable **Allow Background Activity** and **Allow Auto Launch**.

#### User Configuration Steps (Vivo / Funtouch OS)
1. Open **Settings** -> **Battery** -> **High background power consumption**.
2. Enable toggle for **Dead Man's Switch**.

---

### 6.5 Unified `OemBatteryOptimizationHelper.kt` Implementation

```kotlin
package com.dms.app.permissions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object OemBatteryOptimizationHelper {

    /**
     * Detects manufacturer and attempts to launch manufacturer-specific settings screen.
     */
    fun openOemBatterySettings(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()

        when {
            manufacturer.contains("samsung") -> openSamsungSettings(context)
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> openXiaomiSettings(context)
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> openHuaweiSettings(context)
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> openOppoSettings(context)
            manufacturer.contains("vivo") -> openVivoSettings(context)
            else -> showGenericGuidance(context)
        }
    }

    private fun openSamsungSettings(context: Context) {
        showGuideDialog(
            context,
            "Samsung Device Care Setup",
            "1. Please tap 'Open Settings'.\n" +
            "2. Select 'Battery' -> 'Background usage limits'.\n" +
            "3. Add Dead Man's Switch to 'Never sleeping apps'."
        ) {
            launchIntent(context, "com.samsung.android.looper.toolbar", "com.samsung.android.sm.ui.battery.BatteryActivity")
        }
    }

    private fun openXiaomiSettings(context: Context) {
        showGuideDialog(
            context,
            "Xiaomi Auto-start & Battery Saver Setup",
            "1. Enable 'Autostart' toggle.\n" +
            "2. Tap 'Battery Saver' and select 'No restrictions'."
        ) {
            launchIntent(context, "com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
        }
    }

    private fun openHuaweiSettings(context: Context) {
        showGuideDialog(
            context,
            "Huawei App Launch Setup",
            "1. Locate Dead Man's Switch.\n" +
            "2. Toggle off 'Manage automatically'.\n" +
            "3. Enable Auto-launch, Secondary launch, and Run in background."
        ) {
            launchIntent(context, "com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
        }
    }

    private fun openOppoSettings(context: Context) {
        showGuideDialog(
            context,
            "Oppo Auto-launch Setup",
            "Allow 'Auto-launch' and 'Run in Background' for Dead Man's Switch."
        ) {
            launchIntent(context, "com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
        }
    }

    private fun openVivoSettings(context: Context) {
        showGuideDialog(
            context,
            "Vivo Background Power Setup",
            "Enable 'High background power consumption' for Dead Man's Switch."
        ) {
            launchIntent(context, "com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")
        }
    }

    private fun showGenericGuidance(context: Context) {
        Toast.makeText(context, "Please set Battery Saver to 'Unrestricted' in App Info.", Toast.LENGTH_LONG).show()
    }

    private fun showGuideDialog(context: Context, title: String, message: String, onProceed: () -> Unit) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Open Settings") { dialog, _ ->
                dialog.dismiss()
                onProceed()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun launchIntent(context: Context, packageName: String, className: String) {
        try {
            val intent = Intent().apply {
                component = ComponentName(packageName, className)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to standard App Details Settings if OEM activity signature changes
            PermissionManager(context as androidx.activity.ComponentActivity).openAppSettings()
        }
    }
}
```

---

## 7. Verification, Testing & Troubleshooting Guide

### 7.1 Comprehensive ADB Verification Commands

To verify permissions, background executions, Doze mode exemptions, and Direct Boot receivers without waiting for real-time timer expiration, use the following ADB shell commands:

#### 1. Battery Optimization Whitelist Verification & Override
```bash
# Check if app is in Doze whitelist
adb shell dumpsys deviceidle whitelist | grep com.dms.app

# Manually add app to Doze whitelist via ADB
adb shell dumpsys deviceidle whitelist +com.dms.app
```

#### 2. Doze Mode Simulation
```bash
# Unplug device from USB battery charging state
adb shell dumpsys battery unplug

# Step system into Deep Doze Mode
adb shell dumpsys deviceidle step deep

# Force system into Idle state immediately
adb shell dumpsys deviceidle force-idle

# Reset battery state after testing
adb shell dumpsys battery reset
```

#### 3. App Standby Bucket Verification & Simulation
```bash
# Check current standby bucket
adb shell am get-standby-bucket com.dms.app

# Force app into RARE bucket
adb shell am set-standby-bucket com.dms.app rare

# Force app into RESTRICTED bucket
adb shell am set-standby-bucket com.dms.app restricted
```

#### 4. Exact Alarm Permission Verification
```bash
# Verify pending alarms registered for DMS package
adb shell dumpsys alarm | grep com.dms.app

# Manually grant SCHEDULE_EXACT_ALARM via AppOps
adb shell cmd appops set com.dms.app SCHEDULE_EXACT_ALARM allow

# Manually revoke SCHEDULE_EXACT_ALARM via AppOps
adb shell cmd appops set com.dms.app SCHEDULE_EXACT_ALARM ignore
```

#### 5. Direct Boot & Boot Receiver Simulation
```bash
# Simulate Direct Boot broadcast (LOCKED_BOOT_COMPLETED)
adb shell am broadcast -a android.intent.action.LOCKED_BOOT_COMPLETED -n com.dms.app/.receiver.BootReceiver

# Simulate normal boot broadcast (BOOT_COMPLETED)
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -n com.dms.app/.receiver.BootReceiver
```

#### 6. Foreground Service Execution Status
```bash
# Dump active services running for DMS
adb shell dumpsys activity services com.dms.app
```

---
*End of Android Manifest & Permissions Guide Specification.*
