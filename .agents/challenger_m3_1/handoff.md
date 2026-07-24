# Empirical Challenge Report & Handoff — Dead Man's Switch Mobile App

**Milestone**: M3 — Timer Engine, Check-in Reset, Notification Thresholds & Boundary Stress Testing  
**Role**: Empirical Challenger (Critic / Specialist)  
**Verdict**: **DEFECTS_FOUND**  
**Overall Risk Assessment**: **HIGH**  

---

## 1. Observation

Empirical stress testing and static code analysis was conducted on `TimerEngine.kt`, `CheckInUseCase.kt`, `NotificationScheduler.kt`, `OtherUseCases.kt` (`ScheduleNotificationsUseCase`, `EvaluateTimerUseCase`), and `BootAndBatteryServices.kt` (`BootReceiver`). 

An empirical test harness (`TestHarness.java`) was compiled and executed against the project's compiled JVM classes (`app/build/classes/kotlin/main`) using Java 23 and Kotlin stdlib 2.0.21.

### Verbatim Findings from Code & Test Execution:

1. **`app/src/main/java/com/dms/app/services/timer/TimerEngine.kt` (lines 42-46)**:
   ```kotlin
   val status = when {
       currentTimeEpochMillis >= expiryEpochMillis -> TimerStatus.EXPIRED
       remainingMinutes <= 60L || remainingMinutes <= (intervalMinutes * 0.25) -> TimerStatus.WARNING
       else -> TimerStatus.ACTIVE
   }
   ```
   *Execution Result*: Evaluating status for a 30m, 45m, or 60m interval immediately after check-in ($0$ mins elapsed, 100% time remaining) returned `TimerStatus.WARNING` instead of `TimerStatus.ACTIVE`.

2. **`app/src/main/java/com/dms/app/services/timer/TimerEngine.kt` (lines 80-89)**:
   ```kotlin
   // 1 hour remaining: (Expiry - 1 hour)
   val t1hMillis = expiryEpochMillis - MILLIS_PER_HOUR
   val t1hRemMins = 60L

   return listOf(
       ...
       MilestoneThreshold("1_HOUR", (60.0 / intervalMinutes), t1hMillis, t1hRemMins)
   ).sortedBy { it.triggerTimeEpochMillis }
   ```
   *Execution Result*: 
   - For `intervalMinutes = 30L`, `t1hMillis` evaluated to $30$ minutes **before** the check-in time (`triggerOffset = -30m`, `percentageRemaining = 2.0`).
   - For `intervalMinutes = 0L`, `(60.0 / intervalMinutes)` evaluated to `Double.POSITIVE_INFINITY` (`Infinity`).

3. **`app/src/main/java/com/dms/app/services/workmanager/BootAndBatteryServices.kt` (lines 23-31) & `app/src/main/java/com/dms/app/domain/usecases/OtherUseCases.kt` (lines 37-53)**:
   ```kotlin
   // BootReceiver.kt
   ACTION_BOOT_COMPLETED -> {
       scheduleNotificationsUseCase.rescheduleAll()
       BootResult.Handled(...)
   }
   ```
   *Execution Result*: When simulating a device reboot 25 hours after a check-in on a 24-hour interval (timer expired while device was off), `BootReceiver` called `rescheduleAll()`. All 5 milestone alarms were filtered out as past triggers ($0$ alarms scheduled), and `BootReceiver` returned `Handled` without evaluating `TimerStatus.EXPIRED` or invoking `DispatchEmergencyUseCase`.

4. **`app/src/main/java/com/dms/app/services/timer/TimerEngine.kt` (lines 23, 34, 61)**:
   ```kotlin
   val intervalMillis = intervalMinutes * MILLIS_PER_MINUTE
   ```
   *Execution Result*: When `intervalMinutes = Long.MAX_VALUE / 60000L + 1L`, arithmetic overflow wrapped `intervalMillis` to a negative value, causing `remainingMinutes` to evaluate to `0L` (immediate false expiration).

---

## 2. Logic Chain

1. **Short Interval Early Warning Defect**:
   - *Observation*: `TimerEngine.kt` line 44 checks `remainingMinutes <= 60L || remainingMinutes <= (intervalMinutes * 0.25)`.
   - *Reasoning*: For any interval $\le 60$ minutes, `remainingMinutes` at check-in is $\le 60$. Because of the short-circuiting `|| remainingMinutes <= 60L`, the left side of the `||` condition is `true` for all valid states of short timers ($\le 60\text{ min}$).
   - *Conclusion*: Short timers (e.g. 15m, 30m, 45m, 60m) can never be in an `ACTIVE` status; they immediately enter `WARNING` state upon user check-in.

2. **Milestone Calculation & 0-Minute Infinity Defect**:
   - *Observation*: `TimerEngine.kt` lines 81-89 computes `t1hMillis = expiry - 1h` and `percentageRemaining = 60.0 / intervalMinutes`.
   - *Reasoning*: If `intervalMinutes < 60`, `expiry - 1h` produces a timestamp prior to `lastCheckInEpochMillis`. If `intervalMinutes == 0`, floating-point division `60.0 / 0.0` yields IEEE 754 positive infinity (`Double.POSITIVE_INFINITY`).
   - *Conclusion*: Scheduling alarms for short intervals produces past timestamps and corrupt percentage metrics, while 0-minute input causes serialization failures due to infinity values.

3. **Reboot Recovery Missed Emergency Dispatch Defect**:
   - *Observation*: `BootReceiver.kt` delegates post-boot handling exclusively to `scheduleNotificationsUseCase.rescheduleAll()`, which calls `notificationScheduler.scheduleThresholdNotifications(milestones)`.
   - *Reasoning*: `scheduleThresholdNotifications` filters out milestones where `triggerTimeEpochMillis <= now`. If the timer expired while the phone was powered off, all milestones are past triggers and get dropped. `rescheduleAll()` does not call `evaluateStatus()` or trigger emergency dispatches.
   - *Conclusion*: Device reboot after timer expiry results in zero scheduled alarms and zero emergency dispatches until WorkManager eventually runs `CheckInCheckWorker` (up to 15+ minutes later).

4. **Arithmetic Overflow on Large Intervals**:
   - *Observation*: `intervalMinutes * MILLIS_PER_MINUTE` uses standard 64-bit signed integer multiplication.
   - *Reasoning*: Inputs exceeding `Long.MAX_VALUE / 60000L` overflow into negative values, corrupting expiry timestamp math (`lastCheckIn + intervalMillis`).
   - *Conclusion*: Unvalidated custom large intervals cause integer overflow, leading to false immediate expirations.

---

## 3. Stress Test Results Summary

| Scenario | Expected Behavior | Actual Behavior | Verdict |
|---|---|---|---|
| **24-hour interval check-in** | Status: `ACTIVE`, 1440m remaining, 5 valid threshold alarms scheduled | Status: `ACTIVE`, 1440m remaining, 5 alarms scheduled | **PASS** |
| **30-minute interval check-in** | Status: `ACTIVE`, 30m remaining | Status: `WARNING` immediately | **FAIL** (Defect 1) |
| **60-minute interval check-in** | Status: `ACTIVE`, 60m remaining | Status: `WARNING` immediately | **FAIL** (Defect 1) |
| **0-minute interval threshold math** | Safe handling or exception | `percentageRemaining` = `Infinity` | **FAIL** (Defect 2) |
| **30-minute interval 1h threshold** | Skipped or capped | `1_HOUR` trigger is -30m in past (pct=2.0) | **FAIL** (Defect 3) |
| **Leap Year date calculation (Feb 28-29 2028)** | Exactly 1440 minutes between leap days | 1440 minutes computed accurately | **PASS** |
| **Year 2038 epoch rollover** | Safe 64-bit Long timestamp calculation | 1380 minutes remaining after 1 hour elapsed | **PASS** |
| **Arithmetic overflow on large interval** | Graceful bounds error or saturation | Arithmetic overflow wrapping remMins to 0 | **FAIL** (Defect 5) |
| **100 rapid sequential check-ins** | All check-ins saved, 100 audit logs recorded | 100 check-ins saved, 100 audit logs recorded | **PASS** |
| **200 concurrent multi-threaded check-ins** | Thread-safe execution, 200 audit logs | Thread-safe execution, 200 audit logs | **PASS** |
| **Reboot after timer expired while powered off** | Detect `EXPIRED` status and trigger emergency dispatch | BootReceiver returns `Handled`, 0 alarms scheduled, dispatch NOT triggered | **FAIL** (Defect 4) |
| **Corrupt storage ISO timestamp string** | Fallback to current time safely | Fallback to current time safely, status `ACTIVE` | **PASS** |

---

## 4. Caveats

- Android System AlarmManager alarm delivery precision and OS Doze mode deferrals depend on device hardware runtime; empirical verification was executed against JVM bytecode model logic and simulated system state.
- No other caveats.

---

## 5. Conclusion & Actionable Mitigations

The Dead Man's Switch core logic exhibits **4 critical defects** requiring remediation before production deployment:

1. **Fix `TimerEngine.evaluateStatus` for short intervals**:
   Change threshold logic to condition 60-minute warning on interval size:
   ```kotlin
   val warningThresholdMinutes = minOf(60L, (intervalMinutes * 0.25).toLong())
   val status = when {
       currentTimeEpochMillis >= expiryEpochMillis -> TimerStatus.EXPIRED
       remainingMinutes <= warningThresholdMinutes -> TimerStatus.WARNING
       else -> TimerStatus.ACTIVE
   }
   ```
2. **Fix `TimerEngine.calculateNotificationThresholds` for short intervals & 0-minute input**:
   - Guard against `intervalMinutes <= 0L`.
   - Only include `1_HOUR` milestone if `intervalMinutes > 60L`.
3. **Fix `BootReceiver` / `ScheduleNotificationsUseCase` on device reboot**:
   - In `rescheduleAll()`, call `timerEngine.evaluateStatus()`.
   - If status is `TimerStatus.EXPIRED`, invoke `dispatchEmergencyUseCase.executeEmergencyDispatch()` immediately upon boot.
4. **Fix Integer Overflow in `TimerEngine`**:
   - Use `Math.multiplyExact` or clamp `intervalMinutes` to maximum supported interval (e.g. 10080L / 7 days).

---

## 6. Verification Method

To independently verify these findings:
1. Open terminal in project root `c:\Users\cilli\OneDrive\Dokumente\appweg`.
2. Re-compile the test harness:
   ```powershell
   javac -cp "app\build\classes\kotlin\main;C:\Users\cilli\.gradle\caches\modules-2\files-2.1\org.jetbrains.kotlin\kotlin-stdlib\2.0.21\618b539767b4899b4660a83006e052b63f1db551\kotlin-stdlib-2.0.21.jar" .agents\challenger_m3_1\TestHarness.java
   ```
3. Execute the empirical test suite:
   ```powershell
   java -cp "app\build\classes\kotlin\main;C:\Users\cilli\.gradle\caches\modules-2\files-2.1\org.jetbrains.kotlin\kotlin-stdlib\2.0.21\618b539767b4899b4660a83006e052b63f1db551\kotlin-stdlib-2.0.21.jar;.agents\challenger_m3_1" TestHarness
   ```
4. Observe the empirical test output reporting 5 defect failures across short interval warning state, 0-min infinity, past 1h alarm trigger, arithmetic overflow, and boot recovery expired state.
