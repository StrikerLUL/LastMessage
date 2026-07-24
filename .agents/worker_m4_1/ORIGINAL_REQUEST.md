## 2026-07-24T14:58:13Z
<USER_REQUEST>
You are a Worker subagent working on Milestone 4: AndroidManifest & Permissions Guide for the Dead Man's Switch Mobile App.

Working directory: c:\Users\cilli\OneDrive\Dokumente\appweg\.agents\worker_m4_1
Project root: c:\Users\cilli\OneDrive\Dokumente\appweg

Refer to `docs/framework_evaluation.md` and `docs/architecture_and_db_design.md` for background execution rules and permissions requirements.

Tasks:
1. Create a complete, production-ready `AndroidManifest.xml` snippet at `app/src/main/AndroidManifest.xml` (or `docs/AndroidManifest.xml`) declaring all essential permissions and system components:
   - Permissions: `SEND_SMS`, `RECEIVE_BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` / `HEALTH`, `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`.
   - Application components: `MainActivity`, `BootReceiver` (with intent filters for `ACTION_BOOT_COMPLETED` and `ACTION_LOCKED_BOOT_COMPLETED` with `android:directBootAware="true"`), `WorkManager` initializers, `NotificationActionReceiver`, and `EmergencyDispatchService` (Foreground Service).
2. Author a comprehensive Android Permissions & Background Execution Guide in `docs/android_manifest_and_permissions.md`:
   - Step-by-step Runtime Permission Request Workflow (Android 6.0+ dangerous permissions: `SEND_SMS`, `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`).
   - Battery Optimization Whitelisting Guide using `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with intent launching code snippets and user prompt UX design.
   - Comprehensive OEM Background Kill Prevention Guide covering Samsung (One UI Sleeping Apps), Xiaomi (MIUI/HyperOS Auto-start & Battery Saver), Huawei (EMUI App Launch), and Oppo/Vivo background management settings.
   - Code snippets for checking and requesting exact alarm scheduling permissions on Android 12+ (`AlarmManager.canScheduleExactAlarms()`).

Write progress in `c:\Users\cilli\OneDrive\Dokumente\appweg\.agents\worker_m4_1\progress.md` and deliver handoff report in `c:\Users\cilli\OneDrive\Dokumente\appweg\.agents\worker_m4_1\handoff.md`.

MANDATORY INTEGRITY WARNING: DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A Forensic Auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Once complete, send a message to parent (694c060a-cd41-4c7b-ac6a-bc591a703a95) with summary and handoff path.
</USER_REQUEST>
