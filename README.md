# 🛡️ LastMessage — Privacy-First Offline Dead Man's Switch for Android

**LastMessage** is an offline, privacy-first "Dead Man's Switch" mobile application built for Android with full bilingual support (Deutsch 🇩🇪 / English 🇬🇧). It periodically monitors user check-in confirmations ("I am alive"). If the user fails to check in within the configured countdown timer interval (12h, 24h, 48h, 72h, 7 days), the application autonomously triggers emergency dispatch via native SMS and/or TLS encrypted SMTP Email with image attachments, GPS Google Maps links, and voice audio notes — completely without relying on proprietary cloud services or external backends.

---

## ✨ Current Features (Version 0.1)

- **🌐 Bilingual Language Switcher (Deutsch 🇩🇪 / English 🇬🇧):**  
  Easily switch the entire application UI (labels, status badges, diagnostic advice, toast messages) between German and English in the settings screen.

- **🖐️ Biometric & Hardware Fingerprint Lock (Android BiometricPrompt):**  
  Optionally secure the application startup, settings, and check-in with native hardware biometric authentication (Fingerprint, FaceID) or regular PIN code. Easily toggleable on or off in settings.

- **🚨 Duress / Panic PIN (Nötigungs-PIN):**  
  If forced by an attacker to unlock the app or cancel the timer, enter your secret Panic PIN. The application visually feigns a successful unlock and check-in, but **secretly triggers an immediate emergency SMS & Email dispatch in the background!** Features built-in debouncing protection.

- **⏳ Configurable Safety Grace Period (Gnadenfrist):**  
  When the main countdown timer expires, emergency alerts are not sent immediately. You get an additional grace buffer period (0h, 1h, 3h, 6h, 12h, 24h) with hourly push warning notifications to check in or cancel.

- **📍 Automatic GPS Location & Google Maps Link:**  
  When enabled, the app automatically retrieves live GPS coordinates in the background during check-in or emergency dispatch, appending a direct Google Maps link (`https://maps.google.com/?q=lat,lng`) to emergency SMS & Email text. Includes offline location history fallback.

- **🎙️ Voice Audio Note Attachments:**  
  Record or select a voice audio message (`.m4a` / `.mp3`), which is automatically attached as an audio file in emergency emails.

- **📷 Emergency Photo Attachments with Thumbnail Previews:**  
  Attach photos from your device gallery (ID cards, medical notes, emergency documents, location maps). Photos are stored securely in private app storage with preview thumbnails in settings.

- **🔥 Auto-Delete Sensitive Data After Dispatch:**  
  When enabled, the app automatically purges all photo attachment files, voice notes, and sensitive message body text from local device storage immediately after successful emergency dispatch.

- **🔁 Configurable Burst Repeats & Pause Delays:**  
  Customize how many times emergency messages are dispatched (1x, 2x, 3x, 5x) and specify delay pauses between messages (0s, 5s, 10s, 30s, 60s).

- **📩 Multi-Channel Autonomous Emergency Dispatch:**  
  - **Primary SMS Dispatch:** Native `SmsManager` multipart SMS dispatch (`SEND_SMS` permission).  
  - **Secondary TLS SMTP Email:** Jakarta Mail TLS client supporting custom SMTP servers (Gmail, GMX, WEB.DE, Outlook presets).  
  - **Interactive Live Testing:** Test SMS and Email connections directly in settings with human-friendly diagnostic error guides.

- **🛡️ Dual Redundancy & Self-Hosted Companion Server:**  
  - **Boot Recovery & Low Battery Guardian:** Listens for `ACTION_BOOT_COMPLETED` / `ACTION_POWER_CONNECTED` and dispatches immediately upon boot if the timer expired while off. Alerts when battery drops below 15%.  
  - **Raspberry Pi / Docker Watchdog Server:** Open-source Python/Docker server (`server/watchdog_server.py`) that dispatches emergency emails if your phone stays destroyed or offline.

---

## 🚀 Future Roadmap & Upcoming Features

The following features and enhancements are planned for upcoming releases (Version 0.2+):

### 1. UI, Home-Screen Widgets & Smartwatches (Wearables)
- 📲 **Android Home-Screen Widget (2x2 / 4x1):**  
  Interactive home-screen widget displaying real-time live countdown progress and a large 1-tap **"ICH BIN NOCH DA / I AM ALIVE"** check-in button so users don't even need to open the main app.
- ⌚ **Wear OS / Smartwatch Companion App:**  
  Perform 1-click check-ins directly from your wrist via a Wear OS companion app and tile notification.
- 🔘 **Lock Screen & Quick Settings Tile:**  
  Android Quick Settings tile button in the status bar shade for instantaneous timer resets with zero friction.

### 2. Advanced Notifications & Alarms
- 🔊 **Do-Not-Disturb (DND) Volume Bypass Siren:**  
  When the grace period enters critical time (<1 hour remaining), the app can trigger an audible warning alarm siren that overrides silent / Do Not Disturb mode.
- 🔔 **Interactive Push Notifications with Direct Action Buttons:**  
  Check in directly from the system notification shade by tapping `[ ✓ I AM ALIVE ]` without launching the app UI.

### 3. Extended Fail-Safe & Redundancy Channels
- 💬 **Telegram / WhatsApp / Signal Messenger Bots:**  
  Integrate bot webhooks to dispatch emergency alerts directly to private messaging channels or family groups alongside SMS & Email.
- 🌐 **Multi-Server Watchdog Redundancy:**  
  Support multiple fallback watchdog server URLs (e.g. Home Raspberry Pi **AND** an off-site cloud VPS fallback) in case of home power outages.

### 4. Smart Triggers & Automations
- ⚡ **Automatic Check-in Triggers (Optional):**  
  - **Home Wi-Fi:** Auto check-in when connecting to your home Wi-Fi network.  
  - **Charging Cable:** Auto check-in when plugging the phone in to charge at night.  
  - **Step Counter / Motion:** Auto timer reset when daily step count exceeds 1,000 steps.
- 👥 **Multi-Profile Emergency Recipients:**  
  Create distinct emergency profiles (e.g. "Family", "Workplace", "Missing Person") with custom recipient lists and tailored message body templates.

### 5. UI & Design System Refinement
- 🎨 **Glassmorphic UI Animations & Themes:**  
  Enhanced micro-animations, customizable dark/light themes, custom typography, and dynamic visual countdown meters.

### 6. Continuous Stability & Bug Prevention
- 🧪 **Automated Testing & Edge-Case Coverage:**  
  Continuous integration testing, battery consumption optimization, memory leak prevention, and automated unit test suite expansions.

---

## 🏗️ Project Architecture & Tech Stack

```
LastMessage/
├── app/                              # Android Mobile Application (Kotlin 2.0 + Jetpack Compose)
│   ├── src/main/java/com/dms/app/
│   │   ├── data/local/               # SQLCipher SQLite Helper & KeyStoreManager (AES-256 GCM)
│   │   ├── domain/                   # Data Models, Interfaces & Core Use Cases
│   │   ├── services/
│   │   │   ├── dispatch/             # Native SmsManager & Jakarta Mail TLS SmtpMailer
│   │   │   ├── location/             # Automatic GPS Location Provider
│   │   │   ├── notifications/        # AlarmManager & NotificationCompat Scheduler
│   │   │   ├── receivers/            # BootReceiver (Startup Recovery) & BatteryReceiver
│   │   │   ├── security/             # BiometricAuthHelper (Hardware BiometricPrompt)
│   │   │   ├── storage/              # Encrypted Storage Service Implementation
│   │   │   ├── timer/                # Pure Countdown Calculation Engine
│   │   │   └── watchdog/             # HTTP Web-Ping Watchdog Service
│   │   └── ui/                       # Jetpack Compose UI Screens & ViewModels
│   └── build.gradle.kts              # Android Application Build Configuration
├── server/                           # Self-Hosted Companion Watchdog Server
│   ├── watchdog_server.py            # Zero-dependency Python 3 Watchdog Server
│   ├── docker-compose.yml            # 1-Click Docker Deployment Specification
│   └── README.md                     # Server Deployment Setup Guide
└── build.gradle.kts                  # Root Gradle Project Specification
```

- **Language & Framework:** Kotlin 2.0.0, Jetpack Compose, Kotlin Coroutines, StateFlow
- **Biometrics & Security:** `androidx.biometric:biometric:1.1.0`, Android KeyStore AES-256 GCM, SQLite
- **Mail & Telephony:** Jakarta Mail 2.0 (TLS 1.2/1.3), Native Android `SmsManager`
- **Location & Media:** Android `LocationManager` GPS Provider, `MediaRecorder` Audio Notes
- **Build System:** Gradle 8.10.2, Android Gradle Plugin 8.7.2

---

## 📲 Installation & Building

### Prerequisites
- Android Studio Ladybug (2024.2+) or JDK 17+
- Android SDK API Level 34 (Android 14)

### Building the Debug APK
Clone the repository and run Gradle from PowerShell or Terminal:

```bash
git clone https://github.com/StrikerLUL/LastMessage.git
cd LastMessage

# Build the Debug APK
./gradlew assembleDebug
```

The compiled APK will be generated at:  
`app/build/outputs/apk/debug/app-debug.apk`

### Installing directly to a connected Android Device
Ensure USB Debugging is enabled on your phone, then run:

```bash
./gradlew installDebug
```

---

## ⚙️ Configuration & E-Mail Provider Setup

### 🔑 Google Gmail Configuration
Standard Gmail account passwords do **not** work for third-party SMTP clients due to Google 2FA security policies.

To use Gmail:
1. Visit **[myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords)** in your browser.
2. Select **App: Mail** and click **Generate**.
3. Copy the 16-character code (e.g. `abcd efgh ijkl mnop`).
4. In **LastMessage Settings**:
   - **SMTP Server:** `smtp.gmail.com`
   - **Port:** `587`
   - **Username:** Your full Gmail address
   - **Password:** Paste the 16-character App Password (without spaces)
5. Tap **`📧 E-MAIL VERBINDUNG JETZT TESTEN / TEST EMAIL CONNECTION NOW`** to verify connection.

### 🟡 GMX / WEB.DE Configuration
1. Log in to GMX or WEB.DE via desktop web browser.
2. Go to **Settings** -> **POP3 / SMTP Access**.
3. Enable **"Allow POP3 and SMTP access for external programs"**.
4. In LastMessage Settings, select the **GMX** or **WEB.DE** preset button.

---

## 🏠 Self-Hosted Watchdog Server Deployment

If you want absolute guarantee that emergency emails are dispatched even if your phone is completely destroyed, lost, or dead without battery forever, you can host the companion watchdog server on a Raspberry Pi, VPS, or home server.

### Deploy with Docker (Recommended)

Navigate to the `server/` directory:

```bash
cd server
```

Edit `docker-compose.yml` to specify your SMTP credentials and emergency contact:

```yaml
version: '3.8'

services:
  lastmessage-watchdog:
    image: python:3.11-slim
    container_name: lastmessage_watchdog
    restart: always
    ports:
      - "8080:8080"
    volumes:
      - ./:/app
    working_dir: /app
    command: python3 watchdog_server.py
    environment:
      - WATCHDOG_PORT=8080
      - TIMEOUT_MINUTES=1440
      - SMTP_HOST=smtp.gmail.com
      - SMTP_PORT=587
      - SMTP_USER=your_email@gmail.com
      - SMTP_PASS=your_app_password
      - EMERGENCY_RECIPIENT=emergency_contact@example.com
```

Start the container:

```bash
docker-compose up -d
```

---

## 🔒 Security & Privacy Statement

- **No Remote Telemetry:** LastMessage contains zero analytics, tracking scripts, or ad networks.
- **Local Storage Encryption:** All credentials and recipient details are stored using Android KeyStore master keys.
- **Open Source:** Full source code for both the Android app and the self-hosted companion server is completely open for audit and inspection.

---

## 📄 License

Distributed under the MIT License. See `LICENSE` for details.
