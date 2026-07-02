package com.example.medicinecheck

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.util.Calendar

class MedicineReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MedicineRepository.autoMarkMissedDoses(context)
        val doseIndex = intent.getIntExtra(EXTRA_DOSE_INDEX, 1).coerceIn(1, 3)
        val tasks = MedicineRepository.getCurrentReminderTasks(context, doseIndex)
        if (MedicineRepository.isReminderEnabled(context) &&
            MedicineReminderScheduler.canPostNotifications(context) &&
            tasks.isNotEmpty()
        ) {
            MedicineReminderScheduler.showReminder(context, doseIndex, tasks)
        }
        WidgetUpdateHelper.updateAllWidgets(context)
    }

    companion object {
        const val ACTION_REMINDER = "com.example.medicinecheck.ACTION_REMINDER"
        const val EXTRA_DOSE_INDEX = "com.example.medicinecheck.EXTRA_REMINDER_DOSE_INDEX"
    }
}

class MedicineMissedReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MedicineRepository.autoMarkMissedDoses(context)
        val doseIndex = intent.getIntExtra(MedicineReminderReceiver.EXTRA_DOSE_INDEX, 1)
            .coerceIn(1, 3)
        val tasks = MedicineRepository.getCurrentReminderTasks(context, doseIndex)

        if (MedicineRepository.isReminderEnabled(context) &&
            MedicineRepository.getMissedReminderDelayMinutes(context) > 0 &&
            MedicineReminderScheduler.canPostNotifications(context) &&
            tasks.isNotEmpty()
        ) {
            MedicineReminderScheduler.showMissedReminder(context, doseIndex, tasks)
        }
        WidgetUpdateHelper.updateAllWidgets(context)
    }
}

object MedicineReminderScheduler {
    private const val CHANNEL_ID = "medicine_reminders"
    private const val CHANNEL_NAME = "吃药提醒"
    private const val REQUEST_CODE_BASE = 50_000
    private const val MISSED_REQUEST_CODE_BASE = 60_000

    fun scheduleAllIfEnabled(context: Context) {
        MedicineRepository.autoMarkMissedDoses(context)
        if (!MedicineRepository.isReminderEnabled(context)) {
            cancelAll(context)
            return
        }
        if (!canPostNotifications(context)) return

        createNotificationChannel(context)
        cancelAll(context)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        MedicineRepository.getReminderTimes(context).forEach { doseTime ->
            val triggerAtMillis = nextTriggerMillis(doseTime)
            val pendingIntent = reminderPendingIntent(context, doseTime.doseIndex)
            try {
                scheduleAlarmClock(alarmManager, triggerAtMillis, pendingIntent, context)
            } catch (_: SecurityException) {
                scheduleExactFallback(alarmManager, triggerAtMillis, pendingIntent)
            } catch (_: RuntimeException) {
                scheduleExactFallback(alarmManager, triggerAtMillis, pendingIntent)
            }
        }
        scheduleMissedRemindersForPendingToday(context)
    }

    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        for (doseIndex in 1..3) {
            alarmManager.cancel(reminderPendingIntent(context, doseIndex))
            alarmManager.cancel(missedReminderPendingIntent(context, doseIndex))
        }
    }

    fun canPostNotifications(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun showReminder(context: Context, doseIndex: Int, tasks: List<MedicineTask>) {
        createNotificationChannel(context)
        val lines = tasks.take(MedicineRepository.MAX_MEDICINES).joinToString("\n") {
            it.medicine.displayText().ifBlank { context.getString(R.string.medicine_not_set) }
        }
        val content = if (tasks.size == 1) {
            context.getString(R.string.reminder_notification_content, lines, "")
        } else {
            "该吃药了：\n$lines"
        }
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(content)
            .setStyle(Notification.BigTextStyle().bigText(content))
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(REQUEST_CODE_BASE + doseIndex, notification)
    }

    fun showMissedReminder(context: Context, doseIndex: Int, tasks: List<MedicineTask>) {
        createNotificationChannel(context)
        val lines = tasks.take(MedicineRepository.MAX_MEDICINES).joinToString("\n") {
            it.medicine.displayText().ifBlank { context.getString(R.string.medicine_not_set) }
        }
        val content = if (tasks.size == 1) {
            context.getString(R.string.missed_reminder_notification_content, lines, "")
        } else {
            "还没打卡：\n$lines"
        }
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(content)
            .setStyle(Notification.BigTextStyle().bigText(content))
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(MISSED_REQUEST_CODE_BASE + doseIndex, notification)
    }

    private fun scheduleMissedRemindersForPendingToday(context: Context) {
        val delayMinutes = MedicineRepository.getMissedReminderDelayMinutes(context)
        if (delayMinutes <= 0) return

        val now = Calendar.getInstance()
        val todayKey = MedicineRepository.getTodayDateKey()
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        MedicineRepository.getReminderTimes(context).forEach { doseTime ->
            if (MedicineRepository.getCurrentReminderTasks(context, doseTime.doseIndex).isEmpty()) {
                return@forEach
            }

            val triggerAtMillis = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, doseTime.hour)
                set(Calendar.MINUTE, doseTime.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.MINUTE, delayMinutes)
            }.timeInMillis
            if (triggerAtMillis <= now.timeInMillis) return@forEach

            scheduleInexact(
                alarmManager,
                triggerAtMillis,
                missedReminderPendingIntent(context, doseTime.doseIndex)
            )
        }
    }

    private fun scheduleAlarmClock(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent,
        context: Context
    ) {
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAtMillis, openAppIntent(context)),
            pendingIntent
        )
    }

    private fun scheduleExactFallback(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            scheduleInexact(alarmManager, triggerAtMillis, pendingIntent)
        } catch (_: RuntimeException) {
            scheduleInexact(alarmManager, triggerAtMillis, pendingIntent)
        }
    }

    private fun scheduleFallback(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        scheduleInexact(alarmManager, triggerAtMillis, pendingIntent)
    }

    private fun scheduleInexact(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    private fun nextTriggerMillis(doseTime: DoseTime): Long {
        val now = Calendar.getInstance()
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, doseTime.hour)
            set(Calendar.MINUTE, doseTime.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }.timeInMillis
    }

    private fun reminderPendingIntent(context: Context, doseIndex: Int): PendingIntent {
        val intent = Intent(context, MedicineReminderReceiver::class.java).apply {
            action = MedicineReminderReceiver.ACTION_REMINDER
            putExtra(MedicineReminderReceiver.EXTRA_DOSE_INDEX, doseIndex)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_BASE + doseIndex,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun missedReminderPendingIntent(context: Context, doseIndex: Int): PendingIntent {
        val intent = Intent(context, MedicineMissedReminderReceiver::class.java).apply {
            action = "com.example.medicinecheck.ACTION_MISSED_REMINDER"
            putExtra(MedicineReminderReceiver.EXTRA_DOSE_INDEX, doseIndex)
        }
        return PendingIntent.getBroadcast(
            context,
            MISSED_REQUEST_CODE_BASE + doseIndex,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_BASE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }
}
