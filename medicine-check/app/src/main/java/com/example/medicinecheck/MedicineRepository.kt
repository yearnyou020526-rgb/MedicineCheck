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

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFormat = SimpleDateFormat("MM-dd", Locale.US)

    fun getMedicineName(context: Context): String {
        return prefs(context).getString(KEY_MEDICINE_NAME, "") ?: ""
    }

    fun setMedicineName(context: Context, name: String) {
        prefs(context).edit().putString(KEY_MEDICINE_NAME, name.trim()).apply()
    }

    fun isTodayChecked(context: Context): Boolean {
        return prefs(context).getString(KEY_CHECKED_DATE, null) == todayKey()
    }

    fun markTodayChecked(context: Context) {
        val today = todayKey()
        val history = getHistory(context).toMutableSet()
        history.add(today)
        prefs(context).edit()
            .putString(KEY_CHECKED_DATE, today)
            .putStringSet(KEY_HISTORY, history)
            .apply()
    }

    fun clearTodayChecked(context: Context) {
        val today = todayKey()
        val history = getHistory(context).toMutableSet()
        history.remove(today)
        prefs(context).edit()
            .remove(KEY_CHECKED_DATE)
            .putStringSet(KEY_HISTORY, history)
            .apply()
    }

    fun getRecentDays(context: Context, count: Int = 30): List<DayRecord> {
        val history = getHistory(context)
        val calendar = Calendar.getInstance()
        return (0 until count).map {
            val date = calendar.time
            val key = dateFormat.format(date)
            val record = DayRecord(
                dateKey = key,
                displayDate = displayDateFormat.format(date),
                checked = history.contains(key)
            )
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            record
        }
    }

    private fun getHistory(context: Context): Set<String> {
        return prefs(context).getStringSet(KEY_HISTORY, emptySet()) ?: emptySet()
    }

    private fun todayKey(): String {
        return dateFormat.format(Date())
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

data class DayRecord(
    val dateKey: String,
    val displayDate: String,
    val checked: Boolean
)
