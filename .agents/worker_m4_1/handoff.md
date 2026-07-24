# Handoff Report — Milestone 4: AndroidManifest & Permissions Guide

## 1. Observation
- **Original Requirements:**
  - Create complete `AndroidManifest.xml` at `app/src/main/AndroidManifest.xml` (or `docs/AndroidManifest.xml`).
  - Declare permissions: `SEND_SMS`, `RECEIVE_BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` / `HEALTH`, `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`.
  - Declare application components: `MainActivity`, `BootReceiver` (with `ACTION_BOOT_COMPLETED` and `ACTION_LOCKED_BOOT_COMPLETED` intent filters and `android:directBootAware="true"`), WorkManager initializers (`tools:node="remove"` for default WorkManager auto-init), `NotificationActionReceiver`, and `EmergencyDispatchService` (Foreground Service).
  - Author `docs/android_manifest_and_permissions.md` covering runtime permissions (Android 6.0+), battery optimization whitelisting (`Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`), OEM kill prevention (Samsung, Xiaomi, Huawei, Oppo/Vivo), and exact alarm scheduling (`AlarmManager.canScheduleExactAlarms()`).
- **Files Created:**
  - `app/src/main/AndroidManifest.xml` (Production AndroidManifest)
  - `docs/AndroidManifest.xml` (Documentation copy of AndroidManifest)
  - `docs/android_manifest_and_permissions.md` (Comprehensive technical guide)

## 2. Logic Chain
1. **Background Execution Architecture:** The Dead Man's Switch relies on persistent countdown timers that must survive device reboots, Doze mode sleep, and OEM process kills.
2. **Direct Boot Readiness:** By adding `android:directBootAware="true"` to application components (`BootReceiver`, `EmergencyDispatchService`, `NotificationActionReceiver`), the app can receive `LOCKED_BOOT_COMPLETED` and access Device Encrypted (DE) storage immediately post-reboot before user PIN entry.
3. **Foreground Service & WorkManager Configuration:** Disabling standard `WorkManagerInitializer` prevents startup crashes during Direct Boot. Declaring `FOREGROUND_SERVICE_SPECIAL_USE` and `FOREGROUND_SERVICE_HEALTH` with subtype property metadata satisfies Android 14+ Play Store policy rules.
4. **Permissions & OEM Mitigation Guide:** The technical guide in `docs/android_manifest_and_permissions.md` provides production-ready Kotlin managers (`PermissionManager`, `BatteryOptimizationHelper`, `ExactAlarmPermissionHandler`, `OemBatteryOptimizationHelper`) and step-by-step OEM setting configurations alongside ADB shell verification commands.

## 3. Caveats
- Direct Boot mode allows accessing Device Encrypted (DE) storage, but Credential Encrypted (CE) storage remains locked until user unlock. Secret credentials requiring CE storage must not be accessed during the initial Direct Boot phase prior to first unlock.
- Google Play Store deployment will require submitting safety rationale declarations for `SEND_SMS` and `USE_EXACT_ALARM` permissions.

## 4. Conclusion
Milestone 4 is complete. Both `app/src/main/AndroidManifest.xml` and `docs/android_manifest_and_permissions.md` have been fully authored and verified to meet all requirements.

## 5. Verification Method
- **File Inspection:**
  - Inspect `app/src/main/AndroidManifest.xml` and `docs/AndroidManifest.xml` to verify XML syntax and declaration of all 12+ permissions and components (`MainActivity`, `BootReceiver`, `NotificationActionReceiver`, `EmergencyDispatchService`, `WorkManager` initializer override).
  - Inspect `docs/android_manifest_and_permissions.md` to verify all 7 sections (Background execution rules, Manifest specification, Runtime permissions workflow, Battery whitelisting, Exact alarm APIs, OEM kill prevention for Samsung/Xiaomi/Huawei/Oppo/Vivo, ADB test commands).
- **ADB Commands Verification:**
  - `adb shell dumpsys deviceidle whitelist +com.dms.app`
  - `adb shell am broadcast -a android.intent.action.LOCKED_BOOT_COMPLETED -n com.dms.app/.receiver.BootReceiver`
  - `adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -n com.dms.app/.receiver.BootReceiver`
  - `adb shell cmd appops set com.dms.app SCHEDULE_EXACT_ALARM allow`
