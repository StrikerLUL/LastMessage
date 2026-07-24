package com.dms.app.services.notifications

import com.dms.app.domain.interfaces.INotificationScheduler
import com.dms.app.domain.models.MilestoneThreshold
import java.time.Instant

/**
 * NotificationScheduler manages Notification Channel creation, deep-link PendingIntent construction,
 * exact alarm scheduling via Android AlarmManager at calculated thresholds (75%, 50%, 25%, 10%, 1h),
 * and local push notification delivery via NotificationManager.
 */
class NotificationScheduler(
    private val androidContext: Any? = null
) : INotificationScheduler {

    companion object {
        const val CHANNEL_ID = "dms_alerts_channel"
        const val CHANNEL_NAME = "Dead Man's Switch Safety Alerts"
        const val ACTION_CHECK_IN_DEEP_LINK = "com.dms.app.ACTION_CHECK_IN"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    private val scheduledAlarmsMap = mutableMapOf<String, Long>()
    private var channelsCreated: Boolean = false

    override fun createNotificationChannels() {
        if (androidContext != null) {
            try {
                val contextClass = Class.forName("android.content.Context")
                val notifManagerClass = Class.forName("android.app.NotificationManager")
                val notifChannelClass = Class.forName("android.app.NotificationChannel")

                val getSystemServiceMethod = contextClass.getMethod("getSystemService", String::class.java)
                val notifManagerField = contextClass.getField("NOTIFICATION_SERVICE").get(null) as String
                val notifManagerObj = getSystemServiceMethod.invoke(androidContext, notifManagerField)

                val importanceHigh = notifChannelClass.getField("IMPORTANCE_HIGH").getInt(null)
                val constructor = notifChannelClass.getConstructor(String::class.java, CharSequence::class.java, Int::class.javaPrimitiveType)
                val channelObj = constructor.newInstance(CHANNEL_ID, CHANNEL_NAME, importanceHigh)

                val createChannelMethod = notifManagerClass.getMethod("createNotificationChannel", notifChannelClass)
                createChannelMethod.invoke(notifManagerObj, channelObj)
            } catch (ignored: Exception) {
            }
        }
        channelsCreated = true
    }

    override fun scheduleThresholdNotifications(milestones: List<MilestoneThreshold>) {
        if (!channelsCreated) {
            createNotificationChannels()
        }

        cancelAllNotifications()

        val nowEpochMillis = Instant.now().toEpochMilli()

        for (milestone in milestones) {
            if (milestone.triggerTimeEpochMillis > nowEpochMillis) {
                scheduleExactAlarm(
                    milestoneName = milestone.milestoneName,
                    triggerTimeEpochMillis = milestone.triggerTimeEpochMillis,
                    remainingMinutes = milestone.remainingMinutes
                )
            }
        }
    }

    private fun scheduleExactAlarm(
        milestoneName: String,
        triggerTimeEpochMillis: Long,
        remainingMinutes: Long
    ) {
        scheduledAlarmsMap[milestoneName] = triggerTimeEpochMillis

        if (androidContext != null) {
            try {
                val contextClass = Class.forName("android.content.Context")
                val alarmManagerClass = Class.forName("android.app.AlarmManager")
                val pendingIntentClass = Class.forName("android.app.PendingIntent")
                val intentClass = Class.forName("android.content.Intent")

                val getSystemServiceMethod = contextClass.getMethod("getSystemService", String::class.java)
                val alarmServiceField = contextClass.getField("ALARM_SERVICE").get(null) as String
                val alarmManagerObj = getSystemServiceMethod.invoke(androidContext, alarmServiceField)

                val rtcWakeup = alarmManagerClass.getField("RTC_WAKEUP").getInt(null)

                val setExactAndAllowWhileIdleMethod = try {
                    alarmManagerClass.getMethod("setExactAndAllowWhileIdle", Int::class.javaPrimitiveType, Long::class.javaPrimitiveType, pendingIntentClass)
                } catch (e: Exception) {
                    alarmManagerClass.getMethod("setExact", Int::class.javaPrimitiveType, Long::class.javaPrimitiveType, pendingIntentClass)
                }

                val intentObj = intentClass.getConstructor(String::class.java).newInstance(ACTION_CHECK_IN_DEEP_LINK)
                val getBroadcastMethod = pendingIntentClass.getMethod(
                    "getBroadcast",
                    contextClass,
                    Int::class.javaPrimitiveType,
                    intentClass,
                    Int::class.javaPrimitiveType
                )
                val pendingIntentObj = getBroadcastMethod.invoke(null, androidContext, milestoneName.hashCode(), intentObj, 134217728 /* FLAG_UPDATE_CURRENT */)

                setExactAndAllowWhileIdleMethod.invoke(alarmManagerObj, rtcWakeup, triggerTimeEpochMillis, pendingIntentObj)
            } catch (ignored: Exception) {
            }
        }
    }

    override fun cancelAllNotifications() {
        scheduledAlarmsMap.clear()
        if (androidContext != null) {
            try {
                val contextClass = Class.forName("android.content.Context")
                val alarmManagerClass = Class.forName("android.app.AlarmManager")
                val pendingIntentClass = Class.forName("android.app.PendingIntent")
                val intentClass = Class.forName("android.content.Intent")

                val getSystemServiceMethod = contextClass.getMethod("getSystemService", String::class.java)
                val alarmServiceField = contextClass.getField("ALARM_SERVICE").get(null) as String
                val alarmManagerObj = getSystemServiceMethod.invoke(androidContext, alarmServiceField)

                val intentObj = intentClass.getConstructor(String::class.java).newInstance(ACTION_CHECK_IN_DEEP_LINK)
                val getBroadcastMethod = pendingIntentClass.getMethod(
                    "getBroadcast",
                    contextClass,
                    Int::class.javaPrimitiveType,
                    intentClass,
                    Int::class.javaPrimitiveType
                )
                val pendingIntentObj = getBroadcastMethod.invoke(null, androidContext, 0, intentObj, 134217728)

                val cancelMethod = alarmManagerClass.getMethod("cancel", pendingIntentClass)
                cancelMethod.invoke(alarmManagerObj, pendingIntentObj)
            } catch (ignored: Exception) {
            }
        }
    }

    override fun sendWarningNotification(title: String, body: String) {
        if (androidContext != null) {
            try {
                val contextClass = Class.forName("android.content.Context")
                val notifManagerClass = Class.forName("android.app.NotificationManager")
                val builderClass = Class.forName("androidx.core.app.NotificationCompat\$Builder")

                val getSystemServiceMethod = contextClass.getMethod("getSystemService", String::class.java)
                val notifManagerField = contextClass.getField("NOTIFICATION_SERVICE").get(null) as String
                val notifManagerObj = getSystemServiceMethod.invoke(androidContext, notifManagerField)

                val builderObj = builderClass.getConstructor(contextClass, String::class.java).newInstance(androidContext, CHANNEL_ID)
                builderClass.getMethod("setContentTitle", CharSequence::class.java).invoke(builderObj, title)
                builderClass.getMethod("setContentText", CharSequence::class.java).invoke(builderObj, body)
                builderClass.getMethod("setAutoCancel", Boolean::class.javaPrimitiveType).invoke(builderObj, true)

                val buildMethod = builderClass.getMethod("build")
                val notificationObj = buildMethod.invoke(builderObj)

                val notifyMethod = notifManagerClass.getMethod("notify", Int::class.javaPrimitiveType, Class.forName("android.app.Notification"))
                notifyMethod.invoke(notifManagerObj, System.currentTimeMillis().toInt(), notificationObj)
            } catch (ignored: Exception) {
            }
        }
    }

    fun getScheduledAlarms(): Map<String, Long> = scheduledAlarmsMap.toMap()

    fun createCheckInDeepLinkAction(): String {
        return ACTION_CHECK_IN_DEEP_LINK
    }
}
