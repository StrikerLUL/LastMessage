# Project: Dead Man's Switch Mobile App

## Architecture
- **Pattern**: Clean Architecture / MVVM (Model-View-ViewModel) with Modular Service Layers
- **Target Platform**: Native Android (Kotlin 2.x + Jetpack Compose)
- **Data Flow**:
  - User Action / UI -> ViewModel -> Repository -> Encrypted Storage (SQLCipher / EncryptedSharedPreferences)
  - WorkManager Background Worker (15 min periodic) -> Timer Engine -> Check-in Repository -> Notification Service / Emergency Dispatch Engine (SmsManager & SMTP Mailer)
  - System Boot (`RECEIVE_BOOT_COMPLETED` / `LOCKED_BOOT_COMPLETED`) -> BootReceiver -> WorkManager Rescheduler & Notification Planner

## Code Layout
```
app/src/main/java/com/dms/app/
├── data/
│   ├── local/          # SQLCipher database, EncryptedSharedPreferences wrapper, DAOs, KeyStoreManager
│   ├── models/         # Database entities (AppConfig, EmergencyContact, SmtpCredentials, CheckInLog, EmergencyMessage)
│   └── repository/     # Repository implementations (CheckInRepositoryImpl, ConfigRepositoryImpl, etc.)
├── domain/
│   ├── models/         # Immutable domain models (DmsConfig, TimerStatus, DispatchResult)
│   ├── usecases/       # CheckInUseCase, EvaluateTimerUseCase, ScheduleNotificationsUseCase, DispatchEmergencyUseCase
│   └── interfaces/     # Repository & Service interfaces (ISecureStorage, ITimerEngine, INotificationScheduler, IEmergencyDispatcher)
├── services/
│   ├── storage/        # SecureStorageService (Android Keystore + MasterKey + SQLCipher SQLite DDL/CRUD)
│   ├── timer/          # TimerEngine (Interval calculations, short-interval warning logic & threshold math)
│   ├── notifications/  # NotificationScheduler (Local notifications & AlarmManager setExactAndAllowWhileIdle)
│   ├── workmanager/    # CheckInCheckWorker (CoroutineWorker) & BootReceiver (BroadcastReceiver)
│   └── dispatch/       # SmsDispatcher (SmsManager) & SmtpMailer (Jakarta Mail TLS with 3x backoff retry)
└── ui/
    ├── MainActivity.kt # Activity entry point (ComponentActivity)
    ├── checkin/        # Check-in UI screen & countdown timer presenter (CheckInScreen)
    ├── settings/       # Settings, contacts, and SMTP configuration presenters (SettingsScreen)
    └── viewmodels/     # StateFlow ViewModels (CheckInViewModel, SettingsViewModel)
```

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | Framework & Library Evaluation | Compare Flutter vs Native Kotlin, background execution, storage, SMS/Email | none | DONE |
| 2 | Architecture & DB Schema Design | Design MVVM/Clean Architecture, Data Flow diagrams, Encrypted DB schema | M1 | DONE |
| 3 | Modular Starter Implementation & Unit Tests | Complete code modules (Storage, Timer, Notifications, WorkManager, Dispatch, UI) + Unit Tests | M2 | DONE |
| 4 | AndroidManifest & Permissions Guide | Complete Manifest snippet, runtime permissions flow, battery optimization guide | M3 | DONE |
| 5 | Edge Cases Analysis Matrix & Robustness | Matrix covering offline, flight, reboot, doze, kill scenarios + verification | M4 | DONE |

## Interface Contracts
### StorageService ↔ TimerEngine & DispatchEngine
- `fun saveCheckInTimestamp(timestamp: String)`
- `fun getLastCheckInTimestamp(): String?`
- `fun getConfig(): DmsConfig`
- `fun saveConfig(config: DmsConfig)`

### TimerEngine ↔ WorkManagerWorker & NotificationScheduler
- `fun getRemainingDuration(lastCheckIn: Instant, intervalMinutes: Long): Duration`
- `fun evaluateStatus(lastCheckIn: Instant, intervalMinutes: Long, currentTime: Instant): TimerStatus`
- `fun calculateNotificationThresholds(lastCheckIn: Instant, intervalMinutes: Long): List<MilestoneThreshold>`

### EmergencyDispatcher ↔ WorkManagerWorker
- `fun triggerEmergencyDispatch(config: DmsConfig, message: EmergencyMessage): DispatchResult`
- `fun sendMultipartSms(phoneNumber: String, message: String): SmsResult`
- `fun sendSmtpEmailWithRetry(smtp: SmtpCredentials, recipient: String, message: String, maxRetries: Int = 3): EmailResult`
