## 2026-07-24T15:00:15Z
You are a Worker subagent working on Milestone 5: Edge Cases Analysis Matrix & Robustness Verification for the Dead Man's Switch Mobile App.

Working directory: c:\Users\cilli\OneDrive\Dokumente\appweg\.agents\worker_m5_1
Project root: c:\Users\cilli\OneDrive\Dokumente\appweg

Refer to `docs/framework_evaluation.md`, `docs/architecture_and_db_design.md`, and `docs/android_manifest_and_permissions.md`.

Tasks:
1. Create a comprehensive, production-grade Edge Cases Analysis Matrix report in `docs/edge_cases_matrix.md`.
2. Detail the exact behavior, root causes, system impact, mitigation architecture, and verification strategy for each of the following critical scenarios:
   - **Scenario 1: Offline / Flight Mode during Expiry Window**: No cellular or data connection when timer expires. Handles SMS dispatch without internet vs SMTP fallback deferred retry.
   - **Scenario 2: Device Reboot before Expiry (Direct Boot State)**: Device restarts, remaining locked before user PIN entry. Handles `LOCKED_BOOT_COMPLETED` broadcast, Device Encrypted (DE) storage access, and alarm restoration.
   - **Scenario 3: Device Reboot after Expiry**: Device is powered off when expiry time passes, then powered back on. Handles immediate expiry evaluation on boot and emergency dispatch.
   - **Scenario 4: Deep Doze Mode & Aggressive App Standby Buckets**: Phone asleep for hours. Handles `AlarmManager.setExactAndAllowWhileIdle()`, CPU WakeLock, and battery optimization whitelist (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).
   - **Scenario 5: Aggressive OEM Task Killer / Force Stop**: App killed via swipe or OEM Security app (Xiaomi MIUI/HyperOS, Samsung Device Care, Huawei PowerGenie). Handles Foreground Service pinning and OEM setting mitigation steps.
   - **Scenario 6: Missing SIM Card / Flight Mode / SMS Delivery Failure**: No SIM or cellular network unavailable. Handles `SmsManager` delivery status broadcast failure callbacks and automatic fallback to SMTP email dispatch.
   - **Scenario 7: Invalid or Failing SMTP Credentials / Server Outage**: Incorrect password or mail server timeout. Handles exponential backoff retry loop (attempt 1, 2, 3 with 5s, 15s, 45s delays) and audit logging.
   - **Scenario 8: System Time Tampering / Timezone Adjustment**: User manually changes phone clock forward or backward to bypass timer. Handles monotonic `SystemClock.elapsedRealtime()` cross-validation against `System.currentTimeMillis()` and `NTP` / network time drift detection.
3. Include test procedures, code assertion logic, and step-by-step ADB shell commands for triggering and testing each edge case scenario.

Write progress in `c:\Users\cilli\OneDrive\Dokumente\appweg\.agents\worker_m5_1\progress.md` and deliver handoff report in `c:\Users\cilli\OneDrive\Dokumente\appweg\.agents\worker_m5_1\handoff.md`.

MANDATORY INTEGRITY WARNING: DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A Forensic Auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Once complete, send a message to parent (694c060a-cd41-4c7b-ac6a-bc591a703a95) with summary and handoff path.
