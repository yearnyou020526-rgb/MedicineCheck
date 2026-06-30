package com.example.medicinecheck

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class MedicineWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_MARK_TODAY) {
            MedicineRepository.markCurrentTargetChecked(context)
            updateAllWidgets(context)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
        MidnightUpdateScheduler.scheduleNext(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        MidnightUpdateScheduler.scheduleNext(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        MidnightUpdateScheduler.cancel(context)
    }

    companion object {
        const val ACTION_MARK_TODAY = "com.example.medicinecheck.ACTION_MARK_TODAY"
        const val EXTRA_DATE_KEY = "com.example.medicinecheck.EXTRA_DATE_KEY"
        const val EXTRA_DOSE_INDEX = "com.example.medicinecheck.EXTRA_DOSE_INDEX"

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val widgetIds = manager.getAppWidgetIds(
                ComponentName(context, MedicineWidgetProvider::class.java)
            )
            widgetIds.forEach { widgetId ->
                updateWidget(context, manager, widgetId)
            }
            MidnightUpdateScheduler.scheduleNext(context)
        }

        private fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val target = MedicineRepository.getCurrentTarget(context)
            val views = RemoteViews(context.packageName, R.layout.widget_medicine)

            views.setImageViewResource(
                R.id.widget_state_image,
                if (target.checked) R.drawable.widget_checked_green else R.drawable.widget_unchecked_red
            )
            views.setContentDescription(
                R.id.widget_root,
                if (target.checked) context.getString(R.string.widget_checked)
                else context.getString(R.string.widget_unchecked)
            )
            views.setOnClickPendingIntent(
                R.id.widget_root,
                if (target.checked) undoConfirmIntent(context, appWidgetId) else markTodayIntent(context, appWidgetId)
            )

            manager.updateAppWidget(appWidgetId, views)
        }

        private fun markTodayIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, MedicineWidgetProvider::class.java).apply {
                action = ACTION_MARK_TODAY
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            return PendingIntent.getBroadcast(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun undoConfirmIntent(context: Context, appWidgetId: Int): PendingIntent {
            val target = MedicineRepository.getCurrentTarget(context)
            val intent = Intent(context, ConfirmUndoActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(EXTRA_DATE_KEY, target.dateKey)
                putExtra(EXTRA_DOSE_INDEX, target.doseIndex)
            }
            return PendingIntent.getActivity(
                context,
                10_000 + appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
