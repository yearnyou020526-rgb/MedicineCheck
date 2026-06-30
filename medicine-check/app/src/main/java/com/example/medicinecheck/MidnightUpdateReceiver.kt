package com.example.medicinecheck

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build

class MidnightUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MedicineWidgetProvider.updateAllWidgets(context)
    }
}

object MidnightUpdateScheduler {
    private const val ACTION_SCHEDULED_UPDATE =
        "com.example.medicinecheck.ACTION_SCHEDULED_UPDATE"
    private const val REQUEST_CODE_SCHEDULED_UPDATE = 24_000

    fun scheduleNext(context: Context) {
        if (!hasWidgets(context)) return

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = pendingIntent(context)
        val triggerAtMillis = MedicineRepository.nextRefreshTimeMillis(context)

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
            scheduleFallback(alarmManager, triggerAtMillis, pendingIntent)
        } catch (_: RuntimeException) {
            scheduleFallback(alarmManager, triggerAtMillis, pendingIntent)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
    }

    private fun scheduleFallback(
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

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MidnightUpdateReceiver::class.java).apply {
            action = ACTION_SCHEDULED_UPDATE
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_SCHEDULED_UPDATE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun hasWidgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        val widgetIds = manager.getAppWidgetIds(
            ComponentName(context, MedicineWidgetProvider::class.java)
        )
        return widgetIds.isNotEmpty()
    }
}
