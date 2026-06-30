package com.example.medicinecheck

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

object WidgetUpdateHelper {
    fun updateAllWidgets(context: Context) {
        MedicineRepository.autoMarkMissedDoses(context)
        MedicineWidgetProvider.updateHomeWidgets(context)
        MedicineCardWidgetProvider.updateCardWidgets(context)
        MidnightUpdateScheduler.scheduleNext(context)
        MedicineReminderScheduler.scheduleAllIfEnabled(context)
    }

    fun hasAnyWidgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        val homeWidgetIds = manager.getAppWidgetIds(
            ComponentName(context, MedicineWidgetProvider::class.java)
        )
        val cardWidgetIds = manager.getAppWidgetIds(
            ComponentName(context, MedicineCardWidgetProvider::class.java)
        )
        return homeWidgetIds.isNotEmpty() || cardWidgetIds.isNotEmpty()
    }
}
