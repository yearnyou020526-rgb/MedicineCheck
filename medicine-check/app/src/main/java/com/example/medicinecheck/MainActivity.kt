package com.example.medicinecheck

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var medicineNameInput: EditText
    private lateinit var medicineDisplayText: TextView
    private lateinit var statusBadge: TextView
    private lateinit var progressText: TextView
    private lateinit var currentTargetText: TextView
    private lateinit var toggleButton: Button
    private lateinit var doseCountPicker: NumberPicker
    private lateinit var doseTimesContainer: LinearLayout
    private lateinit var calendarContainer: LinearLayout

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        medicineNameInput = findViewById(R.id.medicine_name_input)
        medicineDisplayText = findViewById(R.id.medicine_display_text)
        statusBadge = findViewById(R.id.status_badge)
        progressText = findViewById(R.id.progress_text)
        currentTargetText = findViewById(R.id.current_target_text)
        toggleButton = findViewById(R.id.toggle_today_button)
        doseCountPicker = findViewById(R.id.dose_count_picker)
        doseTimesContainer = findViewById(R.id.dose_times_container)
        calendarContainer = findViewById(R.id.calendar_container)

        configureDoseCountPicker()

        findViewById<Button>(R.id.save_name_button).setOnClickListener {
            MedicineRepository.setMedicineName(this, medicineNameInput.text.toString())
            syncAndRefresh()
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        }

        toggleButton.setOnClickListener {
            val target = MedicineRepository.getCurrentTarget(this)
            if (target.checked) {
                showCancelCurrentTargetDialog(target)
            } else {
                MedicineRepository.markCurrentTargetChecked(this)
                syncAndRefresh()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        MidnightUpdateScheduler.scheduleNext(this)
        refreshUi()
    }

    private fun configureDoseCountPicker() {
        doseCountPicker.minValue = 1
        doseCountPicker.maxValue = 3
        doseCountPicker.displayedValues = arrayOf("1次", "2次", "3次")
        doseCountPicker.wrapSelectorWheel = false
        doseCountPicker.value = MedicineRepository.getDoseCount(this)
        doseCountPicker.setOnValueChangedListener { _, _, newValue ->
            MedicineRepository.setDoseCount(this, newValue)
            syncAndRefresh()
        }
    }

    private fun refreshUi() {
        val medicineName = MedicineRepository.getMedicineName(this)
        if (medicineNameInput.text.toString() != medicineName) {
            medicineNameInput.setText(medicineName)
        }
        medicineDisplayText.text = medicineName.ifBlank {
            getString(R.string.medicine_not_set)
        }

        val doseCount = MedicineRepository.getDoseCount(this)
        if (doseCountPicker.value != doseCount) {
            doseCountPicker.value = doseCount
        }

        val target = MedicineRepository.getCurrentTarget(this)
        val progress = MedicineRepository.getTodayProgress(this)
        updateStatusBadge(target.checked)
        updateToggleButton(target.checked)
        progressText.text = getString(
            R.string.today_progress_format,
            progress.completedDoses,
            progress.totalDoses
        )
        currentTargetText.text = getString(
            R.string.current_target_format,
            target.doseIndex,
            target.time,
            if (target.checked) getString(R.string.status_checked_short)
            else getString(R.string.status_unchecked_short)
        )

        renderDoseTimes()
        renderCalendar()
    }

    private fun renderDoseTimes() {
        doseTimesContainer.removeAllViews()
        MedicineRepository.getDoseTimes(this).forEachIndexed { index, doseTime ->
            if (index > 0) addSpacer(doseTimesContainer, 10)

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            row.addView(TextView(this).apply {
                text = getString(R.string.dose_time_label, doseTime.doseIndex)
                setTextColor(COLOR_TEXT_PRIMARY)
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            row.addView(Button(this).apply {
                text = doseTime.time
                textSize = 15f
                setTextColor(COLOR_GREEN)
                setAllCaps(false)
                minHeight = 0
                background = roundedDrawable(COLOR_SOFT_GREEN, 14f)
                layoutParams = LinearLayout.LayoutParams(dp(104), dp(42))
                setOnClickListener {
                    showTimePicker(doseTime)
                }
            })

            doseTimesContainer.addView(row)
        }
    }

    private fun showTimePicker(doseTime: DoseTime) {
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                val time = String.format(Locale.US, "%02d:%02d", hourOfDay, minute)
                MedicineRepository.setDoseTime(this, doseTime.doseIndex, time)
                syncAndRefresh()
            },
            doseTime.hour,
            doseTime.minute,
            true
        ).show()
    }

    private fun showCancelCurrentTargetDialog(target: DoseTarget) {
        AlertDialog.Builder(this)
            .setTitle(R.string.undo_title)
            .setMessage(R.string.undo_message)
            .setPositiveButton(R.string.undo_confirm) { _, _ ->
                MedicineRepository.clearDoseChecked(this, target.dateKey, target.doseIndex)
                syncAndRefresh()
            }
            .setNegativeButton(R.string.undo_keep, null)
            .show()
    }

    private fun syncAndRefresh() {
        MedicineWidgetProvider.updateAllWidgets(this)
        MidnightUpdateScheduler.scheduleNext(this)
        refreshUi()
    }

    private fun updateStatusBadge(checked: Boolean) {
        statusBadge.text = if (checked) {
            getString(R.string.status_checked_short)
        } else {
            getString(R.string.status_unchecked_short)
        }
        statusBadge.background = roundedDrawable(
            color = if (checked) COLOR_GREEN else COLOR_RED,
            radiusDp = 14f
        )
    }

    private fun updateToggleButton(checked: Boolean) {
        toggleButton.text = if (checked) {
            getString(R.string.cancel_today)
        } else {
            getString(R.string.mark_today)
        }
        toggleButton.setTextColor(if (checked) COLOR_RED else Color.WHITE)
        toggleButton.background = roundedDrawable(
            color = if (checked) COLOR_CANCEL_BUTTON else COLOR_GREEN,
            radiusDp = 16f
        )
    }

    private fun renderCalendar() {
        calendarContainer.removeAllViews()

        val recentDays = MedicineRepository.getRecentDays(this)
        val recordsByDate = recentDays.associateBy { it.dateKey }
        val months = recentDays
            .mapNotNull { MonthKey.fromDateKey(it.dateKey) }
            .distinct()
            .sortedWith(compareBy<MonthKey> { it.year }.thenBy { it.month })

        months.forEachIndexed { index, month ->
            if (index > 0) {
                addSpacer(calendarContainer, 18)
            }
            addMonthCalendar(month, recordsByDate)
        }
    }

    private fun addMonthCalendar(
        month: MonthKey,
        recordsByDate: Map<String, DayRecord>
    ) {
        calendarContainer.addView(TextView(this).apply {
            text = String.format(Locale.CHINA, "%d年%d月", month.year, month.month + 1)
            setTextColor(COLOR_TEXT_PRIMARY)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        })

        addSpacer(calendarContainer, 10)
        calendarContainer.addView(createWeekHeader())
        addSpacer(calendarContainer, 6)

        val cells = monthCells(month)
        cells.chunked(7).forEach { week ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            week.forEach { day ->
                row.addView(createDayCell(day, month, recordsByDate))
            }
            calendarContainer.addView(row)
            addSpacer(calendarContainer, 6)
        }
    }

    private fun createWeekHeader(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
            row.addView(TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                setTextColor(COLOR_TEXT_SECONDARY)
                textSize = 12f
                layoutParams = dayLayoutParams()
            })
        }
        return row
    }

    private fun createDayCell(
        day: Int?,
        month: MonthKey,
        recordsByDate: Map<String, DayRecord>
    ): TextView {
        val view = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 14f
            layoutParams = dayLayoutParams()
            minHeight = dp(38)
        }

        if (day == null) {
            view.text = ""
            return view
        }

        val dateKey = String.format(Locale.US, "%04d-%02d-%02d", month.year, month.month + 1, day)
        val record = recordsByDate[dateKey]
        val todayKey = dateFormat.format(Calendar.getInstance().time)
        val isToday = dateKey == todayKey
        val isFuture = dateKey > todayKey

        view.text = day.toString()
        view.typeface = if (isToday) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

        when {
            record?.checked == true -> {
                view.setTextColor(Color.WHITE)
                view.background = roundedDrawable(
                    color = if (isToday) COLOR_TODAY_GREEN else COLOR_GREEN,
                    radiusDp = 10f
                )
            }

            record?.partiallyChecked == true -> {
                view.setTextColor(COLOR_PARTIAL_TEXT)
                view.background = roundedDrawable(
                    color = COLOR_PARTIAL_BG,
                    radiusDp = 10f,
                    strokeColor = if (isToday) COLOR_PARTIAL_STROKE else null,
                    strokeDp = if (isToday) 2 else 0
                )
            }

            record != null && isToday -> {
                view.setTextColor(COLOR_TEXT_PRIMARY)
                view.background = roundedDrawable(
                    color = Color.WHITE,
                    radiusDp = 10f,
                    strokeColor = COLOR_RED,
                    strokeDp = 2
                )
            }

            record != null -> {
                view.setTextColor(COLOR_TEXT_PRIMARY)
                view.background = roundedDrawable(
                    color = Color.WHITE,
                    radiusDp = 10f,
                    strokeColor = COLOR_BORDER,
                    strokeDp = 1
                )
            }

            isFuture -> {
                view.setTextColor(COLOR_TEXT_MUTED)
                view.background = roundedDrawable(
                    color = COLOR_FUTURE_CELL,
                    radiusDp = 10f
                )
            }

            else -> {
                view.setTextColor(COLOR_TEXT_MUTED)
                view.background = roundedDrawable(
                    color = COLOR_OUT_OF_RANGE_CELL,
                    radiusDp = 10f
                )
            }
        }

        return view
    }

    private fun monthCells(month: MonthKey): List<Int?> {
        val calendar = Calendar.getInstance().apply {
            clear()
            set(month.year, month.month, 1)
        }
        val leadingBlanks = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val cells = MutableList<Int?>(leadingBlanks) { null }
        for (day in 1..daysInMonth) {
            cells.add(day)
        }
        while (cells.size % 7 != 0) {
            cells.add(null)
        }
        return cells
    }

    private fun dayLayoutParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, dp(38), 1f).apply {
            setMargins(dp(2), 0, dp(2), 0)
        }
    }

    private fun addSpacer(parent: LinearLayout, heightDp: Int) {
        parent.addView(android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(heightDp)
            )
        })
    }

    private fun roundedDrawable(
        color: Int,
        radiusDp: Float,
        strokeColor: Int? = null,
        strokeDp: Int = 0
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeColor != null && strokeDp > 0) {
                setStroke(dp(strokeDp), strokeColor)
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private data class MonthKey(val year: Int, val month: Int) {
        companion object {
            fun fromDateKey(dateKey: String): MonthKey? {
                val parts = dateKey.split("-")
                if (parts.size != 3) return null
                return MonthKey(
                    year = parts[0].toIntOrNull() ?: return null,
                    month = (parts[1].toIntOrNull() ?: return null) - 1
                )
            }
        }
    }

    companion object {
        private val COLOR_GREEN = Color.rgb(46, 125, 50)
        private val COLOR_TODAY_GREEN = Color.rgb(31, 122, 53)
        private val COLOR_RED = Color.rgb(211, 47, 47)
        private val COLOR_CANCEL_BUTTON = Color.rgb(246, 238, 238)
        private val COLOR_BORDER = Color.rgb(224, 228, 235)
        private val COLOR_FUTURE_CELL = Color.rgb(244, 246, 248)
        private val COLOR_OUT_OF_RANGE_CELL = Color.rgb(248, 249, 251)
        private val COLOR_TEXT_PRIMARY = Color.rgb(32, 33, 36)
        private val COLOR_TEXT_SECONDARY = Color.rgb(107, 114, 128)
        private val COLOR_TEXT_MUTED = Color.rgb(176, 183, 194)
        private val COLOR_SOFT_GREEN = Color.rgb(241, 248, 242)
        private val COLOR_PARTIAL_BG = Color.rgb(255, 244, 214)
        private val COLOR_PARTIAL_TEXT = Color.rgb(145, 95, 0)
        private val COLOR_PARTIAL_STROKE = Color.rgb(245, 158, 11)
    }
}
