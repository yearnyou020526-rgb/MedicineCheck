package com.example.medicinecheck

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object MedicineRepository {
    private const val PREFS_NAME = "medicine_check"
    private const val KEY_MEDICINE_NAME = "medicine_name"
    private const val KEY_DOSE_VALUE = "dose_value"
    private const val KEY_DOSE_UNIT = "dose_unit"
    private const val KEY_DOSE_PERIOD = "dose_period"

    private const val KEY_CHECKED_DATE = "checked_date"
    private const val KEY_HISTORY = "history_dates"

    private const val KEY_DOSE_COUNT = "dose_count"
    private const val KEY_DOSE_TIME_1 = "dose_time_1"
    private const val KEY_DOSE_TIME_2 = "dose_time_2"
    private const val KEY_DOSE_TIME_3 = "dose_time_3"
    private const val KEY_DOSE_HISTORY = "dose_history"
    private const val KEY_MISSED_HISTORY = "missed_history"
    private const val KEY_LEGACY_MIGRATED = "legacy_dose_history_migrated"
    private const val KEY_REMINDERS_ENABLED = "reminders_enabled"

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFormat = SimpleDateFormat("MM-dd", Locale.US)

    fun getMedicineName(context: Context): String {
        return prefs(context).getString(KEY_MEDICINE_NAME, "") ?: ""
    }

    fun setMedicineName(context: Context, name: String) {
        prefs(context).edit().putString(KEY_MEDICINE_NAME, name.trim()).apply()
    }

    fun getDoseValue(context: Context): String {
        return prefs(context).getString(KEY_DOSE_VALUE, "") ?: ""
    }

    fun getDoseUnit(context: Context): String {
        return prefs(context).getString(KEY_DOSE_UNIT, DEFAULT_DOSE_UNIT) ?: DEFAULT_DOSE_UNIT
    }

    fun getDosePeriod(context: Context): String {
        return prefs(context).getString(KEY_DOSE_PERIOD, DEFAULT_DOSE_PERIOD) ?: DEFAULT_DOSE_PERIOD
    }

    fun setMedicineInfo(
        context: Context,
        medicineName: String,
        doseValue: String,
        doseUnit: String,
        dosePeriod: String
    ) {
        prefs(context).edit()
            .putString(KEY_MEDICINE_NAME, medicineName.trim())
            .putString(KEY_DOSE_VALUE, doseValue.trim())
            .putString(KEY_DOSE_UNIT, normalizeDoseUnit(doseUnit))
            .putString(KEY_DOSE_PERIOD, normalizeDosePeriod(dosePeriod))
            .apply()
    }

    fun isReminderEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_REMINDERS_ENABLED, true)
    }

    fun setReminderEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_REMINDERS_ENABLED, enabled).apply()
    }

    fun getMedicineDisplayText(context: Context): String {
        val name = getMedicineName(context).trim()
        val doseValue = getDoseValue(context).trim()
        if (name.isBlank()) return ""
        if (doseValue.isBlank()) return name
        return "$name $doseValue${getDoseUnit(context)}${getDosePeriod(context)}"
    }

    fun getMedicineShortText(context: Context): String {
        val name = getMedicineName(context).trim()
        return name.ifBlank { getMedicineDisplayText(context) }
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

    fun getCurrentDoseIndex(context: Context): Int {
        return getCurrentTarget(context).doseIndex
    }

    fun isCurrentDoseChecked(context: Context): Boolean {
        return getCurrentTarget(context).checked
    }

    fun getTargetFor(context: Context, calendar: Calendar): DoseTarget {
        migrateLegacyHistory(context)
        val today = dateFormat.format(calendar.time)
        val target = getTargetDoseTimeFor(context, calendar)
        val status = getDoseRecordStatus(context, today, target.doseIndex)
        return DoseTarget(
            dateKey = today,
            doseIndex = target.doseIndex,
            time = target.time,
            status = status,
            checked = status == RecordStatus.DONE
        )
    }

    fun isTodayChecked(context: Context): Boolean {
        return isCurrentDoseChecked(context)
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
        val missedHistory = getMissedHistory(context).toMutableSet()
        history.add(doseRecordKey(dateKey, doseIndex))
        missedHistory.remove(doseRecordKey(dateKey, doseIndex))
        prefs(context).edit()
            .putStringSet(KEY_DOSE_HISTORY, history)
            .putStringSet(KEY_MISSED_HISTORY, missedHistory)
            .apply()
    }

    fun clearDoseChecked(context: Context, dateKey: String, doseIndex: Int) {
        clearDoseRecord(context, dateKey, doseIndex)
    }

    fun markDoseMissed(context: Context, dateKey: String, doseIndex: Int) {
        migrateLegacyHistory(context)
        val history = getDoseHistory(context).toMutableSet()
        val missedHistory = getMissedHistory(context).toMutableSet()
        history.remove(doseRecordKey(dateKey, doseIndex))
        missedHistory.add(doseRecordKey(dateKey, doseIndex))
        prefs(context).edit()
            .putStringSet(KEY_DOSE_HISTORY, history)
            .putStringSet(KEY_MISSED_HISTORY, missedHistory)
            .apply()
    }

    fun markCurrentTargetMissed(context: Context) {
        val target = getCurrentTarget(context)
        markDoseMissed(context, target.dateKey, target.doseIndex)
    }

    fun clearDoseRecord(context: Context, dateKey: String, doseIndex: Int) {
        migrateLegacyHistory(context)
        val history = getDoseHistory(context).toMutableSet()
        val missedHistory = getMissedHistory(context).toMutableSet()
        val key = doseRecordKey(dateKey, doseIndex)
        history.remove(key)
        missedHistory.remove(key)
        prefs(context).edit()
            .putStringSet(KEY_DOSE_HISTORY, history)
            .putStringSet(KEY_MISSED_HISTORY, missedHistory)
            .apply()
    }

    fun isDoseChecked(context: Context, dateKey: String, doseIndex: Int): Boolean {
        migrateLegacyHistory(context)
        return getDoseHistory(context).contains(doseRecordKey(dateKey, doseIndex))
    }

    fun isDoseMissed(context: Context, dateKey: String, doseIndex: Int): Boolean {
        migrateLegacyHistory(context)
        return getMissedHistory(context).contains(doseRecordKey(dateKey, doseIndex))
    }

    fun getDoseRecordStatus(context: Context, dateKey: String, doseIndex: Int): RecordStatus {
        migrateLegacyHistory(context)
        val key = doseRecordKey(dateKey, doseIndex)
        return when {
            getDoseHistory(context).contains(key) -> RecordStatus.DONE
            getMissedHistory(context).contains(key) -> RecordStatus.MISSED
            else -> RecordStatus.NONE
        }
    }

    fun getDoseProgress(context: Context, dateKey: String): DoseProgress {
        migrateLegacyHistory(context)
        val count = getDoseCount(context)
        val history = getDoseHistory(context)
        val missedHistory = getMissedHistory(context)
        val completed = (1..count).count { doseIndex ->
            history.contains(doseRecordKey(dateKey, doseIndex))
        }
        val missed = (1..count).count { doseIndex ->
            missedHistory.contains(doseRecordKey(dateKey, doseIndex))
        }
        return DoseProgress(
            dateKey = dateKey,
            completedDoses = completed,
            totalDoses = count,
            missedDoses = missed
        )
    }

    fun getTodayProgress(context: Context): DoseProgress {
        return getDoseProgress(context, todayKey())
    }

    fun getTodayDateKey(): String {
        return todayKey()
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
                totalDoses = progress.totalDoses,
                missedDoses = progress.missedDoses
            )
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            record
        }
    }

    fun getTodaySummary(context: Context): TodaySummary {
        val progress = getTodayProgress(context)
        val target = getCurrentTarget(context)
        return TodaySummary(
            totalDoses = progress.totalDoses,
            completedDoses = progress.completedDoses,
            missedDoses = progress.missedDoses,
            pendingDoses = progress.pendingDoses,
            currentTarget = target
        )
    }

    fun getStats(context: Context): StatsSummary {
        val today = Calendar.getInstance()
        val monthStart = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        var consecutiveDays = 0
        val streakCalendar = today.clone() as Calendar
        while (true) {
            val progress = getDoseProgress(context, dateFormat.format(streakCalendar.time))
            if (progress.isFullyComplete) {
                consecutiveDays++
                streakCalendar.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }

        var monthTaskDays = 0
        var monthCompleteDays = 0
        var monthMissedDoses = 0
        val monthCalendar = monthStart.clone() as Calendar
        while (!monthCalendar.after(today)) {
            val progress = getDoseProgress(context, dateFormat.format(monthCalendar.time))
            monthTaskDays++
            if (progress.isFullyComplete) monthCompleteDays++
            monthMissedDoses += progress.missedDoses
            monthCalendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        var recent7MissedDoses = 0
        val recentCalendar = today.clone() as Calendar
        repeat(7) {
            recent7MissedDoses += getDoseProgress(
                context,
                dateFormat.format(recentCalendar.time)
            ).missedDoses
            recentCalendar.add(Calendar.DAY_OF_YEAR, -1)
        }

        val completionRate = if (monthTaskDays == 0) {
            0
        } else {
            (monthCompleteDays * 100) / monthTaskDays
        }

        return StatsSummary(
            consecutiveDays = consecutiveDays,
            monthCompletionRate = completionRate,
            recent7MissedDoses = recent7MissedDoses,
            monthMissedDoses = monthMissedDoses
        )
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

    private fun getTargetDoseTimeFor(context: Context, calendar: Calendar): DoseTime {
        val sortedTimes = getSortedDoseTimes(context)
        if (sortedTimes.size == 1) return sortedTimes.first()

        val nowMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        return sortedTimes.lastOrNull { nowMinutes >= it.minutesOfDay } ?: sortedTimes.first()
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

    private fun getMissedHistory(context: Context): Set<String> {
        return prefs(context).getStringSet(KEY_MISSED_HISTORY, emptySet()) ?: emptySet()
    }

    private fun todayKey(): String {
        return dateFormat.format(Date())
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun normalizeDoseUnit(unit: String): String {
        return if (unit in DOSE_UNITS) unit else DEFAULT_DOSE_UNIT
    }

    private fun normalizeDosePeriod(period: String): String {
        return if (period in DOSE_PERIODS) period else DEFAULT_DOSE_PERIOD
    }

    val DOSE_UNITS = listOf("mg", "g", "ml", "片", "粒")
    val DOSE_PERIODS = listOf("/天", "/次")

    private const val DEFAULT_DOSE_UNIT = "mg"
    private const val DEFAULT_DOSE_PERIOD = "/天"
}

enum class RecordStatus {
    NONE,
    DONE,
    MISSED
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
    val status: RecordStatus,
    val checked: Boolean
) {
    val missed: Boolean = status == RecordStatus.MISSED
}

data class DoseProgress(
    val dateKey: String,
    val completedDoses: Int,
    val totalDoses: Int,
    val missedDoses: Int
) {
    val isNoneComplete: Boolean = completedDoses == 0
    val isPartiallyComplete: Boolean = completedDoses in 1 until totalDoses
    val isFullyComplete: Boolean = completedDoses >= totalDoses
    val pendingDoses: Int = (totalDoses - completedDoses - missedDoses).coerceAtLeast(0)
    val hasMissed: Boolean = missedDoses > 0
}

data class DayRecord(
    val dateKey: String,
    val displayDate: String,
    val completedDoses: Int,
    val totalDoses: Int,
    val missedDoses: Int
) {
    val checked: Boolean = completedDoses >= totalDoses
    val partiallyChecked: Boolean = completedDoses in 1 until totalDoses
    val hasMissed: Boolean = missedDoses > 0
}

data class TodaySummary(
    val totalDoses: Int,
    val completedDoses: Int,
    val missedDoses: Int,
    val pendingDoses: Int,
    val currentTarget: DoseTarget
)

data class StatsSummary(
    val consecutiveDays: Int,
    val monthCompletionRate: Int,
    val recent7MissedDoses: Int,
    val monthMissedDoses: Int
)
