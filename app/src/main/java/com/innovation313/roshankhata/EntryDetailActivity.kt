package com.innovation313.roshankhata

import com.innovation313.roshankhata.data.get
import com.innovation313.roshankhata.data.labelOf

import com.innovation313.roshankhata.ui.ChoiceSheet
import com.innovation313.roshankhata.data.UnitWords
import com.innovation313.roshankhata.data.TradeVocab
import android.content.Intent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.ui.SeasonText
import com.innovation313.roshankhata.data.SeasonBook
import com.innovation313.roshankhata.data.TradeFeatures
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.innovation313.roshankhata.data.PaymentMethod
import com.innovation313.roshankhata.data.AppScope
import com.innovation313.roshankhata.data.BatchOption
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.BillPhoto
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.EntryItem
import com.innovation313.roshankhata.data.ProductName
import com.innovation313.roshankhata.ui.SmartSuggest
import com.innovation313.roshankhata.ui.asSuggestions
import com.innovation313.roshankhata.ui.withStarters
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.DateTimeField
import com.innovation313.roshankhata.ui.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import com.innovation313.roshankhata.data.Digits

/**
 * Shows a single ledger entry as a shareable payment receipt, and lets the
 * owner edit the amount/note, share the receipt as an image, or delete it.
 *
 * Everything stays on the device: the shared image is written to the app's
 * cache and handed to the chooser via FileProvider — nothing is uploaded.
 */
class EntryDetailActivity : BaseActivity() {

    companion object {
        const val EXTRA_ENTRY_ID = "entry_id"
        const val EXTRA_PARTY_NAME = "party_name"
    }

    private var entryId: Long = 0
    private var partyName: String = ""
    private var entry: LedgerEntry? = null
    /** This entry's goods lines, in order. Empty for a money-only entry. */
    private var items: List<EntryItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_entry_detail)

        // Edge-to-edge, the mechanism proven on the Home screen.
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)

        entryId = intent.getLongExtra(EXTRA_ENTRY_ID, 0)
        partyName = intent.getStringExtra(EXTRA_PARTY_NAME).orEmpty()

        // The receipt is the shop's paper, so it carries the shop's name
        // (13 Oct); the app's name only until the owner has entered one.
        findViewById<TextView>(R.id.tvBusinessName).text =
            com.innovation313.roshankhata.data.BusinessProfile.businessName(this)
                ?.trim()?.ifEmpty { null } ?: getString(R.string.app_name)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnDelete).setOnClickListener { confirmDelete() }
        findViewById<MaterialButton>(R.id.btnEdit).setOnClickListener { showEditDialog() }
        findViewById<MaterialButton>(R.id.btnShare).setOnClickListener { shareReceipt() }
        findViewById<MaterialButton>(R.id.btnPrint).setOnClickListener { printReceipt() }
        // The customer's signature (6 Oct). A helper's phone shows it but cannot take one.
        findViewById<MaterialButton>(R.id.btnSignature).apply {
            visibility = if (com.innovation313.roshankhata.data.ViewerMode.isOn(this@EntryDetailActivity)) View.GONE else View.VISIBLE
            setOnClickListener { takeSignature() }
        }
        showSignature()

        load()
    }

    /**
     * Back from the invoice editor: if an invoice was saved for this sale,
     * the button must now open it rather than start a second one.
     */
    private var seenOnce = false
    override fun onResume() {
        super.onResume()
        if (seenOnce) load() else seenOnce = true
    }

    /**
     * "Season: Rabi 2026-27 (from date) · Change" on a sale (2 Oct). The
     * choice is the season by date, the one before or after it; picking the
     * date's own clears the override so a later date change still follows.
     */
    private fun renderSeason(e: LedgerEntry) {
        val row = findViewById<View>(R.id.rowSeason)
        if (!e.isGiven || e.isDeleted || !TradeFeatures.seasons(this)) { row.visibility = View.GONE; return }
        row.visibility = View.VISIBLE
        val byDate = SeasonBook.seasonOf(e.timestamp)
        val own = SeasonBook.Season.fromKey(e.season)
        val shown = own ?: byDate
        findViewById<TextView>(R.id.tvSeason).text = getString(
            R.string.season_entry,
            if (own == null) getString(R.string.season_from_date, SeasonText.name(this, shown))
            else SeasonText.name(this, shown)
        )
        findViewById<MaterialButton>(R.id.btnSeason).setOnClickListener {
            val options = listOf(byDate.previous(), byDate, byDate.next())
            val labels = options.map {
                if (it == byDate) getString(R.string.season_from_date, SeasonText.name(this, it))
                else SeasonText.name(this, it)
            }.toTypedArray()
            val choices = labels.map {
                ChoiceSheet.Option(it, icon = R.drawable.ic_tile_season, iconTint = 0, tile = R.color.tile_plans_bg)
            }
            ChoiceSheet.show(this, getString(R.string.season_pick), choices, options.indexOf(shown)) { i ->
                val pick = options[i]
                lifecycleScope.launch {
                    KhataDatabase.get(this@EntryDetailActivity).khataDao()
                        .setEntrySeason(e.id, if (pick == byDate) null else pick.key)
                    load()
                }
            }
        }
    }

    /** "Write spray advice" on a sale with goods lines (v25). */
    private fun renderAdvice(e: LedgerEntry) {
        val btn = findViewById<MaterialButton>(R.id.btnAdvice)
        val goods = items.filter { !it.itemName.isNullOrBlank() }
        if (!e.isGiven || e.isDeleted || goods.isEmpty() || !TradeFeatures.sprayAdvice(this)) {
            btn.visibility = View.GONE; return
        }
        btn.visibility = View.VISIBLE
        btn.setOnClickListener {
            if (goods.size == 1) { editAdvice(goods[0]); return@setOnClickListener }
            ChoiceSheet.show(this, getString(R.string.advice_pick_line),
                goods.map { ChoiceSheet.batch(it.itemName.orEmpty()) }) { i -> editAdvice(goods[i]) }
        }
    }

    private fun editAdvice(item: EntryItem) {
        val view = layoutInflater.inflate(R.layout.dialog_line_advice, null)
        val etCrop = view.findViewById<EditText>(R.id.etAdviceCrop).apply { setText(item.crop) }
        val etPest = view.findViewById<EditText>(R.id.etAdvicePest).apply { setText(item.pest) }
        val etDose = view.findViewById<EditText>(R.id.etAdviceDose).apply { setText(item.dose) }
        fun typed(et: EditText) = et.text.toString().trim().ifEmpty { null }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.advice_title, item.itemName.orEmpty()))
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                AppScope.launch {
                    KhataDatabase.get(this@EntryDetailActivity).khataDao()
                        .setLineAdvice(item.id, typed(etCrop), typed(etPest), typed(etDose))
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (!isFinishing && !isDestroyed) load()
                    }
                }
            }
            .show().also { com.innovation313.roshankhata.ui.KeyboardFollow.dialog(it) }
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
            items = dao.itemsOfEntry(entryId)
            render(e)
        }
    }

    private fun render(e: LedgerEntry) {
        // A sale can become an invoice; a payment received cannot.
        renderSeason(e)
        renderAdvice(e)

        // Once invoiced, the same button opens that invoice instead of a twin.
        val btnInvoice = findViewById<MaterialButton>(R.id.btnMakeInvoice)
        btnInvoice.visibility = if (e.isGiven && !e.isDeleted) View.VISIBLE else View.GONE
        if (e.isGiven && !e.isDeleted) lifecycleScope.launch {
            val existing = KhataDatabase.get(this@EntryDetailActivity).khataDao().invoiceForEntry(e.id)
            btnInvoice.setText(if (existing != null) R.string.entry_open_invoice else R.string.entry_make_invoice)
            btnInvoice.setOnClickListener {
                val intent = Intent(this@EntryDetailActivity, InvoiceEditorActivity::class.java)
                if (existing != null) intent.putExtra(InvoiceEditorActivity.EXTRA_INVOICE_ID, existing)
                else intent.putExtra(InvoiceEditorActivity.EXTRA_FROM_ENTRY_ID, e.id)
                startActivity(intent)
            }
        }
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

        // Each item with its rate and total, then the items' total, the rate
        // type and any difference from the amount — this card IS the receipt
        // the customer is sent.
        val goods = com.innovation313.roshankhata.ui.GoodsText.receipt(this, items, e.rateType, e.amount)
        val rowGoods = findViewById<TableRow>(R.id.rowGoods)
        if (goods == null) {
            rowGoods.visibility = View.GONE
        } else {
            rowGoods.visibility = View.VISIBLE
            findViewById<TextView>(R.id.tvGoods).text = goods
        }

        // How the money arrived, when it was recorded. Hidden entirely when it
        // was not: a blank row would suggest the question was asked and left
        // unanswered, and an entry that predates the column never had it to
        // answer. Never guessed — an unrecorded method is not cash.
        val rowPaymentMethod = findViewById<TableRow>(R.id.rowPaymentMethod)
        val method = PaymentMethod.labelOf(this, e.paymentMethod)
        if (method == null) {
            rowPaymentMethod.visibility = View.GONE
        } else {
            rowPaymentMethod.visibility = View.VISIBLE
            findViewById<TextView>(R.id.tvPaymentMethod).text = method
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
        // An entry made with the item list — several items, a rate, or a free
        // item — is edited on the full form it was made on; this small dialog
        // cannot show a list or a rate honestly. Plain and older entries keep
        // this dialog, unchanged.
        if (items.size > 1 || items.any { it.rate != null || it.isBonus }) {
            startActivity(
                Intent(this, PartyDetailActivity::class.java)
                    .putExtra(PartyDetailActivity.EXTRA_PARTY_ID, e.partyId)
                    .putExtra(PartyDetailActivity.EXTRA_EDIT_ENTRY_ID, e.id)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            finish()
            return
        }
        val view = layoutInflater.inflate(R.layout.dialog_edit_entry, null)
        com.innovation313.roshankhata.ui.TextFit.relax(view)
        val etAmount = view.findViewById<EditText>(R.id.etEditAmount)
        val etNote = view.findViewById<EditText>(R.id.etEditNote)
        val etItemName = view.findViewById<AutoCompleteTextView>(R.id.etEditItemName)
        // The fields sit in outlined boxes (18 Oct): the box carries the
        // label, so the trade's own words go there, not into the field too.
        fun boxHint(field: View, text: CharSequence) {
            val box = generateSequence(field.parent) { it.parent }
                .filterIsInstance<com.google.android.material.textfield.TextInputLayout>().firstOrNull()
            if (box != null) box.hint = text else (field as? EditText)?.hint = text
        }
        boxHint(etItemName, TradeVocab.itemHint(this))
        val etQuantity = view.findViewById<EditText>(R.id.etEditQuantity)
        val etUnit = view.findViewById<AutoCompleteTextView>(R.id.etEditUnit)
        boxHint(etUnit, TradeVocab.unitHint(this))
        val btnBatch = view.findViewById<MaterialButton>(R.id.btnEditBatch)

        // Header (18 Oct): whose entry, which number, and which way.
        val color = { id: Int -> androidx.core.content.ContextCompat.getColor(this, id) }
        view.findViewById<TextView>(R.id.tvEditWhose).text =
            listOf(partyName, e.entryNumber).filter { it.isNotBlank() }.joinToString("  ·  ")
        view.findViewById<TextView>(R.id.tvEditDirection).apply {
            setText(if (e.isGiven) R.string.you_gave else R.string.you_got)
            backgroundTintList = android.content.res.ColorStateList.valueOf(
                color(if (e.isGiven) R.color.red_gave else R.color.brand_green)
            )
        }
        view.findViewById<android.widget.ImageView>(R.id.ivEditDirection).apply {
            setImageResource(if (e.isGiven) R.drawable.ic_arrow_gave else R.drawable.ic_arrow_got)
            imageTintList = android.content.res.ColorStateList.valueOf(
                color(if (e.isGiven) R.color.red_gave_text else R.color.brand_green_text)
            )
        }
        view.findViewById<View>(R.id.editIconTile).backgroundTintList =
            android.content.res.ColorStateList.valueOf(
                color(if (e.isGiven) R.color.tile_stock_bg else R.color.brand_green_soft)
            )
        etAmount.setTextColor(color(if (e.isGiven) R.color.red_gave_text else R.color.brand_green_text))

        // The signature, editable here too; filed only when Save is tapped.
        var editTime = e.timestamp
        val sigRow = com.innovation313.roshankhata.ui.SignatureRow(
            this, view.findViewById(R.id.sigRow),
            about = {
                com.innovation313.roshankhata.ui.SignatureSheet.About(
                    party = partyName.ifBlank { null },
                    isGiven = e.isGiven,
                    amount = Calc.evalAmount(etAmount.text.toString()) ?: e.amount,
                    time = editTime
                )
            }
        )
        lifecycleScope.launch {
            val old = withContext(Dispatchers.IO) {
                com.innovation313.roshankhata.data.EntrySignature.load(this@EntryDetailActivity, e.id)
            }
            sigRow.showExisting(old)
        }

        etAmount.setText(Format.plain(e.amount))
        etNote.setText(e.note.orEmpty())
        // Pre-fill without asking the adapter to match — see the same call in
        // BillsActivity. Harmless today because the products load after this,
        // and not left resting on that ordering.
        // Goods live on the entry's lines (v20). This dialog edits a single
        // line — which every entry has at most, until multi-item entry
        // arrives. If an entry ever carries SEVERAL lines, this simple form
        // cannot show them honestly, so the goods fields are hidden and the
        // lines are left exactly as they are; only amount, note and date can
        // be changed here. Collapsing three lines into one would lose goods.
        val line = items.firstOrNull()
        val canEditGoods = items.size <= 1
        etItemName.setText(line?.itemName.orEmpty(), false)
        etQuantity.setText(line?.quantity?.let { Format.plain(it) } ?: "")
        etUnit.setAdapter(
            ArrayAdapter(this, R.layout.item_dropdown_row, UnitWords.choices(this))
        )
        etUnit.setText(UnitWords.label(line?.unit), false)
        if (!canEditGoods) {
            // The whole goods group, by id: inside outlined boxes a field's
            // parent is the box's frame, not the row it used to be.
            view.findViewById<View>(R.id.blockEditGoods).visibility = View.GONE
        }

        val dao = KhataDatabase.get(this).khataDao()

        // What this entry ends up tagged with. Both start at whatever it
        // already carries, so opening Edit and changing only the amount
        // leaves the product/batch link exactly as it was.
        var selectedBatch: BatchOption? = null
        var matchedProductId: Long? = line?.productId
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
        val entryProductId = line?.productId
        if (!canEditGoods) {
            // Nothing to prime: the goods fields are hidden (see above).
        } else if (e.isGiven && entryProductId != null) {
            lifecycleScope.launch {
                val options = dao.batchOptionsForProduct(entryProductId)
                batchOptions = options
                if (options.isNotEmpty()) {
                    btnBatch.visibility = View.VISIBLE
                    selectedBatch = line?.billItemId?.let { id -> options.find { it.id == id } }
                    btnBatch.text = labelFor(selectedBatch)
                    wireBatchClick()
                } else {
                    btnBatch.visibility = View.GONE
                }
            }
        } else if (e.isGiven && !line?.itemName.isNullOrBlank()) {
            // Not yet tagged to a product — an older entry, or one the
            // backfill has not reached — so fall back to the same
            // name lookup the add-entry screen uses.
            refreshBatchButton(line?.itemName.orEmpty())
        } else {
            btnBatch.visibility = View.GONE
        }

        etItemName.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) refreshBatchButton(etItemName.text.toString().trim())
        }

        // The same product list Add Entry offers, so correcting a name reaches
        // the same products as writing it did. Read once as the dialog opens.
        lifecycleScope.launch {
            SmartSuggest.attach(etItemName, dao.productsOnce().withStarters(this@EntryDetailActivity))
        }

        // Tapping a suggestion finishes the name, so the batch lookup runs
        // now rather than waiting for the focus to leave the box.
        etItemName.setOnItemClickListener { _, _, _, _ ->
            refreshBatchButton(etItemName.text.toString().trim())
        }

        // Starts at whatever the entry already carries, so leaving it alone
        // leaves it alone.
        var chosenTime = e.timestamp
        DateTimeField.attach(
            activity = this,
            button = view.findViewById(R.id.btnEditDate),
            initial = chosenTime
        ) { chosenTime = it; editTime = it }

        MaterialAlertDialogBuilder(this)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val amount = Calc.evalAmount(etAmount.text.toString())
                if (amount == null) {
                    Toast.makeText(this, R.string.invalid_amount, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                // Only money, note and date live on the entry. The legacy goods
                // columns are carried through untouched (copy keeps them) —
                // nothing writes them any more.
                val updated = e.copy(
                    amount = amount,
                    note = etNote.text.toString().trim().ifBlank { null },
                    timestamp = chosenTime
                )
                // The goods, as the entry's complete new set of lines. A
                // several-line entry keeps its lines untouched (see above).
                // A single line keeps its rate and bonus flag, which this
                // dialog does not show; everything else is what was typed.
                val newItems: List<EntryItem> = if (!canEditGoods) {
                    items
                } else {
                    listOfNotNull(
                        EntryItem.ofGoods(
                            itemName = etItemName.text.toString().trim().ifEmpty { null },
                            quantity = Digits.parse(etQuantity.text),
                            unit = UnitWords.canonical(etUnit.text.toString()),
                            productId = matchedProductId,
                            billItemId = selectedBatch?.id,
                            rate = line?.rate
                        )?.copy(
                            isBonus = line?.isBonus ?: false,
                            // The line's spray advice (v25) is not on this
                            // small form; it must survive the edit.
                            crop = line?.crop, pest = line?.pest, dose = line?.dose
                        )
                    )
                }
                // AppScope, not lifecycleScope — see AppScope's own comment.
                // This dialog closes the instant Save is tapped, while the
                // write is still in flight; a quick Back press right after
                // must not be able to cancel a correction any more than it
                // could a new entry.
                val sigChanged = sigRow.changed
                val sig = sigRow.current
                AppScope.launch {
                    dao.updateEntryWithItems(updated, newItems)
                    if (sigChanged) runCatching {
                        if (sig != null) com.innovation313.roshankhata.data.EntrySignature.save(applicationContext, updated.id, sig)
                        else com.innovation313.roshankhata.data.EntrySignature.delete(applicationContext, updated.id)
                    }
                    val savedItems = dao.itemsOfEntry(updated.id)
                    withContext(Dispatchers.Main) {
                        if (!isFinishing && !isDestroyed) {
                            entry = updated
                            items = savedItems
                            render(updated)
                            if (sigChanged) showSignature()
                            Toast.makeText(this@EntryDetailActivity, R.string.saved, Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show().also { com.innovation313.roshankhata.ui.KeyboardFollow.dialog(it) }
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

        ChoiceSheet.show(this, getString(R.string.pick_batch), labels.map { ChoiceSheet.batch(it) }) { which ->
            onPicked(if (which == 0) null else options[which - 1])
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_entry_title)
            .setMessage(R.string.delete_entry_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    val dao = KhataDatabase.get(this@EntryDetailActivity).khataDao()
                    // Half of a cash sale: offer to bin the other half with it —
                    // one half alone leaves the balance wrong.
                    val pair = entry?.pairedEntryId?.let { dao.getEntry(it) }?.takeIf { !it.isDeleted }
                    if (pair != null) {
                        AlertDialog.Builder(this@EntryDetailActivity)
                            .setTitle(R.string.delete_pair_title)
                            .setMessage(getString(R.string.delete_pair_message, pair.entryNumber))
                            .setPositiveButton(R.string.delete_pair_both) { _, _ ->
                                lifecycleScope.launch {
                                    dao.softDeleteEntry(entryId)
                                    dao.softDeleteEntry(pair.id)
                                    entry?.let { com.innovation313.roshankhata.ui.UndoDelete.pending =
                                        com.innovation313.roshankhata.ui.UndoDelete.Pending(it.partyId, listOf(entryId, pair.id)) }
                                    finish()
                                }
                            }
                            .setNegativeButton(R.string.delete_pair_one) { _, _ ->
                                lifecycleScope.launch {
                                    dao.softDeleteEntry(entryId)
                                    entry?.let { com.innovation313.roshankhata.ui.UndoDelete.pending =
                                        com.innovation313.roshankhata.ui.UndoDelete.Pending(it.partyId, listOf(entryId)) }
                                    finish()
                                }
                            }
                            .show()
                        return@launch
                    }
                    dao.softDeleteEntry(entryId)
                    entry?.let { com.innovation313.roshankhata.ui.UndoDelete.pending =
                        com.innovation313.roshankhata.ui.UndoDelete.Pending(it.partyId, listOf(entryId)) }
                    finish()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------- Signature (6 Oct) ----------

    private fun showSignature() {
        lifecycleScope.launch {
            val (sig, at) = withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.innovation313.roshankhata.data.EntrySignature.load(this@EntryDetailActivity, entryId) to
                    com.innovation313.roshankhata.data.EntrySignature.signedAt(this@EntryDetailActivity, entryId)
            }
            findViewById<View>(R.id.blockSignature).visibility = if (sig != null) View.VISIBLE else View.GONE
            findViewById<android.widget.ImageView>(R.id.ivSignature).setImageBitmap(sig)
            findViewById<TextView>(R.id.tvSignedAt).text =
                at?.let { getString(R.string.sig_signed_at, Format.dateTime(it)) }.orEmpty()
            findViewById<MaterialButton>(R.id.btnSignature).setText(if (sig != null) R.string.sig_retake else R.string.sig_take)
        }
    }

    /** What the signature screen shows at the top: this entry. */
    private fun signatureAbout(): com.innovation313.roshankhata.ui.SignatureSheet.About {
        val e = entry
        return com.innovation313.roshankhata.ui.SignatureSheet.About(
            party = partyName.ifBlank { null },
            isGiven = e?.isGiven ?: true,
            amount = e?.amount,
            time = e?.timestamp
        )
    }

    /** The redesigned signature screen (18 Oct); saved the moment it comes back. */
    private fun takeSignature() {
        com.innovation313.roshankhata.ui.SignatureSheet.show(this, signatureAbout()) { bmp ->
            lifecycleScope.launch {
                val ok = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { com.innovation313.roshankhata.data.EntrySignature.save(this@EntryDetailActivity, entryId, bmp) }.isSuccess
                }
                if (ok) {
                    com.innovation313.roshankhata.ui.Motion.savedTick(this@EntryDetailActivity)
                    showSignature()
                } else {
                    Toast.makeText(this@EntryDetailActivity, R.string.share_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ---------- Print (5 Oct): the receipt card on a paired Bluetooth printer ----------

    private val askConnect = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) choosePrinter()
        else Toast.makeText(this, R.string.print_permission, Toast.LENGTH_LONG).show()
    }

    private fun printReceipt() {
        if (android.os.Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            askConnect.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
            return
        }
        choosePrinter()
    }

    /** Pick from the printers the phone has paired; the last one used is ticked. */
    private fun choosePrinter() {
        val adapter = com.innovation313.roshankhata.ui.ReceiptPrinter.adapter(this)
        if (adapter == null) {
            Toast.makeText(this, R.string.print_no_bluetooth, Toast.LENGTH_LONG).show(); return
        }
        if (!adapter.isEnabled) {
            Toast.makeText(this, R.string.print_bluetooth_off, Toast.LENGTH_LONG).show(); return
        }
        val devices = com.innovation313.roshankhata.ui.ReceiptPrinter.paired(this)
        if (devices.isEmpty()) {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setMessage(R.string.print_no_printer)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        val prefs = getSharedPreferences("printer", MODE_PRIVATE)
        val last = devices.indexOfFirst { it.address == prefs.getString("address", null) }
        val choices = devices.map {
            ChoiceSheet.Option(com.innovation313.roshankhata.ui.ReceiptPrinter.label(it), icon = R.drawable.ic_print,
                iconTint = R.color.tile_calc_fg, tile = R.color.tile_calc_bg)
        }
        ChoiceSheet.show(this, getString(R.string.print_choose), choices, last) { which ->
            prefs.edit().putString("address", devices[which].address).apply()
            sendToPrinter(devices[which])
        }
    }

    private fun sendToPrinter(device: android.bluetooth.BluetoothDevice) {
        val card = findViewById<View>(R.id.receiptCard)
        val picture = try {
            Bitmap.createBitmap(card.width, card.height, Bitmap.Config.ARGB_8888).also { card.draw(Canvas(it)) }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.print_failed, Toast.LENGTH_LONG).show(); return
        }
        val button = findViewById<MaterialButton>(R.id.btnPrint)
        button.isEnabled = false
        Toast.makeText(this, R.string.print_sending, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val ok = withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    com.innovation313.roshankhata.ui.ReceiptPrinter.send(
                        device, com.innovation313.roshankhata.ui.ReceiptPrinter.bytes(picture))
                    true
                } catch (e: Exception) {
                    false
                } finally {
                    picture.recycle()
                }
            }
            button.isEnabled = true
            Toast.makeText(this@EntryDetailActivity,
                if (ok) R.string.print_done else R.string.print_failed, Toast.LENGTH_LONG).show()
        }
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
