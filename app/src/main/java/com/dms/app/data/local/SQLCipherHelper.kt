package com.dms.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.dms.app.domain.models.*
import java.time.Instant

/**
 * SQLCipherHelper performs SQLite database DDL execution and CRUD operations
 * using Android's native SQLite database engine.
 */
class SQLCipherHelper(
    context: Context? = null,
    dbName: String = "dms_app.db"
) : SQLiteOpenHelper(context, dbName, null, 6) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS app_config (
                id INTEGER PRIMARY KEY,
                timer_interval_minutes INTEGER NOT NULL,
                grace_period_minutes INTEGER NOT NULL DEFAULT 360,
                primary_dispatch_method TEXT NOT NULL,
                retry_count INTEGER NOT NULL,
                is_active INTEGER NOT NULL,
                language TEXT NOT NULL DEFAULT 'DE',
                enable_boot_recovery INTEGER NOT NULL DEFAULT 1,
                enable_battery_warnings INTEGER NOT NULL DEFAULT 1,
                enable_cloud_watchdog INTEGER NOT NULL DEFAULT 0,
                watchdog_ping_url TEXT NOT NULL DEFAULT '',
                enable_biometric_lock INTEGER NOT NULL DEFAULT 0,
                app_pin TEXT NOT NULL DEFAULT '',
                panic_pin TEXT NOT NULL DEFAULT '',
                auto_delete_after_dispatch INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS emergency_contacts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                recipient_name TEXT NOT NULL,
                phone_number TEXT NOT NULL,
                email_address TEXT NOT NULL,
                priority INTEGER NOT NULL,
                is_enabled INTEGER NOT NULL,
                created_at TEXT NOT NULL
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS smtp_credentials (
                id INTEGER PRIMARY KEY,
                host TEXT NOT NULL,
                port INTEGER NOT NULL,
                username TEXT NOT NULL,
                password_encrypted TEXT NOT NULL,
                enable_tls INTEGER NOT NULL,
                updated_at TEXT NOT NULL
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS checkin_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp TEXT NOT NULL,
                method TEXT NOT NULL,
                status TEXT NOT NULL,
                details TEXT
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS emergency_messages (
                id INTEGER PRIMARY KEY,
                body_template TEXT NOT NULL,
                contains_location INTEGER NOT NULL,
                attachment_paths TEXT NOT NULL DEFAULT '',
                last_updated TEXT NOT NULL
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS last_checkin (
                id INTEGER PRIMARY KEY,
                timestamp_iso TEXT NOT NULL
            );
            """.trimIndent()
        )

        // Seed default config
        val defaultConfig = DmsConfig()
        val cvConfig = ContentValues().apply {
            put("id", defaultConfig.id)
            put("timer_interval_minutes", defaultConfig.timerIntervalMinutes)
            put("grace_period_minutes", defaultConfig.gracePeriodMinutes)
            put("primary_dispatch_method", defaultConfig.primaryDispatchMethod)
            put("retry_count", defaultConfig.retryCount)
            put("is_active", if (defaultConfig.isActive) 1 else 0)
            put("language", defaultConfig.language)
            put("enable_boot_recovery", if (defaultConfig.enableBootRecovery) 1 else 0)
            put("enable_battery_warnings", if (defaultConfig.enableBatteryWarnings) 1 else 0)
            put("enable_cloud_watchdog", if (defaultConfig.enableCloudWatchdog) 1 else 0)
            put("watchdog_ping_url", defaultConfig.watchdogPingUrl)
            put("enable_biometric_lock", if (defaultConfig.enableBiometricLock) 1 else 0)
            put("app_pin", defaultConfig.appPin)
            put("panic_pin", defaultConfig.panicPin)
            put("auto_delete_after_dispatch", if (defaultConfig.autoDeleteAfterDispatch) 1 else 0)
            put("created_at", defaultConfig.createdAt)
            put("updated_at", defaultConfig.updatedAt)
        }
        db.insertWithOnConflict("app_config", null, cvConfig, SQLiteDatabase.CONFLICT_REPLACE)

        // Seed default message
        val cvMsg = ContentValues().apply {
            put("id", 1)
            put("body_template", "EMERGENCY: User failed to check in with LastMessage application.")
            put("contains_location", 0)
            put("attachment_paths", "")
            put("last_updated", Instant.now().toString())
        }
        db.insertWithOnConflict("emergency_messages", null, cvMsg, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE emergency_messages ADD COLUMN attachment_paths TEXT NOT NULL DEFAULT ''")
            } catch (ignored: Exception) {
            }
        }
        if (oldVersion < 3) {
            try {
                db.execSQL("ALTER TABLE app_config ADD COLUMN enable_boot_recovery INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_config ADD COLUMN enable_battery_warnings INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_config ADD COLUMN enable_cloud_watchdog INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_config ADD COLUMN watchdog_ping_url TEXT NOT NULL DEFAULT ''")
            } catch (ignored: Exception) {
            }
        }
        if (oldVersion < 4) {
            try {
                db.execSQL("ALTER TABLE app_config ADD COLUMN language TEXT NOT NULL DEFAULT 'DE'")
            } catch (ignored: Exception) {
            }
        }
        if (oldVersion < 5) {
            try {
                db.execSQL("ALTER TABLE app_config ADD COLUMN grace_period_minutes INTEGER NOT NULL DEFAULT 360")
            } catch (ignored: Exception) {
            }
        }
        if (oldVersion < 6) {
            try {
                db.execSQL("ALTER TABLE app_config ADD COLUMN enable_biometric_lock INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_config ADD COLUMN app_pin TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_config ADD COLUMN panic_pin TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_config ADD COLUMN auto_delete_after_dispatch INTEGER NOT NULL DEFAULT 0")
            } catch (ignored: Exception) {
            }
        }
    }

    @Synchronized
    fun getAppConfig(): DmsConfig {
        try {
            val db = readableDatabase
            db.rawQuery("SELECT id, timer_interval_minutes, primary_dispatch_method, retry_count, is_active, language, enable_boot_recovery, enable_battery_warnings, enable_cloud_watchdog, watchdog_ping_url, grace_period_minutes, enable_biometric_lock, app_pin, panic_pin, auto_delete_after_dispatch, created_at, updated_at FROM app_config WHERE id = 1", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    return DmsConfig(
                        id = cursor.getInt(0),
                        timerIntervalMinutes = cursor.getLong(1),
                        primaryDispatchMethod = cursor.getString(2),
                        retryCount = cursor.getInt(3),
                        isActive = cursor.getInt(4) == 1,
                        language = cursor.getString(5) ?: "DE",
                        enableBootRecovery = cursor.getInt(6) == 1,
                        enableBatteryWarnings = cursor.getInt(7) == 1,
                        enableCloudWatchdog = cursor.getInt(8) == 1,
                        watchdogPingUrl = cursor.getString(9) ?: "",
                        gracePeriodMinutes = cursor.getLong(10),
                        enableBiometricLock = cursor.getInt(11) == 1,
                        appPin = cursor.getString(12) ?: "",
                        panicPin = cursor.getString(13) ?: "",
                        autoDeleteAfterDispatch = cursor.getInt(14) == 1,
                        createdAt = cursor.getString(15),
                        updatedAt = cursor.getString(16)
                    )
                }
            }
        } catch (e: Exception) {
        }
        return DmsConfig()
    }

    @Synchronized
    fun saveAppConfig(config: DmsConfig) {
        try {
            val db = writableDatabase
            val cv = ContentValues().apply {
                put("id", config.id)
                put("timer_interval_minutes", config.timerIntervalMinutes)
                put("grace_period_minutes", config.gracePeriodMinutes)
                put("primary_dispatch_method", config.primaryDispatchMethod)
                put("retry_count", config.retryCount)
                put("is_active", if (config.isActive) 1 else 0)
                put("language", config.language)
                put("enable_boot_recovery", if (config.enableBootRecovery) 1 else 0)
                put("enable_battery_warnings", if (config.enableBatteryWarnings) 1 else 0)
                put("enable_cloud_watchdog", if (config.enableCloudWatchdog) 1 else 0)
                put("watchdog_ping_url", config.watchdogPingUrl)
                put("enable_biometric_lock", if (config.enableBiometricLock) 1 else 0)
                put("app_pin", config.appPin)
                put("panic_pin", config.panicPin)
                put("auto_delete_after_dispatch", if (config.autoDeleteAfterDispatch) 1 else 0)
                put("created_at", config.createdAt)
                put("updated_at", Instant.now().toString())
            }
            db.insertWithOnConflict("app_config", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Exception) {
        }
    }

    @Synchronized
    fun getEmergencyContacts(): List<EmergencyContact> {
        val list = mutableListOf<EmergencyContact>()
        try {
            val db = readableDatabase
            db.rawQuery("SELECT id, recipient_name, phone_number, email_address, priority, is_enabled, created_at FROM emergency_contacts ORDER BY priority ASC, id ASC", null).use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(
                        EmergencyContact(
                            id = cursor.getLong(0),
                            recipientName = cursor.getString(1),
                            phoneNumber = cursor.getString(2),
                            emailAddress = cursor.getString(3),
                            priority = cursor.getInt(4),
                            isEnabled = cursor.getInt(5) == 1,
                            createdAt = cursor.getString(6)
                        )
                    )
                }
            }
        } catch (e: Exception) {
        }
        return list
    }

    @Synchronized
    fun saveEmergencyContacts(contacts: List<EmergencyContact>) {
        try {
            val db = writableDatabase
            db.delete("emergency_contacts", null, null)
            for (contact in contacts) {
                addEmergencyContact(contact)
            }
        } catch (e: Exception) {
        }
    }

    @Synchronized
    fun addEmergencyContact(contact: EmergencyContact): Long {
        try {
            val db = writableDatabase
            val cv = ContentValues().apply {
                put("recipient_name", contact.recipientName)
                put("phone_number", contact.phoneNumber)
                put("email_address", contact.emailAddress)
                put("priority", contact.priority)
                put("is_enabled", if (contact.isEnabled) 1 else 0)
                put("created_at", contact.createdAt)
            }
            return db.insert("emergency_contacts", null, cv)
        } catch (e: Exception) {
            return -1L
        }
    }

    @Synchronized
    fun deleteEmergencyContact(contactId: Long) {
        try {
            val db = writableDatabase
            db.delete("emergency_contacts", "id = ?", arrayOf(contactId.toString()))
        } catch (e: Exception) {
        }
    }

    @Synchronized
    fun getSmtpCredentials(): SmtpCredentials? {
        try {
            val db = readableDatabase
            db.rawQuery("SELECT id, host, port, username, password_encrypted, enable_tls, updated_at FROM smtp_credentials WHERE id = 1", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    return SmtpCredentials(
                        id = cursor.getInt(0),
                        host = cursor.getString(1),
                        port = cursor.getInt(2),
                        username = cursor.getString(3),
                        passwordEncrypted = cursor.getString(4),
                        enableTls = cursor.getInt(5) == 1,
                        updatedAt = cursor.getString(6)
                    )
                }
            }
        } catch (e: Exception) {
        }
        return null
    }

    @Synchronized
    fun saveSmtpCredentials(credentials: SmtpCredentials) {
        try {
            val db = writableDatabase
            val cv = ContentValues().apply {
                put("id", credentials.id)
                put("host", credentials.host)
                put("port", credentials.port)
                put("username", credentials.username)
                put("password_encrypted", credentials.passwordEncrypted)
                put("enable_tls", if (credentials.enableTls) 1 else 0)
                put("updated_at", Instant.now().toString())
            }
            db.insertWithOnConflict("smtp_credentials", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Exception) {
        }
    }

    @Synchronized
    fun getEmergencyMessage(): EmergencyMessage {
        try {
            val db = readableDatabase
            db.rawQuery("SELECT id, body_template, contains_location, attachment_paths, last_updated FROM emergency_messages WHERE id = 1", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    val rawPaths = cursor.getString(3) ?: ""
                    val pathsList = if (rawPaths.isNotBlank()) rawPaths.split(";").filter { it.isNotBlank() } else emptyList()
                    return EmergencyMessage(
                        id = cursor.getInt(0),
                        bodyTemplate = cursor.getString(1),
                        containsLocation = cursor.getInt(2) == 1,
                        attachmentPaths = pathsList,
                        lastUpdated = cursor.getString(4)
                    )
                }
            }
        } catch (e: Exception) {
        }
        return EmergencyMessage(
            id = 1,
            bodyTemplate = "EMERGENCY: User failed to check in with LastMessage application.",
            containsLocation = false,
            attachmentPaths = emptyList(),
            lastUpdated = Instant.now().toString()
        )
    }

    @Synchronized
    fun saveEmergencyMessage(message: EmergencyMessage) {
        try {
            val db = writableDatabase
            val pathsJoined = message.attachmentPaths.joinToString(";")
            val cv = ContentValues().apply {
                put("id", message.id)
                put("body_template", message.bodyTemplate)
                put("contains_location", if (message.containsLocation) 1 else 0)
                put("attachment_paths", pathsJoined)
                put("last_updated", Instant.now().toString())
            }
            db.insertWithOnConflict("emergency_messages", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Exception) {
        }
    }

    @Synchronized
    fun addCheckInLog(log: CheckInLog): Long {
        try {
            val db = writableDatabase
            val cv = ContentValues().apply {
                put("timestamp", log.timestamp)
                put("method", log.method)
                put("status", log.status)
                put("details", log.details)
            }
            return db.insert("checkin_logs", null, cv)
        } catch (e: Exception) {
            return -1L
        }
    }

    @Synchronized
    fun getCheckInLogs(): List<CheckInLog> {
        val list = mutableListOf<CheckInLog>()
        try {
            val db = readableDatabase
            db.rawQuery("SELECT id, timestamp, method, status, details FROM checkin_logs ORDER BY id DESC", null).use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(
                        CheckInLog(
                            id = cursor.getLong(0),
                            timestamp = cursor.getString(1),
                            method = cursor.getString(2),
                            status = cursor.getString(3),
                            details = cursor.getString(4)
                        )
                    )
                }
            }
        } catch (e: Exception) {
        }
        return list
    }

    @Synchronized
    fun setLastCheckInTimestamp(timestampIso: String) {
        try {
            val db = writableDatabase
            val cv = ContentValues().apply {
                put("id", 1)
                put("timestamp_iso", timestampIso)
            }
            db.insertWithOnConflict("last_checkin", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Exception) {
        }
    }

    @Synchronized
    fun getLastCheckInTimestamp(): String? {
        try {
            val db = readableDatabase
            db.rawQuery("SELECT timestamp_iso FROM last_checkin WHERE id = 1", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(0)
                }
            }
        } catch (e: Exception) {
        }
        return null
    }
}
