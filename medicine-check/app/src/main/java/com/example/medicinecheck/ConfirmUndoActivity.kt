package com.example.medicinecheck

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle

class ConfirmUndoActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AlertDialog.Builder(this)
            .setTitle(R.string.undo_title)
            .setMessage(R.string.undo_message)
            .setPositiveButton(R.string.undo_confirm) { _, _ ->
                val dateKey = intent.getStringExtra(MedicineWidgetProvider.EXTRA_DATE_KEY)
                val doseIndex = intent.getIntExtra(MedicineWidgetProvider.EXTRA_DOSE_INDEX, -1)
                if (dateKey != null && doseIndex in 1..3) {
                    MedicineRepository.clearDoseChecked(this, dateKey, doseIndex)
                } else {
                    MedicineRepository.clearCurrentTargetChecked(this)
                }
                MedicineWidgetProvider.updateAllWidgets(this)
                finish()
            }
            .setNegativeButton(R.string.undo_keep) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }
}
