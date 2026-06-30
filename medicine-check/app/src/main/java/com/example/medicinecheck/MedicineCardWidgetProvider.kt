package com.example.medicinecheck

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

class MedicineCardWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == MedicineWidgetProvider.ACTION_MARK_TODAY) {
            MedicineRepository.markCurrentTargetChecked(context)
            MedicineWidgetProvider.updateAllWidgets(context)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateCardWidget(context, appWidgetManager, appWidgetId)
        }
        MedicineWidgetProvider.updateAllWidgets(context)
        MidnightUpdateScheduler.scheduleNext(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        MidnightUpdateScheduler.scheduleNext(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        MidnightUpdateScheduler.scheduleNext(context)
    }

    companion object {
        fun updateAllCardWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val widgetIds = manager.getAppWidgetIds(
                ComponentName(context, MedicineCardWidgetProvider::class.java)
            )
            widgetIds.forEach { widgetId ->
                updateCardWidget(context, manager, widgetId)
            }
        }

        private fun updateCardWidget(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val target = MedicineRepository.getCurrentTarget(context)
            val views = RemoteViews(context.packageName, R.layout.widget_medicine_card)
            val medicineText = MedicineRepository.getMedicineDisplayText(context)
                .ifBlank { context.getString(R.string.medicine_not_set) }

            views.setInt(
                R.id.card_root,
                "setBackgroundResource",
                if (target.checked) R.drawable.widget_card_bg_checked
                else R.drawable.widget_card_bg_unchecked
            )
            views.setInt(R.id.card_pill, "setImageAlpha", if (target.checked) 86 else 255)
            views.setViewVisibility(R.id.card_check, if (target.checked) View.VISIBLE else View.GONE)
            views.setTextViewText(R.id.card_medicine_text, medicineText)
            views.setTextViewText(
                R.id.card_status_text,
                if (target.checked) context.getString(R.string.status_checked_short)
                else context.getString(R.string.widget_waiting_short)
            )
            views.setTextViewText(R.id.card_dose_text, "第${target.doseIndex}次")
            views.setContentDescription(
                R.id.card_root,
                if (target.checked) context.getString(R.string.widget_checked)
                else context.getString(R.string.widget_unchecked)
            )
            views.setOnClickPendingIntent(
                R.id.card_root,
                if (target.checked) undoConfirmIntent(context, appWidgetId)
                else markCurrentTargetIntent(context, appWidgetId)
            )

            manager.updateAppWidget(appWidgetId, views)
        }

        private fun markCurrentTargetIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, MedicineCardWidgetProvider::class.java).apply {
                action = MedicineWidgetProvider.ACTION_MARK_TODAY
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            return PendingIntent.getBroadcast(
                context,
                20_000 + appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun undoConfirmIntent(context: Context, appWidgetId: Int): PendingIntent {
            val target = MedicineRepository.getCurrentTarget(context)
            val intent = Intent(context, ConfirmUndoActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(MedicineWidgetProvider.EXTRA_DATE_KEY, target.dateKey)
                putExtra(MedicineWidgetProvider.EXTRA_DOSE_INDEX, target.doseIndex)
            }
            return PendingIntent.getActivity(
                context,
                30_000 + appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
