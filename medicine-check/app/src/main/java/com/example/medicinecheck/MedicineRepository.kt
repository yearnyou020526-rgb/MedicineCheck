package com.example.medicinecheck

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object MedicineRepository {
    private const val PREFS_NAME = "medicine_check"
    private const val KEY_MEDICINE_NAME = "medicine_name"

    private const val KEY_CHECKED_DATE = "checked_date"
    private const val KEY_HISTORY = "history_dates"

    private const val KEY_DOSE_COUNT = "dose_count"
    private const val KEY_DOSE_TIME_1 = "dose_time_1"
    private const val KEY_DOSE_TIME_2 = "dose_time_2"
    private const val KEY_DOSE_TIME_3 = "dose_time_3"
    private const val KEY_DOSE_HISTORY = "dose_history"
    private const val KEY_LEGACY_MIGRATED = "legacy_dose_history_migrated"

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFormat = SimpleDateFormat("MM-dd", Locale.US)

    fun getMedicineName(context: Context): String {
        return prefs(context).getString(KEY_MEDICINE_NAME, "") ?: ""
    }

    fun setMedicineName(context: Context, name: String) {
        prefs(context).edit().putString(KEY_MEDICINE_NAME, name.trim()).apply()
    }

    fun getDoseCount(context: Context): Int {
        return prefs(context).getInt(KEY_DOSE_COUNT, 1).coerceIn(1, 3)
    }

    fun setDoseCount(context: Context, count: Int) {
        prefs(context).edit().putInt(KEY_DOSE_COUNT, count.coerceIn(1, 3)).apply()
    }

    fun getDoseTimes(context: Context): List<DoseTime> {
        val count = getDoseCount(context)
        return (1..count).map { doseIndex ->
            DoseTime(
                doseIndex = doseIndex,
                time = getDoseTime(context, doseIndex, count)
            )
        }
    }

    fun getSortedDoseTimes(context: Context): List<DoseTime> {
        return getDoseTimes(context).sortedWith(
            compareBy<DoseTime> { it.minutesOfDay }.thenBy { it.doseIndex }
        )
    }

    fun setDoseTime(context: Context, doseIndex: Int, time: String) {
        if (doseIndex !in 1..3 || !isValidTime(time)) return
        prefs(context).edit().putString(doseTimeKey(doseIndex), time).apply()
    }

    fun getCurrentTarget(context: Context): DoseTarget {
        val now = Calendar.getInstance()
        return getTargetFor(context, now)
    }

    fun getTargetFor(context: Context, calendar: Calendar): DoseTarget {
        migrateLegacyHistory(context)
        val today = dateFormat.format(calendar.time)
        val nowMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val sortedTimes = getSortedDoseTimes(context)
        val target = sortedTimes.lastOrNull { nowMinutes >= it.minutesOfDay } ?: sortedTimes.first()
        return DoseTarget(
            dateKey = today,
            doseIndex = target.doseIndex,
            time = target.time,
            checked = isDoseChecked(context, today, target.doseIndex)
        )
    }

    fun isTodayChecked(context: Context): Boolean {
        return getCurrentTarget(context).checked
    }

    fun markTodayChecked(context: Context) {
        markCurrentTargetChecked(context)
    }

    fun clearTodayChecked(context: Context) {
        clearCurrentTargetChecked(context)
    }

    fun markCurrentTargetChecked(context: Context) {
        val target = getCurrentTarget(context)
        markDoseChecked(context, target.dateKey, target.doseIndex)
    }

    fun clearCurrentTargetChecked(context: Context) {
        val target = getCurrentTarget(context)
        clearDoseChecked(context, target.dateKey, target.doseIndex)
    }

    fun markDoseChecked(context: Context, dateKey: String, doseIndex: Int) {
        migrateLegacyHistory(context)
        val history = getDoseHistory(context).toMutableSet()
        history.add(doseRecordKey(dateKey, doseIndex))
        prefs(context).edit().putStringSet(KEY_DOSE_HISTORY, history).apply()
    }

    fun clearDoseChecked(context: Context, dateKey: String, doseIndex: Int) {
        migrateLegacyHistory(context)
        val history = getDoseHistory(context).toMutableSet()
        history.remove(doseRecordKey(dateKey, doseIndex))
        prefs(context).edit().putStringSet(KEY_DOSE_HISTORY, history).apply()
    }

    fun isDoseChecked(context: Context, dateKey: String, doseIndex: Int): Boolean {
        migrateLegacyHistory(context)
        return getDoseHistory(context).contains(doseRecordKey(dateKey, doseIndex))
    }

    fun getDoseProgress(context: Context, dateKey: String): DoseProgress {
        migrateLegacyHistory(context)
        val count = getDoseCount(context)
        val history = getDoseHistory(context)
        val completed = (1..count).count { doseIndex ->
            history.contains(doseRecordKey(dateKey, doseIndex))
        }
        return DoseProgress(
            dateKey = dateKey,
            completedDoses = completed,
            totalDoses = count
        )
    }

    fun getTodayProgress(context: Context): DoseProgress {
        return getDoseProgress(context, todayKey())
    }

    fun getRecentDays(context: Context, count: Int = 30): List<DayRecord> {
        migrateLegacyHistory(context)
        val calendar = Calendar.getInstance()
        return (0 until count).map {
            val date = calendar.time
            val key = dateFormat.format(date)
            val progress = getDoseProgress(context, key)
            val record = DayRecord(
                dateKey = key,
                displayDate = displayDateFormat.format(date),
                completedDoses = progress.completedDoses,
                totalDoses = progress.totalDoses
            )
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            record
        }
    }

    fun nextRefreshTimeMillis(context: Context): Long {
        val now = Calendar.getInstance()
        val nowMillis = now.timeInMillis
        val candidates = mutableListOf<Long>()

        val midnight = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        candidates.add(midnight.timeInMillis)

        getDoseTimes(context).forEach { doseTime ->
            val doseCalendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, doseTime.hour)
                set(Calendar.MINUTE, doseTime.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (doseCalendar.timeInMillis <= nowMillis) {
                doseCalendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            candidates.add(doseCalendar.timeInMillis)
        }

        return candidates.minOrNull() ?: midnight.timeInMillis
    }

    private fun migrateLegacyHistory(context: Context) {
        val sharedPreferences = prefs(context)
        if (sharedPreferences.getBoolean(KEY_LEGACY_MIGRATED, false)) return

        val doseHistory = getDoseHistory(context).toMutableSet()
        val legacyHistory = sharedPreferences.getStringSet(KEY_HISTORY, emptySet()) ?: emptySet()
        legacyHistory.forEach { dateKey ->
            if (dateKey.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                doseHistory.add(doseRecordKey(dateKey, 1))
            }
        }

        val checkedDate = sharedPreferences.getString(KEY_CHECKED_DATE, null)
        if (checkedDate != null && checkedDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            doseHistory.add(doseRecordKey(checkedDate, 1))
        }

        sharedPreferences.edit()
            .putStringSet(KEY_DOSE_HISTORY, doseHistory)
            .putBoolean(KEY_LEGACY_MIGRATED, true)
            .apply()
    }

    private fun getDoseTime(context: Context, doseIndex: Int, count: Int): String {
        return prefs(context).getString(doseTimeKey(doseIndex), null)
            ?: defaultDoseTime(count, doseIndex)
    }

    private fun defaultDoseTime(count: Int, doseIndex: Int): String {
        return when (count.coerceIn(1, 3)) {
            1 -> listOf("08:00")
            2 -> listOf("08:00", "20:00")
            else -> listOf("08:00", "14:00", "20:00")
        }.getOrElse(doseIndex - 1) { "08:00" }
    }

    private fun isValidTime(time: String): Boolean {
        val parts = time.split(":")
        if (parts.size != 2) return false
        val hour = parts[0].toIntOrNull() ?: return false
        val minute = parts[1].toIntOrNull() ?: return false
        return hour in 0..23 && minute in 0..59
    }

    private fun doseTimeKey(doseIndex: Int): String {
        return when (doseIndex) {
            1 -> KEY_DOSE_TIME_1
            2 -> KEY_DOSE_TIME_2
            else -> KEY_DOSE_TIME_3
        }
    }

    private fun doseRecordKey(dateKey: String, doseIndex: Int): String {
        return "$dateKey#$doseIndex"
    }

    private fun getDoseHistory(context: Context): Set<String> {
        return prefs(context).getStringSet(KEY_DOSE_HISTORY, emptySet()) ?: emptySet()
    }

    private fun todayKey(): String {
        return dateFormat.format(Date())
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

data class DoseTime(
    val doseIndex: Int,
    val time: String
) {
    val hour: Int = time.substringBefore(":").toIntOrNull() ?: 8
    val minute: Int = time.substringAfter(":").toIntOrNull() ?: 0
    val minutesOfDay: Int = hour * 60 + minute
}

data class DoseTarget(
    val dateKey: String,
    val doseIndex: Int,
    val time: String,
    val checked: Boolean
)

data class DoseProgress(
    val dateKey: String,
    val completedDoses: Int,
    val totalDoses: Int
) {
    val isNoneComplete: Boolean = completedDoses == 0
    val isPartiallyComplete: Boolean = completedDoses in 1 until totalDoses
    val isFullyComplete: Boolean = completedDoses >= totalDoses
}

data class DayRecord(
    val dateKey: String,
    val displayDate: String,
    val completedDoses: Int,
    val totalDoses: Int
) {
    val checked: Boolean = completedDoses >= totalDoses
    val partiallyChecked: Boolean = completedDoses in 1 until totalDoses
}
