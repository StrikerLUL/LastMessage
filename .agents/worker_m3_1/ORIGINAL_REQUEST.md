## 2026-07-24T14:57:58Z
You are a Worker subagent working on Milestone 3: Modular Starter Implementation & Unit Tests for the Dead Man's Switch Mobile App.

Working directory: c:\Users\cilli\OneDrive\Dokumente\appweg\.agents\worker_m3_1
Project root: c:\Users\cilli\OneDrive\Dokumente\appweg

Refer to `docs/framework_evaluation.md` and `docs/architecture_and_db_design.md` for architecture decisions and specifications.

Tasks:
Build the complete, modular, runnable starter code implementation and unit test suite in `app/src/main/java/com/dms/app/` (or `lib/` if cross-platform wrappers are included) and `app/src/test/java/com/dms/app/`:

1. **Storage Module (R1)** (`services/storage/` & `data/local/`):
   - `SecureStorageService.kt`: AES-256 encrypted storage service managing Android Keystore MasterKey, `EncryptedSharedPreferences`, and SQLCipher database helper.
   - Implement storage for user config, emergency contacts, encrypted SMTP credentials, message body template, and last check-in timestamp.

2. **Timer & Check-in Logic Module (R2)** (`services/timer/` & `domain/usecases/`):
   - `TimerEngine.kt`: Core countdown math engine supporting intervals (12h, 24h, 48h, 72h, 7 days; default 24h).
   - `CheckInUseCase.kt`: Handles "I am alive" button trigger, updates encrypted timestamp in persistent storage, and resets notification thresholds.
   - Recalculate remaining duration accurately across app restarts/reboots.

3. **Local Push Notification System Module (R3)** (`services/notifications/`):
   - `NotificationScheduler.kt`: Notification Channel builder, exact alarm scheduler using `AlarmManager` at 75%, 50%, 25%, 10%, and 1h remaining time thresholds.
   - Include deep-link `PendingIntent` launching the check-in screen on notification tap.

4. **WorkManager & Boot Receiver Module (R4)** (`services/workmanager/`):
   - `CheckInCheckWorker.kt`: `WorkManager` `CoroutineWorker` running `PeriodicWorkRequest` every 15 minutes.
   - `BootReceiver.kt`: `BroadcastReceiver` listening for `RECEIVE_BOOT_COMPLETED` & `LOCKED_BOOT_COMPLETED` to reschedule WorkManager tasks and notification alarms.
   - `BatteryOptimizationHelper.kt`: Utility to check and launch `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

5. **Autonomous Emergency Dispatch Module (R5)** (`services/dispatch/`):
   - `SmsDispatcher.kt`: Native `SmsManager` using `sendMultipartTextMessage` with `PendingIntent` sent/delivered callbacks for silent background SMS dispatch without user interaction.
   - `SmtpMailer.kt`: Outbound SMTP email dispatcher with exponential backoff retry logic (up to 3 attempts).
   - `EmergencyDispatchEngine.kt`: Dispatch orchestrator managing SMS primary sending and SMTP fallback.

6. **Comprehensive Unit Test Suite** (`app/src/test/java/com/dms/app/`):
   - `TimerEngineTest.kt`: Unit tests for timer calculation, threshold milestone calculation (75%, 50%, 25%, 10%, 1h), and expiry detection.
   - `EmergencyDispatchTest.kt`: Unit tests for SMS multipart message handling, SMTP 3x retry loop behavior, and fallback execution.
   - `StorageServiceTest.kt`: Unit tests for encryption configuration, model mapping, and data retrieval.

Write progress in `c:\Users\cilli\OneDrive\Dokumente\appweg\.agents\worker_m3_1\progress.md` and deliver handoff in `c:\Users\cilli\OneDrive\Dokumente\appweg\.agents\worker_m3_1\handoff.md`.

MANDATORY INTEGRITY WARNING: DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A Forensic Auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Once complete, send a message to parent (694c060a-cd41-4c7b-ac6a-bc591a703a95) with summary and handoff path.
