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
            WidgetUpdateHelper.updateAllWidgets(context)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        WidgetUpdateHelper.updateAllWidgets(context)
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
            WidgetUpdateHelper.updateAllWidgets(context)
        }

        fun updateCardWidgets(context: Context) {
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
            val dueTasks = MedicineRepository.getCurrentDueMedicines(context)
            val stageTasks = MedicineRepository.getCurrentStageTasks(context)
            val hasDue = dueTasks.isNotEmpty()
            val views = RemoteViews(context.packageName, R.layout.widget_medicine_card)
            val medicineLines = stageTasks
                .map { it.medicine.displayText().ifBlank { context.getString(R.string.medicine_not_set) } }
                .take(MedicineRepository.MAX_MEDICINES)

            views.setInt(
                R.id.card_root,
                "setBackgroundResource",
                if (hasDue) R.drawable.widget_card_bg_unchecked
                else R.drawable.widget_card_bg_checked
            )
            views.setInt(R.id.card_pill, "setImageAlpha", if (hasDue) 255 else 86)
            views.setViewVisibility(R.id.card_check, if (hasDue) View.GONE else View.VISIBLE)
            if (hasDue) {
                setMedicineLine(views, R.id.card_medicine_text, medicineLines.getOrNull(0))
                setMedicineLine(views, R.id.card_medicine_text_2, medicineLines.getOrNull(1))
                setMedicineLine(views, R.id.card_medicine_text_3, medicineLines.getOrNull(2))
            } else {
                setMedicineLine(views, R.id.card_medicine_text, null)
                setMedicineLine(views, R.id.card_medicine_text_2, null)
                setMedicineLine(views, R.id.card_medicine_text_3, null)
            }
            views.setViewVisibility(R.id.card_status_text, View.GONE)
            views.setTextViewText(R.id.card_status_text, "")
            val target = MedicineRepository.getCurrentTarget(context)
            if (!hasDue || MedicineRepository.getDoseCount(context) == 1) {
                views.setViewVisibility(R.id.card_dose_text, View.GONE)
            } else {
                views.setViewVisibility(R.id.card_dose_text, View.VISIBLE)
                views.setTextViewText(R.id.card_dose_text, "第${target.doseIndex}次")
            }
            views.setContentDescription(
                R.id.card_root,
                if (!hasDue) context.getString(R.string.widget_checked)
                else context.getString(R.string.widget_unchecked)
            )
            views.setOnClickPendingIntent(
                R.id.card_root,
                if (hasDue) markCurrentTargetIntent(context, appWidgetId)
                else openAppIntent(context, appWidgetId)
            )

            manager.updateAppWidget(appWidgetId, views)
        }

        private fun setMedicineLine(views: RemoteViews, viewId: Int, text: String?) {
            if (text.isNullOrBlank()) {
                views.setViewVisibility(viewId, View.GONE)
                views.setTextViewText(viewId, "")
            } else {
                views.setViewVisibility(viewId, View.VISIBLE)
                views.setTextViewText(viewId, text)
            }
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

        private fun openAppIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                70_000 + appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
