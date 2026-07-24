# Context Index

## Project Requirements Summary (from ORIGINAL_REQUEST.md)
- **R1. Encrypted Local Storage & Configuration**: Store message body, recipient phone number, SMTP email credentials, and last check-in timestamp completely offline and encrypted using AES-256 (`flutter_secure_storage` / SQLCipher / `hive`). No cloud dependency.
- **R2. Persistent Timer & Check-in Logic**: Configurable countdown intervals (12h, 24h, 48h, 72h, 7d, default 24h). "I am alive" check-in button updates timestamp. Recalculates remaining time accurately on app restart.
- **R3. Local Notification System**: Push notifications at 75%, 50%, 25%, 10%, and 1h before expiry using `flutter_local_notifications`. Tapping opens check-in screen directly.
- **R4. Reliable Background Execution (WorkManager)**: Android `WorkManager` with `PeriodicWorkRequest` (15 min interval). Handles Doze Mode, App Standby Buckets, Battery Optimization Whitelisting. Auto-reschedules on boot (`RECEIVE_BOOT_COMPLETED`).
- **R5. Autonomous Emergency Dispatch (SMS & SMTP Email)**: Native `SmsManager` (`SEND_SMS`) supporting multipart SMS without user interaction. Fallback SMTP email via `mailer` package. Retry logic (up to 3 attempts).

## Acceptance Criteria Summary
- [ ] Comprehensive Framework & Library evaluation (Flutter vs Native Kotlin).
- [ ] Data flow & architecture diagram (Mermaid/ASCII) covering MVVM/Clean Architecture and DB schema.
- [ ] Complete, runnable modular starter code for Timer, WorkManager, SMS dispatch, and Notifications.
- [ ] Complete `AndroidManifest.xml` snippet with runtime permission handling guide.
- [ ] Edge cases analysis matrix covering offline, flight mode, reboot, doze mode, and app kill scenarios.

## Working Directory
- `.agents/orchestrator/`
