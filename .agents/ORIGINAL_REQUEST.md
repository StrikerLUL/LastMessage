# Original User Request

## 2026-07-24T14:55:42Z

# Teamwork Project Prompt — Dead Man's Switch Mobile App

An offline, privacy-first "Dead Man's Switch" mobile application built with Flutter/Android. The app periodically checks if the user performs a check-in action. If the configurable timer expires, the app autonomously sends an emergency SMS and/or Email directly from the device without relying on any cloud or external backend.

Working directory: c:\Users\cilli\OneDrive\Dokumente\appweg

Integrity mode: development

## Requirements

### R1. Encrypted Local Storage & Configuration
- Store message body, recipient phone number, SMTP email credentials, and last check-in timestamp completely offline and encrypted using AES-256 (`flutter_secure_storage` / SQLCipher / `hive`).
- No data may leave the device until the timer expires and emergency dispatch is triggered.

### R2. Persistent Timer & Check-in Logic
- Support configurable countdown timer intervals (12h, 24h, 48h, 72h, 7 days). Default is 24h.
- "I am alive" button updates the last check-in timestamp in encrypted persistent storage.
- App restart must accurately recalculate remaining time based on the persisted timestamp.

### R3. Local Notification System
- Schedule local push notifications at 75%, 50%, 25%, 10% remaining time, and 1 hour before expiry.
- Tapping notifications opens the app directly to the check-in screen.
- Notifications must be scheduled offline using `flutter_local_notifications`.

### R4. Reliable Background Execution (WorkManager)
- Run background tasks using Android `WorkManager` with `PeriodicWorkRequest` (15 min interval).
- Handle Doze Mode, App Standby Buckets, and prompt user for Battery Optimization Whitelisting.
- On device reboot, auto-reschedule WorkManager worker and re-plan local notifications (`RECEIVE_BOOT_COMPLETED`).

### R5. Autonomous Emergency Dispatch (SMS & SMTP Email)
- Primary dispatch: Send SMS via native `SmsManager` (`SEND_SMS` permission), supporting multipart SMS for long messages without user interaction.
- Fallback/Secondary dispatch: Send email directly via SMTP client (`mailer` package) using stored encrypted credentials.
- Implement retry logic (up to 3 attempts) on failure.

## Acceptance Criteria

### Technical & Architectural Deliverables
- [ ] Comprehensive Framework & Library evaluation (Flutter vs Native Kotlin).
- [ ] Data flow & architecture diagram (Mermaid/ASCII) covering MVVM/Clean Architecture and DB schema.
- [ ] Complete, runnable modular starter code for Timer, WorkManager, SMS dispatch, and Notifications.
- [ ] Complete `AndroidManifest.xml` snippet with runtime permission handling guide.
- [ ] Edge cases analysis matrix covering offline, flight mode, reboot, doze mode, and app kill scenarios.
