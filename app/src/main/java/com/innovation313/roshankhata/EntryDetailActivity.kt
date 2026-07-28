package com.innovation313.roshankhata

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
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
import com.innovation313.roshankhata.data.AppScope
import com.innovation313.roshankhata.data.BatchOption
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.BillPhoto
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.ProductName
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.DateTimeField
import com.innovation313.roshankhata.ui.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

        // Edge-to-edge, the mechanism proven on the Home screen.
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)

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

        // The bill, if one was attached. Loaded off the main thread — it is a
        // file read, and a receipt should not stutter on the way in.
        val block = findViewById<View>(R.id.blockBillPhoto)
        val image = findViewById<android.widget.ImageView>(R.id.ivBillPhoto)
        if (e.billPhotoPath.isNullOrBlank()) {
            block.visibility = View.GONE
        } else {
            lifecycleScope.launch {
                val bitmap = withContext(Dispatchers.IO) { BillPhoto.load(e.billPhotoPath) }
                if (bitmap == null) {
                    // The file has gone — cleared by the system, or restored
                    // from a backup that carried the ledger but not the photos.
                    block.visibility = View.GONE
                } else {
                    image.setImageBitmap(bitmap)
                    block.visibility = View.VISIBLE
                }
            }
        }

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

    /**
     * The one thing changed here from the version this replaced: goods,
     * quantity, unit and — for a sale — which batch can now be corrected
     * too, not only the amount, note and date. Direction (I gave / I got) is
     * NOT offered, the same reasoning as a supplier bill's own edit screen:
     * that is not a typo an owner makes, and flipping it is a different and
     * far riskier operation than fixing what this entry actually says.
     */
    private fun showEditDialog() {
        val e = entry ?: return
        val view = layoutInflater.inflate(R.layout.dialog_edit_entry, null)
        val etAmount = view.findViewById<EditText>(R.id.etEditAmount)
        val etNote = view.findViewById<EditText>(R.id.etEditNote)
        val etItemName = view.findViewById<EditText>(R.id.etEditItemName)
        val etQuantity = view.findViewById<EditText>(R.id.etEditQuantity)
        val etUnit = view.findViewById<AutoCompleteTextView>(R.id.etEditUnit)
        val btnBatch = view.findViewById<MaterialButton>(R.id.btnEditBatch)

        etAmount.setText(Format.plain(e.amount))
        etNote.setText(e.note.orEmpty())
        etItemName.setText(e.itemName.orEmpty())
        etQuantity.setText(e.quantity?.let { Format.plain(it) } ?: "")
        etUnit.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, resources.getStringArray(R.array.units))
        )
        etUnit.setText(e.unit.orEmpty(), false)

        val dao = KhataDatabase.get(this).khataDao()

        // What this entry ends up tagged with. Both start at whatever it
        // already carries, so opening Edit and changing only the amount
        // leaves the product/batch link exactly as it was.
        var selectedBatch: BatchOption? = null
        var matchedProductId: Long? = e.productId
        var batchOptions: List<BatchOption> = emptyList()

        fun labelFor(batch: BatchOption?) = if (batch == null) {
            getString(R.string.pick_batch)
        } else {
            getString(R.string.batch_chosen, batch.batchNumber ?: getString(R.string.batch_none))
        }

        fun wireBatchClick() {
            btnBatch.setOnClickListener {
                showBatchPickerDialog(batchOptions) { chosen ->
                    selectedBatch = chosen
                    btnBatch.text = labelFor(chosen)
                }
            }
        }

        /**
         * Only for a sale. Runs on this screen's own lifecycleScope — a read
         * for the UI, not a write that must survive the screen closing, so it
         * is correct for it to be cancelled if the owner navigates away
         * mid-lookup, same as the add-entry screen's own version of this.
         */
        fun refreshBatchButton(typed: String) {
            if (!e.isGiven || typed.isEmpty()) {
                btnBatch.visibility = View.GONE
                selectedBatch = null
                matchedProductId = null
                return
            }
            lifecycleScope.launch {
                val product = dao.productByKey(ProductName.key(typed))
                if (etItemName.text.toString().trim() != typed) return@launch

                matchedProductId = product?.id
                batchOptions = product?.let { dao.batchOptionsForProduct(it.id) } ?: emptyList()

                if (batchOptions.isEmpty()) {
                    btnBatch.visibility = View.GONE
                    selectedBatch = null
                } else {
                    btnBatch.visibility = View.VISIBLE
                    // A batch already chosen survives a lookup that still
                    // offers it; the item name did not really change.
                    selectedBatch = selectedBatch?.takeIf { sel -> batchOptions.any { it.id == sel.id } }
                    btnBatch.text = labelFor(selectedBatch)
                    wireBatchClick()
                }
            }
        }

        // Prime the button with what this entry already has, before the
        // owner touches anything, so opening Edit never looks like a choice
        // was forgotten.
        val entryProductId = e.productId
        if (e.isGiven && entryProductId != null) {
            lifecycleScope.launch {
                val options = dao.batchOptionsForProduct(entryProductId)
                batchOptions = options
                if (options.isNotEmpty()) {
                    btnBatch.visibility = View.VISIBLE
                    selectedBatch = e.billItemId?.let { id -> options.find { it.id == id } }
                    btnBatch.text = labelFor(selectedBatch)
                    wireBatchClick()
                } else {
                    btnBatch.visibility = View.GONE
                }
            }
        } else if (e.isGiven && !e.itemName.isNullOrBlank()) {
            // Not yet tagged to a product — an older entry, or one the
            // backfill has not reached — so fall back to the same
            // name lookup the add-entry screen uses.
            refreshBatchButton(e.itemName)
        } else {
            btnBatch.visibility = View.GONE
        }

        etItemName.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) refreshBatchButton(etItemName.text.toString().trim())
        }

        // Starts at whatever the entry already carries, so leaving it alone
        // leaves it alone.
        var chosenTime = e.timestamp
        DateTimeField.attach(
            activity = this,
            button = view.findViewById(R.id.btnEditDate),
            initial = chosenTime
        ) { chosenTime = it }

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
                    timestamp = chosenTime,
                    itemName = etItemName.text.toString().trim().ifEmpty { null },
                    quantity = etQuantity.text.toString().trim().toDoubleOrNull(),
                    unit = etUnit.text.toString().trim().ifEmpty { null },
                    productId = matchedProductId,
                    billItemId = selectedBatch?.id
                )
                // AppScope, not lifecycleScope — see AppScope's own comment.
                // This dialog closes the instant Save is tapped, while the
                // write is still in flight; a quick Back press right after
                // must not be able to cancel a correction any more than it
                // could a new entry.
                AppScope.launch {
                    dao.updateEntry(updated)
                    withContext(Dispatchers.Main) {
                        if (!isFinishing && !isDestroyed) {
                            entry = updated
                            render(updated)
                            Toast.makeText(this@EntryDetailActivity, R.string.saved, Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Which batch this sale came out of, or none. The same picker the
     * add-entry screen uses, kept here as its own copy rather than shared,
     * since the two screens are different classes and a shared helper would
     * need to live somewhere neither naturally owns.
     */
    private fun showBatchPickerDialog(options: List<BatchOption>, onPicked: (BatchOption?) -> Unit) {
        val labels = arrayOf(getString(R.string.pick_batch_clear)) + options.map { o ->
            buildString {
                append(
                    o.batchNumber?.let { getString(R.string.batch_label, it) }
                        ?: getString(R.string.batch_none)
                )
                append(" — ")
                append(getString(R.string.batch_remaining, Format.qty(o.remaining, o.unit)))
                o.expiryDate?.let {
                    append(" · ")
                    append(Format.dateOnly(it))
                }
            }
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.pick_batch)
            .setItems(labels) { _, which ->
                onPicked(if (which == 0) null else options[which - 1])
            }
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
