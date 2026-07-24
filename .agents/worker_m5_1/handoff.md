# Handoff Report — Milestone 5: Edge Cases Analysis Matrix & Robustness Verification

**Agent Directory:** `c:\Users\cilli\OneDrive\Dokumente\appweg\.agents\worker_m5_1`  
**Target Specification File:** `docs/edge_cases_matrix.md`  
**Date:** July 24, 2026  
**Status:** Completed (Hard Handoff)  

---

## 1. Observation

- Re-examined existing architectural documentation in `docs/framework_evaluation.md`, `docs/architecture_and_db_design.md`, and `docs/android_manifest_and_permissions.md`.
- Produced complete production specification in `docs/edge_cases_matrix.md` (947 lines, 47,881 bytes).
- The specification explicitly addresses all 8 requested edge case scenarios:
  1. **Scenario 1 (Offline / Flight Mode during Expiry Window)**: Cellular radio SMS dispatch without internet vs. WorkManager deferred `NetworkType.CONNECTED` retry for SMTP fallback.
  2. **Scenario 2 (Device Reboot before Expiry / Direct Boot State)**: `directBootAware` receiver, Device Encrypted (DE) storage partitioning via `createDeviceProtectedStorageContext()`, and alarm restoration prior to user PIN entry.
  3. **Scenario 3 (Device Reboot after Expiry)**: `LOCKED_BOOT_COMPLETED` wall-clock vs expiry evaluation and immediate emergency dispatch trigger for missed expiry windows.
  4. **Scenario 4 (Deep Doze Mode & Aggressive App Standby Buckets)**: `AlarmManager.setExactAndAllowWhileIdle()`, partial CPU `WAKE_LOCK`, and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` exemption.
  5. **Scenario 5 (Aggressive OEM Task Killer / Force Stop)**: Sticky `ForegroundService` (`specialUse|health`), `system_server` persistence for native alarms, and `OemBatteryOptimizationHelper` guided setup modals.
  6. **Scenario 6 (Missing SIM Card / Flight Mode / SMS Delivery Failure)**: Async `SmsManager` status broadcast callbacks (`sentIntent`) detecting failure codes (`RESULT_ERROR_NO_SERVICE`, `RADIO_OFF`, etc.) and triggering instant SMTP email fallback.
  7. **Scenario 7 (Invalid or Failing SMTP Credentials / Server Outage)**: Exponential backoff retry loop (Attempt 1 immediate, Attempt 2 at 5s, Attempt 3 at 15s, Attempt 4 at 45s), sanitized audit log error tracking, and WorkManager background job persistence.
  8. **Scenario 8 (System Time Tampering / Timezone Adjustment)**: Monotonic `SystemClock.elapsedRealtime()` cross-validation against `System.currentTimeMillis()`, `ACTION_TIME_CHANGED` listener, and NTP time drift correction.

---

## 2. Logic Chain

1. **Safety-Critical Requirement Analysis:** A Dead Man's Switch application must maintain zero-trust reliability under adversarial OS conditions (battery throttling, forced task killing, system clock manipulation, network outages).
2. **Scenarios 1 & 6 (Communication Failure Resilience):** Primary SMS operates over cellular baseband signaling independent of IP data plans. When cellular network or SIM is unavailable, `SmsManager` pending intents capture failure codes asynchronously and invoke automatic fallback to SMTP email, using `WorkManager` with network constraints when offline.
3. **Scenarios 2 & 3 (System Reboot & Direct Boot Protection):** `LOCKED_BOOT_COMPLETED` fires before user PIN entry. Placing timer state and encryption master keys in Device Encrypted (DE) storage allows `BootReceiver` to recalculate countdown expiry and re-enqueues `AlarmManager` exact alarms before credential unlock. If the device was off when expiry occurred, post-boot calculation triggers immediate foreground dispatch.
4. **Scenarios 4 & 5 (Doze Mode & OEM Mitigation):** Standard workers are throttled during Doze. `setExactAndAllowWhileIdle()` wakes CPU execution; holding partial `WAKE_LOCK` prevents CPU sleep during calculation; battery whitelist exemptions preserve active network sockets. Pinned Foreground Services combined with `system_server` alarm persistence guarantee process re-spawning after force-stop.
5. **Scenario 7 (SMTP Retry Engine):** Transient socket network failures are handled via in-memory exponential backoff delays (5s, 15s, 45s) before writing sanitized diagnostics to `checkin_logs`.
6. **Scenario 8 (Time Tampering Protection):** Wall-clock time can be manipulated by users. Measuring elapsed time monotonically via `SystemClock.elapsedRealtime()` detects drift (`|deltaWall - deltaMono| > 60s`) and automatically re-anchors expiry targets to monotonic boot time.

---

## 3. Caveats

- **OEM Battery Settings UI Differences:** Vendor settings screens (MIUI, One UI, EMUI, ColorOS) vary across Android OS versions (e.g. Android 12 vs Android 14). `OemBatteryOptimizationHelper` includes fallback intent handlers to prevent crashes if vendor activity names change.
- **Physical SIM vs eSIM behavior:** Flight mode powers off both physical SIM and eSIM baseband radios. Test verification commands using `cmd connectivity airplane-mode enable` test both types identically.

---

## 4. Conclusion

Milestone 5 task is fully complete. `docs/edge_cases_matrix.md` is a production-grade specification containing complete architectural breakdown, state diagrams, Kotlin test assertion code snippets, and ADB shell validation commands for all 8 required edge case scenarios.

---

## 5. Verification Method

To independently verify the work:
1. Inspect file existence and completeness at `docs/edge_cases_matrix.md`.
2. Confirm all 8 scenarios are covered with:
   - Behavior & Root Cause analysis
   - System & User Impact Analysis
   - Mitigation Architecture & Diagrams
   - Code Assertion Logic (Kotlin / JUnit 5 / Robolectric)
   - Step-by-Step ADB Shell Commands
3. Run ADB commands specified in Section 2 of `docs/edge_cases_matrix.md` on an Android emulator or test device to verify system behavior.
