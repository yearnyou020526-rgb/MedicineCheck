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
    private const val KEY_MEDICINES_MIGRATED = "slot_medicine_items_migrated"
    private const val KEY_MEDICINE_IDS = "medicine_ids"
    private const val KEY_REMINDERS_ENABLED = "reminders_enabled"
    private const val KEY_MISSED_REMINDER_DELAY = "missed_reminder_delay"
    private const val KEY_AUTO_MISS_LAST_CHECK_DATE = "auto_miss_last_check_date"

    private const val DEFAULT_MEDICINE_ID = "med1"
    private const val DEFAULT_DOSE_UNIT = "mg"
    private const val DEFAULT_DOSE_PERIOD = "/天"
    const val MAX_MEDICINES = 3

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFormat = SimpleDateFormat("MM-dd", Locale.US)

    fun getMedicines(context: Context): List<MedicineItem> {
        migrateMedicineItems(context)
        return getMedicineIds(context).map { loadMedicine(context, it) }
    }

    fun getEnabledMedicines(context: Context): List<MedicineItem> {
        return getMedicines(context).filter { it.enabled }
    }

    fun saveMedicine(context: Context, item: MedicineItem): Boolean {
        migrateMedicineItems(context)
        val normalized = item.normalized()
        val ids = getMedicineIds(context).toMutableList()
        if (!ids.contains(normalized.id)) {
            if (ids.size >= MAX_MEDICINES) return false
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
            .putBoolean(medicineKey(normalized.id, "enabled"), normalized.enabled)
            .apply()

        if (normalized.id == DEFAULT_MEDICINE_ID) {
            setLegacyMedicineMirror(context, normalized)
        }
        return true
    }

    fun createMedicine(
        context: Context,
        name: String,
        doseValue: String,
        doseUnit: String,
        dosePeriod: String
    ): MedicineItem? {
        val ids = getMedicineIds(context)
        if (ids.size >= MAX_MEDICINES) return null
        val id = (1..MAX_MEDICINES).map { "med$it" }.firstOrNull { !ids.contains(it) }
            ?: return null
        val item = MedicineItem(
            id = id,
            name = name,
            doseValue = doseValue,
            doseUnit = doseUnit,
            dosePeriod = dosePeriod,
            enabled = true
        )
        return if (saveMedicine(context, item)) item.normalized() else null
    }

    fun deleteMedicine(context: Context, medicineId: String) {
        migrateMedicineItems(context)
        val ids = getMedicineIds(context).filterNot { it == medicineId }
        prefs(context).edit().putString(KEY_MEDICINE_IDS, ids.joinToString(",")).apply()
        (1..3).forEach { slotIndex ->
            val updated = getSlotMedicineIds(context, slotIndex).filterNot { it == medicineId }
            setSlotMedicineIds(context, slotIndex, updated)
        }
    }

    fun getMedicineName(context: Context): String {
        return getMedicines(context).firstOrNull()?.name.orEmpty()
    }

    fun setMedicineName(context: Context, name: String) {
        val first = getMedicines(context).firstOrNull() ?: return
        saveMedicine(context, first.copy(name = name))
    }

    fun getDoseValue(context: Context): String {
        return getMedicines(context).firstOrNull()?.doseValue.orEmpty()
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
        val first = getMedicines(context).firstOrNull()
        if (first == null) {
            createMedicine(context, medicineName, doseValue, doseUnit, dosePeriod)
        } else {
            saveMedicine(
                context,
                first.copy(
                    name = medicineName,
                    doseValue = doseValue,
                    doseUnit = doseUnit,
                    dosePeriod = dosePeriod
                )
            )
        }
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
        return getCurrentSlotMedicines(context).firstOrNull()?.name
            ?: getMedicines(context).firstOrNull()?.name.orEmpty()
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
            DoseTime(doseIndex = doseIndex, time = getDoseTime(context, doseIndex))
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

    fun getSlotMedicineIds(context: Context, slotIndex: Int): List<String> {
        migrateMedicineItems(context)
        if (slotIndex !in 1..3) return emptyList()
        val validIds = getMedicineIds(context).toSet()
        return (prefs(context).getString(slotMedicineKey(slotIndex), "") ?: "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() && validIds.contains(it) }
            .distinct()
            .take(MAX_MEDICINES)
    }

    fun setSlotMedicineIds(context: Context, slotIndex: Int, medicineIds: List<String>) {
        if (slotIndex !in 1..3) return
        val validIds = getMedicineIds(context).toSet()
        val normalized = medicineIds.filter { validIds.contains(it) }.distinct().take(MAX_MEDICINES)
        prefs(context).edit()
            .putString(slotMedicineKey(slotIndex), normalized.joinToString(","))
            .apply()
    }

    fun getSlotMedicines(context: Context, slotIndex: Int): List<MedicineItem> {
        val medicinesById = getMedicines(context).associateBy { it.id }
        return getSlotMedicineIds(context, slotIndex)
            .mapNotNull { medicinesById[it] }
            .filter { it.enabled }
            .take(MAX_MEDICINES)
    }

    fun getCurrentSlotMedicines(context: Context): List<MedicineItem> {
        return getSlotMedicines(context, getCurrentDoseIndex(context))
    }

    fun getCurrentDueMedicines(context: Context): List<MedicineTask> {
        autoMarkMissedDoses(context)
        val target = getCurrentTarget(context)
        return getSlotTasks(context, target.dateKey, target.doseIndex)
            .filter { it.status == RecordStatus.NONE }
    }

    fun getCurrentStageTasks(context: Context): List<MedicineTask> {
        autoMarkMissedDoses(context)
        val target = getCurrentTarget(context)
        return getSlotTasks(context, target.dateKey, target.doseIndex)
    }

    fun hasCurrentDueMedicines(context: Context): Boolean {
        return getCurrentDueMedicines(context).isNotEmpty()
    }

    fun autoMarkMissedDoses(context: Context): Boolean {
        migrateMedicineItems(context)
        migrateLegacyHistory(context)

        val now = Calendar.getInstance()
        val todayKey = dateFormat.format(now.time)
        val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        val yesterdayKey = dateFormat.format(yesterday.time)
        val lastCheckedKey = prefs(context).getString(KEY_AUTO_MISS_LAST_CHECK_DATE, null)
        val startCalendar = parseDateKey(lastCheckedKey)?.takeIf {
            !dateFormat.format(it.time).let { key -> key > yesterdayKey }
        } ?: yesterday
        val earliestCalendar = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -30) }
        if (startCalendar.before(earliestCalendar)) startCalendar.time = earliestCalendar.time

        val changed = autoMarkPastDays(context, startCalendar, yesterday) or
            autoMarkTodayExpiredDoses(context, now)

        prefs(context).edit().putString(KEY_AUTO_MISS_LAST_CHECK_DATE, todayKey).apply()
        return changed
    }

    fun getCurrentTarget(context: Context): DoseTarget {
        autoMarkMissedDoses(context)
        return getTargetFor(context, Calendar.getInstance())
    }

    fun getCurrentDoseIndex(context: Context): Int {
        return getTargetFor(context, Calendar.getInstance()).doseIndex
    }

    fun isCurrentDoseChecked(context: Context): Boolean {
        return getCurrentDueMedicines(context).isEmpty()
    }

    fun getTargetFor(context: Context, calendar: Calendar): DoseTarget {
        migrateMedicineItems(context)
        migrateLegacyHistory(context)
        val today = dateFormat.format(calendar.time)
        val target = getTargetDoseTimeFor(context, calendar)
        val tasks = getSlotTasks(context, today, target.doseIndex)
        val status = when {
            tasks.isEmpty() -> RecordStatus.DONE
            tasks.any { it.status == RecordStatus.NONE } -> RecordStatus.NONE
            tasks.all { it.status == RecordStatus.DONE } -> RecordStatus.DONE
            else -> RecordStatus.MISSED
        }
        return DoseTarget(
            dateKey = today,
            doseIndex = target.doseIndex,
            time = target.time,
            status = status,
            checked = status == RecordStatus.DONE || tasks.isEmpty()
        )
    }

    fun isTodayChecked(context: Context): Boolean = isCurrentDoseChecked(context)

    fun markTodayChecked(context: Context) = markCurrentTargetChecked(context)

    fun clearTodayChecked(context: Context) = clearCurrentTargetChecked(context)

    fun markCurrentTargetChecked(context: Context) {
        val target = getCurrentTarget(context)
        getSlotTasks(context, target.dateKey, target.doseIndex).forEach { task ->
            markDoseChecked(context, task.dateKey, task.doseIndex, task.medicine.id)
        }
    }

    fun clearCurrentTargetChecked(context: Context) {
        val target = getCurrentTarget(context)
        getSlotTasks(context, target.dateKey, target.doseIndex).forEach { task ->
            clearDoseRecord(context, task.dateKey, task.doseIndex, task.medicine.id)
        }
    }

    fun markCurrentTargetMissed(context: Context) {
        val target = getCurrentTarget(context)
        getSlotTasks(context, target.dateKey, target.doseIndex).forEach { task ->
            markDoseMissed(context, task.dateKey, task.doseIndex, task.medicine.id)
        }
    }

    fun markDoseChecked(context: Context, dateKey: String, doseIndex: Int) {
        getSlotTasks(context, dateKey, doseIndex).forEach { task ->
            markDoseChecked(context, dateKey, doseIndex, task.medicine.id)
        }
    }

    fun markDoseChecked(context: Context, dateKey: String, doseIndex: Int, medicineId: String) {
        migrateLegacyHistory(context)
        val history = getDoseHistory(context).toMutableSet()
        val missedHistory = getMissedHistory(context).toMutableSet()
        val key = doseRecordKey(dateKey, doseIndex, medicineId)
        history.add(key)
        missedHistory.remove(key)
        prefs(context).edit()
            .putStringSet(KEY_DOSE_HISTORY, history)
            .putStringSet(KEY_MISSED_HISTORY, missedHistory)
            .apply()
    }

    fun clearDoseChecked(context: Context, dateKey: String, doseIndex: Int) {
        clearDoseRecord(context, dateKey, doseIndex)
    }

    fun markDoseMissed(context: Context, dateKey: String, doseIndex: Int) {
        getSlotTasks(context, dateKey, doseIndex).forEach { task ->
            markDoseMissed(context, dateKey, doseIndex, task.medicine.id)
        }
    }

    fun markDoseMissed(context: Context, dateKey: String, doseIndex: Int, medicineId: String) {
        migrateLegacyHistory(context)
        val history = getDoseHistory(context).toMutableSet()
        val missedHistory = getMissedHistory(context).toMutableSet()
        val key = doseRecordKey(dateKey, doseIndex, medicineId)
        history.remove(key)
        missedHistory.add(key)
        prefs(context).edit()
            .putStringSet(KEY_DOSE_HISTORY, history)
            .putStringSet(KEY_MISSED_HISTORY, missedHistory)
            .apply()
    }

    fun clearDoseRecord(context: Context, dateKey: String, doseIndex: Int) {
        getSlotTasks(context, dateKey, doseIndex).forEach { task ->
            clearDoseRecord(context, dateKey, doseIndex, task.medicine.id)
        }
    }

    fun clearDoseRecord(context: Context, dateKey: String, doseIndex: Int, medicineId: String) {
        migrateLegacyHistory(context)
        val history = getDoseHistory(context).toMutableSet()
        val missedHistory = getMissedHistory(context).toMutableSet()
        val key = doseRecordKey(dateKey, doseIndex, medicineId)
        history.remove(key)
        missedHistory.remove(key)
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
        val tasks = getSlotTasks(context, dateKey, doseIndex)
        return when {
            tasks.isEmpty() -> RecordStatus.DONE
            tasks.any { it.status == RecordStatus.NONE } -> RecordStatus.NONE
            tasks.all { it.status == RecordStatus.DONE } -> RecordStatus.DONE
            else -> RecordStatus.MISSED
        }
    }

    fun getDoseRecordStatus(
        context: Context,
        dateKey: String,
        doseIndex: Int,
        medicineId: String
    ): RecordStatus {
        migrateLegacyHistory(context)
        val key = doseRecordKey(dateKey, doseIndex, medicineId)
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
        val today = todayKey()
        if (dateKey > today) return false
        if (dateKey < today) return true
        return getExpiredDoseIndexesForToday(context, Calendar.getInstance()).contains(doseIndex)
    }

    fun getDoseProgress(context: Context, dateKey: String): DoseProgress {
        migrateMedicineItems(context)
        migrateLegacyHistory(context)
        val rows = getAllDoseRowsForDate(context, dateKey)
        val completed = rows.count { it.status == RecordStatus.DONE }
        val missed = rows.count { it.status == RecordStatus.MISSED }
        return DoseProgress(dateKey, completed, rows.size, missed)
    }

    fun getTodayProgress(context: Context): DoseProgress = getDoseProgress(context, todayKey())

    fun getTodayDateKey(): String = todayKey()

    fun getRecentDays(context: Context, count: Int = 30): List<DayRecord> {
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
        return TodaySummary(
            totalDoses = progress.totalDoses,
            completedDoses = progress.completedDoses,
            missedDoses = progress.missedDoses,
            pendingDoses = progress.pendingDoses,
            currentTarget = getCurrentTarget(context),
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

        val completionRate = if (monthTaskDays == 0) 0 else (monthCompleteDays * 100) / monthTaskDays
        return StatsSummary(consecutiveDays, completionRate, recent7MissedDoses, monthMissedDoses)
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

    fun getReminderTimes(context: Context): List<DoseTime> {
        return getDoseTimes(context).filter { getSlotMedicines(context, it.doseIndex).isNotEmpty() }
    }

    fun getCurrentReminderTasks(context: Context, doseIndex: Int): List<MedicineTask> {
        autoMarkMissedDoses(context)
        return getSlotTasks(context, todayKey(), doseIndex).filter { it.status == RecordStatus.NONE }
    }

    fun getAllDoseRowsForDate(context: Context, dateKey: String): List<MedicineDoseRow> {
        return getDoseTimes(context).flatMap { doseTime ->
            getSlotMedicines(context, doseTime.doseIndex).map { medicine ->
                MedicineDoseRow(
                    dateKey = dateKey,
                    medicine = medicine,
                    doseIndex = doseTime.doseIndex,
                    time = doseTime.time,
                    status = getDoseRecordStatus(context, dateKey, doseTime.doseIndex, medicine.id)
                )
            }
        }
    }

    fun getSlotTasks(context: Context, dateKey: String, doseIndex: Int): List<MedicineTask> {
        val doseTime = getDoseTimes(context).firstOrNull { it.doseIndex == doseIndex }
            ?: DoseTime(doseIndex, getDoseTime(context, doseIndex))
        return getSlotMedicines(context, doseIndex).map { medicine ->
            MedicineTask(
                dateKey = dateKey,
                medicine = medicine,
                doseIndex = doseIndex,
                time = doseTime.time,
                status = getDoseRecordStatus(context, dateKey, doseIndex, medicine.id),
                minutesOfDay = doseTime.minutesOfDay
            )
        }
    }

    private fun getTargetDoseTimeFor(context: Context, calendar: Calendar): DoseTime {
        val sortedTimes = getSortedDoseTimes(context)
        val nowMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        return sortedTimes.lastOrNull { nowMinutes >= it.minutesOfDay }
            ?: sortedTimes.firstOrNull()
            ?: DoseTime(1, "08:00")
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
        while (!cursor.after(end)) {
            val dateKey = dateFormat.format(cursor.time)
            getAllDoseRowsForDate(context, dateKey).forEach { row ->
                if (row.status == RecordStatus.NONE) {
                    markDoseMissed(context, dateKey, row.doseIndex, row.medicine.id)
                    changed = true
                }
            }
            cursor.add(Calendar.DAY_OF_YEAR, 1)
        }
        return changed
    }

    private fun autoMarkTodayExpiredDoses(context: Context, now: Calendar): Boolean {
        var changed = false
        val today = dateFormat.format(now.time)
        getExpiredDoseIndexesForToday(context, now).forEach { doseIndex ->
            getSlotTasks(context, today, doseIndex).forEach { task ->
                if (task.status == RecordStatus.NONE) {
                    markDoseMissed(context, today, doseIndex, task.medicine.id)
                    changed = true
                }
            }
        }
        return changed
    }

    private fun getExpiredDoseIndexesForToday(context: Context, calendar: Calendar): Set<Int> {
        val sortedTimes = getSortedDoseTimes(context)
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
            return
        }

        val legacyName = sharedPreferences.getString(KEY_MEDICINE_NAME, "") ?: ""
        val legacyDose = sharedPreferences.getString(KEY_DOSE_VALUE, "") ?: ""
        if (legacyName.isBlank() && legacyDose.isBlank()) {
            sharedPreferences.edit()
                .putString(KEY_MEDICINE_IDS, "")
                .putBoolean(KEY_MEDICINES_MIGRATED, true)
                .apply()
            migrateLegacyHistory(context)
            return
        }
        val migrated = MedicineItem(
            id = DEFAULT_MEDICINE_ID,
            name = legacyName,
            doseValue = legacyDose,
            doseUnit = sharedPreferences.getString(KEY_DOSE_UNIT, DEFAULT_DOSE_UNIT) ?: DEFAULT_DOSE_UNIT,
            dosePeriod = sharedPreferences.getString(KEY_DOSE_PERIOD, DEFAULT_DOSE_PERIOD)
                ?: DEFAULT_DOSE_PERIOD,
            enabled = true
        ).normalized()

        sharedPreferences.edit()
            .putString(KEY_MEDICINE_IDS, DEFAULT_MEDICINE_ID)
            .putString(medicineKey(DEFAULT_MEDICINE_ID, "name"), migrated.name)
            .putString(medicineKey(DEFAULT_MEDICINE_ID, "dose_value"), migrated.doseValue)
            .putString(medicineKey(DEFAULT_MEDICINE_ID, "dose_unit"), migrated.doseUnit)
            .putString(medicineKey(DEFAULT_MEDICINE_ID, "dose_period"), migrated.dosePeriod)
            .putBoolean(medicineKey(DEFAULT_MEDICINE_ID, "enabled"), true)
            .putString(slotMedicineKey(1), DEFAULT_MEDICINE_ID)
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
                doseHistory.add(doseRecordKey(dateKey, 1, DEFAULT_MEDICINE_ID))
            }
        }
        val checkedDate = sharedPreferences.getString(KEY_CHECKED_DATE, null)
        if (checkedDate != null && checkedDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            doseHistory.add(doseRecordKey(checkedDate, 1, DEFAULT_MEDICINE_ID))
        }

        val previousDoseRecords = getDoseHistory(context)
        previousDoseRecords.forEach { record ->
            val parts = record.split("#")
            if (parts.size == 2 && parts[0].matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                val doseIndex = parts[1].toIntOrNull()
                if (doseIndex != null && doseIndex in 1..3) {
                    doseHistory.add(doseRecordKey(parts[0], doseIndex, DEFAULT_MEDICINE_ID))
                }
            }
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
            .distinct()
            .take(MAX_MEDICINES)
    }

    private fun loadMedicine(context: Context, id: String): MedicineItem {
        val sharedPreferences = prefs(context)
        return MedicineItem(
            id = id,
            name = sharedPreferences.getString(medicineKey(id, "name"), "") ?: "",
            doseValue = sharedPreferences.getString(medicineKey(id, "dose_value"), "") ?: "",
            doseUnit = sharedPreferences.getString(medicineKey(id, "dose_unit"), DEFAULT_DOSE_UNIT)
                ?: DEFAULT_DOSE_UNIT,
            dosePeriod = sharedPreferences.getString(medicineKey(id, "dose_period"), DEFAULT_DOSE_PERIOD)
                ?: DEFAULT_DOSE_PERIOD,
            enabled = sharedPreferences.getBoolean(medicineKey(id, "enabled"), true)
        ).normalized()
    }

    private fun setLegacyMedicineMirror(context: Context, item: MedicineItem) {
        prefs(context).edit()
            .putString(KEY_MEDICINE_NAME, item.name)
            .putString(KEY_DOSE_VALUE, item.doseValue)
            .putString(KEY_DOSE_UNIT, item.doseUnit)
            .putString(KEY_DOSE_PERIOD, item.dosePeriod)
            .apply()
    }

    private fun getDoseTime(context: Context, doseIndex: Int): String {
        return prefs(context).getString(doseTimeKey(doseIndex), null)
            ?: defaultDoseTime(doseIndex)
    }

    private fun defaultDoseTime(doseIndex: Int): String {
        return when (doseIndex) {
            1 -> "08:00"
            2 -> "14:00"
            else -> "20:00"
        }
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

    private fun slotMedicineKey(slotIndex: Int): String {
        return "slot_${slotIndex}_medicine_ids"
    }

    private fun doseRecordKey(dateKey: String, doseIndex: Int, medicineId: String): String {
        return "$dateKey#$doseIndex#$medicineId"
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

    private fun todayKey(): String = dateFormat.format(Date())

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
        "袋", "包", "条", "支", "瓶", "盒",
        "贴", "喷", "揿", "吸",
        "勺", "格", "块"
    )
    val DOSE_PERIODS = listOf("/天", "/次", "/周", "/月")
    val MISSED_REMINDER_DELAYS = listOf(0, 30, 60)
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
    val enabled: Boolean
) {
    fun displayText(): String {
        val cleanName = name.trim()
        val cleanDose = doseValue.trim()
        if (cleanName.isBlank()) return ""
        if (cleanDose.isBlank()) return cleanName
        return "$cleanName $cleanDose$doseUnit$dosePeriod"
    }

    fun normalized(): MedicineItem {
        return copy(
            id = id.ifBlank { "med1" },
            name = name.trim(),
            doseValue = doseValue.trim(),
            doseUnit = if (doseUnit.isBlank()) "mg" else doseUnit,
            dosePeriod = if (dosePeriod.isBlank()) "/天" else dosePeriod
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
    val isFullyComplete: Boolean = totalDoses > 0 && completedDoses >= totalDoses && missedDoses == 0
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
