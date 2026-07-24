# 🛡️ LastMessage — Privacy-First Offline Dead Man's Switch for Android

**LastMessage** is an offline, privacy-first "Dead Man's Switch" mobile application built for Android. It periodically monitors user check-in confirmations ("I am alive"). If the user fails to check in within the configured countdown timer interval (12h, 24h, 48h, 72h, 7 days), the application autonomously triggers emergency dispatch via native SMS and/or TLS encrypted SMTP Email with image attachments — completely without relying on proprietary cloud services or external backends.

---

## ✨ Features

- **🔒 100% Offline & Encrypted Storage:**  
  All sensitive credentials (SMTP passwords, recipient phone numbers, email addresses, emergency message body, image attachments, and check-in logs) are stored locally on the device using AES-256 GCM double-envelope encryption backed by Android KeyStore and SQLite. No data ever leaves the device until emergency dispatch is triggered.

- **📱 Modern Jetpack Compose UI:**  
  Designed with modern dark-mode aesthetics, dynamic status badges (`ACTIVE`, `WARNING`, `EXPIRED`), countdown timer display, and simple one-tap check-in confirmation ("ICH BIN NOCH DA").

- **📷 Emergency Image File Attachments with Thumbnail Previews:**  
  Attach photos from your device gallery (ID cards, medical notes, emergency documents, location maps). Photos are stored securely in private app storage, rendered as small rounded thumbnails in settings, and attached automatically as `MimeMultipart` image attachments in emergency emails.

- **📩 Multi-Channel Autonomous Emergency Dispatch:**  
  - **Primary SMS Dispatch:** Native `SmsManager` multipart SMS dispatch (`SEND_SMS` permission).  
  - **Secondary TLS SMTP Email:** Jakarta Mail TLS client supporting custom SMTP servers (Gmail, GMX, WEB.DE, Outlook, custom domain servers).  
  - **Interactive Live Testing:** Test SMS and Email connections directly in settings with human-friendly diagnostic error resolution guides (e.g. step-by-step Gmail App Password setup).

- **🛡️ Dual Redundancy & Fail-Safe Protection:**  
  - **Mode 1: Boot Recovery & Low Battery Guardian (100% Offline):**  
    If the phone powers off due to a dead battery, `BootReceiver` listens for `ACTION_BOOT_COMPLETED` / `ACTION_POWER_CONNECTED` and evaluates whether the timer expired while powered off. If expired, it triggers emergency dispatch immediately upon boot! `BatteryReceiver` alerts the user when the battery drops below 15%.  
  - **Mode 2: Self-Hosted Companion Server / Raspberry Pi Watchdog:**  
    Includes an open-source Python/Docker companion server (`server/watchdog_server.py`) that you can host on your own Raspberry Pi or Linux server. The app sends a silent heartbeat ping on check-in. If your phone is destroyed or remains off, your home server automatically dispatches the emergency email!

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
│   │   │   ├── notifications/        # AlarmManager & NotificationCompat Scheduler
│   │   │   ├── receivers/            # BootReceiver (Startup Recovery) & BatteryReceiver
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
- **Database & Security:** Native Android SQLite (`SQLiteOpenHelper`), Android KeyStore AES-256 GCM
- **Mail & Telephony:** Jakarta Mail 2.0 (TLS 1.2/1.3), Native Android `SmsManager`
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
5. Tap **`📧 E-MAIL VERBINDUNG JETZT TESTEN`** to verify connection.

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

### Linking the App to your Server
In the **LastMessage Android App**:
1. Open **Settings** -> **Fail-Safe & Redundancy**.
2. Enable **`🏠 Eigenen Server / Raspberry Pi nutzen`**.
3. Enter your server URL (e.g., `http://192.168.1.100:8080/ping` or `https://your-domain.com/ping`).
4. Tap **`🌐 SERVER-PING JETZT TESTEN`** to confirm connection.

---

## 🔒 Security & Privacy Statement

- **No Remote Telemetry:** LastMessage contains zero analytics, tracking scripts, or ad networks.
- **Local Storage Encryption:** All credentials and recipient details are stored using Android KeyStore master keys.
- **Open Source:** Full source code for both the Android app and the self-hosted companion server is completely open for audit and inspection.

---

## 📄 License

Distributed under the MIT License. See `LICENSE` for details.
