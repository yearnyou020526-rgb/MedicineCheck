package com.example.medicinecheck

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var medicineNameInput: EditText
    private lateinit var doseValueInput: EditText
    private lateinit var doseUnitSpinner: Spinner
    private lateinit var dosePeriodSpinner: Spinner
    private lateinit var medicineListContainer: LinearLayout
    private lateinit var addMedicineButton: Button
    private lateinit var medicineDisplayText: TextView
    private lateinit var statusBadge: TextView
    private lateinit var progressText: TextView
    private lateinit var overviewCountsText: TextView
    private lateinit var currentTargetText: TextView
    private lateinit var toggleButton: Button
    private lateinit var markMissedButton: Button
    private lateinit var doseCountPicker: NumberPicker
    private lateinit var doseTimesContainer: LinearLayout
    private lateinit var calendarContainer: LinearLayout
    private lateinit var statsStreakText: TextView
    private lateinit var statsMonthRateText: TextView
    private lateinit var statsRecentMissedText: TextView
    private lateinit var statsMonthMissedText: TextView
    private lateinit var reminderSwitch: Switch
    private lateinit var missedReminderSpinner: Spinner
    private lateinit var permissionNotificationText: TextView
    private lateinit var permissionReminderText: TextView
    private lateinit var permissionAutostartText: TextView
    private lateinit var permissionBackgroundText: TextView
    private lateinit var permissionUnusedAppText: TextView
    private var updatingReminderSwitch = false
    private var updatingMissedReminderSpinner = false

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        medicineNameInput = findViewById(R.id.medicine_name_input)
        doseValueInput = findViewById(R.id.dose_value_input)
        doseUnitSpinner = findViewById(R.id.dose_unit_spinner)
        dosePeriodSpinner = findViewById(R.id.dose_period_spinner)
        medicineListContainer = findViewById(R.id.medicine_list_container)
        addMedicineButton = findViewById(R.id.add_medicine_button)
        medicineDisplayText = findViewById(R.id.medicine_display_text)
        statusBadge = findViewById(R.id.status_badge)
        progressText = findViewById(R.id.progress_text)
        overviewCountsText = findViewById(R.id.overview_counts_text)
        currentTargetText = findViewById(R.id.current_target_text)
        toggleButton = findViewById(R.id.toggle_today_button)
        markMissedButton = findViewById(R.id.mark_missed_button)
        doseCountPicker = findViewById(R.id.dose_count_picker)
        doseTimesContainer = findViewById(R.id.dose_times_container)
        calendarContainer = findViewById(R.id.calendar_container)
        statsStreakText = findViewById(R.id.stats_streak_text)
        statsMonthRateText = findViewById(R.id.stats_month_rate_text)
        statsRecentMissedText = findViewById(R.id.stats_recent_missed_text)
        statsMonthMissedText = findViewById(R.id.stats_month_missed_text)
        reminderSwitch = findViewById(R.id.reminder_switch)
        missedReminderSpinner = findViewById(R.id.missed_reminder_spinner)
        permissionNotificationText = findViewById(R.id.permission_notification_text)
        permissionReminderText = findViewById(R.id.permission_reminder_text)
        permissionAutostartText = findViewById(R.id.permission_autostart_text)
        permissionBackgroundText = findViewById(R.id.permission_background_text)
        permissionUnusedAppText = findViewById(R.id.permission_unused_app_text)

        configureDoseCountPicker()
        configureMedicineSpinners()
        configureReminderSwitch()
        configureMissedReminderSpinner()
        configurePermissionButtons()
        hideLegacySingleMedicineEditors()
        MedicineRepository.autoMarkMissedDoses(this)

        addMedicineButton.setOnClickListener {
            val item = MedicineRepository.addDefaultMedicine(this)
            if (item == null) {
                Toast.makeText(this, R.string.max_medicine_message, Toast.LENGTH_SHORT).show()
            } else {
                showMedicineEditor(item)
            }
        }

        findViewById<Button>(R.id.save_name_button).setOnClickListener {
            MedicineRepository.setMedicineInfo(
                context = this,
                medicineName = medicineNameInput.text.toString(),
                doseValue = doseValueInput.text.toString(),
                doseUnit = doseUnitSpinner.selectedItem?.toString() ?: "mg",
                dosePeriod = dosePeriodSpinner.selectedItem?.toString() ?: "/天"
            )
            syncAndRefresh()
            refreshReminderSchedule()
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        }

        toggleButton.setOnClickListener {
            if (MedicineRepository.getCurrentDueMedicines(this).isEmpty()) {
                Toast.makeText(this, R.string.today_all_done, Toast.LENGTH_SHORT).show()
            } else {
                MedicineRepository.markCurrentDueMedicinesChecked(this)
                syncAndRefresh()
                refreshReminderSchedule()
            }
        }

        markMissedButton.setOnClickListener {
            if (MedicineRepository.getCurrentDueMedicines(this).isEmpty()) {
                Toast.makeText(this, R.string.today_all_done, Toast.LENGTH_SHORT).show()
            } else {
                MedicineRepository.markCurrentDueMedicinesMissed(this)
                syncAndRefresh()
                refreshReminderSchedule()
            }
        }

        if (MedicineRepository.isReminderEnabled(this)) {
            requestNotificationPermissionAndSchedule()
        } else {
            MedicineReminderScheduler.cancelAll(this)
        }
    }

    override fun onResume() {
        super.onResume()
        MedicineRepository.autoMarkMissedDoses(this)
        WidgetUpdateHelper.updateAllWidgets(this)
        refreshReminderSchedule()
        refreshUi()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_POST_NOTIFICATIONS) return

        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            MedicineReminderScheduler.scheduleAllIfEnabled(this)
        } else if (MedicineRepository.isReminderEnabled(this)) {
            Toast.makeText(this, R.string.reminder_permission_needed, Toast.LENGTH_SHORT).show()
        }
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
            refreshReminderSchedule()
        }
    }

    private fun configureMedicineSpinners() {
        doseUnitSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            MedicineRepository.DOSE_UNITS
        )
        dosePeriodSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            MedicineRepository.DOSE_PERIODS
        )
    }

    private fun configureReminderSwitch() {
        reminderSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (updatingReminderSwitch) return@setOnCheckedChangeListener

            MedicineRepository.setReminderEnabled(this, isChecked)
            if (isChecked) {
                requestNotificationPermissionAndSchedule()
            } else {
                MedicineReminderScheduler.cancelAll(this)
            }
            refreshUi()
        }
    }

    private fun configureMissedReminderSpinner() {
        missedReminderSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf(
                getString(R.string.missed_reminder_off),
                getString(R.string.missed_reminder_30),
                getString(R.string.missed_reminder_60)
            )
        )
        missedReminderSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: android.view.View?,
                position: Int,
                id: Long
            ) {
                if (updatingMissedReminderSpinner) return
                val delay = when (position) {
                    1 -> 30
                    2 -> 60
                    else -> 0
                }
                MedicineRepository.setMissedReminderDelayMinutes(this@MainActivity, delay)
                refreshReminderSchedule()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun configurePermissionButtons() {
        findViewById<Button>(R.id.open_notification_settings_button).setOnClickListener {
            openNotificationSettings()
        }
        findViewById<Button>(R.id.open_app_settings_button).setOnClickListener {
            openAppSettings()
        }
    }

    private fun hideLegacySingleMedicineEditors() {
        medicineNameInput.visibility = View.GONE
        doseValueInput.visibility = View.GONE
        doseUnitSpinner.visibility = View.GONE
        dosePeriodSpinner.visibility = View.GONE
        findViewById<View>(R.id.legacy_dose_row).visibility = View.GONE
        findViewById<View>(R.id.save_name_button).visibility = View.GONE
        findViewById<View>(R.id.dose_count_card).visibility = View.GONE
        findViewById<View>(R.id.dose_times_card).visibility = View.GONE
    }

    private fun refreshUi() {
        val medicineName = MedicineRepository.getMedicineName(this)
        if (medicineNameInput.text.toString() != medicineName) {
            medicineNameInput.setText(medicineName)
        }
        val doseValue = MedicineRepository.getDoseValue(this)
        if (doseValueInput.text.toString() != doseValue) {
            doseValueInput.setText(doseValue)
        }
        setSpinnerSelection(doseUnitSpinner, MedicineRepository.getDoseUnit(this))
        setSpinnerSelection(dosePeriodSpinner, MedicineRepository.getDosePeriod(this))

        val medicineDisplay = MedicineRepository.getMedicineDisplayText(this)
        medicineDisplayText.text = medicineDisplay.ifBlank {
            getString(R.string.medicine_not_set)
        }

        val doseCount = MedicineRepository.getDoseCount(this)
        if (doseCountPicker.value != doseCount) {
            doseCountPicker.value = doseCount
        }
        updatingReminderSwitch = true
        reminderSwitch.isChecked = MedicineRepository.isReminderEnabled(this)
        updatingReminderSwitch = false
        updatingMissedReminderSpinner = true
        missedReminderSpinner.setSelection(
            when (MedicineRepository.getMissedReminderDelayMinutes(this)) {
                30 -> 1
                60 -> 2
                else -> 0
            }
        )
        updatingMissedReminderSpinner = false

        renderMedicineCards()

        val summary = MedicineRepository.getTodaySummary(this)
        val target = summary.currentTarget
        val dueMedicines = summary.currentDueMedicines
        val progress = MedicineRepository.getTodayProgress(this)
        val displayStatus = if (dueMedicines.isEmpty()) RecordStatus.DONE else RecordStatus.NONE
        updateStatusBadge(displayStatus)
        updateToggleButton(displayStatus)
        updateMissedButton(displayStatus)
        progressText.text = getString(
            R.string.today_progress_format,
            progress.completedDoses,
            progress.totalDoses
        )
        overviewCountsText.text = getString(
            R.string.today_overview_counts,
            summary.completedDoses,
            summary.missedDoses,
            summary.pendingDoses
        )
        medicineDisplayText.text = if (dueMedicines.isEmpty()) {
            getString(R.string.today_all_done)
        } else {
            dueMedicines.joinToString("\n") { it.medicine.displayText().ifBlank { "药品" } }
        }
        currentTargetText.text = if (dueMedicines.isEmpty()) {
            getString(R.string.today_all_done)
        } else {
            getString(R.string.current_due_count, dueMedicines.size)
        }

        renderDoseTimes()
        renderCalendar()
        renderStats()
        renderPermissionStatus()
    }

    private fun setSpinnerSelection(spinner: Spinner, value: String) {
        val adapter = spinner.adapter ?: return
        for (index in 0 until adapter.count) {
            if (adapter.getItem(index)?.toString() == value) {
                if (spinner.selectedItemPosition != index) {
                    spinner.setSelection(index)
                }
                return
            }
        }
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
                refreshReminderSchedule()
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
                MedicineRepository.clearDoseRecord(
                    this,
                    target.dateKey,
                    target.medicineId,
                    target.doseIndex
                )
                syncAndRefresh()
                refreshReminderSchedule()
            }
            .setNegativeButton(R.string.undo_keep, null)
            .show()
    }

    private fun confirmChangeFromMissed(action: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_change_title)
            .setMessage(R.string.confirm_change_message)
            .setPositiveButton(android.R.string.ok) { _, _ -> action() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun syncAndRefresh() {
        WidgetUpdateHelper.updateAllWidgets(this)
        refreshUi()
    }

    private fun renderMedicineCards() {
        medicineListContainer.removeAllViews()
        MedicineRepository.getMedicines(this).forEachIndexed { index, medicine ->
            if (index > 0) addSpacer(medicineListContainer, 10)
            medicineListContainer.addView(createMedicineCard(medicine))
        }
    }

    private fun createMedicineCard(medicine: MedicineItem): LinearLayout {
        val todayKey = MedicineRepository.getTodayDateKey()
        val progressRows = MedicineRepository.getDoseTimes(medicine).map { doseTime ->
            MedicineRepository.getDoseRecordStatus(
                this,
                todayKey,
                medicine.id,
                doseTime.doseIndex
            )
        }
        val doneCount = progressRows.count { it == RecordStatus.DONE }
        val missedCount = progressRows.count { it == RecordStatus.MISSED }
        val statusText = if (!medicine.enabled) {
            getString(R.string.medicine_paused)
        } else {
            getString(R.string.medicine_card_status, doneCount, medicine.doseCount, missedCount)
        }
        val currentTask = MedicineRepository.getCurrentStageTasks(this)
            .firstOrNull { it.medicine.id == medicine.id }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(
                if (medicine.enabled) Color.WHITE else COLOR_FUTURE_CELL,
                16f,
                COLOR_BORDER,
                1
            )
            setPadding(dp(14), dp(12), dp(14), dp(12))

            addView(TextView(this@MainActivity).apply {
                text = medicine.displayText().ifBlank { getString(R.string.medicine_not_set) }
                setTextColor(COLOR_TEXT_PRIMARY)
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(this@MainActivity).apply {
                text = getString(
                    R.string.medicine_card_times,
                    medicine.doseCount,
                    MedicineRepository.getDoseTimes(medicine).joinToString("、") { it.time }
                )
                setTextColor(COLOR_TEXT_SECONDARY)
                textSize = 13f
                setPadding(0, dp(6), 0, 0)
            })
            addView(TextView(this@MainActivity).apply {
                text = statusText
                setTextColor(if (medicine.enabled) COLOR_TEXT_SECONDARY else COLOR_RED)
                textSize = 13f
                setPadding(0, dp(4), 0, 0)
            })
            if (currentTask != null) {
                addView(TextView(this@MainActivity).apply {
                    text = getString(
                        R.string.current_medicine_status,
                        currentTask.doseIndex,
                        currentTask.time,
                        statusLabel(currentTask.status)
                    )
                    setTextColor(COLOR_TEXT_SECONDARY)
                    textSize = 13f
                    setPadding(0, dp(4), 0, 0)
                })
            }

            val actions = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, 0)
            }
            actions.addView(cardActionButton(getString(R.string.edit_medicine)) {
                showMedicineEditor(medicine)
            })
            actions.addView(cardActionButton(
                if (medicine.enabled) getString(R.string.pause_medicine)
                else getString(R.string.enable_medicine)
            ) {
                MedicineRepository.saveMedicine(this@MainActivity, medicine.copy(enabled = !medicine.enabled))
                syncAndRefresh()
                refreshReminderSchedule()
            })
            actions.addView(cardActionButton(getString(R.string.delete_medicine), COLOR_RED) {
                confirmDeleteMedicine(medicine)
            })
            addView(actions)

            val doneTask = currentTask?.takeIf { it.status == RecordStatus.DONE }
            if (doneTask != null) {
                addView(Button(this@MainActivity).apply {
                    text = getString(R.string.undo_current_dose)
                    textSize = 14f
                    setTextColor(COLOR_RED)
                    setAllCaps(false)
                    minHeight = 0
                    background = roundedDrawable(COLOR_CANCEL_BUTTON, 14f)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(42)
                    ).apply { setMargins(0, dp(10), 0, 0) }
                    setOnClickListener { showCancelDoseDialog(doneTask) }
                })
            }
        }
    }

    private fun cardActionButton(
        label: String,
        color: Int = COLOR_GREEN,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text = label
            textSize = 13f
            setTextColor(color)
            setAllCaps(false)
            minHeight = 0
            background = roundedDrawable(COLOR_SOFT_GREEN, 12f)
            layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                setMargins(dp(3), 0, dp(3), 0)
            }
            setOnClickListener { action() }
        }
    }

    private fun showMedicineEditor(medicine: MedicineItem) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), 0)
        }
        val nameInput = dialogEditText(medicine.name, getString(R.string.medicine_name_hint))
        val doseInput = dialogEditText(medicine.doseValue, getString(R.string.dose_value_hint))
        val unitSpinner = dialogSpinner(MedicineRepository.DOSE_UNITS, medicine.doseUnit)
        val periodSpinner = dialogSpinner(MedicineRepository.DOSE_PERIODS, medicine.dosePeriod)
        val enabledSwitch = Switch(this).apply {
            text = getString(R.string.enable_medicine)
            isChecked = medicine.enabled
            textSize = 15f
            setTextColor(COLOR_TEXT_PRIMARY)
        }
        val doseCountPicker = NumberPicker(this).apply {
            minValue = 1
            maxValue = 3
            displayedValues = arrayOf("1次", "2次", "3次")
            wrapSelectorWheel = false
            value = medicine.doseCount
        }
        val times = medicine.doseTimes.toMutableList().apply {
            val defaults = defaultEditorTimes(medicine.doseCount)
            while (size < 3) add(defaults[size])
        }
        val timeContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun renderEditorTimes() {
            timeContainer.removeAllViews()
            for (doseIndex in 1..doseCountPicker.value) {
                val time = times[doseIndex - 1]
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(6), 0, 0)
                }
                row.addView(TextView(this).apply {
                    text = getString(R.string.dose_time_label, doseIndex)
                    setTextColor(COLOR_TEXT_PRIMARY)
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                row.addView(Button(this).apply {
                    text = time
                    setTextColor(COLOR_GREEN)
                    setAllCaps(false)
                    background = roundedDrawable(COLOR_SOFT_GREEN, 12f)
                    layoutParams = LinearLayout.LayoutParams(dp(104), dp(40))
                    setOnClickListener {
                        val doseTime = DoseTime(doseIndex, times[doseIndex - 1])
                        TimePickerDialog(
                            this@MainActivity,
                            { _, hourOfDay, minute ->
                                times[doseIndex - 1] = String.format(
                                    Locale.US,
                                    "%02d:%02d",
                                    hourOfDay,
                                    minute
                                )
                                renderEditorTimes()
                            },
                            doseTime.hour,
                            doseTime.minute,
                            true
                        ).show()
                    }
                })
                timeContainer.addView(row)
            }
        }

        doseCountPicker.setOnValueChangedListener { _, oldValue, newValue ->
            val defaults = defaultEditorTimes(newValue)
            for (index in 0..2) {
                if (index >= oldValue || times[index].isBlank()) {
                    times[index] = defaults[index]
                }
            }
            renderEditorTimes()
        }

        content.addView(labelText(getString(R.string.medicine_name)))
        content.addView(nameInput)
        content.addView(labelText(getString(R.string.dose_value_label)))
        content.addView(doseInput)
        content.addView(labelText(getString(R.string.dose_unit_label)))
        content.addView(unitSpinner)
        content.addView(labelText(getString(R.string.dose_period_label)))
        content.addView(periodSpinner)
        content.addView(labelText(getString(R.string.dose_count_title)))
        content.addView(doseCountPicker)
        content.addView(enabledSwitch)
        content.addView(labelText(getString(R.string.dose_times_title)))
        content.addView(timeContainer)
        renderEditorTimes()

        AlertDialog.Builder(this)
            .setTitle(R.string.edit_medicine)
            .setView(content)
            .setPositiveButton(R.string.save) { _, _ ->
                MedicineRepository.saveMedicine(
                    this,
                    medicine.copy(
                        name = nameInput.text.toString(),
                        doseValue = doseInput.text.toString(),
                        doseUnit = unitSpinner.selectedItem?.toString() ?: "mg",
                        dosePeriod = periodSpinner.selectedItem?.toString() ?: "/天",
                        doseCount = doseCountPicker.value,
                        doseTimes = times,
                        enabled = enabledSwitch.isChecked
                    )
                )
                syncAndRefresh()
                refreshReminderSchedule()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun defaultEditorTimes(count: Int): List<String> {
        return when (count.coerceIn(1, 3)) {
            1 -> listOf("08:00", "20:00", "20:00")
            2 -> listOf("08:00", "20:00", "20:00")
            else -> listOf("08:00", "14:00", "20:00")
        }
    }

    private fun showCancelDoseDialog(task: MedicineTask) {
        AlertDialog.Builder(this)
            .setTitle(R.string.undo_dose_title)
            .setMessage(R.string.undo_dose_message)
            .setPositiveButton(R.string.undo_dose_confirm) { _, _ ->
                MedicineRepository.clearDoseRecord(
                    this,
                    task.dateKey,
                    task.medicine.id,
                    task.doseIndex
                )
                syncAndRefresh()
                refreshReminderSchedule()
            }
            .setNegativeButton(R.string.undo_dose_keep, null)
            .show()
    }

    private fun confirmDeleteMedicine(medicine: MedicineItem) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_medicine)
            .setMessage(R.string.delete_medicine_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                MedicineRepository.deleteMedicine(this, medicine.id)
                syncAndRefresh()
                refreshReminderSchedule()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun dialogEditText(value: String, hint: String): EditText {
        return EditText(this).apply {
            setText(value)
            this.hint = hint
            maxLines = 1
            textSize = 15f
            setPadding(dp(12), 0, dp(12), 0)
            background = roundedDrawable(Color.WHITE, 12f, COLOR_BORDER, 1)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)
            ).apply { setMargins(0, dp(6), 0, dp(8)) }
        }
    }

    private fun dialogSpinner(values: List<String>, selected: String): Spinner {
        return Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                values
            )
            setSpinnerSelection(this, selected)
            background = roundedDrawable(Color.WHITE, 12f, COLOR_BORDER, 1)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)
            ).apply { setMargins(0, dp(6), 0, dp(8)) }
        }
    }

    private fun labelText(label: String): TextView {
        return TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(COLOR_TEXT_SECONDARY)
            setPadding(0, dp(8), 0, 0)
        }
    }

    private fun refreshReminderSchedule() {
        if (!MedicineRepository.isReminderEnabled(this)) {
            MedicineReminderScheduler.cancelAll(this)
            return
        }
        if (MedicineReminderScheduler.canPostNotifications(this)) {
            MedicineReminderScheduler.scheduleAllIfEnabled(this)
        }
    }

    private fun requestNotificationPermissionAndSchedule() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_POST_NOTIFICATIONS
            )
            return
        }
        MedicineReminderScheduler.scheduleAllIfEnabled(this)
    }

    private fun renderPermissionStatus() {
        val enabled = getString(R.string.permission_enabled)
        val disabled = getString(R.string.permission_disabled)
        val manualCheck = getString(R.string.permission_manual_check)
        permissionNotificationText.text = getString(
            R.string.permission_notification,
            if (MedicineReminderScheduler.canPostNotifications(this)) enabled else disabled
        )
        permissionReminderText.text = getString(
            R.string.permission_reminder_switch,
            if (MedicineRepository.isReminderEnabled(this)) enabled else disabled
        )
        permissionAutostartText.text = getString(R.string.permission_autostart, manualCheck)
        permissionBackgroundText.text = getString(R.string.permission_background, manualCheck)
        permissionUnusedAppText.text = getString(R.string.permission_unused_app, manualCheck)
    }

    private fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
        } else {
            appSettingsIntent()
        }
        startActivitySafely(intent)
    }

    private fun openAppSettings() {
        startActivitySafely(appSettingsIntent())
    }

    private fun appSettingsIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
    }

    private fun startActivitySafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: RuntimeException) {
            startActivity(appSettingsIntent())
        }
    }

    private fun updateStatusBadge(status: RecordStatus) {
        statusBadge.text = statusLabel(status)
        statusBadge.background = roundedDrawable(
            color = when (status) {
                RecordStatus.DONE -> COLOR_GREEN
                RecordStatus.MISSED -> COLOR_PARTIAL_STROKE
                RecordStatus.NONE -> COLOR_RED
            },
            radiusDp = 14f
        )
    }

    private fun updateToggleButton(status: RecordStatus) {
        toggleButton.text = when (status) {
            RecordStatus.DONE -> getString(R.string.today_all_done)
            RecordStatus.MISSED -> getString(R.string.mark_today)
            RecordStatus.NONE -> getString(R.string.mark_today)
        }
        toggleButton.isEnabled = status != RecordStatus.DONE
        toggleButton.setTextColor(if (status == RecordStatus.DONE) COLOR_TEXT_SECONDARY else Color.WHITE)
        toggleButton.background = roundedDrawable(
            color = if (status == RecordStatus.DONE) COLOR_FUTURE_CELL else COLOR_GREEN,
            radiusDp = 16f
        )
    }

    private fun updateMissedButton(status: RecordStatus) {
        markMissedButton.text = when (status) {
            RecordStatus.DONE -> getString(R.string.today_all_done)
            RecordStatus.MISSED -> getString(R.string.clear_missed)
            RecordStatus.NONE -> getString(R.string.mark_missed)
        }
        markMissedButton.isEnabled = status != RecordStatus.DONE
        markMissedButton.setTextColor(
            when (status) {
                RecordStatus.MISSED -> COLOR_PARTIAL_TEXT
                RecordStatus.DONE -> COLOR_TEXT_MUTED
                RecordStatus.NONE -> COLOR_TEXT_SECONDARY
            }
        )
    }

    private fun statusLabel(status: RecordStatus): String {
        return when (status) {
            RecordStatus.DONE -> getString(R.string.status_checked_short)
            RecordStatus.MISSED -> getString(R.string.status_missed_short)
            RecordStatus.NONE -> getString(R.string.status_unchecked_short)
        }
    }

    private fun renderStats() {
        val stats = MedicineRepository.getStats(this)
        statsStreakText.text = getString(R.string.stats_streak, stats.consecutiveDays)
        statsMonthRateText.text = getString(R.string.stats_month_rate, stats.monthCompletionRate)
        statsRecentMissedText.text = getString(R.string.stats_recent_missed, stats.recent7MissedDoses)
        statsMonthMissedText.text = getString(R.string.stats_month_missed, stats.monthMissedDoses)
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
        view.setOnClickListener {
            if (!isFuture) {
                showDayRecordDialog(dateKey)
            }
        }

        when {
            record?.hasMissed == true && record.checked -> {
                view.setTextColor(Color.WHITE)
                view.background = roundedDrawable(
                    color = if (isToday) COLOR_TODAY_GREEN else COLOR_GREEN,
                    radiusDp = 10f,
                    strokeColor = COLOR_RED,
                    strokeDp = 2
                )
            }

            record?.hasMissed == true -> {
                view.setTextColor(COLOR_RED)
                view.background = roundedDrawable(
                    color = if (record.partiallyChecked) COLOR_PARTIAL_BG else Color.WHITE,
                    radiusDp = 10f,
                    strokeColor = COLOR_RED,
                    strokeDp = 2
                )
            }

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

    private fun showDayRecordDialog(dateKey: String) {
        val rows = MedicineRepository.getAllDoseRowsForDate(this, dateKey)
        val items = rows.map { row ->
            getString(
                R.string.record_detail_item_multi,
                row.medicine.displayText().ifBlank { getString(R.string.medicine_not_set) },
                row.doseIndex,
                row.time,
                statusLabel(row.status)
            )
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.record_detail_title, dateKey))
            .setItems(items) { _, which ->
                showDoseRecordActionDialog(rows[which])
            }
            .show()
    }

    private fun showDoseRecordActionDialog(row: MedicineDoseRow) {
        val status = MedicineRepository.getDoseRecordStatus(
            this,
            row.dateKey,
            row.medicine.id,
            row.doseIndex
        )
        val actions = arrayOf(
            getString(R.string.record_action_done),
            getString(R.string.record_action_missed),
            getString(R.string.record_action_clear)
        )

        AlertDialog.Builder(this)
            .setTitle("${row.medicine.displayText().ifBlank { getString(R.string.medicine_not_set) }} 第${row.doseIndex}次")
            .setItems(actions) { _, which ->
                val applyChange = {
                    when (which) {
                        0 -> MedicineRepository.markDoseChecked(
                            this,
                            row.dateKey,
                            row.medicine.id,
                            row.doseIndex
                        )
                        1 -> MedicineRepository.markDoseMissed(
                            this,
                            row.dateKey,
                            row.medicine.id,
                            row.doseIndex
                        )
                        else -> MedicineRepository.clearDoseRecord(
                            this,
                            row.dateKey,
                            row.medicine.id,
                            row.doseIndex
                        )
                    }
                    syncAndRefresh()
                    refreshReminderSchedule()
                }
                if (status == RecordStatus.MISSED && which != 1) {
                    confirmChangeFromMissed(applyChange)
                } else if (
                    which == 2 &&
                    status != RecordStatus.NONE &&
                    MedicineRepository.isDoseExpired(this, row.dateKey, row.medicine, row.doseIndex)
                ) {
                    AlertDialog.Builder(this)
                        .setTitle(R.string.confirm_clear_expired_title)
                        .setMessage(R.string.confirm_clear_expired_message)
                        .setPositiveButton(android.R.string.ok) { _, _ -> applyChange() }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                } else {
                    applyChange()
                }
            }
            .show()
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
        private const val REQUEST_POST_NOTIFICATIONS = 12_300
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
