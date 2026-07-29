package com.innovation313.roshankhata

import android.os.Bundle
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.innovation313.roshankhata.data.InvoiceFeatureSettings

/**
 * Which optional invoice fields this shop wants to see at all — the
 * owner's own request after Vyapar's Transaction settings screen. Every
 * switch here applies instantly, the same way Vyapar's own toggles do;
 * there is no separate Save button to forget to press.
 *
 * Turning a feature off only hides it from [InvoiceEditorActivity]. It
 * never touches a value already saved on a past invoice, and a template
 * still only prints a line when that invoice actually has a value for it —
 * these switches decide what can be ENTERED going forward, not what gets
 * printed on invoices already made.
 */
class InvoiceSettingsActivity : AppCompatActivity() {

    private data class FeatureRow(
        val rowId: Int,
        val titleRes: Int,
        val descRes: Int,
        val get: (android.content.Context) -> Boolean,
        val set: (android.content.Context, Boolean) -> Unit
    )

    private val rows = listOf(
        FeatureRow(
            R.id.rowDiscount, R.string.invoice_feature_discount, R.string.invoice_feature_discount_desc,
            InvoiceFeatureSettings::discountEnabled, InvoiceFeatureSettings::setDiscountEnabled
        ),
        FeatureRow(
            R.id.rowTax, R.string.invoice_feature_tax, R.string.invoice_feature_tax_desc,
            InvoiceFeatureSettings::taxEnabled, InvoiceFeatureSettings::setTaxEnabled
        ),
        FeatureRow(
            R.id.rowExtraCharge, R.string.invoice_feature_extra_charge, R.string.invoice_feature_extra_charge_desc,
            InvoiceFeatureSettings::extraChargeEnabled, InvoiceFeatureSettings::setExtraChargeEnabled
        ),
        FeatureRow(
            R.id.rowReceived, R.string.invoice_feature_received, R.string.invoice_feature_received_desc,
            InvoiceFeatureSettings::receivedEnabled, InvoiceFeatureSettings::setReceivedEnabled
        ),
        FeatureRow(
            R.id.rowNote, R.string.invoice_feature_note, R.string.invoice_feature_note_desc,
            InvoiceFeatureSettings::noteEnabled, InvoiceFeatureSettings::setNoteEnabled
        ),
        FeatureRow(
            R.id.rowDueDate, R.string.invoice_feature_due_date, R.string.invoice_feature_due_date_desc,
            InvoiceFeatureSettings::dueDateEnabled, InvoiceFeatureSettings::setDueDateEnabled
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_invoice_settings)
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)

        setSupportActionBar(findViewById<Toolbar>(R.id.settingsToolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        rows.forEach { row ->
            val rowView = findViewById<android.view.View>(row.rowId)
            rowView.findViewById<TextView>(R.id.tvFeatureTitle).setText(row.titleRes)
            rowView.findViewById<TextView>(R.id.tvFeatureDesc).setText(row.descRes)
            val switch = rowView.findViewById<Switch>(R.id.switchFeature)
            switch.isChecked = row.get(this)
            switch.setOnCheckedChangeListener { _, checked -> row.set(this, checked) }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
