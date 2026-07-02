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
    private const val KEY_MEDICINES_MIGRATED = "medicine_items_migrated"
    private const val KEY_MEDICINE_IDS = "medicine_ids"
    private const val KEY_REMINDERS_ENABLED = "reminders_enabled"
    private const val KEY_MISSED_REMINDER_DELAY = "missed_reminder_delay"
    private const val KEY_AUTO_MISS_LAST_CHECK_DATE = "auto_miss_last_check_date"

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFormat = SimpleDateFormat("MM-dd", Locale.US)

    fun getMedicines(context: Context): List<MedicineItem> {
        migrateMedicineItems(context)
        val ids = getMedicineIds(context)
        return ids.map { id -> loadMedicine(context, id) }
    }

    fun getEnabledMedicines(context: Context): List<MedicineItem> {
        return getMedicines(context).filter { it.enabled }
    }

    fun saveMedicine(context: Context, item: MedicineItem) {
        migrateMedicineItems(context)
        val normalized = item.normalized()
        val ids = getMedicineIds(context).toMutableList()
        if (!ids.contains(normalized.id)) {
            if (ids.size >= MAX_MEDICINES) return
            ids.add(normalized.id)
        }

        prefs(context).edit()
            .putString(KEY_MEDICINE_IDS, ids.joinToString(","))
            .putString(medicineKey(normalized.id, "name"), normalized.name)
            .putString(medicineKey(normalized.id, "dose_value"), normalized.doseValue)
            .putString(medicineKey(normalized.id, "dose_unit"), normalizeDoseUnit(normalized.doseUnit))
            .putString(
                medicineKey(normalized.id, "dose_period"),
                normalizeDosePeriod(normalized.dosePeriod)
            )
            .putInt(medicineKey(normalized.id, "dose_count"), normalized.doseCount.coerceIn(1, 3))
            .putString(medicineKey(normalized.id, "dose_time_1"), normalized.timeFor(1))
            .putString(medicineKey(normalized.id, "dose_time_2"), normalized.timeFor(2))
            .putString(medicineKey(normalized.id, "dose_time_3"), normalized.timeFor(3))
            .putBoolean(medicineKey(normalized.id, "enabled"), normalized.enabled)
            .apply()

        if (normalized.id == DEFAULT_MEDICINE_ID) {
            setLegacyMedicineMirror(context, normalized)
        }
    }

    fun addDefaultMedicine(context: Context): MedicineItem? {
        migrateMedicineItems(context)
        val ids = getMedicineIds(context).toMutableList()
        if (ids.size >= MAX_MEDICINES) return null
        val id = (1..MAX_MEDICINES).map { "med$it" }.firstOrNull { !ids.contains(it) } ?: return null
        val item = MedicineItem(
            id = id,
            name = "",
            doseValue = "",
            doseUnit = DEFAULT_DOSE_UNIT,
            dosePeriod = DEFAULT_DOSE_PERIOD,
            doseCount = 1,
            doseTimes = defaultDoseTimes(1),
            enabled = true
        )
        saveMedicine(context, item)
        return item
    }

    fun deleteMedicine(context: Context, medicineId: String) {
        migrateMedicineItems(context)
        val ids = getMedicineIds(context).filterNot { it == medicineId }
        if (ids.isEmpty()) {
            saveMedicine(context, loadMedicine(context, medicineId).copy(enabled = false))
            return
        }
        prefs(context).edit().putString(KEY_MEDICINE_IDS, ids.joinToString(",")).apply()
    }

    fun getMedicineName(context: Context): String {
        return getMedicines(context).firstOrNull()?.name ?: ""
    }

    fun setMedicineName(context: Context, name: String) {
        val first = getMedicines(context).firstOrNull() ?: defaultMedicine(context)
        saveMedicine(context, first.copy(name = name.trim()))
    }

    fun getDoseValue(context: Context): String {
        return getMedicines(context).firstOrNull()?.doseValue ?: ""
    }

    fun getDoseUnit(context: Context): String {
        return getMedicines(context).firstOrNull()?.doseUnit ?: DEFAULT_DOSE_UNIT
    }

    fun getDosePeriod(context: Context): String {
        return getMedicines(context).firstOrNull()?.dosePeriod ?: DEFAULT_DOSE_PERIOD
    }

    fun setMedicineInfo(
        context: Context,
        medicineName: String,
        doseValue: String,
        doseUnit: String,
        dosePeriod: String
    ) {
        val first = getMedicines(context).firstOrNull() ?: defaultMedicine(context)
        saveMedicine(
            context,
            first.copy(
                name = medicineName.trim(),
                doseValue = doseValue.trim(),
                doseUnit = normalizeDoseUnit(doseUnit),
                dosePeriod = normalizeDosePeriod(dosePeriod)
            )
        )
    }

    fun isReminderEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_REMINDERS_ENABLED, true)
    }

    fun setReminderEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_REMINDERS_ENABLED, enabled).apply()
    }

    fun getMissedReminderDelayMinutes(context: Context): Int {
        return prefs(context).getInt(KEY_MISSED_REMINDER_DELAY, 0).let { delay ->
            if (delay in MISSED_REMINDER_DELAYS) delay else 0
        }
    }

    fun setMissedReminderDelayMinutes(context: Context, delayMinutes: Int) {
        val normalized = if (delayMinutes in MISSED_REMINDER_DELAYS) delayMinutes else 0
        prefs(context).edit().putInt(KEY_MISSED_REMINDER_DELAY, normalized).apply()
    }

    fun getMedicineDisplayText(context: Context): String {
        return getMedicines(context).firstOrNull()?.displayText().orEmpty()
    }

    fun getMedicineShortText(context: Context): String {
        return getCurrentDueMedicines(context).firstOrNull()?.medicine?.name
            ?: getMedicines(context).firstOrNull()?.name.orEmpty()
    }

    fun getWidgetMedicineLines(context: Context): List<String> {
        val due = getCurrentDueMedicines(context)
        return if (due.isNotEmpty()) {
            due.take(MAX_MEDICINES).map { it.medicine.displayText().ifBlank { "药品" } }
        } else {
            emptyList()
        }
    }

    fun getDoseCount(context: Context): Int {
        return getMedicines(context).firstOrNull()?.doseCount ?: 1
    }

    fun setDoseCount(context: Context, count: Int) {
        val first = getMedicines(context).firstOrNull() ?: defaultMedicine(context)
        saveMedicine(
            context,
            first.copy(
                doseCount = count.coerceIn(1, 3),
                doseTimes = normalizeDoseTimes(first.doseTimes, count.coerceIn(1, 3))
            )
        )
    }

    fun getDoseTimes(context: Context): List<DoseTime> {
        val first = getMedicines(context).firstOrNull() ?: defaultMedicine(context)
        return first.doseTimesForCount().mapIndexed { index, time ->
            DoseTime(index + 1, time)
        }
    }

    fun getDoseTimes(medicine: MedicineItem): List<DoseTime> {
        return medicine.doseTimesForCount().mapIndexed { index, time ->
            DoseTime(index + 1, time)
        }
    }

    fun getSortedDoseTimes(context: Context): List<DoseTime> {
        return getDoseTimes(context).sortedWith(
            compareBy<DoseTime> { it.minutesOfDay }.thenBy { it.doseIndex }
        )
    }

    fun setDoseTime(context: Context, doseIndex: Int, time: String) {
        if (doseIndex !in 1..3 || !isValidTime(time)) return
        val first = getMedicines(context).firstOrNull() ?: defaultMedicine(context)
        val times = normalizeDoseTimes(first.doseTimes, first.doseCount).toMutableList()
        while (times.size < 3) times.add(defaultDoseTime(3, times.size + 1))
        times[doseIndex - 1] = time
        saveMedicine(context, first.copy(doseTimes = times))
    }

    fun autoMarkMissedDoses(context: Context): Boolean {
        migrateMedicineItems(context)
        migrateLegacyHistory(context)

        val now = Calendar.getInstance()
        val todayKey = dateFormat.format(now.time)
        val yesterday = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val yesterdayKey = dateFormat.format(yesterday.time)
        val lastCheckedKey = prefs(context).getString(KEY_AUTO_MISS_LAST_CHECK_DATE, null)
        val startCalendar = parseDateKey(lastCheckedKey)?.takeIf {
            !dateFormat.format(it.time).let { key -> key > yesterdayKey }
        } ?: yesterday
        val earliestCalendar = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -30)
        }
        if (startCalendar.before(earliestCalendar)) {
            startCalendar.time = earliestCalendar.time
        }

        val changed = autoMarkPastDays(context, startCalendar, yesterday) or
            autoMarkTodayExpiredDoses(context, now)

        prefs(context).edit().putString(KEY_AUTO_MISS_LAST_CHECK_DATE, todayKey).apply()
        return changed
    }

    fun getCurrentDueMedicines(context: Context): List<MedicineTask> {
        migrateMedicineItems(context)
        migrateLegacyHistory(context)
        val now = Calendar.getInstance()
        val today = dateFormat.format(now.time)
        return getEnabledMedicines(context).mapNotNull { medicine ->
            val target = getTargetForMedicine(context, medicine, now, today)
            target.takeIf { it.isDue && it.status == RecordStatus.NONE }
        }.sortedWith(compareBy<MedicineTask> { it.minutesOfDay }.thenBy { it.medicine.id })
    }

    fun hasCurrentDueMedicines(context: Context): Boolean {
        return getCurrentDueMedicines(context).isNotEmpty()
    }

    fun markCurrentDueMedicinesChecked(context: Context) {
        getCurrentDueMedicines(context).forEach { task ->
            markDoseChecked(context, task.dateKey, task.medicine.id, task.doseIndex)
        }
    }

    fun markCurrentDueMedicinesMissed(context: Context) {
        getCurrentDueMedicines(context).forEach { task ->
            markDoseMissed(context, task.dateKey, task.medicine.id, task.doseIndex)
        }
    }

    fun getCurrentTarget(context: Context): DoseTarget {
        autoMarkMissedDoses(context)
        val now = Calendar.getInstance()
        return getTargetFor(context, now)
    }

    fun getCurrentDoseIndex(context: Context): Int {
        return getCurrentTarget(context).doseIndex
    }

    fun isCurrentDoseChecked(context: Context): Boolean {
        return getCurrentDueMedicines(context).isEmpty()
    }

    fun getTargetFor(context: Context, calendar: Calendar): DoseTarget {
        migrateMedicineItems(context)
        migrateLegacyHistory(context)
        val today = dateFormat.format(calendar.time)
        val due = getEnabledMedicines(context).mapNotNull { medicine ->
            val task = getTargetForMedicine(context, medicine, calendar, today)
            task.takeIf { it.isDue && it.status == RecordStatus.NONE }
        }.sortedWith(compareBy<MedicineTask> { it.minutesOfDay }.thenBy { it.medicine.id })

        val task = due.firstOrNull()
            ?: getEnabledMedicines(context).firstOrNull()?.let {
                getTargetForMedicine(context, it, calendar, today)
            }

        return if (task == null) {
            DoseTarget(today, DEFAULT_MEDICINE_ID, 1, "08:00", RecordStatus.DONE, checked = true)
        } else if (due.isEmpty()) {
            DoseTarget(
                dateKey = task.dateKey,
                medicineId = task.medicine.id,
                doseIndex = task.doseIndex,
                time = task.time,
                status = RecordStatus.DONE,
                checked = true,
                medicineText = task.medicine.displayText(),
                isDue = false
            )
        } else {
            DoseTarget(
                dateKey = task.dateKey,
                medicineId = task.medicine.id,
                doseIndex = task.doseIndex,
                time = task.time,
                status = task.status,
                checked = false,
                medicineText = task.medicine.displayText(),
                isDue = true
            )
        }
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
        if (target.isDue) {
            markDoseChecked(context, target.dateKey, target.medicineId, target.doseIndex)
        }
    }

    fun clearCurrentTargetChecked(context: Context) {
        val target = getCurrentTarget(context)
        clearDoseRecord(context, target.dateKey, target.medicineId, target.doseIndex)
    }

    fun markDoseChecked(context: Context, dateKey: String, doseIndex: Int) {
        val medicineId = getMedicines(context).firstOrNull()?.id ?: DEFAULT_MEDICINE_ID
        markDoseChecked(context, dateKey, medicineId, doseIndex)
    }

    fun markDoseChecked(context: Context, dateKey: String, medicineId: String, doseIndex: Int) {
        migrateMedicineItems(context)
        migrateLegacyHistory(context)
        val history = getDoseHistory(context).toMutableSet()
        val missedHistory = getMissedHistory(context).toMutableSet()
        val key = doseRecordKey(dateKey, medicineId, doseIndex)
        history.add(key)
        missedHistory.remove(key)
        if (medicineId == DEFAULT_MEDICINE_ID) {
            history.add(legacyDoseRecordKey(dateKey, doseIndex))
            missedHistory.remove(legacyDoseRecordKey(dateKey, doseIndex))
        }
        prefs(context).edit()
            .putStringSet(KEY_DOSE_HISTORY, history)
            .putStringSet(KEY_MISSED_HISTORY, missedHistory)
            .apply()
    }

    fun clearDoseChecked(context: Context, dateKey: String, doseIndex: Int) {
        clearDoseRecord(context, dateKey, doseIndex)
    }

    fun markDoseMissed(context: Context, dateKey: String, doseIndex: Int) {
        val medicineId = getMedicines(context).firstOrNull()?.id ?: DEFAULT_MEDICINE_ID
        markDoseMissed(context, dateKey, medicineId, doseIndex)
    }

    fun markDoseMissed(context: Context, dateKey: String, medicineId: String, doseIndex: Int) {
        migrateMedicineItems(context)
        migrateLegacyHistory(context)
        val history = getDoseHistory(context).toMutableSet()
        val missedHistory = getMissedHistory(context).toMutableSet()
        val key = doseRecordKey(dateKey, medicineId, doseIndex)
        history.remove(key)
        missedHistory.add(key)
        if (medicineId == DEFAULT_MEDICINE_ID) {
            history.remove(legacyDoseRecordKey(dateKey, doseIndex))
            missedHistory.add(legacyDoseRecordKey(dateKey, doseIndex))
        }
        prefs(context).edit()
            .putStringSet(KEY_DOSE_HISTORY, history)
            .putStringSet(KEY_MISSED_HISTORY, missedHistory)
            .apply()
    }

    fun markCurrentTargetMissed(context: Context) {
        val target = getCurrentTarget(context)
        if (target.isDue) {
            markDoseMissed(context, target.dateKey, target.medicineId, target.doseIndex)
        }
    }

    fun clearDoseRecord(context: Context, dateKey: String, doseIndex: Int) {
        val medicineId = getMedicines(context).firstOrNull()?.id ?: DEFAULT_MEDICINE_ID
        clearDoseRecord(context, dateKey, medicineId, doseIndex)
    }

    fun clearDoseRecord(context: Context, dateKey: String, medicineId: String, doseIndex: Int) {
        migrateMedicineItems(context)
        migrateLegacyHistory(context)
        val history = getDoseHistory(context).toMutableSet()
        val missedHistory = getMissedHistory(context).toMutableSet()
        val key = doseRecordKey(dateKey, medicineId, doseIndex)
        history.remove(key)
        missedHistory.remove(key)
        if (medicineId == DEFAULT_MEDICINE_ID) {
            history.remove(legacyDoseRecordKey(dateKey, doseIndex))
            missedHistory.remove(legacyDoseRecordKey(dateKey, doseIndex))
        }
        prefs(context).edit()
            .putStringSet(KEY_DOSE_HISTORY, history)
            .putStringSet(KEY_MISSED_HISTORY, missedHistory)
            .apply()
    }

    fun isDoseChecked(context: Context, dateKey: String, doseIndex: Int): Boolean {
        return getDoseRecordStatus(context, dateKey, doseIndex) == RecordStatus.DONE
    }

    fun isDoseMissed(context: Context, dateKey: String, doseIndex: Int): Boolean {
        return getDoseRecordStatus(context, dateKey, doseIndex) == RecordStatus.MISSED
    }

    fun getDoseRecordStatus(context: Context, dateKey: String, doseIndex: Int): RecordStatus {
        val medicineId = getMedicines(context).firstOrNull()?.id ?: DEFAULT_MEDICINE_ID
        return getDoseRecordStatus(context, dateKey, medicineId, doseIndex)
    }

    fun getDoseRecordStatus(
        context: Context,
        dateKey: String,
        medicineId: String,
        doseIndex: Int
    ): RecordStatus {
        migrateMedicineItems(context)
        migrateLegacyHistory(context)
        val key = doseRecordKey(dateKey, medicineId, doseIndex)
        val legacyKey = legacyDoseRecordKey(dateKey, doseIndex)
        val history = getDoseHistory(context)
        val missedHistory = getMissedHistory(context)
        return when {
            history.contains(key) -> RecordStatus.DONE
            medicineId == DEFAULT_MEDICINE_ID && history.contains(legacyKey) -> RecordStatus.DONE
            missedHistory.contains(key) -> RecordStatus.MISSED
            medicineId == DEFAULT_MEDICINE_ID && missedHistory.contains(legacyKey) -> RecordStatus.MISSED
            else -> RecordStatus.NONE
        }
    }

    fun isDoseExpired(context: Context, dateKey: String, doseIndex: Int): Boolean {
        val medicine = getMedicines(context).firstOrNull() ?: defaultMedicine(context)
        return isDoseExpired(context, dateKey, medicine, doseIndex)
    }

    fun isDoseExpired(
        context: Context,
        dateKey: String,
        medicine: MedicineItem,
        doseIndex: Int
    ): Boolean {
        val today = todayKey()
        if (dateKey > today) return false
        if (dateKey < today) return true

        val now = Calendar.getInstance()
        return getExpiredDoseIndexesForToday(medicine, now).contains(doseIndex)
    }

    fun getDoseProgress(context: Context, dateKey: String): DoseProgress {
        migrateMedicineItems(context)
        migrateLegacyHistory(context)
        val medicines = getEnabledMedicines(context)
        var total = 0
        var completed = 0
        var missed = 0
        medicines.forEach { medicine ->
            for (doseIndex in 1..medicine.doseCount) {
                total++
                when (getDoseRecordStatus(context, dateKey, medicine.id, doseIndex)) {
                    RecordStatus.DONE -> completed++
                    RecordStatus.MISSED -> missed++
                    RecordStatus.NONE -> Unit
                }
            }
        }
        return DoseProgress(dateKey, completed, total, missed)
    }

    fun getTodayProgress(context: Context): DoseProgress {
        return getDoseProgress(context, todayKey())
    }

    fun getTodayDateKey(): String {
        return todayKey()
    }

    fun getRecentDays(context: Context, count: Int = 30): List<DayRecord> {
        migrateMedicineItems(context)
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
            currentTarget = target,
            currentDueMedicines = getCurrentDueMedicines(context)
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
            if (progress.totalDoses > 0 && progress.isFullyComplete && !progress.hasMissed) {
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
            if (progress.totalDoses > 0) {
                monthTaskDays++
                if (progress.isFullyComplete && !progress.hasMissed) monthCompleteDays++
            }
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

        getEnabledMedicines(context).flatMap { getDoseTimes(it) }.forEach { doseTime ->
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

    fun getReminderTimes(context: Context): List<DoseTime> {
        return getEnabledMedicines(context)
            .flatMap { medicine ->
                getDoseTimes(medicine).map { doseTime -> doseTime.minutesOfDay to doseTime }
            }
            .distinctBy { it.first }
            .map { it.second }
            .sortedBy { it.minutesOfDay }
    }

    fun getCurrentReminderTasks(context: Context): List<MedicineTask> {
        autoMarkMissedDoses(context)
        return getCurrentDueMedicines(context)
    }

    fun getAllDoseRowsForDate(context: Context, dateKey: String): List<MedicineDoseRow> {
        return getEnabledMedicines(context).flatMap { medicine ->
            getDoseTimes(medicine).map { doseTime ->
                MedicineDoseRow(
                    dateKey = dateKey,
                    medicine = medicine,
                    doseIndex = doseTime.doseIndex,
                    time = doseTime.time,
                    status = getDoseRecordStatus(context, dateKey, medicine.id, doseTime.doseIndex)
                )
            }
        }
    }

    private fun getTargetForMedicine(
        context: Context,
        medicine: MedicineItem,
        calendar: Calendar,
        dateKey: String
    ): MedicineTask {
        val sortedTimes = getDoseTimes(medicine).sortedWith(
            compareBy<DoseTime> { it.minutesOfDay }.thenBy { it.doseIndex }
        )
        val nowMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val firstTime = sortedTimes.firstOrNull()
        val target = sortedTimes.lastOrNull { nowMinutes >= it.minutesOfDay }
            ?: firstTime
            ?: DoseTime(1, "08:00")
        val due = firstTime != null && nowMinutes >= firstTime.minutesOfDay
        val status = getDoseRecordStatus(context, dateKey, medicine.id, target.doseIndex)
        return MedicineTask(
            dateKey = dateKey,
            medicine = medicine,
            doseIndex = target.doseIndex,
            time = target.time,
            status = status,
            isDue = due,
            minutesOfDay = target.minutesOfDay
        )
    }

    private fun autoMarkPastDays(
        context: Context,
        startCalendar: Calendar,
        endCalendar: Calendar
    ): Boolean {
        if (startCalendar.after(endCalendar)) return false

        var changed = false
        val cursor = (startCalendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = (endCalendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val medicines = getEnabledMedicines(context)
        while (!cursor.after(end)) {
            val dateKey = dateFormat.format(cursor.time)
            medicines.forEach { medicine ->
                getDoseTimes(medicine).forEach { doseTime ->
                    if (
                        getDoseRecordStatus(context, dateKey, medicine.id, doseTime.doseIndex) ==
                        RecordStatus.NONE
                    ) {
                        markDoseMissed(context, dateKey, medicine.id, doseTime.doseIndex)
                        changed = true
                    }
                }
            }
            cursor.add(Calendar.DAY_OF_YEAR, 1)
        }
        return changed
    }

    private fun autoMarkTodayExpiredDoses(context: Context, now: Calendar): Boolean {
        var changed = false
        val today = dateFormat.format(now.time)
        getEnabledMedicines(context).forEach { medicine ->
            getExpiredDoseIndexesForToday(medicine, now).forEach { doseIndex ->
                if (getDoseRecordStatus(context, today, medicine.id, doseIndex) == RecordStatus.NONE) {
                    markDoseMissed(context, today, medicine.id, doseIndex)
                    changed = true
                }
            }
        }
        return changed
    }

    private fun getExpiredDoseIndexesForToday(medicine: MedicineItem, calendar: Calendar): Set<Int> {
        val sortedTimes = getDoseTimes(medicine).sortedWith(
            compareBy<DoseTime> { it.minutesOfDay }.thenBy { it.doseIndex }
        )
        if (sortedTimes.size <= 1) return emptySet()

        val nowMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val expired = mutableSetOf<Int>()
        for (index in 0 until sortedTimes.lastIndex) {
            val nextDoseTime = sortedTimes[index + 1]
            if (nowMinutes >= nextDoseTime.minutesOfDay) {
                expired.add(sortedTimes[index].doseIndex)
            }
        }
        return expired
    }

    private fun migrateMedicineItems(context: Context) {
        val sharedPreferences = prefs(context)
        if (sharedPreferences.getBoolean(KEY_MEDICINES_MIGRATED, false)) {
            if (getMedicineIds(context).isEmpty()) {
                sharedPreferences.edit()
                    .putString(KEY_MEDICINE_IDS, DEFAULT_MEDICINE_ID)
                    .putString(medicineKey(DEFAULT_MEDICINE_ID, "name"), "")
                    .putString(medicineKey(DEFAULT_MEDICINE_ID, "dose_value"), "")
                    .putString(medicineKey(DEFAULT_MEDICINE_ID, "dose_unit"), DEFAULT_DOSE_UNIT)
                    .putString(medicineKey(DEFAULT_MEDICINE_ID, "dose_period"), DEFAULT_DOSE_PERIOD)
                    .putInt(medicineKey(DEFAULT_MEDICINE_ID, "dose_count"), 1)
                    .putString(medicineKey(DEFAULT_MEDICINE_ID, "dose_time_1"), "08:00")
                    .putString(medicineKey(DEFAULT_MEDICINE_ID, "dose_time_2"), "20:00")
                    .putString(medicineKey(DEFAULT_MEDICINE_ID, "dose_time_3"), "20:00")
                    .putBoolean(medicineKey(DEFAULT_MEDICINE_ID, "enabled"), true)
                    .apply()
            }
            return
        }

        val migrated = MedicineItem(
            id = DEFAULT_MEDICINE_ID,
            name = sharedPreferences.getString(KEY_MEDICINE_NAME, "") ?: "",
            doseValue = sharedPreferences.getString(KEY_DOSE_VALUE, "") ?: "",
            doseUnit = sharedPreferences.getString(KEY_DOSE_UNIT, DEFAULT_DOSE_UNIT)
                ?: DEFAULT_DOSE_UNIT,
            dosePeriod = sharedPreferences.getString(KEY_DOSE_PERIOD, DEFAULT_DOSE_PERIOD)
                ?: DEFAULT_DOSE_PERIOD,
            doseCount = sharedPreferences.getInt(KEY_DOSE_COUNT, 1).coerceIn(1, 3),
            doseTimes = (1..3).map { index ->
                sharedPreferences.getString(doseTimeKey(index), null)
                    ?: defaultDoseTime(sharedPreferences.getInt(KEY_DOSE_COUNT, 1), index)
            },
            enabled = true
        ).normalized()

        sharedPreferences.edit()
            .putString(KEY_MEDICINE_IDS, DEFAULT_MEDICINE_ID)
            .putString(medicineKey(DEFAULT_MEDICINE_ID, "name"), migrated.name)
            .putString(medicineKey(DEFAULT_MEDICINE_ID, "dose_value"), migrated.doseValue)
            .putString(medicineKey(DEFAULT_MEDICINE_ID, "dose_unit"), migrated.doseUnit)
            .putString(medicineKey(DEFAULT_MEDICINE_ID, "dose_period"), migrated.dosePeriod)
            .putInt(medicineKey(DEFAULT_MEDICINE_ID, "dose_count"), migrated.doseCount)
            .putString(medicineKey(DEFAULT_MEDICINE_ID, "dose_time_1"), migrated.timeFor(1))
            .putString(medicineKey(DEFAULT_MEDICINE_ID, "dose_time_2"), migrated.timeFor(2))
            .putString(medicineKey(DEFAULT_MEDICINE_ID, "dose_time_3"), migrated.timeFor(3))
            .putBoolean(medicineKey(DEFAULT_MEDICINE_ID, "enabled"), true)
            .putBoolean(KEY_MEDICINES_MIGRATED, true)
            .apply()

        migrateLegacyHistory(context)
    }

    private fun migrateLegacyHistory(context: Context) {
        val sharedPreferences = prefs(context)
        if (sharedPreferences.getBoolean(KEY_LEGACY_MIGRATED, false)) return

        val doseHistory = getDoseHistory(context).toMutableSet()
        val legacyHistory = sharedPreferences.getStringSet(KEY_HISTORY, emptySet()) ?: emptySet()
        legacyHistory.forEach { dateKey ->
            if (dateKey.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                doseHistory.add(doseRecordKey(dateKey, DEFAULT_MEDICINE_ID, 1))
                doseHistory.add(legacyDoseRecordKey(dateKey, 1))
            }
        }

        val checkedDate = sharedPreferences.getString(KEY_CHECKED_DATE, null)
        if (checkedDate != null && checkedDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            doseHistory.add(doseRecordKey(checkedDate, DEFAULT_MEDICINE_ID, 1))
            doseHistory.add(legacyDoseRecordKey(checkedDate, 1))
        }

        sharedPreferences.edit()
            .putStringSet(KEY_DOSE_HISTORY, doseHistory)
            .putBoolean(KEY_LEGACY_MIGRATED, true)
            .apply()
    }

    private fun getMedicineIds(context: Context): List<String> {
        return (prefs(context).getString(KEY_MEDICINE_IDS, "") ?: "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(MAX_MEDICINES)
    }

    private fun loadMedicine(context: Context, id: String): MedicineItem {
        val sharedPreferences = prefs(context)
        val count = sharedPreferences.getInt(medicineKey(id, "dose_count"), 1).coerceIn(1, 3)
        return MedicineItem(
            id = id,
            name = sharedPreferences.getString(medicineKey(id, "name"), "") ?: "",
            doseValue = sharedPreferences.getString(medicineKey(id, "dose_value"), "") ?: "",
            doseUnit = sharedPreferences.getString(medicineKey(id, "dose_unit"), DEFAULT_DOSE_UNIT)
                ?: DEFAULT_DOSE_UNIT,
            dosePeriod = sharedPreferences.getString(
                medicineKey(id, "dose_period"),
                DEFAULT_DOSE_PERIOD
            ) ?: DEFAULT_DOSE_PERIOD,
            doseCount = count,
            doseTimes = (1..3).map { index ->
                sharedPreferences.getString(medicineKey(id, "dose_time_$index"), null)
                    ?: defaultDoseTime(count, index)
            },
            enabled = sharedPreferences.getBoolean(medicineKey(id, "enabled"), true)
        ).normalized()
    }

    private fun defaultMedicine(context: Context): MedicineItem {
        return MedicineItem(
            id = DEFAULT_MEDICINE_ID,
            name = prefs(context).getString(KEY_MEDICINE_NAME, "") ?: "",
            doseValue = prefs(context).getString(KEY_DOSE_VALUE, "") ?: "",
            doseUnit = DEFAULT_DOSE_UNIT,
            dosePeriod = DEFAULT_DOSE_PERIOD,
            doseCount = 1,
            doseTimes = defaultDoseTimes(1),
            enabled = true
        )
    }

    private fun setLegacyMedicineMirror(context: Context, item: MedicineItem) {
        val editor = prefs(context).edit()
            .putString(KEY_MEDICINE_NAME, item.name)
            .putString(KEY_DOSE_VALUE, item.doseValue)
            .putString(KEY_DOSE_UNIT, item.doseUnit)
            .putString(KEY_DOSE_PERIOD, item.dosePeriod)
            .putInt(KEY_DOSE_COUNT, item.doseCount)
        (1..3).forEach { index ->
            editor.putString(doseTimeKey(index), item.timeFor(index))
        }
        editor.apply()
    }

    private fun defaultDoseTimes(count: Int): List<String> {
        return (1..3).map { defaultDoseTime(count, it) }
    }

    private fun normalizeDoseTimes(times: List<String>, count: Int): List<String> {
        return (1..3).map { index ->
            times.getOrNull(index - 1)
                ?.takeIf { isValidTime(it) }
                ?: defaultDoseTime(count, index)
        }
    }

    private fun defaultDoseTime(count: Int, doseIndex: Int): String {
        return when (count.coerceIn(1, 3)) {
            1 -> listOf("08:00", "20:00", "20:00")
            2 -> listOf("08:00", "20:00", "20:00")
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

    private fun medicineKey(medicineId: String, field: String): String {
        return "medicine_${medicineId}_$field"
    }

    private fun doseRecordKey(dateKey: String, medicineId: String, doseIndex: Int): String {
        return "$dateKey#$medicineId#$doseIndex"
    }

    private fun legacyDoseRecordKey(dateKey: String, doseIndex: Int): String {
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

    private fun parseDateKey(dateKey: String?): Calendar? {
        if (dateKey == null) return null
        return try {
            val date = dateFormat.parse(dateKey) ?: return null
            Calendar.getInstance().apply {
                time = date
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun normalizeDoseUnit(unit: String): String {
        return if (unit in DOSE_UNITS) unit else DEFAULT_DOSE_UNIT
    }

    private fun normalizeDosePeriod(period: String): String {
        return if (period in DOSE_PERIODS) period else DEFAULT_DOSE_PERIOD
    }

    val DOSE_UNITS = listOf(
        "mg", "g", "μg", "mcg",
        "ml", "mL", "L", "滴",
        "IU", "U", "万单位",
        "片", "粒", "颗", "丸", "枚",
        "袋", "包", "条",
        "支", "瓶", "盒", "贴",
        "喷", "揿", "吸",
        "勺", "格", "块"
    )
    val DOSE_PERIODS = listOf("/天", "/次", "/周", "/月")
    val MISSED_REMINDER_DELAYS = listOf(0, 30, 60)

    private const val DEFAULT_MEDICINE_ID = "med1"
    private const val DEFAULT_DOSE_UNIT = "mg"
    private const val DEFAULT_DOSE_PERIOD = "/天"
    const val MAX_MEDICINES = 3
}

enum class RecordStatus {
    NONE,
    DONE,
    MISSED
}

data class MedicineItem(
    val id: String,
    val name: String,
    val doseValue: String,
    val doseUnit: String,
    val dosePeriod: String,
    val doseCount: Int,
    val doseTimes: List<String>,
    val enabled: Boolean
) {
    fun displayText(): String {
        val cleanName = name.trim()
        val cleanDose = doseValue.trim()
        if (cleanName.isBlank()) return ""
        if (cleanDose.isBlank()) return cleanName
        return "$cleanName $cleanDose$doseUnit$dosePeriod"
    }

    fun doseTimesForCount(): List<String> = (1..doseCount.coerceIn(1, 3)).map { timeFor(it) }

    fun timeFor(index: Int): String {
        return doseTimes.getOrNull(index - 1)?.takeIf { it.matches(Regex("\\d{2}:\\d{2}")) }
            ?: when (doseCount.coerceIn(1, 3)) {
                1 -> listOf("08:00", "20:00", "20:00")
                2 -> listOf("08:00", "20:00", "20:00")
                else -> listOf("08:00", "14:00", "20:00")
            }.getOrElse(index - 1) { "08:00" }
    }

    fun normalized(): MedicineItem {
        val count = doseCount.coerceIn(1, 3)
        val normalizedTimes = (1..3).map { timeFor(it) }
        return copy(
            id = id.ifBlank { "med1" },
            name = name.trim(),
            doseValue = doseValue.trim(),
            doseCount = count,
            doseTimes = normalizedTimes
        )
    }
}

data class DoseTime(
    val doseIndex: Int,
    val time: String
) {
    val hour: Int = time.substringBefore(":").toIntOrNull() ?: 8
    val minute: Int = time.substringAfter(":").toIntOrNull() ?: 0
    val minutesOfDay: Int = hour * 60 + minute
}

data class MedicineTask(
    val dateKey: String,
    val medicine: MedicineItem,
    val doseIndex: Int,
    val time: String,
    val status: RecordStatus,
    val isDue: Boolean,
    val minutesOfDay: Int
)

data class MedicineDoseRow(
    val dateKey: String,
    val medicine: MedicineItem,
    val doseIndex: Int,
    val time: String,
    val status: RecordStatus
)

data class DoseTarget(
    val dateKey: String,
    val medicineId: String,
    val doseIndex: Int,
    val time: String,
    val status: RecordStatus,
    val checked: Boolean,
    val medicineText: String = "",
    val isDue: Boolean = false
) {
    constructor(
        dateKey: String,
        doseIndex: Int,
        time: String,
        status: RecordStatus,
        checked: Boolean
    ) : this(dateKey, "med1", doseIndex, time, status, checked, "", false)

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
    val isFullyComplete: Boolean = totalDoses > 0 && completedDoses >= totalDoses
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
    val checked: Boolean = totalDoses > 0 && completedDoses >= totalDoses && missedDoses == 0
    val partiallyChecked: Boolean = completedDoses in 1 until totalDoses
    val hasMissed: Boolean = missedDoses > 0
}

data class TodaySummary(
    val totalDoses: Int,
    val completedDoses: Int,
    val missedDoses: Int,
    val pendingDoses: Int,
    val currentTarget: DoseTarget,
    val currentDueMedicines: List<MedicineTask> = emptyList()
)

data class StatsSummary(
    val consecutiveDays: Int,
    val monthCompletionRate: Int,
    val recent7MissedDoses: Int,
    val monthMissedDoses: Int
)
