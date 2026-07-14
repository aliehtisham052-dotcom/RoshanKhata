package com.innovation313.roshankhata

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.data.BusinessProfile
import com.innovation313.roshankhata.data.EntryNumber
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.PdfExport
import com.innovation313.roshankhata.data.Recovery
import com.innovation313.roshankhata.ui.EntryAdapter
import com.innovation313.roshankhata.ui.EntryRow
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.Reminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A single party's ledger: full entry history with running balances,
 * plus the two core actions — "I Gave" and "I Got".
 */
class PartyDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PARTY_ID = "party_id"
    }

    private var partyId: Long = 0
    private var partyName: String = ""
    private var partyPhone: String? = null
    private var currentBalance: Double = 0.0
    private var currentRows: List<EntryRow> = emptyList()
    private lateinit var adapter: EntryAdapter
    private lateinit var tvPartyName: TextView
    private lateinit var tvPartyBalance: TextView
    private lateinit var tvBalanceHint: TextView
    private lateinit var tvNoEntries: TextView

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_party_detail)

        partyId = intent.getLongExtra(EXTRA_PARTY_ID, 0)
        if (partyId == 0L) {
            finish()
            return
        }

        tvPartyName = findViewById(R.id.tvPartyName)
        tvPartyBalance = findViewById(R.id.tvPartyBalance)
        tvBalanceHint = findViewById(R.id.tvBalanceHint)
        tvNoEntries = findViewById(R.id.tvNoEntries)

        adapter = EntryAdapter { entry -> confirmDeleteEntry(entry) }
        val rv: RecyclerView = findViewById(R.id.rvEntries)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        findViewById<MaterialButton>(R.id.btnGave).setOnClickListener { showAddEntryDialog(true) }
        findViewById<MaterialButton>(R.id.btnGot).setOnClickListener { showAddEntryDialog(false) }

        findViewById<MaterialButton>(R.id.btnWhatsApp).setOnClickListener {
            showReminderPreview(viaWhatsApp = true)
        }
        findViewById<MaterialButton>(R.id.btnSms).setOnClickListener {
            showReminderPreview(viaWhatsApp = false)
        }
        findViewById<MaterialButton>(R.id.btnPdf).setOnClickListener {
            exportStatement()
        }

        loadParty()
        observeEntries()
    }

    private fun loadParty() {
        lifecycleScope.launch {
            dao.getParty(partyId)?.let { p ->
                partyName = p.name
                partyPhone = p.phone
                tvPartyName.text = p.name
            }
        }
    }

    private fun observeEntries() {
        lifecycleScope.launch {
            // Entries arrive newest-first. Running balance must be computed
            // oldest-first, then mapped back so each row shows the balance
            // as it stood right after that entry.
            dao.observeEntries(partyId).collectLatest { newestFirst ->
                val oldestFirst = newestFirst.reversed()

                var running = 0.0
                val rowsOldestFirst = oldestFirst.map { e ->
                    running += if (e.isGiven) e.amount else -e.amount
                    EntryRow(e, running)
                }

                currentRows = rowsOldestFirst.reversed()
                adapter.submit(currentRows)
                tvNoEntries.visibility = if (newestFirst.isEmpty()) View.VISIBLE else View.GONE

                updateBalanceHeader(running)
            }
        }
    }

    private fun updateBalanceHeader(balance: Double) {
        currentBalance = balance
        tvPartyBalance.text = Format.money(balance)
        when {
            balance > 0 -> {
                tvPartyBalance.setTextColor(ContextCompat.getColor(this, R.color.green_got))
                tvBalanceHint.setText(R.string.you_will_get)
            }
            balance < 0 -> {
                tvPartyBalance.setTextColor(ContextCompat.getColor(this, R.color.red_gave))
                tvBalanceHint.setText(R.string.you_will_give)
            }
            else -> {
                tvPartyBalance.setTextColor(ContextCompat.getColor(this, R.color.gold_accent))
                tvBalanceHint.setText(R.string.settled)
            }
        }
    }

    private fun showAddEntryDialog(isGiven: Boolean) {
        val view = layoutInflater.inflate(R.layout.dialog_add_entry, null)
        val etAmount: EditText = view.findViewById(R.id.etAmount)
        val etNote: EditText = view.findViewById(R.id.etNote)
        val etItemName: EditText = view.findViewById(R.id.etItemName)
        val etQuantity: EditText = view.findViewById(R.id.etQuantity)
        val etUnit: AutoCompleteTextView = view.findViewById(R.id.etUnit)
        val cbQarzeHasna: MaterialCheckBox = view.findViewById(R.id.cbQarzeHasna)

        // Suggest the units this trade actually uses — bag, maund, seer,
        // litre — rather than making the shopkeeper type them out each time.
        etUnit.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                resources.getStringArray(R.array.units)
            )
        )
        val rgRecovery: RadioGroup = view.findViewById(R.id.rgRecovery)
        val rbDoubtful: RadioButton = view.findViewById(R.id.rbDoubtful)
        val tvRecoveryLabel: TextView = view.findViewById(R.id.tvRecoveryLabel)

        // Recovery confidence only means something for money going *out*
        // (something I expect back). On an "I Got" entry there is nothing to
        // recover, so the choice is hidden rather than left to confuse.
        if (!isGiven) {
            tvRecoveryLabel.visibility = View.GONE
            rgRecovery.visibility = View.GONE
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(if (isGiven) R.string.i_gave else R.string.i_got)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val amount = etAmount.text.toString().trim().toDoubleOrNull()
                if (amount == null || amount <= 0.0) {
                    Toast.makeText(this, R.string.enter_valid_amount, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val note = etNote.text.toString().trim().ifEmpty { null }

                val recovery = if (isGiven && rbDoubtful.isChecked) {
                    Recovery.DOUBTFUL
                } else {
                    Recovery.CERTAIN
                }

                val itemName = etItemName.text.toString().trim().ifEmpty { null }
                val quantity = etQuantity.text.toString().trim().toDoubleOrNull()
                val unit = etUnit.text.toString().trim().ifEmpty { null }

                lifecycleScope.launch {
                    val count = dao.totalEntryCount()
                    dao.insertEntry(
                        LedgerEntry(
                            partyId = partyId,
                            amount = amount,
                            isGiven = isGiven,
                            note = note,
                            entryNumber = EntryNumber.next(count),
                            isQarzeHasna = cbQarzeHasna.isChecked,
                            recovery = recovery,
                            itemName = itemName,
                            quantity = quantity,
                            unit = unit
                        )
                    )
                }
            }
            .show()
    }

    /** Deleting an entry is reversible — it moves to the Recycle Bin. */
    private fun confirmDeleteEntry(entry: LedgerEntry) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_entry_title)
            .setMessage(R.string.delete_entry_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    dao.softDeleteEntry(entry.id)
                    Toast.makeText(
                        this@PartyDetailActivity,
                        R.string.moved_to_bin,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .show()
    }

    /**
     * Shows the message before it goes anywhere. The owner can edit every word,
     * then hands it off to WhatsApp or SMS and presses send themselves.
     * Roshan Khata never sends anything on its own.
     */
    private fun showReminderPreview(viaWhatsApp: Boolean) {
        if (currentBalance == 0.0) {
            Toast.makeText(this, R.string.nothing_outstanding, Toast.LENGTH_SHORT).show()
            return
        }

        if (partyPhone.isNullOrBlank()) {
            Toast.makeText(this, R.string.no_phone_number, Toast.LENGTH_LONG).show()
            return
        }

        val view = layoutInflater.inflate(R.layout.dialog_reminder_preview, null)
        val etMessage: EditText = view.findViewById(R.id.etMessage)
        etMessage.setText(
            Reminder.buildMessage(
                context = this,
                partyName = partyName,
                balance = currentBalance,
                businessName = BusinessProfile.businessName(this)
            )
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.send_reminder)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(
                if (viaWhatsApp) R.string.open_whatsapp else R.string.open_sms
            ) { _, _ ->
                val message = etMessage.text.toString()
                if (viaWhatsApp) {
                    Reminder.sendViaWhatsApp(this, partyPhone, message)
                } else {
                    Reminder.sendViaSms(this, partyPhone, message)
                }
            }
            .show()
    }

    /**
     * Builds the statement and hands it to whichever app the owner picks —
     * WhatsApp, email, the printer. The PDF lives in cache and is shared under
     * a temporary grant, so no other app can reach into our storage.
     */
    private fun exportStatement() {
        if (currentRows.isEmpty()) {
            Toast.makeText(this, R.string.no_entries_to_export, Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, R.string.generating_statement, Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            // Rendering a long ledger is real work — keep it off the main thread.
            val file = withContext(Dispatchers.IO) {
                PdfExport.buildStatement(
                    context = this@PartyDetailActivity,
                    partyName = partyName,
                    partyPhone = partyPhone,
                    rows = currentRows.map {
                        PdfExport.StatementRow(it.entry, it.runningBalance)
                    },
                    closingBalance = currentBalance,
                    businessName = BusinessProfile.businessName(this@PartyDetailActivity),
                    paymentQr = BusinessProfile.loadQr(this@PartyDetailActivity)
                )
            }

            if (file == null) {
                Toast.makeText(
                    this@PartyDetailActivity,
                    R.string.statement_failed,
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            val uri = FileProvider.getUriForFile(
                this@PartyDetailActivity,
                "$packageName.fileprovider",
                file
            )

            val share = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_statement))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(share, getString(R.string.share_statement)))
        }
    }
}
