# Dead Man's Switch Mobile App: Framework & Library Evaluation Report

**Document Version:** 1.0.0  
**Date:** July 24, 2026  
**Author:** Explorer Agent (Milestone 1)  
**Status:** Completed & Approved  
**Target Project:** Offline, Privacy-First Dead Man's Switch Mobile Application  

---

## Executive Summary

A **Dead Man's Switch (DMS)** application is fundamentally different from typical consumer mobile applications. Its primary value proposition depends on **uncompromising background execution reliability**, **resilience against aggressive OS battery management and reboots**, **hardware-backed local encryption for sensitive credentials and emergency contacts**, and **automated, silent emergency dispatch (SMS and SMTP Email) without user interaction**.

This evaluation report conducts an exhaustive technical comparison between **Flutter** and **Native Android (Kotlin)**, alongside deep evaluations of **local encrypted storage options**, **background execution frameworks**, **Doze mode / OEM battery killer mitigations**, and **communication dispatch libraries**.

### Key Recommendation Summary
* **Core Framework Choice:** **Native Android (Kotlin + Jetpack Compose)** is recommended as the primary architecture for the Dead Man's Switch app. Native Kotlin eliminates dual-layer bridge friction, guarantees Direct Boot compatibility before device unlock, provides direct access to Android system services (`SmsManager`, `WorkManager`, `AlarmManager`), and minimizes RAM overhead to prevent OS Low Memory Killer (LMK) termination.
* **Encrypted Storage:** **SQLCipher** (for relational logs, contacts, and check-in history) paired with Jetpack Security **EncryptedSharedPreferences** (for secrets and KeyStore-backed keys). Partitioned into Device Encrypted (DE) storage to enable background timer validation during Direct Boot.
* **Background Engine:** Native **`WorkManager` CoroutineWorker** combined with **`AlarmManager.setExactAndAllowWhileIdle()`** and a pinned **`ForegroundService`** during active emergency countdown phases.
* **Emergency Dispatch:** Native **`SmsManager`** (using `sendMultipartTextMessage` with `PendingIntent` delivery callbacks) and **Jakarta Mail / JavaMail** executing inside a network-constrained `CoroutineWorker` with exponential retry backoff.

---

## 1. Framework Evaluation: Flutter vs. Native Kotlin

### 1.1 Overview & Architecture Fit

| Evaluation Domain | Native Kotlin (Jetpack Compose) | Flutter (Dart Framework) | Winner for DMS App |
| :--- | :--- | :--- | :--- |
| **Background Execution Reliability** | Direct integration with `WorkManager`, `AlarmManager`, and `ForegroundService`. Zero VM boot overhead. | Wraps native APIs via plugins. Requires launching a background Dart Isolate, increasing startup delay and failure modes. | **Native Kotlin** |
| **Direct Boot Compatibility (`LOCKED_BOOT_COMPLETED`)** | Native support via `DeviceProtectedStorageContext` and native `BroadcastReceiver`. | Plugin ecosystem lacks Direct Boot support out-of-the-box; requires writing native Kotlin wrappers regardless. | **Native Kotlin** |
| **RAM & Process Overhead** | ~15–30 MB RAM per background worker execution. Extremely low LMK risk. | ~50–90 MB RAM startup cost for Flutter Engine + Dart VM Isolate. High LMK risk on low-end devices. | **Native Kotlin** |
| **Native API Access (SMS / KeyStore)** | Direct compile-time binding to Android SDK (`SmsManager`, `KeyStore`, `JobScheduler`). | Dependent on 3rd-party community plugins (`telephony`, `flutter_sms`), many of which are unmaintained. | **Native Kotlin** |
| **Security Posture & Attack Surface** | Minimal binary (~4–8 MB APK), smaller library transitive dependency tree, hardware KeyStore integration. | Larger binary (~20–35 MB APK), Dart runtime overhead, complex cross-isolate key passing. | **Native Kotlin** |
| **UI/UX & Development Speed** | Jetpack Compose (Declarative UI, Material 3, fast iteration for 3–4 app screens). | Flutter Declarative UI (Hot Reload, rich widget ecosystem). | **Tie / Slight Flutter Edge** |
| **Cross-Platform Potential** | Android-specific (iOS requires separate Swift codebase or Kotlin Multiplatform Mobile). | Single codebase for iOS and Android. | **Flutter** |

### 1.2 In-Depth Analysis: The Flutter Background Problem

In a standard application, Flutter’s architecture is an asset. However, for a Dead Man's Switch:

1. **Double-Bridge Latency & Failure Points:** When `WorkManager` triggers a periodic timer evaluation in Flutter, the OS calls Android native code -> Android boots the Flutter Engine -> MethodChannel registers -> Dart Isolate spawns -> Dart code executes. If the device is low on RAM or in deep Doze, the Flutter Engine startup can time out or get killed by the Android OS LMK before the Dart callback executes.
2. **Plugin Bitrot for Low-Level APIs:** Android 12, 13, and 14 introduced strict background execution changes (e.g., `SCHEDULE_EXACT_ALARM` permissions, `ForegroundService` types, `PendingIntent` immutability flags). Most Flutter SMS and background execution packages have failed to update, leading to runtime crashes on modern Android versions.
3. **The "Native Wrapper" Paradox:** To make Flutter reliable for a Dead Man's Switch, developers end up writing 80% of the background logic (`WorkManager`, `BootReceiver`, `SmsManager`, `EncryptedSharedPreferences`) in native Kotlin inside the `android/` directory anyway. Using pure Native Kotlin eliminates this redundant dual-architecture complexity.

---

## 2. Encrypted Storage Comparison for AES-256 Local Data Protection

The Dead Man's Switch app requires storing:
* **Sensitive Credentials:** SMTP passwords, custom secret keys, PIN/Passcode hashes.
* **Emergency Contacts:** Phone numbers, email addresses, custom emergency message payloads.
* **Audit & Check-in Logs:** Timestamps, check-in history, system state logs.

### 2.1 Storage Options Matrix

| Storage Library | Encrypted Strategy | Key Storage Security | Query Capability | Direct Boot (DE Storage) | Crash/Corruption Resilience | Verdict & Use Case |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **SQLCipher (`android-database-sqlcipher`)** | AES-256-CBC (Full Database Page Encryption) | Hardware-backed MasterKey in Android Keystore | Full Relational SQL (`SELECT`, `JOIN`, Indexing, Migrations) | **Supported** via `createDeviceProtectedStorageContext()` | **High** (SQLite WAL mode prevents corruption on kill) | **PRIMARY DB** (Contacts, Logs, Check-in History) |
| **EncryptedSharedPreferences (Jetpack Security)** | AES-256 GCM (Values) / SIV (Keys) | Hardware-backed MasterKey in Android Keystore | Key-Value Pairs | **Supported** via `createDeviceProtectedStorageContext()` | **High** (Atomic file write backchannel) | **PRIMARY SECRETS** (SMTP Credentials, Config) |
| **`flutter_secure_storage`** | Wraps `EncryptedSharedPreferences` / KeyStore | Android Keystore | Key-Value Pairs | **Not Supported** natively in Dart background isolates without unlock | **Medium** (Known async deadlocks in background isolates) | **REJECTED** for Native; Secondary for Flutter |
| **Hive with AES Encryption** | AES-256 CTR (Pure Dart Binary Box) | Managed manually (Stored in SecureStorage) | NoSQL Key-Value / Document | **Not Supported** (Pure Dart; unusable by native BootReceivers) | **Low** (Risk of box corruption during forced OS kill) | **REJECTED** (Insecure RAM footprint & crash vulnerability) |

### 2.2 Security Architecture Details

1. **Android Keystore Integration:**
   All cryptographic keys must be generated using `KeyGenerator` backed by the Android Keystore system (`AndroidKeyStore` provider). On supported hardware, keys are protected by the Trusted Execution Environment (TEE) or StrongBox Keymaster hardware module.
2. **Device Encrypted (DE) vs. Credential Encrypted (CE) Storage Partitioning:**
   * **CE Storage:** Encrypted with a key derived from the user's lock screen credential (PIN/Pattern/Password). Unreadable before first user unlock after reboot.
   * **DE Storage:** Encrypted with a key tied to the device hardware. Accessible immediately after boot, even during Direct Boot (`LOCKED_BOOT_COMPLETED`).
   * **Implementation Strategy:** Essential timer configurations and SQLCipher master key fragments are stored in DE Storage. Extended user profiles remain in CE Storage.

---

## 3. Background Execution Capabilities, Doze Mode & OEM Constraints

### 3.1 Background Scheduler Matrix

```
[System Boot / Event] 
       │
       ├──> BootReceiver (LOCKED_BOOT_COMPLETED)
       │        │
       │        └──> Reads DE Storage Config ──> Reschedules AlarmManager (setExactAndAllowWhileIdle)
       │
       └──> WorkManager (Periodic Worker - 15 Min Backup)
                │
                └──> CoroutineWorker ──> Evaluates Timer Expiry
                         │
                         ├──> [Active / Healthy] ──> Reschedules Local Notification
                         │
                         └──> [EXPIRED / EMERGENCY] ──> Triggers EmergencyDispatchEngine
                                                              │
                                                              ├──> SmsManager (Multipart SMS)
                                                              └──> JavaMail / SMTP (Network Coroutine)
```

### 3.2 Doze Mode & Standby Buckets Mitigation

1. **Doze Mode Handling:** Standard background workers (`JobScheduler`, inexact `WorkManager`) are delayed during deep Doze. The DMS app uses **`AlarmManager.setExactAndAllowWhileIdle()`** or **`AlarmManager.setAlarmClock()`** for critical check-in expiration windows, waking the CPU even in deep sleep.
2. **App Standby Buckets Mitigation:** Since a DMS app is rarely opened interactively, Android OS automatically demotes it to `RARE` or `RESTRICTED` standby buckets, restricting network access and background jobs.
   * **Solution:** The app MUST request explicit battery optimization exemption via `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. This places the app in the exempt bucket, preserving network sockets during emergency dispatches.

### 3.3 OEM Background Task Killing ("DontKillMyApp" Realities)

Aggressive OEM power management daemons (Samsung Device Care, Xiaomi HyperOS/MIUI Security, Huawei PowerGenie, Oppo/Vivo) kill background tasks indiscriminately.

| OEM Vendor | Primary Obstacle | Mitigation Strategy |
| :--- | :--- | :--- |
| **Xiaomi (MIUI / HyperOS)** | Auto-start disabled by default; kills broadcast receivers on app swipe. | Prompt user to grant "Auto-start" in Xiaomi Security settings; use `ForegroundService` with notification. |
| **Samsung (One UI)** | Puts inactive apps into "Sleeping Apps" or "Deep Sleeping Apps". | Request `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`; prompt user to add app to "Never sleeping apps". |
| **Huawei (EMUI / HarmonyOS)** | Kills background sockets aggressively; suppresses `BOOT_COMPLETED`. | Deep-link to "App Launch" settings to toggle manual background management. |
| **Stock Android (Pixel)** | Strict Doze mode & exact alarm permission enforcement. | Hold `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` permission & foreground service lock. |

---

## 4. SMS & SMTP Email Dispatch Comparison

### 4.1 SMS Dispatch Architecture

| Parameter | Native `SmsManager` | Flutter SMS Packages (`telephony`, `flutter_sms`) |
| :--- | :--- | :--- |
| **Background Dispatch without UI** | **Supported** (Runs silently in background worker) | Partial (Unreliable in background isolates) |
| **Multipart SMS Support (>160 chars)** | **Supported** (`sendMultipartTextMessage`) | Unsupported or truncated in most packages |
| **Delivery Tracking** | **Supported** (Uses `PendingIntent` broadcast callbacks for Sent/Delivered status) | Unsupported |
| **Multi-SIM Handling** | **Supported** (`SmsManager.getSmsManagerForSubscriptionId()`) | Single default SIM only |
| **Verdict** | **MANDATORY** | **REJECTED** |

### 4.2 SMTP Email Dispatch Architecture

| Parameter | Native JavaMail / Jakarta Mail (or OkHttp SMTP) | Flutter `mailer` Package |
| :--- | :--- | :--- |
| **Doze Mode Socket Resilience** | **High** (Integrates with `ConnectivityManager` & `CoroutineWorker` retry rules) | **Medium** (Subject to `SocketException` when Dart isolate network is frozen) |
| **Encryption Protocols** | TLS 1.2 / TLS 1.3, STARTTLS, custom SSLSocketFactory | TLS / STARTTLS |
| **Retry Strategy** | `WorkManager` exponential backoff (`BackoffPolicy.EXPONENTIAL`) | Manual retry loop implementation |
| **Verdict** | **RECOMMENDED** | **ACCEPTABLE (Foreground only)** |

---

## 5. Architectural Recommendations & Implementation Plan

### 5.1 Final Architecture Decision
For an **offline, privacy-first Dead Man's Switch mobile app**, **Native Android (Kotlin + Jetpack Compose)** is selected.

### 5.2 Recommended Technology Stack
* **Language:** Kotlin 2.x
* **UI Framework:** Jetpack Compose (Material 3)
* **Architecture:** Clean Architecture + MVVM
* **Local Database:** SQLCipher (`net.zetetic:android-database-sqlcipher`)
* **Secure Key/Config Store:** `androidx.security:security-crypto` (`EncryptedSharedPreferences`)
* **Background Scheduler:** Android `WorkManager` + `AlarmManager` exact alarms
* **SMS Dispatch:** Native `SmsManager` with `PendingIntent` delivery tracking
* **Email Dispatch:** Jakarta Mail / OkHttp inside `CoroutineWorker`
* **Dependency Injection:** Hilt / Koin

---

## 6. Trade-off Analysis Matrix

```
+-----------------------------------------------------------------------------------+
| Metric                        | Native Kotlin Stack    | Flutter Stack            |
+-----------------------------------------------------------------------------------+
| Background Reliability        | [====================] | [============        ]   |
| Direct Boot Support           | [====================] | [========            ]   |
| Security & Memory Overhead    | [====================] | [============        ]   |
| OEM Battery Compatibility     | [====================] | [=============       ]   |
| Cross-Platform Support        | [======              ] | [====================]   |
| UI Development Speed          | [=================   ] | [====================]   |
+-----------------------------------------------------------------------------------+
```

---
*End of Framework Evaluation Report.*
