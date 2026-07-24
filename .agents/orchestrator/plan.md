# Project Plan: Dead Man's Switch Mobile App

## Objective
Orchestrate research, architecture, modular starter implementation, tests, and deliverables for an offline, privacy-first Dead Man's Switch mobile application (Requirements R1 - R5).

## Milestones

### Milestone 1: Framework & Library Evaluation
- Compare Flutter vs Native Kotlin across performance, background execution reliability (WorkManager vs Flutter background channels), background SMS/SMTP sending without UI, platform permissions, battery optimizations, and long-term maintainability.
- Evaluate storage libraries (`flutter_secure_storage`, SQLCipher, EncryptedSharedPreferences, Hive).
- Deliverable: Comprehensive Framework & Library Evaluation report (`docs/framework_evaluation.md`).

### Milestone 2: Architecture & Database Schema Design
- Design MVVM / Clean Architecture for the application.
- Create Data Flow diagrams (Mermaid & ASCII) covering user check-in, background monitoring timer, notification scheduling, and emergency dispatch.
- Design Encrypted Database Schema & Data Models (SQLCipher/Encrypted Storage) for storing user settings, message body, contacts, SMTP config, and check-in logs.
- Deliverable: Architecture & DB Schema specification (`docs/architecture_and_db_design.md`).

### Milestone 3: Modular Starter Implementation & Unit Tests
- Build complete, runnable, modular starter implementation modules:
  - **Storage Module (R1)**: Encrypted storage wrapper using AES-256 for credentials, contacts, and check-in timestamp.
  - **Timer Module (R2)**: Persistent timer logic with configurable intervals (12h, 24h, 48h, 72h, 7d), recalculation on boot/restart, and check-in reset.
  - **Notification Module (R3)**: Scheduled local push notifications at 75%, 50%, 25%, 10%, and 1h remaining time, with deep-link navigation to check-in screen.
  - **WorkManager & Boot Receiver Module (R4)**: `PeriodicWorkRequest` (15 min interval), Doze mode handling, battery optimization whitelist helper, and `RECEIVE_BOOT_COMPLETED` receiver.
  - **Emergency Dispatch Module (R5)**: Autonomous native `SmsManager` dispatch (supporting multipart SMS) + SMTP Email fallback with 3x retry logic.
  - **Unit Tests**: Full unit test suite testing storage, timer calculations, retry logic, and state transitions.
- Deliverable: Codebase in `lib/` or `app/src/` with accompanying test suite.

### Milestone 4: AndroidManifest & Permissions Guide
- Complete production-ready `AndroidManifest.xml` snippet with all required permissions (`SEND_SMS`, `RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `POST_NOTIFICATIONS`, etc.).
- Runtime permission request flow guide & code instructions for requesting dangerous permissions gracefully.
- Guide for Battery Optimization Whitelisting setup and OEM background kill prevention (Samsung, Xiaomi, etc.).
- Deliverable: Permissions guide & manifest documentation (`docs/android_manifest_and_permissions.md`).

### Milestone 5: Edge Cases Analysis Matrix & Robustness Verification
- Comprehensive Edge Cases Analysis Matrix covering:
  - Offline / Flight mode scenario during expiry
  - Device reboot before & after expiry
  - Doze Mode & App Standby Buckets
  - Task Killer / Force Stop / App Kill scenarios
  - Missing SIM card or SMS failure scenario
  - Invalid / failing SMTP credentials scenario
  - System time manual tampering scenario
- Test execution verification and Forensic Integrity Audit.
- Deliverable: Edge cases matrix (`docs/edge_cases_matrix.md`) and overall completion report.
