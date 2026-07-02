package com.example.medicinecheck

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.TypedValue
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
            val displayLines = adaptiveMedicineLines(medicineLines)
            val medicineCount = medicineLines.size.coerceIn(1, MedicineRepository.MAX_MEDICINES)

            views.setInt(
                R.id.card_root,
                "setBackgroundResource",
                if (hasDue) R.drawable.widget_card_bg_unchecked
                else R.drawable.widget_card_bg_checked
            )
            configureArtwork(views, medicineCount, hasDue)
            configureTextSizes(views, medicineLines.size)
            setMedicineLine(views, R.id.card_medicine_text, displayLines.getOrNull(0))
            setMedicineLine(views, R.id.card_medicine_text_2, displayLines.getOrNull(1))
            setMedicineLine(views, R.id.card_medicine_text_3, displayLines.getOrNull(2))
            views.setViewVisibility(R.id.card_status_text, View.GONE)
            views.setTextViewText(R.id.card_status_text, "")
            views.setViewVisibility(R.id.card_dose_text, View.GONE)
            views.setTextViewText(R.id.card_dose_text, "")
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

        private fun adaptiveMedicineLines(lines: List<String>): List<String> {
            if (lines.size != 1) return lines
            val single = lines.firstOrNull()?.trim().orEmpty()
            if (single.isBlank()) return emptyList()
            val splitIndex = single.indexOf(' ')
            if (splitIndex <= 0 || splitIndex >= single.lastIndex) return listOf(single)
            return listOf(single.substring(0, splitIndex), single.substring(splitIndex + 1))
        }

        private fun configureArtwork(
            views: RemoteViews,
            medicineCount: Int,
            hasDue: Boolean
        ) {
            val large = medicineCount <= 1
            val medium = medicineCount == 2
            val small = medicineCount >= 3
            views.setViewVisibility(R.id.card_art_large, if (large) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.card_art_medium, if (medium) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.card_art_small, if (small) View.VISIBLE else View.GONE)

            val alpha = if (hasDue) 255 else 86
            views.setInt(R.id.card_pill_large, "setImageAlpha", alpha)
            views.setInt(R.id.card_pill_medium, "setImageAlpha", alpha)
            views.setInt(R.id.card_pill_small, "setImageAlpha", alpha)
            views.setViewVisibility(
                R.id.card_check_large,
                if (!hasDue && large) View.VISIBLE else View.GONE
            )
            views.setViewVisibility(
                R.id.card_check_medium,
                if (!hasDue && medium) View.VISIBLE else View.GONE
            )
            views.setViewVisibility(
                R.id.card_check_small,
                if (!hasDue && small) View.VISIBLE else View.GONE
            )
        }

        private fun configureTextSizes(views: RemoteViews, medicineCount: Int) {
            val firstSize: Float
            val otherSize: Float
            when (medicineCount) {
                0 -> {
                    firstSize = 13f
                    otherSize = 13f
                }
                1 -> {
                    firstSize = 16f
                    otherSize = 14f
                }
                2 -> {
                    firstSize = 14f
                    otherSize = 14f
                }
                else -> {
                    firstSize = 12.5f
                    otherSize = 12.5f
                }
            }
            views.setTextViewTextSize(R.id.card_medicine_text, TypedValue.COMPLEX_UNIT_SP, firstSize)
            views.setTextViewTextSize(R.id.card_medicine_text_2, TypedValue.COMPLEX_UNIT_SP, otherSize)
            views.setTextViewTextSize(R.id.card_medicine_text_3, TypedValue.COMPLEX_UNIT_SP, otherSize)
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
