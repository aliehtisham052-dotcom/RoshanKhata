package com.innovation313.roshankhata

import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.DateRangeFilter
import com.innovation313.roshankhata.ui.DateTimeField

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
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
import com.innovation313.roshankhata.data.PartyPhoto
import com.innovation313.roshankhata.data.PdfExport
import com.innovation313.roshankhata.data.BillPhoto
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

        /** A camera capture waiting to come back, kept across a rebuild. */
        private const val STATE_CAMERA_PATH = "camera_path"
        private const val STATE_CAMERA_TARGET = "camera_target"

        /**
         * A spoken entry, handed over from the ledger for the owner to check.
         *
         * Pre-filled, never saved: it lands in the normal dialog so the credit
         * limit warning and every other check still run, and so a mishearing
         * is caught before it reaches the books rather than after.
         */
        const val EXTRA_VOICE_AMOUNT = "voice_amount"
        const val EXTRA_VOICE_IS_GIVEN = "voice_is_given"
    }

    /**
     * Set while an entry dialog is open and waiting for a bill photo.
     *
     * The picker takes over the screen, so the dialog is not on top when the
     * result comes back — the callback updates this and the button, and the
     * dialog reads it when Save is pressed.
     */
    private var pendingBillPhoto: String? = null
    private var billButton: com.google.android.material.button.MaterialButton? = null

    private val pickBillPhoto = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri: android.net.Uri? ->
        if (uri != null) saveBillPhoto(uri, null)
    }

    /**
     * Keep a bill, wherever it came from.
     *
     * [temp] is the camera's working file when the photo was just taken, and
     * null when it was picked from the phone — in that case the picture
     * belongs to the owner's gallery and is not ours to delete.
     */
    private fun saveBillPhoto(uri: android.net.Uri, temp: java.io.File?) {
        lifecycleScope.launch {
            val path = withContext(Dispatchers.IO) {
                BillPhoto.save(this@PartyDetailActivity, uri)
            }
            temp?.delete()
            if (path == null) {
                Toast.makeText(
                    this@PartyDetailActivity,
                    R.string.bill_photo_failed,
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            // Replacing an earlier pick: the old file is nothing but bytes now.
            BillPhoto.delete(pendingBillPhoto)
            pendingBillPhoto = path
            billButton?.setText(R.string.bill_photo_attached)
        }
    }

    private var partyId: Long = 0
    private var partyName: String = ""
    private var partyPhone: String? = null
    private var currentBalance: Double = 0.0
    private var creditLimit: Double? = null
    private var currentRows: List<EntryRow> = emptyList()
    private lateinit var etSearchEntries: EditText
    private lateinit var ivAvatar: ImageView
    private lateinit var tvInitials: TextView

    private val pickPhoto = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) savePhoto(uri)
    }

    /**
     * Which photo the camera is being opened for.
     *
     * One launcher serves both the customer's photo and a bill, because the
     * camera does not care which, and two launchers would be two places to
     * get the clean-up wrong.
     */
    private enum class PhotoTarget { PARTY, BILL }

    /**
     * The capture in flight, if any.
     *
     * Held as a path rather than a Uri because both are needed afterwards: the
     * Uri to read the picture back, the File to delete it. And kept across
     * [onSaveInstanceState] because the camera is another app — Android is
     * free to destroy this screen while it is in front, and a photo that came
     * back to a screen that had forgotten it asked would be a photo lost after
     * the owner had already taken it.
     */
    private var cameraPath: String? = null
    private var cameraTarget: PhotoTarget? = null

    private val takePhoto = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { written: Boolean ->
        val file = cameraPath?.let { java.io.File(it) }
        val target = cameraTarget
        cameraPath = null
        cameraTarget = null

        // Cancelled, or the camera came back with nothing. Either way there is
        // no photo, and any empty file it left behind is rubbish.
        if (!written || file == null || target == null) {
            file?.delete()
            return@registerForActivityResult
        }

        val uri = try {
            androidx.core.content.FileProvider.getUriForFile(
                this, "$packageName.fileprovider", file
            )
        } catch (e: Exception) {
            file.delete()
            Toast.makeText(this, R.string.photo_save_failed, Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }

        when (target) {
            PhotoTarget.PARTY -> savePhoto(uri, file)
            PhotoTarget.BILL -> saveBillPhoto(uri, file)
        }
    }

    /** Every row, with running balances already computed. Views derive from this. */
    private var allRows: List<EntryRow> = emptyList()
    private var entrySortMode = EntrySort.NEWEST

    /** Which stretch of days the ledger is showing. All of them, until asked. */
    private var entryDateRange = DateRangeFilter.Range.ALL

    private enum class EntrySort { NEWEST, OLDEST, AMOUNT_HIGH, AMOUNT_LOW }
    private lateinit var adapter: EntryAdapter
    private lateinit var tvPartyName: TextView
    private lateinit var tvPartyBalance: TextView
    private lateinit var tvBalanceHint: TextView
    private lateinit var tvNoEntries: TextView

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // A capture that was in flight when this screen was destroyed. The
        // camera is another app and can be in front for minutes; Android may
        // reclaim what is behind it. Without this the photo would come back to
        // a screen that had forgotten it asked, and be thrown away after the
        // owner had already taken it.
        cameraPath = savedInstanceState?.getString(STATE_CAMERA_PATH)
        cameraTarget = savedInstanceState?.getString(STATE_CAMERA_TARGET)
            ?.let { runCatching { PhotoTarget.valueOf(it) }.getOrNull() }
        setContentView(R.layout.activity_party_detail)

        partyId = intent.getLongExtra(EXTRA_PARTY_ID, 0)

        // Arrived by voice: open the entry already filled in, once the screen
        // has settled and the balance is known.
        val spokenAmount = intent.getDoubleExtra(EXTRA_VOICE_AMOUNT, 0.0)
        if (spokenAmount > 0.0) {
            val spokenGiven = intent.getBooleanExtra(EXTRA_VOICE_IS_GIVEN, true)
            findViewById<View>(android.R.id.content).post {
                showAddEntryDialog(spokenGiven, prefillAmount = spokenAmount)
            }
        }
        if (partyId == 0L) {
            finish()
            return
        }

        setSupportActionBar(findViewById<Toolbar>(R.id.detailToolbar))
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        tvPartyName = findViewById(R.id.tvPartyName)
        tvPartyBalance = findViewById(R.id.tvPartyBalance)
        tvPartyBalance.setOnClickListener { copyBalance() }
        tvBalanceHint = findViewById(R.id.tvBalanceHint)
        tvNoEntries = findViewById(R.id.tvNoEntries)

        adapter = EntryAdapter(
            onClick = { entry ->
                startActivity(
                    Intent(this, EntryDetailActivity::class.java)
                        .putExtra(EntryDetailActivity.EXTRA_ENTRY_ID, entry.id)
                        .putExtra(EntryDetailActivity.EXTRA_PARTY_NAME, partyName)
                )
            },
            onLongClick = { entry -> confirmDeleteEntry(entry) }
        )
        val rv: RecyclerView = findViewById(R.id.rvEntries)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        findViewById<MaterialButton>(R.id.btnGave).setOnClickListener { showAddEntryDialog(true) }
        findViewById<MaterialButton>(R.id.btnGot).setOnClickListener { showAddEntryDialog(false) }

        findViewById<MaterialButton>(R.id.btnCall).setOnClickListener {
            if (partyPhone.isNullOrBlank()) {
                Toast.makeText(this, R.string.no_phone_number, Toast.LENGTH_SHORT).show()
            } else {
                // Opens the dialer with the number filled in. We never place the
                // call ourselves — the owner presses the green button. That also
                // means no CALL_PHONE permission is needed.
                try {
                    startActivity(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + partyPhone)))
                } catch (e: Exception) {
                    Toast.makeText(this, R.string.no_dialer, Toast.LENGTH_SHORT).show()
                }
            }
        }
        findViewById<MaterialButton>(R.id.btnWhatsApp).setOnClickListener {
            showReminderPreview(viaWhatsApp = true)
        }
        findViewById<MaterialButton>(R.id.btnSms).setOnClickListener {
            showReminderPreview(viaWhatsApp = false)
        }
        findViewById<MaterialButton>(R.id.btnPdf).setOnClickListener {
            exportStatement()
        }

        ivAvatar = findViewById(R.id.ivDetailAvatar)
        tvInitials = findViewById(R.id.tvDetailInitials)
        findViewById<View>(R.id.flAvatar).setOnClickListener { showPhotoOptions() }

        etSearchEntries = findViewById(R.id.etSearchEntries)
        etSearchEntries.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = renderEntries()
        })

        val btnDateFilter = findViewById<MaterialButton>(R.id.btnFilterEntryDate)
        btnDateFilter.setOnClickListener {
            DateRangeFilter.choose(this, entryDateRange) { picked ->
                entryDateRange = picked
                btnDateFilter.text = DateRangeFilter.label(this, picked)
                renderEntries()
            }
        }

        findViewById<MaterialButton>(R.id.btnSortEntries).setOnClickListener {
            showEntrySortDialog()
        }

        loadParty()
        observeEntries()
    }

    private fun loadParty() {
        lifecycleScope.launch {
            dao.getParty(partyId)?.let { p ->
                partyName = p.name
                partyPhone = p.phone
                creditLimit = p.creditLimit
                tvPartyName.text = p.name
                refreshAvatar()
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

                // Running balances are computed once, against the ledger's own
                // chronological order. Sorting or searching afterwards only
                // reorders or hides rows — it never recomputes the balance, so
                // each row keeps the balance it genuinely had at that moment.
                // (Recomputing per view would produce a running total of
                // whatever happened to be on screen, which would be a lie.)
                allRows = rowsOldestFirst.reversed()

                updateBalanceHeader(running)
                renderEntries()
            }
        }
    }

    private fun updateBalanceHeader(balance: Double) {
        currentBalance = balance
        tvPartyBalance.text = Format.customerBalance(balance)
        when {
            balance > 0 -> {
                tvPartyBalance.setTextColor(ContextCompat.getColor(this, R.color.bal_owed_to_me_on_dark))
                tvBalanceHint.setText(R.string.you_will_get)
            }
            balance < 0 -> {
                tvPartyBalance.setTextColor(ContextCompat.getColor(this, R.color.bal_i_owe_on_dark))
                tvBalanceHint.setText(R.string.you_will_give)
            }
            else -> {
                tvPartyBalance.setTextColor(ContextCompat.getColor(this, R.color.white))
                tvBalanceHint.setText(R.string.settled)
            }
        }
    }

    private fun showAddEntryDialog(isGiven: Boolean, prefillAmount: Double? = null) {
        val view = layoutInflater.inflate(R.layout.dialog_add_entry, null)
        val etAmount: EditText = view.findViewById(R.id.etAmount)

        // What the microphone heard, sitting in the box for the owner to read
        // back before it becomes an entry.
        prefillAmount?.let {
            etAmount.setText(Calc.trim(it))
            etAmount.setSelection(etAmount.text.length)
        }

        // Suppress the system keyboard — the on-screen pad is the only input.
        etAmount.showSoftInputOnFocus = false
        etAmount.setOnClickListener {
            etAmount.requestFocus()
            (getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager)
                .hideSoftInputFromWindow(etAmount.windowToken, 0)
        }

        // Fresh dialog, fresh attachment. Anything picked for a dialog that was
        // then cancelled is a file nobody will ever look at.
        BillPhoto.delete(pendingBillPhoto)
        pendingBillPhoto = null

        billButton = view.findViewById(R.id.btnAddBill)
        billButton?.setText(R.string.add_bill_photo)
        billButton?.setOnClickListener {
            // A bill is more often photographed at the counter than found in
            // the gallery afterwards, so the camera is offered first here too.
            // No privacy note: a bill is a receipt, not somebody's face.
            showPhotoChooser(
                titleRes = R.string.bill_photo_label,
                noteRes = null,
                target = PhotoTarget.BILL,
                onPick = {
                    pickBillPhoto.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            androidx.activity.result.contract.ActivityResultContracts
                                .PickVisualMedia.ImageOnly
                        )
                    )
                },
                onRemove = null
            )
        }

        // When it happened. Defaults to now — right most of the time — but an
        // entry written up in the evening for something that changed hands at
        // noon should carry noon, not the evening.
        var chosenTime = System.currentTimeMillis()
        DateTimeField.attach(
            activity = this,
            button = view.findViewById(R.id.btnEntryDate),
            initial = chosenTime
        ) { chosenTime = it }

        // The running total, shown as the sum is typed rather than waiting on
        // the equals key — the Calculator screen answers as you go, and this
        // pad looked broken beside it.
        val tvResult = view.findViewById<android.widget.TextView>(R.id.tvAmountResult)
        fun showResult() {
            val text = etAmount.text.toString()
            // Nothing to total until there is arithmetic in the box: a plain
            // "3500" repeated underneath as "Rs 3,500" is noise.
            val isSum = text.any { it in "+-\u2212*\u00d7/\u00f7%" }
            val value = if (isSum) Calc.evalPad(text) else null
            if (value == null) {
                tvResult.visibility = android.view.View.GONE
            } else {
                tvResult.text = Format.money(value)
                tvResult.visibility = android.view.View.VISIBLE
            }
        }

        etAmount.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = showResult()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, cc: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, cc: Int) {}
        })

        // Full calculator pad. Digits, 00, and the dot append; operators append;
        // ⌫ deletes the last character; C clears; = evaluates in place.
        fun append(ch: String) { etAmount.append(ch); etAmount.setSelection(etAmount.text.length) }
        fun key(id: Int, ch: String) = view.findViewById<android.widget.Button>(id).setOnClickListener { append(ch) }

        key(R.id.calc0, "0"); key(R.id.calc00, "00"); key(R.id.calc1, "1")
        key(R.id.calc2, "2"); key(R.id.calc3, "3"); key(R.id.calc4, "4")
        key(R.id.calc5, "5"); key(R.id.calc6, "6"); key(R.id.calc7, "7")
        key(R.id.calc8, "8"); key(R.id.calc9, "9"); key(R.id.calcDot, ".")
        key(R.id.calcPlus, "+"); key(R.id.calcMinus, "\u2212")
        key(R.id.calcTimes, "\u00d7"); key(R.id.calcDivide, "\u00f7")
        key(R.id.calcPercent, "%")

        view.findViewById<android.widget.Button>(R.id.calcClear).setOnClickListener {
            etAmount.setText("")
        }
        view.findViewById<android.widget.Button>(R.id.calcBack).setOnClickListener {
            val t = etAmount.text
            if (t.isNotEmpty()) etAmount.text.delete(t.length - 1, t.length)
        }

        view.findViewById<android.widget.Button>(R.id.calcEquals).setOnClickListener {
            // evalPad, not eval: it translates the pad's × ÷ − and resolves a
            // percentage before the arithmetic runs, so "1200-15%" comes out
            // as 1020 rather than as nothing at all.
            val result = Calc.evalPad(etAmount.text.toString())
            if (result != null) {
                etAmount.setText(Calc.trim(result))
                etAmount.setSelection(etAmount.text.length)
            }
        }
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
                val amount = Calc.evalPad(etAmount.text.toString())
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

                val entry = LedgerEntry(
                    partyId = partyId,
                    amount = amount,
                    isGiven = isGiven,
                    note = note,
                    entryNumber = "",
                    isQarzeHasna = cbQarzeHasna.isChecked,
                    recovery = recovery,
                    itemName = itemName,
                    quantity = quantity,
                    unit = unit,
                    timestamp = chosenTime,
                    billPhotoPath = pendingBillPhoto
                )

                // Warn BEFORE writing, not after — a warning that arrives once
                // the entry is already in the ledger is just an accusation.
                val limit = creditLimit
                val projected = currentBalance + (if (isGiven) amount else -amount)

                if (isGiven && limit != null && limit > 0 && projected > limit && currentBalance <= limit) {
                    warnOverLimit(entry, limit, projected)
                } else {
                    saveEntry(entry)
                }
            }
            .show()
            .also { dialog ->
                // Full width and height, not a card floating in the middle.
                //
                // Writing an entry is the one thing this app is for; it
                // deserves the screen rather than a box with the ledger
                // greyed out around it. Set after show() because that is when
                // the window exists.
                dialog.window?.apply {
                    setBackgroundDrawable(
                        android.graphics.drawable.ColorDrawable(
                            androidx.core.content.ContextCompat.getColor(
                                this@PartyDetailActivity, R.color.white
                            )
                        )
                    )
                    setLayout(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                // The second half opens on request and is never in the way.
                //
                // Save is live from the first page: most entries are a figure
                // and a date, and making someone page past goods and recovery
                // to reach it would tax every entry for the sake of the few
                // that need them.
                val stepOne = view.findViewById<View>(R.id.stepOne)
                val stepTwo = view.findViewById<View>(R.id.stepTwo)
                val cancel = dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)

                fun show(next: View, gone: View, anim: Int, cancelLabel: Int) {
                    gone.visibility = View.GONE
                    next.visibility = View.VISIBLE
                    next.startAnimation(
                        android.view.animation.AnimationUtils.loadAnimation(
                            this@PartyDetailActivity, anim
                        )
                    )
                    cancel.setText(cancelLabel)
                }

                view.findViewById<MaterialButton>(R.id.btnMoreDetails).setOnClickListener {
                    // Cancel becomes Back: from here, leaving should mean
                    // returning to the amount, not discarding it.
                    show(stepTwo, stepOne, R.anim.slide_in_right, R.string.back)
                }

                cancel.setOnClickListener {
                    if (stepTwo.visibility == View.VISIBLE) {
                        show(stepOne, stepTwo, R.anim.slide_in_left, R.string.cancel)
                    } else {
                        dialog.dismiss()
                    }
                }
            }
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
    /**
     * Copy a short, ready-to-send line about this balance to the clipboard —
     * the owner can paste it straight into WhatsApp. Tapping the balance is the
     * quick path when they don't want the full reminder dialog.
     */
    private fun copyBalance() {
        val line = when {
            currentBalance > 0 -> getString(R.string.copy_balance_owed, partyName, Format.money(currentBalance))
            currentBalance < 0 -> getString(R.string.copy_balance_i_owe, partyName, Format.money(-currentBalance))
            else -> getString(R.string.copy_balance_settled, partyName)
        }
        val clip = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clip.setPrimaryClip(android.content.ClipData.newPlainText("balance", line))
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

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
        if (allRows.isEmpty()) {
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
                    // Always the full ledger, in the ledger's own order —
                    // never the filtered view. A statement that silently drops
                    // rows because a search box was open would be a false
                    // document, and it goes to a customer.
                    rows = allRows.map {
                        PdfExport.StatementRow(it.entry, it.runningBalance)
                    },
                    closingBalance = currentBalance,
                    businessName = BusinessProfile.businessName(this@PartyDetailActivity),
                    paymentQr = BusinessProfile.loadQr(this@PartyDetailActivity),
                    // Only if the owner has turned it on. Off by default.
                    partyPhoto = if (BusinessProfile.photoOnStatement(this@PartyDetailActivity)) {
                        PartyPhoto.load(this@PartyDetailActivity, partyId)
                    } else {
                        null
                    }
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

    /**
     * Filter and sort the rows for display. The running balance carried by each
     * row was fixed when it was computed chronologically — it is never
     * recalculated here, so it stays truthful no matter how the list is
     * arranged or narrowed.
     */
    private fun renderEntries() {
        val query = etSearchEntries.text.toString().trim().lowercase()

        // Days first, then the search box. Narrowing to a date and then
        // searching within it is how the question is usually asked: "what did
        // he take on the 21st".
        val inRange = if (entryDateRange == DateRangeFilter.Range.ALL) {
            allRows
        } else {
            allRows.filter { entryDateRange.contains(it.entry.timestamp) }
        }

        val filtered = if (query.isEmpty()) {
            inRange
        } else {
            inRange.filter { row ->
                val e = row.entry
                val haystack = listOfNotNull(
                    e.note,
                    e.itemName,
                    e.unit,
                    e.entryNumber,
                    Format.money(e.amount)
                ).joinToString(" ").lowercase()

                haystack.contains(query)
            }
        }

        val sorted = when (entrySortMode) {
            EntrySort.NEWEST -> filtered.sortedByDescending { it.entry.timestamp }
            EntrySort.OLDEST -> filtered.sortedBy { it.entry.timestamp }
            EntrySort.AMOUNT_HIGH -> filtered.sortedByDescending { it.entry.amount }
            EntrySort.AMOUNT_LOW -> filtered.sortedBy { it.entry.amount }
        }

        currentRows = sorted
        adapter.submit(sorted)

        tvNoEntries.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
        tvNoEntries.setText(
            if (allRows.isEmpty()) R.string.no_entries_yet
            else R.string.no_matching_entries
        )
    }

    private fun showEntrySortDialog() {
        val options = arrayOf(
            getString(R.string.sort_newest),
            getString(R.string.sort_oldest),
            getString(R.string.sort_amount_high),
            getString(R.string.sort_amount_low)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sort_by)
            .setSingleChoiceItems(options, entrySortMode.ordinal) { dialog, which ->
                entrySortMode = EntrySort.values()[which]
                renderEntries()
                dialog.dismiss()
            }
            .show()
    }

    private fun refreshAvatar() {
        val photo = PartyPhoto.load(this, partyId)
        if (photo != null) {
            ivAvatar.setImageBitmap(photo)
            ivAvatar.clipToOutline = true
            ivAvatar.background = ContextCompat.getDrawable(this, R.drawable.bg_avatar_circle)
            ivAvatar.visibility = View.VISIBLE
            tvInitials.visibility = View.GONE
        } else {
            ivAvatar.visibility = View.GONE
            tvInitials.visibility = View.VISIBLE
            tvInitials.text = initialsOf(partyName)
        }
    }

    private fun initialsOf(name: String): String {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            words.isEmpty() -> "?"
            words.size == 1 -> words[0].take(1).uppercase()
            else -> (words[0].take(1) + words[1].take(1)).uppercase()
        }
    }

    /**
     * The photo is optional in every sense: it can be set, changed, or taken
     * away at any time, and nothing in the app depends on it existing.
     */
    private fun showPhotoOptions() {
        val hasPhoto = PartyPhoto.exists(this, partyId)
        showPhotoChooser(
            titleRes = R.string.party_photo,
            noteRes = R.string.photo_privacy_note,
            target = PhotoTarget.PARTY,
            onPick = {
                pickPhoto.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onRemove = if (!hasPhoto) null else {
                {
                    PartyPhoto.remove(this, partyId)
                    refreshAvatar()
                    Toast.makeText(this, R.string.photo_removed, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    /**
     * Where a photo comes from — the camera, or one already on the phone.
     *
     * Built from a layout rather than setItems(), because this dialog also
     * carries the privacy note and an AlertDialog will not show both: give it
     * a message and a list and it keeps the message and quietly drops the
     * list. That is what used to happen here. The note appeared, the choices
     * did not, and a photo could not be added at all.
     */
    private fun showPhotoChooser(
        titleRes: Int,
        noteRes: Int?,
        target: PhotoTarget,
        onPick: () -> Unit,
        onRemove: (() -> Unit)?
    ) {
        val view = layoutInflater.inflate(R.layout.dialog_photo_options, null)
        val note = view.findViewById<TextView>(R.id.tvPhotoNote)
        if (noteRes == null) note.visibility = View.GONE else note.setText(noteRes)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(titleRes)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .show()

        view.findViewById<View>(R.id.optCamera).setOnClickListener {
            dialog.dismiss()
            launchCamera(target)
        }
        view.findViewById<View>(R.id.optGallery).setOnClickListener {
            dialog.dismiss()
            onPick()
        }
        view.findViewById<View>(R.id.optRemove).apply {
            if (onRemove == null) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                setOnClickListener {
                    dialog.dismiss()
                    onRemove()
                }
            }
        }
    }

    /**
     * Open the camera for [target].
     *
     * The camera app writes straight into a file of ours through the
     * FileProvider; it cannot reach into private storage on its own. Nothing
     * is remembered beyond the path and what it was for, and both are given up
     * as soon as the picture comes back.
     */
    private fun launchCamera(target: PhotoTarget) {
        val file = try {
            val dir = java.io.File(cacheDir, "camera").apply { mkdirs() }
            java.io.File(dir, "capture_${System.currentTimeMillis()}.jpg")
        } catch (e: Exception) {
            null
        }

        val uri = file?.let {
            try {
                androidx.core.content.FileProvider.getUriForFile(
                    this, "$packageName.fileprovider", it
                )
            } catch (e: Exception) {
                null
            }
        }

        if (uri == null) {
            Toast.makeText(this, R.string.photo_save_failed, Toast.LENGTH_LONG).show()
            return
        }

        cameraPath = file.absolutePath
        cameraTarget = target
        try {
            takePhoto.launch(uri)
        } catch (e: android.content.ActivityNotFoundException) {
            // No camera app on the phone. Say so, rather than leaving a row
            // that does nothing when tapped.
            cameraPath = null
            cameraTarget = null
            file.delete()
            Toast.makeText(this, R.string.camera_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Keep a customer's photo. [temp] is the camera's working file when the
     * photo was just taken, and null when it came from the phone's gallery,
     * which is the owner's and not ours to delete.
     */
    private fun savePhoto(uri: Uri, temp: java.io.File? = null) {
        lifecycleScope.launch {
            val path = withContext(Dispatchers.IO) {
                PartyPhoto.save(this@PartyDetailActivity, partyId, uri)
            }
            temp?.delete()

            if (path == null) {
                Toast.makeText(
                    this@PartyDetailActivity,
                    R.string.photo_save_failed,
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            // Keep the DB in step with what is actually on disk, so a future
            // export or backup knows the photo exists.
            dao.getParty(partyId)?.let { p ->
                dao.updateParty(p.copy(photoPath = path))
            }

            refreshAvatar()
            Toast.makeText(this@PartyDetailActivity, R.string.photo_saved, Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveEntry(entry: LedgerEntry) {
        lifecycleScope.launch {
            val count = dao.totalEntryCount()
            dao.insertEntry(entry.copy(entryNumber = EntryNumber.next(count)))
        }
    }

    /**
     * The limit is advice, not a gate. The owner knows their customer and their
     * own risk better than a number in a database does — so we tell them
     * plainly what this entry will do, and let them decide.
     */
    private fun warnOverLimit(entry: LedgerEntry, limit: Double, projected: Double) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.limit_warning_title)
            .setMessage(
                getString(
                    R.string.limit_warning_message,
                    partyName,
                    Format.money(currentBalance),
                    Format.money(entry.amount),
                    Format.money(projected),
                    Format.money(limit)
                )
            )
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.proceed_anyway) { _, _ -> saveEntry(entry) }
            .show()
    }

    private fun showCreditLimitDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_credit_limit, null)
        val etLimit: EditText = view.findViewById(R.id.etCreditLimit)
        val tvOwed: TextView = view.findViewById(R.id.tvCurrentOwed)

        tvOwed.text = if (currentBalance > 0) {
            getString(R.string.credit_limit_current, Format.money(currentBalance))
        } else {
            getString(R.string.settled)
        }

        creditLimit?.let { etLimit.setText(Format.plain(it)) }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.set_credit_limit)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                // A blank field means "no limit" — that is a real choice, not
                // an error, and it must be able to undo a limit set earlier.
                val newLimit = etLimit.text.toString().trim().toDoubleOrNull()
                    ?.takeIf { it > 0 }

                lifecycleScope.launch {
                    dao.getParty(partyId)?.let { p ->
                        dao.updateParty(p.copy(creditLimit = newLimit))
                    }
                    creditLimit = newLimit

                    Toast.makeText(
                        this@PartyDetailActivity,
                        if (newLimit == null) R.string.credit_limit_removed
                        else R.string.credit_limit_saved,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        cameraPath?.let { outState.putString(STATE_CAMERA_PATH, it) }
        cameraTarget?.let { outState.putString(STATE_CAMERA_TARGET, it.name) }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_party_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_report -> {
                startActivity(
                    Intent(this, ReportActivity::class.java)
                        .putExtra(ReportActivity.EXTRA_PARTY_ID, partyId)
                )
                true
            }
            R.id.action_credit_limit -> {
                showCreditLimitDialog()
                true
            }
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
