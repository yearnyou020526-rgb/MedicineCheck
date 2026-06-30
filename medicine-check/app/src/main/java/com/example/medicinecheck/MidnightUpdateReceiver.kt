package com.example.medicinecheck

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

class MidnightUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MedicineWidgetProvider.updateAllWidgets(context)
    }
}

object MidnightUpdateScheduler {
    private const val ACTION_MIDNIGHT_UPDATE =
        "com.example.medicinecheck.ACTION_MIDNIGHT_UPDATE"
    private const val REQUEST_CODE_MIDNIGHT_UPDATE = 24_000

    fun scheduleNext(context: Context) {
        if (!hasWidgets(context)) return

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = pendingIntent(context)
        val triggerAtMillis = nextMidnightMillis()

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

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MidnightUpdateReceiver::class.java).apply {
            action = ACTION_MIDNIGHT_UPDATE
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_MIDNIGHT_UPDATE,
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

    private fun nextMidnightMillis(): Long {
        return Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
