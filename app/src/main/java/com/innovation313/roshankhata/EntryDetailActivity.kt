package com.innovation313.roshankhata

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.Format
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * Shows a single ledger entry as a shareable payment receipt, and lets the
 * owner edit the amount/note, share the receipt as an image, or delete it.
 *
 * Everything stays on the device: the shared image is written to the app's
 * cache and handed to the chooser via FileProvider — nothing is uploaded.
 */
class EntryDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ENTRY_ID = "entry_id"
        const val EXTRA_PARTY_NAME = "party_name"
    }

    private var entryId: Long = 0
    private var partyName: String = ""
    private var entry: LedgerEntry? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_entry_detail)

        entryId = intent.getLongExtra(EXTRA_ENTRY_ID, 0)
        partyName = intent.getStringExtra(EXTRA_PARTY_NAME).orEmpty()

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnDelete).setOnClickListener { confirmDelete() }
        findViewById<MaterialButton>(R.id.btnEdit).setOnClickListener { showEditDialog() }
        findViewById<MaterialButton>(R.id.btnShare).setOnClickListener { shareReceipt() }

        load()
    }

    private fun load() {
        lifecycleScope.launch {
            val dao = KhataDatabase.get(this@EntryDetailActivity).khataDao()
            val e = dao.getEntry(entryId)
            if (e == null) {
                Toast.makeText(this@EntryDetailActivity, R.string.entry_not_found, Toast.LENGTH_SHORT).show()
                finish(); return@launch
            }
            entry = e
            render(e)
        }
    }

    private fun render(e: LedgerEntry) {
        val tvDirection = findViewById<TextView>(R.id.tvDirection)
        val tvAmount = findViewById<TextView>(R.id.tvAmount)
        val banner = findViewById<View>(R.id.amountBanner)

        // "I gave" is money/goods out (a receivable) — red banner; "I got" is a
        // payment in — green banner. Same convention as the ledger colours.
        if (e.isGiven) {
            tvDirection.setText(R.string.you_gave)
            banner.setBackgroundResource(R.drawable.bg_receipt_red)
        } else {
            tvDirection.setText(R.string.you_got)
            banner.setBackgroundResource(R.drawable.bg_receipt_green)
        }
        tvAmount.text = Format.money(e.amount)

        findViewById<TextView>(R.id.tvParty).text = partyName
        findViewById<TextView>(R.id.tvEntryNumber).text = e.entryNumber
        findViewById<TextView>(R.id.tvDateTime).text = Format.dateTime(e.timestamp)

        val rowNote = findViewById<TableRow>(R.id.rowNote)
        if (e.note.isNullOrBlank()) {
            rowNote.visibility = View.GONE
        } else {
            rowNote.visibility = View.VISIBLE
            findViewById<TextView>(R.id.tvNote).text = e.note
        }

        val goods = Format.goods(e.itemName, e.quantity, e.unit)
        val rowGoods = findViewById<TableRow>(R.id.rowGoods)
        if (goods == null) {
            rowGoods.visibility = View.GONE
        } else {
            rowGoods.visibility = View.VISIBLE
            findViewById<TextView>(R.id.tvGoods).text = goods
        }

        findViewById<TextView>(R.id.tvBalance).text = Format.money(e.amount)
    }

    private fun showEditDialog() {
        val e = entry ?: return
        val view = layoutInflater.inflate(R.layout.dialog_edit_entry, null)
        val etAmount = view.findViewById<EditText>(R.id.etEditAmount)
        val etNote = view.findViewById<EditText>(R.id.etEditNote)
        etAmount.setText(Format.plain(e.amount))
        etNote.setText(e.note.orEmpty())

        // Starts at whatever the entry already carries, so leaving it alone
        // leaves it alone.
        var chosenTime = e.timestamp
        val btnDate = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnEditDate)
        val dateFmt = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())

        fun showChosenDate() {
            btnDate.text = getString(R.string.entry_date, dateFmt.format(java.util.Date(chosenTime)))
        }
        showChosenDate()

        btnDate.setOnClickListener {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = chosenTime }
            android.app.DatePickerDialog(
                this,
                { _, year, month, day ->
                    cal.set(java.util.Calendar.YEAR, year)
                    cal.set(java.util.Calendar.MONTH, month)
                    cal.set(java.util.Calendar.DAY_OF_MONTH, day)
                    android.app.TimePickerDialog(
                        this,
                        { _, hour, minute ->
                            cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
                            cal.set(java.util.Calendar.MINUTE, minute)
                            chosenTime = cal.timeInMillis
                            showChosenDate()
                        },
                        cal.get(java.util.Calendar.HOUR_OF_DAY),
                        cal.get(java.util.Calendar.MINUTE),
                        false
                    ).show()
                },
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH),
                cal.get(java.util.Calendar.DAY_OF_MONTH)
            ).apply {
                // Same rule as a new entry: nothing in the future.
                datePicker.maxDate = System.currentTimeMillis()
            }.show()
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.edit)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val amount = Calc.evalPad(etAmount.text.toString())
                if (amount == null || amount <= 0) {
                    Toast.makeText(this, R.string.invalid_amount, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val updated = e.copy(
                    amount = amount,
                    note = etNote.text.toString().trim().ifBlank { null },
                    timestamp = chosenTime
                )
                lifecycleScope.launch {
                    KhataDatabase.get(this@EntryDetailActivity).khataDao().updateEntry(updated)
                    entry = updated
                    render(updated)
                    Toast.makeText(this@EntryDetailActivity, R.string.saved, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_entry_title)
            .setMessage(R.string.delete_entry_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    KhataDatabase.get(this@EntryDetailActivity).khataDao().softDeleteEntry(entryId)
                    Toast.makeText(this@EntryDetailActivity, R.string.entry_deleted, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Render the receipt card to an image and offer it to the share sheet. */
    private fun shareReceipt() {
        val card = findViewById<View>(R.id.receiptCard)
        try {
            val bitmap = Bitmap.createBitmap(card.width, card.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            card.draw(canvas)

            val dir = File(cacheDir, "receipts").apply { mkdirs() }
            val file = File(dir, "receipt_${entryId}.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(share, getString(R.string.share)))
        } catch (ex: Exception) {
            Toast.makeText(this, R.string.share_failed, Toast.LENGTH_SHORT).show()
        }
    }
}
