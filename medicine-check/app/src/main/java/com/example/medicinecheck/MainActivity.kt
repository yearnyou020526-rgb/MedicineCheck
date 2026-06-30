package com.example.medicinecheck

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var medicineNameInput: EditText
    private lateinit var todayStatusText: TextView
    private lateinit var toggleButton: Button
    private lateinit var historyList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        medicineNameInput = findViewById(R.id.medicine_name_input)
        todayStatusText = findViewById(R.id.today_status_text)
        toggleButton = findViewById(R.id.toggle_today_button)
        historyList = findViewById(R.id.history_list)

        findViewById<Button>(R.id.save_name_button).setOnClickListener {
            MedicineRepository.setMedicineName(this, medicineNameInput.text.toString())
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        }

        toggleButton.setOnClickListener {
            if (MedicineRepository.isTodayChecked(this)) {
                MedicineRepository.clearTodayChecked(this)
            } else {
                MedicineRepository.markTodayChecked(this)
            }
            MedicineWidgetProvider.updateAllWidgets(this)
            refreshUi()
        }
    }

    override fun onResume() {
        super.onResume()
        MidnightUpdateScheduler.scheduleNext(this)
        refreshUi()
    }

    private fun refreshUi() {
        val medicineName = MedicineRepository.getMedicineName(this)
        medicineNameInput.setText(medicineName)

        val checked = MedicineRepository.isTodayChecked(this)
        todayStatusText.text = if (checked) {
            getString(R.string.today_checked)
        } else {
            getString(R.string.today_unchecked)
        }
        toggleButton.text = if (checked) {
            getString(R.string.cancel_today)
        } else {
            getString(R.string.mark_today)
        }

        historyList.removeAllViews()
        MedicineRepository.getRecentDays(this).forEach { record ->
            val row = TextView(this).apply {
                text = getString(
                    if (record.checked) R.string.history_checked else R.string.history_unchecked,
                    record.displayDate
                )
                textSize = 16f
                setPadding(0, 10, 0, 10)
            }
            historyList.addView(row)
        }
    }
}
