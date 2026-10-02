package com.innovation313.roshankhata

import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.SmartSuggest
import com.innovation313.roshankhata.ui.asSuggestions
import com.innovation313.roshankhata.ui.DateRangeFilter
import com.innovation313.roshankhata.ui.DateTimeField
import com.innovation313.roshankhata.ui.fillDialogHeight

import android.app.Dialog
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
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.data.AppScope
import com.innovation313.roshankhata.data.BatchOption
import com.innovation313.roshankhata.data.Product
import com.innovation313.roshankhata.data.ProductName
import com.innovation313.roshankhata.data.BusinessProfile
import com.innovation313.roshankhata.data.QrTag
import com.innovation313.roshankhata.ui.QrImage
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.SeasonBook
import com.innovation313.roshankhata.data.EntryItem
import com.innovation313.roshankhata.data.RateType
import com.innovation313.roshankhata.data.RateOffer
import com.innovation313.roshankhata.data.LineDraft
import com.innovation313.roshankhata.data.EntryLines
import com.innovation313.roshankhata.data.LastRate
import com.innovation313.roshankhata.data.EntryWithItems
import com.innovation313.roshankhata.data.Money
import com.innovation313.roshankhata.data.PartyPhoto
import com.innovation313.roshankhata.data.PdfExport
import com.innovation313.roshankhata.data.BillPhoto
import com.innovation313.roshankhata.data.ScannedBill
import com.innovation313.roshankhata.data.ScannedItem
import com.innovation313.roshankhata.data.ScannedPayment
import com.innovation313.roshankhata.ui.BillScanFlow
import com.google.android.material.chip.ChipGroup
import com.innovation313.roshankhata.data.PaymentMethod
import com.innovation313.roshankhata.data.Recovery
import com.innovation313.roshankhata.ui.EntryAdapter
import com.innovation313.roshankhata.ui.EntryRow
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.Reminder
import com.innovation313.roshankhata.ui.SeasonText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import com.innovation313.roshankhata.data.Digits

/**
 * A single party's ledger: full entry history with running balances,
 * plus the two core actions — "I Gave" and "I Got".
 */
class PartyDetailActivity : BaseActivity() {

    companion object {
        const val EXTRA_PARTY_ID = "party_id"

        /**
         * Open this entry on the full add-entry form, to edit it. The only
         * way to change an entry carrying items, rates or free goods; the
         * small dialog on the entry screen cannot show a list honestly.
         */
        const val EXTRA_EDIT_ENTRY_ID = "edit_entry_id"

        /** A camera capture waiting to come back, kept across a rebuild. */
        private const val STATE_CAMERA_PATH = "camera_path"
        private const val STATE_SCAN_CAMERA_PATH = "scan_camera_path"
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
            billButton?.setText(R.string.entry_bill_chip_done)
        }
    }

    // ---------- Scan bill (1 Oct) ----------
    //
    // The same flow as a supplier's bill (BillScanFlow): scanner, camera or
    // gallery, read on the phone, checked line by line. On a customer's sale
    // the ticked lines join the entry's item list, the bill's picture becomes
    // the entry's bill photo, and nothing is saved until the owner presses
    // Save. The entry form stays open behind the scanner, so the open form
    // hands in [onScanUsed]; with no form open, a reading is let go.

    private val scanFlow = BillScanFlow(
        activity = this,
        onPhoto = { uri, temp -> readScannedBill(uri, temp) },
        onNothing = {}
    )

    /** Set while a sale form is open: what to do with the lines the owner kept. */
    private var onScanUsed: ((ScannedBill, List<ScannedItem>, String?) -> Unit)? = null

    /**
     * Set while an "I got" form is open: what to do with a payment slip the
     * owner checked (amount, reference, date) and the slip's kept photo.
     */
    private var onPaymentScanUsed: ((ScannedPayment, String?) -> Unit)? = null

    /**
     * Which reading the picture on its way back is for. One BillScanFlow
     * serves both (its launchers must be registered once, up front), and only
     * one form, and so one kind of scan, can be open at a time.
     */
    private var scanForPayment = false

    private fun readScannedBill(photo: android.net.Uri, temp: java.io.File?) {
        if (scanForPayment) {
            readScannedPayment(photo, temp)
            return
        }
        lifecycleScope.launch {
            val known = runCatching { dao.productsOnce().map { it.name } }.getOrDefault(emptyList())
            when (val outcome = BillScanFlow.read(this@PartyDetailActivity, photo, temp, known, emptyList())) {
                is BillScanFlow.Outcome.Found -> BillScanFlow.review(
                    activity = this@PartyDetailActivity,
                    bill = outcome.bill,
                    keptPhoto = outcome.keptPhoto,
                    showSupplier = false,
                    onUse = { kept ->
                        val use = onScanUsed
                        // The form was closed meanwhile (screen restarted):
                        // nothing to put the lines into, so the copy goes too.
                        if (use == null) BillPhoto.delete(outcome.keptPhoto)
                        else use(outcome.bill, kept, outcome.keptPhoto)
                    },
                    onCancel = {}
                )
                else -> BillScanFlow.showProblem(this@PartyDetailActivity, outcome) {}
            }
        }
    }

    private fun readScannedPayment(photo: android.net.Uri, temp: java.io.File?) {
        lifecycleScope.launch {
            when (val outcome = BillScanFlow.readPayment(this@PartyDetailActivity, photo, temp)) {
                is BillScanFlow.Outcome.PaymentFound -> BillScanFlow.reviewPayment(
                    activity = this@PartyDetailActivity,
                    payment = outcome.payment,
                    keptPhoto = outcome.keptPhoto,
                    onUse = { payment ->
                        val use = onPaymentScanUsed
                        // The form was closed meanwhile: the copy goes too.
                        if (use == null) BillPhoto.delete(outcome.keptPhoto)
                        else use(payment, outcome.keptPhoto)
                    },
                    onCancel = {}
                )
                else -> BillScanFlow.showProblem(this@PartyDetailActivity, outcome, forPayment = true) {}
            }
        }
    }

    private var partyId: Long = 0
    private var partyName: String = ""
    private var partyPhone: String? = null
    /** Party.noEntryShare (v23): skip the after-entry WhatsApp offer. */
    private var noEntryShare: Boolean = false
    /** Party.harvestPromise (v28): "I'll pay after the harvest" date. */
    private var harvestPromise: Long? = null
    private var currentBalance: Double = 0.0
    private var creditLimit: Double? = null
    private var currentRows: List<EntryRow> = emptyList()

    /**
     * Select-and-delete for this party's own history — the bounded
     * replacement for the "Delete all customers" button removed from the
     * Recycle Bin (see KhataDao.softDeleteEntries). Scoped to whichever
     * entries are on screen for THIS party, never the whole ledger.
     */
    private var selectionMode = false
    private val selectedEntryIds = mutableSetOf<Long>()
    private var updatingSelectionUi = false
    private lateinit var bottomBarContainer: View
    private lateinit var buttonBar: View
    private lateinit var selectionBar: View
    private lateinit var cbSelectAll: CheckBox
    private lateinit var btnDeleteSelected: MaterialButton

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
    private lateinit var tvPartyIdentity: TextView
    private lateinit var tvSeasonRecord: TextView
    private lateinit var tvPartyPhone: TextView
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
        scanFlow.restoreState(savedInstanceState, STATE_SCAN_CAMERA_PATH)
        cameraTarget = savedInstanceState?.getString(STATE_CAMERA_TARGET)
            ?.let { runCatching { PhotoTarget.valueOf(it) }.getOrNull() }
        setContentView(R.layout.activity_party_detail)
        onBackPressedDispatcher.addCallback(this, selectionBack)

        // Edge-to-edge, the mechanism proven on the Home screen.
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)

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
        intent.getLongExtra(EXTRA_EDIT_ENTRY_ID, 0L).takeIf { it > 0L }?.let { openEditor(it) }

        setSupportActionBar(findViewById<Toolbar>(R.id.detailToolbar))
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        tvPartyName = findViewById(R.id.tvPartyName)
        tvPartyIdentity = findViewById(R.id.tvPartyIdentity)
        tvSeasonRecord = findViewById(R.id.tvSeasonRecord)
        tvPartyPhone = findViewById(R.id.tvPartyPhone)
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
            onLongClick = { entry -> confirmDeleteEntry(entry) },
            onToggleSelect = { entry -> toggleEntrySelection(entry.id) }
        )
        val rv: RecyclerView = findViewById(R.id.rvEntries)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        bottomBarContainer = findViewById(R.id.bottomBarContainer)
        buttonBar = findViewById(R.id.buttonBar)
        selectionBar = findViewById(R.id.selectionBar)
        cbSelectAll = findViewById(R.id.cbSelectAll)
        btnDeleteSelected = findViewById(R.id.btnDeleteSelected)
        findViewById<MaterialButton>(R.id.btnCancelSelection).setOnClickListener { exitSelectionMode() }
        btnDeleteSelected.setOnClickListener { confirmDeleteSelectedEntries() }
        cbSelectAll.setOnCheckedChangeListener { _, checked ->
            // Guarded rather than relying on the checkbox's own pressed
            // state: refreshSelectionUi() sets isChecked programmatically
            // to reflect "everything is already selected", and that must
            // not be read back as a fresh tap that clears and re-adds
            // every id right after.
            if (updatingSelectionUi) return@setOnCheckedChangeListener
            selectedEntryIds.clear()
            if (checked) selectedEntryIds.addAll(currentRows.map { it.entry.id })
            refreshSelectionUi()
        }

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
        findViewById<View>(R.id.flAvatar).setOnClickListener {
            if (PartyPhoto.exists(this, partyId)) viewPhotoFullSize() else showPhotoOptions()
        }
        findViewById<View>(R.id.ivAvatarCameraBadge).setOnClickListener { showPhotoOptions() }

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
                noEntryShare = p.noEntryShare
                harvestPromise = p.harvestPromise
                creditLimit = p.creditLimit
                tvPartyName.text = p.name
                tvPartyPhone.text = p.phone.orEmpty()
                tvPartyPhone.visibility = if (p.phone.isNullOrBlank()) View.GONE else View.VISIBLE

                // "s/o Muhammad · Khichi" — whichever parts are known, joined
                // by a dot, and nothing at all when neither is. Built here
                // rather than in the layout so a customer with only a village
                // does not get a stray "s/o" in front of it.
                val identity = listOfNotNull(
                    p.fatherName?.takeIf { it.isNotBlank() }
                        ?.let { getString(R.string.son_of_prefix, it) },
                    p.village?.takeIf { it.isNotBlank() }
                ).joinToString("  ·  ")
                tvPartyIdentity.text = identity
                tvPartyIdentity.visibility = if (identity.isEmpty()) View.GONE else View.VISIBLE

                refreshAvatar()
            }
        }
    }

    private fun observeEntries() {
        lifecycleScope.launch {
            // Entries arrive newest-first. Running balance must be computed
            // oldest-first, then mapped back so each row shows the balance
            // as it stood right after that entry.
            dao.observeEntriesWithItems(partyId).collectLatest { newestFirst ->
                val oldestFirst = newestFirst.reversed()

                var running = 0.0
                val rowsOldestFirst = oldestFirst.map { ew ->
                    val e = ew.entry
                    running += if (e.isGiven) e.amount else -e.amount
                    EntryRow(e, running, ew.orderedItems())
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
        refreshSeasonRecord()
        tvPartyBalance.text = Format.customerBalance(balance)
        when {
            Money.isPositive(balance) -> {
                tvPartyBalance.setTextColor(ContextCompat.getColor(this, R.color.bal_owed_to_me_on_dark))
                tvBalanceHint.setText(R.string.you_will_get)
            }
            Money.isNegative(balance) -> {
                tvPartyBalance.setTextColor(ContextCompat.getColor(this, R.color.bal_i_owe_on_dark))
                tvBalanceHint.setText(R.string.you_will_give)
            }
            else -> {
                tvPartyBalance.setTextColor(ContextCompat.getColor(this, R.color.white))
                tvBalanceHint.setText(R.string.settled)
            }
        }
    }

    // Arriving again with an entry to edit, while this customer's screen is
    // already open underneath (the entry screen asks with CLEAR_TOP|SINGLE_TOP,
    // so the khata is reused rather than stacked twice).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val editId = intent.getLongExtra(EXTRA_EDIT_ENTRY_ID, 0L)
        if (editId > 0L && intent.getLongExtra(EXTRA_PARTY_ID, 0L) == partyId) openEditor(editId)
    }

    /** Put an existing entry back on the full form. Its own customer only; never a binned one. */
    private fun openEditor(entryId: Long) {
        lifecycleScope.launch {
            val e = dao.getEntry(entryId) ?: return@launch
            if (e.partyId != partyId || e.isDeleted) return@launch
            if (partyName.isEmpty()) dao.getParty(partyId)?.let { partyName = it.name }
            showAddEntryDialog(e.isGiven, editing = EntryWithItems(e, dao.itemsOfEntry(entryId)))
        }
    }

    /**
     * The add-entry form. With [editing] it is the same form holding an
     * existing entry: its amount, note, date, recovery, payment method, rate
     * type and items are put back, and Save rewrites THAT entry (number and
     * all) with its complete new set of items in one transaction. The twin
     * and credit-limit warnings are for new entries and are skipped.
     */
    private fun showAddEntryDialog(
        isGiven: Boolean,
        prefillAmount: Double? = null,
        editing: EntryWithItems? = null
    ) {
        val view = layoutInflater.inflate(R.layout.dialog_add_entry, null)
        com.innovation313.roshankhata.ui.TextFit.relax(view)
        val etAmount: EditText = view.findViewById(R.id.etAmount)

        // What the microphone heard, sitting in the box for the owner to read
        // back before it becomes an entry.
        prefillAmount?.let {
            etAmount.setText(Calc.trim(it))
            etAmount.setSelection(etAmount.text.length)
        }
        editing?.let {
            etAmount.setText(Calc.trim(it.entry.amount))
            etAmount.setSelection(etAmount.text.length)
        }

        // Suppress the system keyboard — the on-screen pad is the only input.
        // The field's click and focus handling is set once the dialog is
        // showing, next to the step switch it needs; see openDetails().
        etAmount.showSoftInputOnFocus = false

        // Fresh dialog, fresh attachment. Anything picked for a dialog that was
        // then cancelled is a file nobody will ever look at.
        BillPhoto.delete(pendingBillPhoto)
        pendingBillPhoto = null

        billButton = view.findViewById(R.id.btnAddBill)
        billButton?.setText(R.string.entry_bill_chip)
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
        // An entry's bill photo is managed on its own screen; editing here
        // keeps whatever it has.
        if (editing != null) billButton?.visibility = View.GONE

        // When it happened. Defaults to now — right most of the time — but an
        // entry written up in the evening for something that changed hands at
        // noon should carry noon, not the evening.
        var chosenTime = editing?.entry?.timestamp ?: System.currentTimeMillis()
        fun attachDate() = DateTimeField.attach(
            activity = this,
            button = view.findViewById(R.id.btnEntryDate),
            initial = chosenTime,
            compact = true
        ) { chosenTime = it }
        attachDate()

        // The running total, shown as the sum is typed rather than waiting on
        // the equals key — the Calculator screen answers as you go, and this
        // pad looked broken beside it.
        val tvResult = view.findViewById<android.widget.TextView>(R.id.tvAmountResult)
        // Set once the dialog exists (header, SAVE, preview); called on every
        // change to the amount alongside showResult().
        var onAmountChanged: () -> Unit = {}
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
            override fun afterTextChanged(s: android.text.Editable?) {
                showResult()
                onAmountChanged()
            }
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
        // Two backspaces, one behaviour: the key on the pad, where the thumb
        // is, and the small one inside the amount box.
        val backspace = android.view.View.OnClickListener {
            val t = etAmount.text
            if (t.isNotEmpty()) etAmount.text.delete(t.length - 1, t.length)
        }
        view.findViewById<android.widget.Button>(R.id.calcBack).setOnClickListener(backspace)
        view.findViewById<android.widget.Button>(R.id.calcBackKey).setOnClickListener(backspace)

        view.findViewById<android.widget.Button>(R.id.calcEquals).setOnClickListener {
            // evalPad, not eval: it translates the pad's × ÷ − and resolves a
            // percentage before the arithmetic runs, so "1200-15%" comes out
            // as 1020 rather than as nothing at all. Written back as a
            // magnitude (abs), same as everywhere else this box is read —
            // "7926-35554" must land back in the box as 27628, not -27628;
            // a minus sign has no meaning in an amount field.
            val result = Calc.evalPad(etAmount.text.toString())
            if (result != null) {
                etAmount.setText(Calc.trim(kotlin.math.abs(result)))
                etAmount.setSelection(etAmount.text.length)
            }
        }
        // ---- Quick shares of the outstanding balance ----
        //
        // Only for money coming IN, and only when there is something owed to
        // collect against. On an "I Gave" entry, or a settled customer, the
        // row stays hidden: a percentage of nothing is not a shortcut.
        //
        // The figure is put in the box for the owner to see and change, not
        // saved. Nothing here reaches the ledger by itself.
        val quickAmounts: View = view.findViewById(R.id.quickAmounts)
        // Not when editing: the balance already contains this entry.
        if (editing == null && !isGiven && Money.isPositive(currentBalance)) {
            quickAmounts.visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.tvQuickAmountLabel).text =
                getString(R.string.quick_amount_label, Format.money(currentBalance))

            fun share(fraction: Double) {
                // Rounded to the rupee: a shopkeeper counts notes, not paisa,
                // and "5,749.99" in the box would be corrected by hand every
                // single time. The full share is left exactly as the balance
                // stands — rounding THAT would leave a few paisa outstanding
                // on a customer the owner was told is now clear.
                val raw = currentBalance * fraction
                val value = if (fraction == 1.0) currentBalance else kotlin.math.round(raw)
                etAmount.setText(Calc.trim(value))
                etAmount.setSelection(etAmount.text.length)
            }

            view.findViewById<MaterialButton>(R.id.btnQuarter).setOnClickListener { share(0.25) }
            view.findViewById<MaterialButton>(R.id.btnHalf).setOnClickListener { share(0.50) }
            view.findViewById<MaterialButton>(R.id.btnThreeQuarter).setOnClickListener { share(0.75) }
            view.findViewById<MaterialButton>(R.id.btnFullAmount).setOnClickListener { share(1.0) }
        }

        val etNote: EditText = view.findViewById(R.id.etNote)
        val etItemName: AutoCompleteTextView = view.findViewById(R.id.etItemName)
        val etQuantity: EditText = view.findViewById(R.id.etQuantity)
        val etUnit: AutoCompleteTextView = view.findViewById(R.id.etUnit)

        // The products this shop actually deals in, offered as the name is
        // typed. Read once as the dialog opens rather than on every keystroke
        // — a shop's product list is hundreds of rows, not thousands, and
        // re-reading it per letter would put the database on the typing path
        // for no gain.
        //
        // Nothing is required of the owner here: a product that has never
        // been recorded is still perfectly typeable, and an empty list simply
        // leaves this an ordinary text box.
        lifecycleScope.launch {
            val products = dao.productsOnce()
            // Three rows, not the default eight: this list opens over Quantity,
            // Unit and the rest of the form, and eight two-line rows fill the
            // screen below the field.
            SmartSuggest.attach(etItemName, products.asSuggestions(), maxRows = 3)
        }
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
        val btnBatch: MaterialButton = view.findViewById(R.id.btnBatch)

        // Which batch this sale is drawn from, once the owner picks one. Both
        // are null until then, and stay null for anything that never matches
        // a known, batched product — a cash sale of something never bought in
        // through a bill has nothing to offer here, and is not asked.
        var selectedBatch: BatchOption? = null
        var matchedProductId: Long? = null
        var matchedProduct: Product? = null

        // ---- Items, their rates, and the total ----
        //
        // A customer rarely takes one thing: urea, sulphur and a pesticide go
        // in one visit. The boxes above (name, quantity, unit, rate) are the
        // item being written; "+ Add item" moves it into the list and clears
        // the boxes for the next. An owner who never adds a second item sees
        // the form work exactly as a single-item entry always has.
        //
        // RATE. Filled from the product's price for the chosen chip — Udhar
        // (credit, pre-chosen) or Naqd (cash) — through RateOffer, which also
        // refuses a price per bori for a line in kg and never passes the cash
        // price off as the credit one. The owner may type over it (a bargain);
        // a typed rate is his and is never overwritten, not even by a chip
        // switch, which re-prices only the lines whose rate came from the
        // product. Shown only for a sale to a CUSTOMER: on a supplier's
        // account these selling prices are the wrong prices, and on money
        // coming in no rate applies.
        //
        // TOTAL. The items added up, to the paisa (LineMath). It is OFFERED —
        // "Items total Rs 19,200. Tap to fill." — and goes into the amount box
        // only on a tap: the ledger is the money, and the owner writes it. If
        // what he writes differs (a discount, a round-off) the difference is
        // shown in words, and the book keeps what he wrote.
        val btnTotalFill: MaterialButton = view.findViewById(R.id.btnRateSuggestion)
        val rateSection: View = view.findViewById(R.id.rateSection)
        val cgRateType: ChipGroup = view.findViewById(R.id.cgRateType)
        val tvRateNote: TextView = view.findViewById(R.id.tvRateNote)
        val etRate: EditText = view.findViewById(R.id.etRate)
        val linesContainer: LinearLayout = view.findViewById(R.id.linesContainer)
        val btnAddLine: MaterialButton = view.findViewById(R.id.btnAddLine)
        val tvLinesNote: TextView = view.findViewById(R.id.tvLinesNote)
        val paymentSection: View = view.findViewById(R.id.paymentMethodSection)
        val btnRepeatLast: MaterialButton = view.findViewById(R.id.btnRepeatLast)
        val tvLastRate: TextView = view.findViewById(R.id.tvLastRate)
        val cbUpdateRate: MaterialCheckBox = view.findViewById(R.id.cbUpdateRate)
        val cbAddProduct: MaterialCheckBox = view.findViewById(R.id.cbAddProduct)
        val btnSupplierBill: MaterialButton = view.findViewById(R.id.btnSupplierBill)
        val btnScanBill: MaterialButton = view.findViewById(R.id.btnScanBill)

        // False until the lookup below confirms a customer, so nothing
        // sale-only ever flashes up on a supplier's account.
        var partyIsCustomer = false
        val lines = mutableListOf<LineDraft>()
        // The amount follows the items' total, as a bill does. Two facts,
        // kept apart so neither is guessed from the other:
        //  - amountOwn: the owner wrote the figure while goods were on the
        //    form (a discount, a round-off). The items leave it alone.
        //    A figure typed BEFORE any goods is not that: it cannot be a
        //    discount on items not yet written, so the items' total replaces
        //    it the moment there is one (30 Sep 2026: 8,787 typed first,
        //    then 2 x 1,300 stayed 8,787 and the receipt read 'Rs 6,187 more').
        //  - amountByItems: the figure in the box is one the items wrote, so
        //    it goes when the items go. Only such a figure is ever cleared:
        //    a plain entry's typed amount never is.
        var amountOwn = false
        var amountByItems = false
        // True only while the items are writing the box, so that write is
        // not mistaken for the owner's.
        var autoWriting = false
        // The value the app itself last put in the Rate box. Anything else
        // there was typed by the owner, and is left alone.
        var autoRate: Double? = null
        // Where an item tapped back out of the list came from, so it goes
        // back into the same place rather than to the end.
        var editingIndex: Int? = null
        // A batch to re-select once the product lookup returns (an item
        // tapped back out of the list keeps the batch it was drawn from).
        var pendingBatchId: Long? = null
        // For a return: this customer's last price for the product.
        var lastSale: LastRate? = null
        // Set once the owner has seen (or not needed) the over-return
        // warning for exactly what is on the form; any change clears it.
        var returnChecked = false
        // For a sale: this customer's last price for the product, per type.
        var lastCredit: LastRate? = null
        var lastCash: LastRate? = null
        // This customer's last sale with items, for "Last time's items".
        var lastSaleEntry: EntryWithItems? = null

        fun chosenRateType(): String =
            if (cgRateType.checkedChipId == R.id.chipRateCash) RateType.CASH else RateType.CREDIT

        fun isSale(): Boolean = isGiven && partyIsCustomer

        // Goods coming BACK from a customer ("I got" with items): returned at
        // the price they went out at, and put back on the shelf. On a
        // supplier's account goods arrive through a bill, not here.
        fun isReturn(): Boolean = !isGiven && partyIsCustomer

        /** A list of items is offered on a customer's account, both ways. */
        fun goodsListAllowed(): Boolean = partyIsCustomer

        fun rateWasTyped(): Boolean {
            val r = Digits.parse(etRate.text)
            return r != null && r != autoRate
        }

        /** The item being written in the boxes, or null if nothing about goods is there. */
        fun editorDraft(): LineDraft? {
            val name = etItemName.text.toString().trim().ifEmpty { null }
            val qty = Digits.parse(etQuantity.text)
            if (name == null && qty == null && matchedProductId == null && selectedBatch == null) return null
            val product = matchedProduct
            return LineDraft(
                itemName = name,
                quantity = qty,
                unit = etUnit.text.toString().trim().ifEmpty { null },
                rate = if (goodsListAllowed()) Digits.parse(etRate.text) else null,
                productId = matchedProductId,
                billItemId = selectedBatch?.id,
                creditPrice = product?.creditPrice,
                cashPrice = product?.salePrice,
                productUnit = product?.defaultUnit,
                rateEdited = rateWasTyped(),
                updateProductRate = cbUpdateRate.visibility == View.VISIBLE && cbUpdateRate.isChecked,
                addToProducts = cbAddProduct.visibility == View.VISIBLE && cbAddProduct.isChecked
            )
        }

        fun writeAmount(text: String) {
            autoWriting = true
            try {
                etAmount.setText(text)
                etAmount.setSelection(etAmount.text.length)
                amountByItems = text.isNotEmpty()
            } finally {
                autoWriting = false
            }
        }

        fun refreshTotal() {
            returnChecked = false
            // "Last time's items" only on an empty new sale form.
            btnRepeatLast.visibility =
                if (isSale() && editing == null && lastSaleEntry != null && lines.isEmpty() && editorDraft() == null)
                    View.VISIBLE else View.GONE
            // A printed bill read into a new sale; editing has its own items.
            btnScanBill.visibility = if (isSale() && editing == null) View.VISIBLE else View.GONE
            btnTotalFill.visibility = View.GONE
            tvLinesNote.visibility = View.GONE
            if (!goodsListAllowed()) return
            val all = lines + listOfNotNull(editorDraft())
            // Goods coming back were not "paid" by cash, bank or cheque:
            // "How was it paid?" makes no sense on a return and is hidden.
            if (isReturn()) paymentSection.visibility = if (all.isEmpty()) View.VISIBLE else View.GONE
            if (all.isEmpty()) {
                // Every item removed: a figure the items wrote goes with them.
                if (amountByItems && etAmount.text.isNotEmpty()) writeAmount("")
                return
            }

            fun note(text: String) {
                tvLinesNote.text = text
                tvLinesNote.visibility = View.VISIBLE
            }

            val total = EntryLines.total(all)
            if (total == null) {
                // Said once there is a list, or whenever the box is empty and
                // Save therefore off: the owner must learn why (a single item
                // with a rate but no quantity used to leave Save grey, silent).
                // Name what is missing: the quantity first, as the form reads.
                if (lines.isNotEmpty() || etAmount.text.isBlank()) {
                    note(getString(
                        if (all.any { it.quantity == null }) R.string.lines_total_need_qty
                        else R.string.lines_total_unknown
                    ))
                }
                return
            }
            // Following the items: the box is the total, live, item by item.
            // The write re-enters here through the box's watcher and finds
            // the figures equal, so it stops there.
            val written = Calc.evalAmount(etAmount.text.toString())
            // A total of 0 (a rate of 0) is not an amount: the box would read
            // it as empty and be written again, forever. Leave it to the owner.
            if (!amountOwn && total > 0.0 && (written == null ||
                    EntryLines.compare(total, written) !is EntryLines.AmountCheck.Equal)) {
                writeAmount(Calc.trim(total))
            }
            // Nothing to offer from a zero total (the same endless write).
            if (total <= 0.0) return
            val amount = Calc.evalAmount(etAmount.text.toString())
            if (amount != null && EntryLines.compare(total, amount) is EntryLines.AmountCheck.Equal) return
            // The owner's own figure differs: offer the items' total back.
            btnTotalFill.visibility = View.VISIBLE
            btnTotalFill.text = getString(R.string.lines_total_fill, Format.money(total))
            btnTotalFill.setOnClickListener {
                amountOwn = false
                writeAmount(Calc.trim(total))
            }
            if (amount == null) return
            when (val check = EntryLines.compare(total, amount)) {
                is EntryLines.AmountCheck.Equal -> Unit
                is EntryLines.AmountCheck.Less -> note(
                    getString(R.string.lines_amount_less, Format.money(total), Format.money(amount), Format.money(check.by))
                )
                is EntryLines.AmountCheck.More -> note(
                    getString(R.string.lines_amount_more, Format.money(total), Format.money(amount), Format.money(check.by))
                )
            }
        }

        // fill = false when the owner is typing in the Rate box himself: the
        // hints and offers follow his figure, but the box is never refilled
        // under his fingers (clearing it must leave it clear).
        fun refreshRateSuggestion(fill: Boolean = true) {
            tvRateNote.visibility = View.GONE
            val product = matchedProduct
            val result = if (product == null || !isSale()) {
                RateOffer.Result.Hidden
            } else {
                RateOffer.price(
                    isCustomer = true,
                    creditPrice = product.creditPrice,
                    cashPrice = product.salePrice,
                    productUnit = product.defaultUnit,
                    rateType = chosenRateType(),
                    typedUnit = etUnit.text.toString()
                )
            }
            // The chips stay while any listed item can still be re-priced by them.
            val listPriced = lines.any { it.productId != null && (it.creditPrice != null || it.cashPrice != null) }
            // Any goods on a sale show the choice, priced list item or not. It was
            // hidden for goods not on the products list, yet the entry was still
            // saved as "Credit rate", a choice the owner never saw, and a cash
            // sale of such goods could not be recorded at all (the "money
            // received?" pairing follows Cash). Udhar stays the default.
            val goodsOnSale = goodsListAllowed() &&
                (lines.isNotEmpty() || etItemName.text.isNotBlank())
            rateSection.visibility =
                if (isSale() && (result !is RateOffer.Result.Hidden || listPriced || goodsOnSale)) View.VISIBLE else View.GONE

            // Fill (or clear) the Rate box — never over a rate the owner
            // typed. A sale takes the product's price for the chip; a return
            // takes the price this customer was last charged, when it was
            // per the same unit (else nothing — never a guess).
            if (fill && goodsListAllowed() && !rateWasTyped()) {
                val typedUnit = etUnit.text.toString().trim()
                val offered = when {
                    isSale() -> (result as? RateOffer.Result.Offer)?.rate
                    isReturn() -> lastSale?.takeIf {
                        typedUnit.isEmpty() || it.unit == null || it.unit.equals(typedUnit, ignoreCase = true)
                    }?.rate
                    else -> null
                }
                val text = offered?.let { Calc.trim(it) } ?: ""
                if (etRate.text.toString() != text) etRate.setText(text)
                autoRate = offered
            }

            fun note(text: String) {
                tvRateNote.text = text
                tvRateNote.visibility = View.VISIBLE
            }
            when (result) {
                is RateOffer.Result.NotSet -> note(
                    getString(
                        if (chosenRateType() == RateType.CASH) R.string.rate_not_set_cash
                        else R.string.rate_not_set_credit
                    )
                )
                is RateOffer.Result.UnitMismatch ->
                    note(getString(R.string.rate_unit_mismatch, result.productUnit, result.typedUnit))
                else -> Unit
            }

            // What this customer paid last time, same product, same rate
            // type and unit — so a bargain is made knowing the history.
            val typedUnit = etUnit.text.toString().trim()
            val sameUnit = { r: LastRate -> typedUnit.isEmpty() || r.unit == null || r.unit.equals(typedUnit, ignoreCase = true) }
            val last = if (isSale()) {
                (if (chosenRateType() == RateType.CASH) lastCash else lastCredit)?.takeIf(sameUnit)
            } else null
            tvLastRate.visibility = if (last != null) View.VISIBLE else View.GONE
            if (last != null) tvLastRate.text = getString(R.string.last_rate_hint, Format.money(last.rate))

            // A typed rate that differs from the product's price for this chip
            // may be made the product's price too — offered, off by default.
            val typedRate = Digits.parse(etRate.text)
            val offeredRate = (result as? RateOffer.Result.Offer)?.rate
            val showUpdate = isSale() && product != null && rateWasTyped() &&
                typedRate != null && typedRate > 0.0 && typedRate != offeredRate &&
                result !is RateOffer.Result.UnitMismatch
            if (showUpdate && product != null && typedRate != null) {
                cbUpdateRate.text = getString(
                    R.string.update_product_rate,
                    product.name,
                    getString(if (chosenRateType() == RateType.CASH) R.string.rate_type_cash else R.string.rate_type_credit),
                    Format.money(typedRate)
                )
                cbUpdateRate.visibility = View.VISIBLE
            } else {
                cbUpdateRate.isChecked = false
                cbUpdateRate.visibility = View.GONE
            }

            // A name that is not a product yet may be added to the list.
            val typedName = etItemName.text.toString().trim()
            if (goodsListAllowed() && product == null && typedName.isNotEmpty()) {
                cbAddProduct.text = getString(R.string.add_to_products, typedName)
                cbAddProduct.visibility = View.VISIBLE
            } else {
                cbAddProduct.isChecked = false
                cbAddProduct.visibility = View.GONE
            }
            refreshTotal()
        }

        // Set just below renderLines (it needs refreshBatchButton, defined later).
        var pullBack: (Int) -> Unit = {}

        fun renderLines() {
            linesContainer.removeAllViews()
            val density = resources.displayMetrics.density
            lines.forEachIndexed { i, d ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                // Built OUTSIDE the TextView's apply block: inside it, a bare
                // append() binds to TextView.append (which returns nothing),
                // not to a StringBuilder — that broke the build once.
                val rateText = if (d.isBonus) " (" + getString(R.string.bonus_tag) + ")"
                    else d.rate?.let { " × " + Format.money(it) }.orEmpty()
                val totalText = if (d.isBonus) "" else d.total()?.let { " = " + Format.money(it) }.orEmpty()
                val lineText = "${i + 1}. " +
                    Format.goods(d.itemName, d.quantity, d.unit).orEmpty() + rateText + totalText
                val label = TextView(this).apply {
                    text = lineText
                    setTextColor(ContextCompat.getColor(this@PartyDetailActivity, R.color.ink))
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    minHeight = (48 * density).toInt()
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    // Tap to correct an item: it goes back into the boxes
                    // (and later back into the same place in the list).
                    setOnClickListener { pullBack(i) }
                }
                val remove = android.widget.ImageButton(this).apply {
                    setImageResource(R.drawable.ic_close)
                    contentDescription = getString(R.string.remove_line)
                    imageTintList = android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(this@PartyDetailActivity, R.color.text_muted)
                    )
                    val ripple = android.util.TypedValue()
                    theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)
                    setBackgroundResource(ripple.resourceId)
                    layoutParams = LinearLayout.LayoutParams((48 * density).toInt(), (48 * density).toInt())
                    setOnClickListener {
                        lines.removeAt(i)
                        editingIndex = editingIndex?.let { if (i < it) it - 1 else it }
                        renderLines()
                        refreshRateSuggestion()
                    }
                }
                row.addView(label)
                row.addView(remove)
                linesContainer.addView(row)
            }
            // Qarz-e-Hasna is a loan of money. An entry carrying a list of
            // goods is a sale, and cannot be one.
            if (lines.isNotEmpty()) {
                cbQarzeHasna.isChecked = false
                cbQarzeHasna.isEnabled = false
            } else {
                cbQarzeHasna.isEnabled = true
            }
        }

        cgRateType.setOnCheckedStateChangeListener { _, _ ->
            val type = chosenRateType()
            for (i in lines.indices) lines[i] = lines[i].repriced(type, partyIsCustomer)
            renderLines()
            refreshRateSuggestion()
        }

        btnAddLine.setOnClickListener {
            val draft = editorDraft()
            if (draft == null || draft.itemName.isNullOrBlank()) {
                etItemName.error = getString(R.string.line_need_name)
                etItemName.requestFocus()
                return@setOnClickListener
            }
            if (draft.quantity == null || draft.quantity <= 0.0) {
                etQuantity.error = getString(R.string.line_need_qty)
                etQuantity.requestFocus()
                return@setOnClickListener
            }
            val at = editingIndex
            if (at != null && at <= lines.size) lines.add(at, draft) else lines += draft
            editingIndex = null
            // Clear the boxes for the next item.
            etItemName.setText("", false)
            etQuantity.setText("")
            etUnit.setText("", false)
            etRate.setText("")
            autoRate = null
            matchedProduct = null
            matchedProductId = null
            selectedBatch = null
            pendingBatchId = null
            lastSale = null
            lastCredit = null
            lastCash = null
            cbUpdateRate.isChecked = false
            cbAddProduct.isChecked = false
            btnBatch.visibility = View.GONE
            renderLines()
            refreshRateSuggestion()
            etItemName.requestFocus()
        }

        val simpleWatcher = { action: () -> Unit ->
            object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) = action()
                override fun beforeTextChanged(c: CharSequence?, a: Int, b: Int, cc: Int) {}
                override fun onTextChanged(c: CharSequence?, a: Int, b: Int, cc: Int) {}
            }
        }
        etRate.addTextChangedListener(simpleWatcher { refreshRateSuggestion(fill = false) })
        etAmount.addTextChangedListener(simpleWatcher {
            // The owner's own change. It is his figure only when goods are
            // already on the form; before that, or cleared, the items rule.
            if (!autoWriting) {
                amountByItems = false
                amountOwn = etAmount.text.isNotBlank() && (lines.isNotEmpty() || editorDraft() != null)
            }
            refreshTotal()
        })

        // Whether this is a customer decides everything sale-only above.
        lifecycleScope.launch {
            partyIsCustomer = dao.getParty(partyId)?.isCustomer ?: false
            if (isSale() && editing == null) lastSaleEntry = dao.lastSaleWithItems(partyId)
            lastSaleEntry?.let {
                btnRepeatLast.text = getString(R.string.repeat_last_sale, Format.dateOnly(it.entry.timestamp))
            }
            // Goods coming IN from a supplier belong on a supplier bill.
            btnSupplierBill.visibility =
                if (!partyIsCustomer && !isGiven && editing == null) View.VISIBLE else View.GONE
            val list = goodsListAllowed()
            etRate.visibility = if (list) View.VISIBLE else View.GONE
            btnAddLine.visibility = if (list) View.VISIBLE else View.GONE
            refreshRateSuggestion()
        }

        /**
         * Look up whether the typed name is a product with recorded batches,
         * and show or hide the batch button accordingly.
         *
         * Only for a sale (isGiven) — a batch is something stock LEAVES from,
         * and "I Got" is money or goods coming back, not going out. Runs on
         * this screen's own lifecycleScope: it is a read for the UI, not a
         * write that must survive the screen closing, so it should be
         * cancelled along with everything else if the owner navigates away
         * mid-lookup.
         */
        fun refreshBatchButton() {
            if (!isGiven && !isReturn()) return
            val typed = etItemName.text.toString().trim()
            if (typed.isEmpty()) {
                selectedBatch = null
                lastSale = null
                lastCredit = null
                lastCash = null
                matchedProductId = null
                // The product goes with the name: a cleared name must not
                // leave the last product's rate sitting in the Rate box.
                matchedProduct = null
                btnBatch.visibility = View.GONE
                refreshRateSuggestion()
                return
            }
            lifecycleScope.launch {
                val product = dao.productByKey(ProductName.key(typed))
                // Batches are what a SALE is drawn from; a return offers none.
                val options = if (isGiven) product?.let { dao.batchOptionsForProduct(it.id) } ?: emptyList()
                    else emptyList()
                val last = if (isReturn() && product != null) dao.lastSaleRate(partyId, product.id) else null
                val lastCr = if (isSale() && product != null) dao.lastSaleRate(partyId, product.id, RateType.CREDIT) else null
                val lastCa = if (isSale() && product != null) dao.lastSaleRate(partyId, product.id, RateType.CASH) else null

                // The item name changed since this lookup started (the owner
                // kept typing) — do not act on a stale answer.
                if (etItemName.text.toString().trim() != typed) return@launch

                matchedProductId = product?.id
                matchedProduct = product
                lastSale = last
                lastCredit = lastCr
                lastCash = lastCa
                // The product's own unit, when the owner has not typed one —
                // so the same goods are not counted as "bori" one day and
                // "bag" the next (stock adds only like to like). Never over
                // what he typed; filter=false keeps the dropdown shut.
                val productUnit = product?.defaultUnit?.trim()
                if (!productUnit.isNullOrEmpty() && etUnit.text.toString().isBlank()) {
                    etUnit.setText(productUnit, false)
                }
                refreshRateSuggestion()
                if (options.isEmpty()) {
                    selectedBatch = null
                    btnBatch.visibility = View.GONE
                } else {
                    btnBatch.visibility = View.VISIBLE
                    // An item tapped back out of the list keeps its batch.
                    selectedBatch = pendingBatchId?.let { id -> options.find { it.id == id } }
                    pendingBatchId = null
                    btnBatch.text = selectedBatch?.let {
                        getString(R.string.batch_chosen, it.batchNumber ?: getString(R.string.batch_none))
                    } ?: getString(R.string.pick_batch)
                    btnBatch.setOnClickListener {
                        showBatchPicker(options) { chosen ->
                            selectedBatch = chosen
                            btnBatch.text = if (chosen == null) {
                                getString(R.string.pick_batch)
                            } else {
                                getString(
                                    R.string.batch_chosen,
                                    chosen.batchNumber ?: getString(R.string.batch_none)
                                )
                            }
                        }
                    }
                }
            }
        }

        pullBack = pull@{ i ->
            // One item in the boxes at a time: the owner finishes (adds) or
            // clears what is there before pulling another back.
            if (editorDraft() != null) {
                Toast.makeText(this, R.string.line_editor_busy, Toast.LENGTH_SHORT).show()
                return@pull
            }
            if (i !in lines.indices) return@pull
            val d = lines.removeAt(i)
            editingIndex = i
            etItemName.setText(d.itemName.orEmpty(), false)
            etQuantity.setText(d.quantity?.let { Format.plain(it) } ?: "")
            etUnit.setText(d.unit.orEmpty(), false)
            // The Free option was retired (30 Sep 2026): an old free line comes
            // back as a plain one with no rate, for the owner to price or remove.
            etRate.setText(if (d.isBonus) "" else d.rate?.let { Calc.trim(it) } ?: "")
            // A rate the owner typed stays his; one that came from the
            // product may be refreshed by the lookup below.
            autoRate = if (d.rateEdited) null else d.rate
            matchedProductId = d.productId
            pendingBatchId = d.billItemId
            renderLines()
            refreshBatchButton()
            refreshRateSuggestion()
        }

        // "Last time's items": the same goods, at TODAY's prices for the chip
        // (an item with no product keeps its old rate, as the owner's). No
        // batches — last time's batch may be finished. Everything lands in the
        // list, to change or remove before saving.
        // "Scan bill photo": the kept lines join the list at the BILL's rate
        // (what was actually charged — marked as the owner's, so no price
        // chip re-prices it), matched to a product where the name is one.
        // The bill's picture becomes this entry's bill photo, and its number
        // goes into an empty note. All of it stays editable until Save.
        btnScanBill.setOnClickListener {
            scanForPayment = false
            scanFlow.chooseSource()
        }
        onScanUsed = { bill, kept, photo ->
            if (photo != null) {
                BillPhoto.delete(pendingBillPhoto)
                pendingBillPhoto = photo
                billButton?.setText(R.string.entry_bill_chip_done)
            }
            if (etNote.text.isBlank()) {
                bill.billNumber?.let { etNote.setText(getString(R.string.bill_scan_number, it)) }
            }
            lifecycleScope.launch {
                val products = runCatching { dao.productsOnce() }.getOrDefault(emptyList())
                val drafts = kept.map { item ->
                    val product = products.firstOrNull { it.name.trim().equals(item.name.trim(), ignoreCase = true) }
                    LineDraft(
                        itemName = item.name, quantity = item.quantity, unit = item.unit,
                        rate = item.rate, rateEdited = true,
                        productId = product?.id,
                        creditPrice = product?.creditPrice, cashPrice = product?.salePrice,
                        productUnit = product?.defaultUnit
                    )
                }
                lines.addAll(drafts)
                renderLines()
                refreshRateSuggestion()
            }
        }

        btnRepeatLast.setOnClickListener {
            val source = lastSaleEntry ?: return@setOnClickListener
            lifecycleScope.launch {
                val type = chosenRateType()
                val drafts = source.orderedItems().map { item ->
                    val product = item.productId?.let { dao.productById(it) }
                    when {
                        // Went free last time. Free is no longer offered, so it
                        // comes as a plain line with NO rate: never priced
                        // silently; the owner prices it or removes it.
                        item.isBonus -> LineDraft(
                            itemName = item.itemName, quantity = item.quantity, unit = item.unit, rate = null,
                            productId = product?.id
                        )
                        product != null -> {
                            val price = RateOffer.price(
                                isCustomer = true,
                                creditPrice = product.creditPrice,
                                cashPrice = product.salePrice,
                                productUnit = product.defaultUnit,
                                rateType = type,
                                typedUnit = item.unit.orEmpty()
                            )
                            LineDraft(
                                itemName = item.itemName, quantity = item.quantity, unit = item.unit,
                                rate = (price as? RateOffer.Result.Offer)?.rate,
                                productId = product.id,
                                creditPrice = product.creditPrice, cashPrice = product.salePrice,
                                productUnit = product.defaultUnit
                            )
                        }
                        else -> LineDraft(
                            itemName = item.itemName, quantity = item.quantity, unit = item.unit,
                            rate = item.rate, rateEdited = true
                        )
                    }
                }
                lines.addAll(drafts)
                renderLines()
                refreshRateSuggestion()
            }
        }

        etItemName.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // The list opens ABOVE the field, not below: below runs across
                // Quantity, Unit and the rest of the form still to be filled.
                // Supplier Bill's own product field already reads this way.
                // Row height is approximate (a row is 48dp min, taller with
                // its second line) — the constant is the worst case, so the
                // list sits a little clear of the field rather than touching
                // it, never overlapping it.
                val rowHeight = (56 * resources.displayMetrics.density).toInt()
                etItemName.dropDownVerticalOffset = -(etItemName.height + 3 * rowHeight)
            } else {
                refreshBatchButton()
            }
        }

        // Tapping a suggestion is the owner finishing the name, so the
        // product lookup runs there and then. Without this it would wait for
        // the focus to leave the box, and the rate offer and batch button
        // would sit hidden while the name they name is already on screen.
        etItemName.setOnItemClickListener { _, _, _, _ -> refreshBatchButton() }

        etQuantity.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = refreshRateSuggestion()
            override fun beforeTextChanged(c: CharSequence?, a: Int, b: Int, cc: Int) {}
            override fun onTextChanged(c: CharSequence?, a: Int, b: Int, cc: Int) {}
        })
        // The unit decides whether the rate applies at all (see above).
        etUnit.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = refreshRateSuggestion()
            override fun beforeTextChanged(c: CharSequence?, a: Int, b: Int, cc: Int) {}
            override fun onTextChanged(c: CharSequence?, a: Int, b: Int, cc: Int) {}
        })

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

        // ---- How the money arrived ----
        //
        // The mirror image of recovery above: meaningful only on money coming
        // IN. Goods handed over on udhar were not paid by any method, so on an
        // "I Gave" entry the whole section stays hidden and the column stays
        // null.
        val cgPaymentMethod: ChipGroup = view.findViewById(R.id.cgPaymentMethod)
        if (!isGiven) {
            view.findViewById<View>(R.id.paymentMethodSection).visibility = View.VISIBLE
        }

        // "Scan payment slip" (1 Oct): a JazzCash/Easypaisa screenshot or a
        // bank slip, read on the phone (PaymentScan) and checked by the owner.
        // A new "I got" entry only: an entry being edited keeps what it has.
        // What was read only fills boxes; nothing is saved until Save.
        //  - amount: replaces the box, since the owner chose to use it;
        //  - reference: into the note, added to whatever is written there;
        //  - date: only a day that has already come, kept at the time now
        //    when it is today (a ledger never takes a future date);
        //  - the slip's picture: this entry's bill photo.
        // How it was paid is left to the owner: a screenshot does not say
        // for certain whether it was a bank or a wallet.
        val btnScanPayment: MaterialButton = view.findViewById(R.id.btnScanPayment)
        btnScanPayment.visibility = if (!isGiven && editing == null) View.VISIBLE else View.GONE
        btnScanPayment.setOnClickListener {
            scanForPayment = true
            scanFlow.chooseSource(R.string.payment_scan)
        }
        onPaymentScanUsed = { payment, photo ->
            payment.amount?.let {
                etAmount.setText(Calc.trim(it))
                etAmount.setSelection(etAmount.text.length)
            }
            payment.reference?.let { ref ->
                val tag = getString(R.string.payment_scan_reference, ref)
                val now = etNote.text.toString().trim()
                if (!now.contains(ref)) etNote.setText(if (now.isEmpty()) tag else "$now\n$tag")
            }
            payment.date?.let { day ->
                val now = System.currentTimeMillis()
                if (!android.text.format.DateUtils.isToday(day) && day <= now) {
                    chosenTime = day
                    attachDate()
                }
            }
            if (photo != null) {
                BillPhoto.delete(pendingBillPhoto)
                pendingBillPhoto = photo
                billButton?.setText(R.string.entry_bill_chip_done)
            }
        }

        /** The key behind whichever chip is checked, or null when none is. */
        fun chosenPaymentMethod(): String? = when (cgPaymentMethod.checkedChipId) {
            R.id.chipCash -> PaymentMethod.CASH
            R.id.chipBank -> PaymentMethod.BANK
            R.id.chipCheque -> PaymentMethod.CHEQUE
            R.id.chipOnline -> PaymentMethod.ONLINE
            else -> null
        }

        // ---- Editing: put the entry back on the form ----
        if (editing != null) {
            val e = editing.entry
            etNote.setText(e.note.orEmpty())
            cbQarzeHasna.isChecked = e.isQarzeHasna
            if (isGiven && e.recovery == Recovery.DOUBTFUL) rbDoubtful.isChecked = true
            when (e.paymentMethod) {
                PaymentMethod.CASH -> cgPaymentMethod.check(R.id.chipCash)
                PaymentMethod.BANK -> cgPaymentMethod.check(R.id.chipBank)
                PaymentMethod.CHEQUE -> cgPaymentMethod.check(R.id.chipCheque)
                PaymentMethod.ONLINE -> cgPaymentMethod.check(R.id.chipOnline)
            }
            // The chip first, while the list is still empty, so nothing is
            // re-priced by setting it.
            if (e.rateType == RateType.CASH) cgRateType.check(R.id.chipRateCash)
            // Every saved rate counts as the owner's own: it was agreed at the
            // time of the sale, and a chip switch while editing must not move
            // it to today's price.
            lines.addAll(editing.orderedItems().map {
                LineDraft(
                    itemName = it.itemName,
                    quantity = it.quantity,
                    unit = it.unit,
                    rate = it.rate,
                    productId = it.productId,
                    billItemId = it.billItemId,
                    rateEdited = true,
                    isBonus = it.isBonus,
                    // Advice written on the entry screen survives an edit.
                    crop = it.crop,
                    pest = it.pest,
                    dose = it.dose
                )
            })
            // Saved at exactly its items' total: keeps following them. Saved
            // at a figure of its own (a discount, or items with no total):
            // keeps that figure.
            val savedAtTotal = EntryLines.total(lines)?.let {
                EntryLines.compare(it, e.amount) is EntryLines.AmountCheck.Equal
            } ?: false
            amountByItems = savedAtTotal
            amountOwn = lines.isNotEmpty() && !savedAtTotal
            renderLines()
            refreshRateSuggestion()
        }

        // The Save button's action, set once the dialog exists below; the
        // over-return check calls it again after the owner has answered.
        var requestSave: () -> Unit = {}

        // Validates and saves. False (and the screen stays open) when the
        // amount is not a positive figure; the old dialog button closed the
        // form even then, throwing away whatever had been typed.
        fun trySave(): Boolean {
            val amount = Calc.evalAmount(etAmount.text.toString())
            if (amount == null) {
                Toast.makeText(this, R.string.enter_valid_amount, Toast.LENGTH_SHORT).show()
                return false
            }
            val note = etNote.text.toString().trim().ifEmpty { null }

            val recovery = if (isGiven && rbDoubtful.isChecked) {
                Recovery.DOUBTFUL
            } else {
                Recovery.CERTAIN
            }

            // The items: every one in the list, plus whatever is still in the
            // boxes. With a list, the boxes must hold a complete item or
            // nothing — half an item is not saved silently.
            val editor = editorDraft()
            if (lines.isNotEmpty() && editor != null && !editor.isComplete()) {
                if (editor.itemName.isNullOrBlank()) {
                    etItemName.error = getString(R.string.line_need_name)
                    etItemName.requestFocus()
                } else {
                    etQuantity.error = getString(R.string.line_need_qty)
                    etQuantity.requestFocus()
                }
                return false
            }
            val all = lines.toMutableList()
            val at = editingIndex
            if (editor != null) {
                if (at != null && at <= all.size) all.add(at, editor) else all.add(editor)
            }
            val items = all.mapNotNull { it.toItem() }

            // The product offers the owner ticked — add a new name to the
            // products list, or make a typed rate the product's price — are
            // applied first, then Save runs again with each item linked.
            if (all.any { it.updateProductRate || it.addToProducts }) {
                // Exactly where the boxes' item was placed above — not a
                // search, which would find an identical listed item first.
                val editorPos = when {
                    editor == null -> -1
                    at != null && at <= lines.size -> at
                    else -> all.size - 1
                }
                val type = chosenRateType()
                lifecycleScope.launch {
                    for (idx in all.indices) {
                        val d = all[idx]
                        if (!d.updateProductRate && !d.addToProducts) continue
                        var productId = d.productId
                        if (d.addToProducts && productId == null && !d.itemName.isNullOrBlank()) {
                            productId = dao.findOrCreateProduct(d.itemName, defaultUnit = d.unit).id
                        }
                        val rate = d.rate
                        if (isSale() && productId != null && rate != null && rate > 0.0 && !d.isBonus) {
                            dao.productById(productId)?.let { p ->
                                dao.updateProduct(
                                    if (type == RateType.CASH) p.copy(salePrice = rate) else p.copy(creditPrice = rate)
                                )
                            }
                        }
                        all[idx] = d.copy(productId = productId, updateProductRate = false, addToProducts = false)
                    }
                    lines.clear()
                    all.forEachIndexed { i, d -> if (i != editorPos) lines += d }
                    if (editorPos >= 0) matchedProductId = all[editorPos].productId
                    cbUpdateRate.isChecked = false
                    cbAddProduct.isChecked = false
                    renderLines()
                    requestSave()
                }
                return false
            }

            // A return larger than this customer holds on the book is not
            // refused (the goods may predate the app), but the owner is asked
            // FIRST, while the form is still open — "No" loses nothing.
            if (isReturn() && !returnChecked && items.any { it.productId != null && it.quantity != null }) {
                lifecycleScope.launch {
                    val over = mutableListOf<String>()
                    items.filter { it.productId != null && it.quantity != null }
                        .groupBy { Pair(it.productId ?: 0L, it.unit) }
                        .forEach { (key, group) ->
                            val returning = group.sumOf { it.quantity ?: 0.0 }
                            val held = dao.netGoodsWithParty(
                                partyId, key.first, key.second,
                                excludeEntryId = editing?.entry?.id ?: 0L
                            )
                            if (returning > held + 1e-9) {
                                over += getString(
                                    R.string.return_more_than_taken,
                                    partyName,
                                    Format.plain(maxOf(held, 0.0)),
                                    key.second.orEmpty(),
                                    group.first().itemName.orEmpty()
                                )
                            }
                        }
                    if (over.isEmpty()) {
                        returnChecked = true
                        requestSave()
                    } else {
                        MaterialAlertDialogBuilder(this@PartyDetailActivity)
                            .setMessage(over.joinToString("\n\n"))
                            .setPositiveButton(R.string.proceed_anyway) { _, _ ->
                                returnChecked = true
                                requestSave()
                            }
                            .setNegativeButton(R.string.cancel, null)
                            .show()
                    }
                }
                return false
            }

            // Which price list the sale was at — recorded when a rate was.
            // Fixed now, never recalculated (see LedgerEntry.rateType).
            val rateType = if (isSale() && items.any { it.rate != null }) chosenRateType() else null

            val entry = LedgerEntry(
                partyId = partyId,
                amount = amount,
                isGiven = isGiven,
                note = note,
                entryNumber = "",
                isQarzeHasna = cbQarzeHasna.isChecked,
                recovery = recovery,
                timestamp = chosenTime,
                billPhotoPath = pendingBillPhoto,
                // Null unless a chip was actually tapped, and null on
                // every "I Gave" entry, where the section never appeared.
                // A goods return was not "paid" by any method (the section is
                // hidden for it), so nothing is recorded there either.
                paymentMethod = if (isGiven || (isReturn() && items.isNotEmpty())) null
                    else chosenPaymentMethod(),
                rateType = rateType
            )

            // The goods travel as lines, not on the entry (v20 — see
            // EntryItem). A bare money entry has none. Product and batch are
            // tagged per line at the moment the owner confirms, so this sale
            // never needs the "tie existing entries" backfill to count towards
            // stock. entryId and lineNo are set by the DAO from the id the
            // insert returns, inside the same transaction.

            if (editing != null) {
                // The same entry, its number and creation untouched, with its
                // complete new set of items — one transaction (see
                // updateEntryWithItems). Legacy goods columns ride along as
                // they were (copy), written by nothing.
                val updated = editing.entry.copy(
                    amount = amount,
                    note = note,
                    isQarzeHasna = entry.isQarzeHasna,
                    recovery = recovery,
                    timestamp = chosenTime,
                    paymentMethod = entry.paymentMethod,
                    rateType = rateType
                )
                AppScope.launch { dao.updateEntryWithItems(updated, items) }
                return true
            }

            // Warn BEFORE writing, not after — a warning that arrives once
            // the entry is already in the ledger is just an accusation.
            checkTwinThenSave(entry, items)
            return true
        }

        val dialog = MaterialAlertDialogBuilder(this).setView(view).create()
        // A scan that comes back after the form is gone has nowhere to go.
        dialog.setOnDismissListener {
            onScanUsed = null
            onPaymentScanUsed = null
        }

        btnSupplierBill.setOnClickListener {
            dialog.dismiss()
            startActivity(
                Intent(this, BillsActivity::class.java)
                    .putExtra(BillsActivity.EXTRA_NEW_BILL_SUPPLIER, partyName)
            )
        }

        // ---- Header, SAVE, preview: who, which way, how much, live ----
        val color = { id: Int -> ContextCompat.getColor(this, id) }
        view.findViewById<View>(R.id.entryHeader).visibility = View.VISIBLE
        view.findViewById<View>(R.id.entryHeaderBand).visibility = View.VISIBLE
        val tvDirection = view.findViewById<TextView>(R.id.tvEntryDirection)
        val tvTitle = view.findViewById<TextView>(R.id.tvEntryTitle)
        val btnSave = view.findViewById<MaterialButton>(R.id.btnEntrySave)
        btnSave.visibility = View.VISIBLE
        tvDirection.setText(if (isGiven) R.string.i_gave else R.string.i_got)
        // I Gave: red pill, white text. I Got: white pill, green text, so it
        // still reads on the green header.
        tvDirection.backgroundTintList = android.content.res.ColorStateList.valueOf(
            color(if (isGiven) R.color.red_gave else R.color.white)
        )
        tvDirection.setTextColor(color(if (isGiven) R.color.white else R.color.brand_green))
        etAmount.setTextColor(color(if (isGiven) R.color.red_gave_text else R.color.brand_green_text))

        val preview = view.findViewById<View>(R.id.entryBalancePreview)
        val tvPreviewLine = view.findViewById<TextView>(R.id.tvPreviewLine)
        if (isGiven) {
            preview.visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.tvPreviewTitle).text =
                getString(R.string.entry_preview_title, partyName)
        }

        onAmountChanged = {
            val value = Calc.evalAmount(etAmount.text.toString())
            val valid = value != null
            val shown = value ?: 0.0
            tvTitle.text = getString(
                if (isGiven) R.string.entry_header_gave else R.string.entry_header_got,
                partyName, Format.money(shown)
            )
            btnSave.isEnabled = valid
            btnSave.alpha = if (valid) 1f else 0.45f
            if (isGiven) {
                // Positive balance = the customer owes the shop; money given
                // on udhar adds to it.
                // When editing, "before" is the balance WITHOUT this entry.
                val before = currentBalance - (editing?.entry?.let { if (it.isGiven) it.amount else -it.amount } ?: 0.0)
                tvPreviewLine.text = getString(
                    R.string.entry_preview_line,
                    Format.customerBalance(before),
                    Format.customerBalance(before + shown)
                )
            }
        }
        onAmountChanged()

        requestSave = { if (trySave()) dialog.dismiss() }
        btnSave.setOnClickListener { requestSave() }

        dialog.show()
        dialog.also { dialog ->
                // Full width and height, not a card floating in the middle.
                // Set after show() because that is when the window exists.
                dialog.window?.apply {
                    setBackgroundDrawable(
                        android.graphics.drawable.ColorDrawable(color(R.color.page_bg))
                    )
                    setLayout(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    // The note field brings up the phone keyboard; SAVE is
                    // pinned at the bottom and must ride above it.
                    setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                }
                view.fillDialogHeight()

                val stepOne = view.findViewById<View>(R.id.stepOne)
                val stepTwo = view.findViewById<View>(R.id.stepTwo)
                val keypad = view.findViewById<View>(R.id.entryKeypad)
                val more = view.findViewById<MaterialButton>(R.id.btnMoreDetails)

                // The chip shows whether details are open (green outline and
                // fill) and whether any were written (tick instead of plus).
                fun renderDetailsChip(open: Boolean) {
                    val filled = etNote.text.isNotBlank() || etItemName.text.isNotBlank()
                    val base = getString(R.string.entry_details_chip)
                    more.text = if (filled) "\u2713" + base.removePrefix("+") else base
                    more.strokeColor = android.content.res.ColorStateList.valueOf(
                        color(if (open || filled) R.color.brand_green else R.color.page_line)
                    )
                    more.backgroundTintList = android.content.res.ColorStateList.valueOf(
                        color(if (open) R.color.brand_green_soft else R.color.navy_surface)
                    )
                }

                fun show(next: View, gone: View, anim: Int) {
                    gone.visibility = View.GONE
                    next.visibility = View.VISIBLE
                    next.startAnimation(
                        android.view.animation.AnimationUtils.loadAnimation(
                            this@PartyDetailActivity, anim
                        )
                    )
                }

                // Back closes the details before it closes the form. A
                // callback, not onBackPressed/KEYCODE_BACK, so it also works
                // on Android 16+ (API 36 target).
                val detailsBack = object : OnBackPressedCallback(false) {
                    override fun handleOnBackPressed() {
                        more.performClick()
                    }
                }
                dialog.onBackPressedDispatcher.addCallback(detailsBack)

                fun openDetails() {
                    show(stepTwo, stepOne, R.anim.slide_in_right)
                    keypad.visibility = View.GONE
                    detailsBack.isEnabled = true
                    renderDetailsChip(open = true)
                }

                fun closeDetails() {
                    show(stepOne, stepTwo, R.anim.slide_in_left)
                    keypad.visibility = View.VISIBLE
                    detailsBack.isEnabled = false
                    renderDetailsChip(open = false)
                    (getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                        as android.view.inputmethod.InputMethodManager)
                        .hideSoftInputFromWindow(etAmount.windowToken, 0)
                }

                // The same chip both ways.
                more.setOnClickListener {
                    if (stepTwo.visibility == View.VISIBLE) closeDetails() else openDetails()
                }
                // Editing opens on the items — that is what it is for.
                if (editing != null) openDetails()

                // With the details open the keypad is hidden and the system
                // keyboard is suppressed on this field, so a tap on the amount
                // would otherwise do nothing. Focus as well as click: coming
                // from the note field, the first tap only moves focus.
                etAmount.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus && stepTwo.visibility == View.VISIBLE) closeDetails()
                }
                etAmount.setOnClickListener {
                    if (stepTwo.visibility == View.VISIBLE) closeDetails()
                    etAmount.requestFocus()
                    (getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                        as android.view.inputmethod.InputMethodManager)
                        .hideSoftInputFromWindow(etAmount.windowToken, 0)
                }

                // Header arrow: same as the system Back.
                view.findViewById<View>(R.id.btnEntryBack).setOnClickListener {
                    if (stepTwo.visibility == View.VISIBLE) closeDetails() else dialog.dismiss()
                }
            }
    }

    /**
     * "Moved to Recycle Bin · Undo" (2 Oct). The bin was always the way back;
     * this puts it one tap away for the few seconds a slip is noticed in.
     */
    private fun offerUndo(entryIds: List<Long>) {
        if (entryIds.isEmpty() || isFinishing || isDestroyed) return
        com.google.android.material.snackbar.Snackbar
            .make(findViewById(android.R.id.content), R.string.moved_to_bin, 6000)
            .setAction(R.string.undo) {
                AppScope.launch { entryIds.forEach { dao.restoreEntry(it) } }
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        // An entry binned on its own screen: offer the undo here, where we land.
        com.innovation313.roshankhata.ui.UndoDelete.take(partyId)?.let { offerUndo(it.entryIds) }
    }

    /** Deleting an entry is reversible — it moves to the Recycle Bin. */
    private fun confirmDeleteEntry(entry: LedgerEntry) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_entry_title)
            .setMessage(R.string.delete_entry_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    // Half of a cash sale: ask about the other half first.
                    val pair = entry.pairedEntryId?.let { dao.getEntry(it) }?.takeIf { !it.isDeleted }
                    val alsoPair = if (pair == null) false else askDeletePair(pair.entryNumber)
                    AppScope.launch {
                        dao.softDeleteEntry(entry.id)
                        if (alsoPair && pair != null) dao.softDeleteEntry(pair.id)
                    }.join()
                    offerUndo(listOfNotNull(entry.id, pair?.id?.takeIf { alsoPair }))
                }
            }
            .show()
    }

    /** Delete the other half of a cash sale too? Suspends until answered; true = both. */
    private suspend fun askDeletePair(pairNumber: String): Boolean =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_pair_title)
                .setMessage(getString(R.string.delete_pair_message, pairNumber))
                .setPositiveButton(R.string.delete_pair_both) { _, _ -> if (cont.isActive) cont.resume(true) }
                .setNegativeButton(R.string.delete_pair_one) { _, _ -> if (cont.isActive) cont.resume(false) }
                .setOnCancelListener { if (cont.isActive) cont.resume(false) }
                .show()
        }

    // ---------- Select-and-delete, several entries at once ----------

    private fun enterSelectionMode() {
        selectionMode = true
        selectionBack.isEnabled = true
        selectedEntryIds.clear()
        buttonBar.visibility = View.GONE
        selectionBar.visibility = View.VISIBLE
        adapter.setSelectionState(true, selectedEntryIds)
        refreshSelectionUi()
    }

    private fun exitSelectionMode() {
        selectionMode = false
        selectionBack.isEnabled = false
        selectedEntryIds.clear()
        buttonBar.visibility = View.VISIBLE
        selectionBar.visibility = View.GONE
        adapter.setSelectionState(false, emptySet())
    }

    private fun toggleEntrySelection(entryId: Long) {
        if (!selectedEntryIds.remove(entryId)) selectedEntryIds.add(entryId)
        adapter.setSelectionState(true, selectedEntryIds)
        refreshSelectionUi()
    }

    /** Keeps Select All and the Delete button's count in step with what is actually picked. */
    private fun refreshSelectionUi() {
        updatingSelectionUi = true
        cbSelectAll.isChecked = currentRows.isNotEmpty() && selectedEntryIds.size == currentRows.size
        updatingSelectionUi = false

        btnDeleteSelected.isEnabled = selectedEntryIds.isNotEmpty()
        btnDeleteSelected.text = if (selectedEntryIds.isEmpty()) {
            getString(R.string.delete)
        } else {
            getString(R.string.delete) + " (${selectedEntryIds.size})"
        }
    }

    /**
     * Named with the count, not asked generically — "Delete 4 selected
     * entries?" is a different sentence from "delete these?", and the
     * number is what lets someone catch a wrong selection before it is
     * gone. Still reversible: the same soft delete a single entry gets, so
     * every one of them lands in the Recycle Bin and can be restored.
     */
    private fun confirmDeleteSelectedEntries() {
        val ids = selectedEntryIds.toList()
        if (ids.isEmpty()) return

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_entry_title)
            .setMessage(resources.getQuantityString(
                R.plurals.delete_selected_entries_confirm, ids.size, ids.size
            ))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    AppScope.launch { dao.softDeleteEntries(ids) }.join()
                    Toast.makeText(this@PartyDetailActivity, R.string.moved_to_bin, Toast.LENGTH_SHORT).show()
                    exitSelectionMode()
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
        // Nothing outstanding means nothing a person could hand over. A
        // fraction of a paisa left by the arithmetic is not a debt to remind
        // anyone about.
        if (Money.isZero(currentBalance)) {
            Toast.makeText(this, R.string.nothing_outstanding, Toast.LENGTH_SHORT).show()
            return
        }

        if (partyPhone.isNullOrBlank()) {
            Toast.makeText(this, R.string.no_phone_number, Toast.LENGTH_LONG).show()
            return
        }

        // The date they agreed to pay on, if there is one. Read before the
        // dialog is built so the message the owner reads is the message that
        // gets sent — a date arriving late would change the text underneath them.
        lifecycleScope.launch {
            val promisedDate = withContext(Dispatchers.IO) {
                runCatching { dao.reminderDateForParty(partyId) }.getOrNull()
            }
            showReminderDialog(viaWhatsApp, promisedDate)
        }
    }

    private fun showReminderDialog(viaWhatsApp: Boolean, promisedDate: Long?) {
        val view = layoutInflater.inflate(R.layout.dialog_reminder_preview, null)
        val etMessage: EditText = view.findViewById(R.id.etMessage)
        etMessage.setText(
            Reminder.buildMessage(
                context = this,
                partyName = partyName,
                balance = currentBalance,
                businessName = BusinessProfile.businessName(this),
                promisedDate = promisedDate,
                // The preview must be what actually gets sent: SMS shows no
                // bold, so the owner should not be reading asterisks here
                // and wondering what the customer will see.
                forSms = !viaWhatsApp
            )
        )

        // Park the cursor past the last character. setText leaves it at
        // position 0 — sitting inside the amount, where one accidental key
        // turns "Rs 200" into a figure the customer was never owed.
        etMessage.setSelection(etMessage.text?.length ?: 0)

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
                        PdfExport.StatementRow(it.entry, it.runningBalance, it.items)
                    },
                    // What the account stood at before the earliest row
                    // printed here. Derived from that row's own running
                    // balance by undoing its entry, so it holds whichever way
                    // the list happens to be sorted, and is exactly zero on a
                    // statement that starts at the beginning of the ledger.
                    openingBalance = allRows.minByOrNull { it.entry.timestamp }?.let { r ->
                        r.runningBalance - if (r.entry.isGiven) r.entry.amount else -r.entry.amount
                    } ?: 0.0,
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

            // The same open-first hand-over every other PDF in the app uses:
            // a viewer first, sharing one deliberate tap later. This used to
            // jump straight to the share sheet, so the owner was choosing
            // where to send a document they had never seen open.
            com.innovation313.roshankhata.ui.PdfShare.present(
                this@PartyDetailActivity,
                file,
                R.string.statement_ready_title
            )
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
                // Every line's goods are searchable, not just the first —
                // "urea" must find the visit where urea was the third item.
                val haystack = (
                    listOfNotNull(e.note, e.entryNumber, Format.money(e.amount)) +
                        row.items.flatMap { listOfNotNull(it.itemName, it.unit) }
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

    /**
     * The avatar tap means "show me this" now, not "change it" — that moved
     * to the small camera badge so a look at the photo can never turn into
     * an accidental removal. A 56dp circle only proves a photo exists; it is
     * not big enough to actually recognise anyone by.
     */
    private fun viewPhotoFullSize() {
        val photo = PartyPhoto.load(this, partyId) ?: return
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_photo_viewer)
        dialog.findViewById<ImageView>(R.id.ivFullPhoto).setImageBitmap(photo)
        val close = View.OnClickListener { dialog.dismiss() }
        dialog.findViewById<View>(R.id.ivFullPhoto).setOnClickListener(close)
        dialog.findViewById<View>(R.id.btnClosePhotoViewer).setOnClickListener(close)
        dialog.show()
    }

    /**
     * Put the avatar on screen without making the screen wait for it.
     *
     * The decode used to happen right here, on the main thread, while the
     * ledger was still being built — a file read and a JPEG decode standing
     * between the tap and the first frame. (The same mistake was found and
     * fixed on the Profile screen in f18dc78; this call site was missed.)
     *
     * Warm cache or a customer already known to have no photo is answered on
     * this frame, so nothing flickers in the common case. Only a genuinely
     * cold photo costs a read, and that read now happens on the IO thread
     * with the initials showing meanwhile.
     */
    private fun refreshAvatar() {
        PartyPhoto.cached(partyId)?.let { showAvatar(it); return }
        if (PartyPhoto.knownAbsent(partyId)) { showAvatar(null); return }

        showAvatar(null)
        lifecycleScope.launch {
            val photo = withContext(Dispatchers.IO) {
                PartyPhoto.load(this@PartyDetailActivity, partyId)
            }
            // The owner can remove the photo while this is in flight; only
            // paint what is still true.
            if (photo != null && PartyPhoto.cached(partyId) != null) showAvatar(photo)
        }
    }

    private fun showAvatar(photo: android.graphics.Bitmap?) {
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

    private fun initialsOf(name: String): String = Format.initials(name)

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
            //
            // AppScope: the photo file is ALREADY written by this point. If
            // this pointer never lands, the file sits on disk with nothing
            // referring to it and the owner's photo simply does not appear —
            // the one case here where cancelling loses something that cannot
            // be recovered by repeating the action, since the camera moment
            // has passed.
            AppScope.launch {
                dao.getParty(partyId)?.let { p ->
                    dao.updateParty(p.copy(photoPath = path))
                }
            }

            refreshAvatar()
            Toast.makeText(this@PartyDetailActivity, R.string.photo_saved, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Which batch this sale came out of, or none — offered as a real option
     * every time, not forced. Most sales in a small shop are sold out of
     * whichever carton happens to be open, and the exact batch is simply not
     * always known; asking is right, requiring an answer is not.
     */
    private fun showBatchPicker(options: List<BatchOption>, onPicked: (BatchOption?) -> Unit) {
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

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pick_batch)
            .setItems(labels) { _, which ->
                onPicked(if (which == 0) null else options[which - 1])
            }
            .show()
    }

    /**
     * Runs on [AppScope], not this screen's lifecycleScope — see AppScope's
     * own comment. This dialog closes the instant Save is tapped, while the
     * write is still in flight; tying it to the screen meant a quick Back
     * press right after saving could cancel it before it reached the disk,
     * with nothing on screen ever saying so.
     */
    /**
     * Ask about a repeat before writing it, then run the credit-limit check.
     *
     * The owner found two identical entries a minute apart — the same customer,
     * the same Rs 200, the same direction. That is what a double-tap looks
     * like, or a "did that save?" written twice, and in a ledger it is
     * indistinguishable afterwards from two real sales. Only the person holding
     * the phone can tell, and only in the moment.
     *
     * So it asks rather than blocks, and it shows the TIME of the earlier one,
     * because that is the fact that settles it: a minute ago is a mis-tap, this
     * morning is a second sale. A guard that decided this on its own would
     * eventually delete a real entry.
     *
     * Same DAY, as the owner asked, not the same hour. A shop can sell the same
     * customer the same amount twice in a day; the question is cheap to ask
     * once and the answer is his.
     */
    private fun checkTwinThenSave(entry: LedgerEntry, items: List<EntryItem>) {
        lifecycleScope.launch {
            val day = java.util.Calendar.getInstance().apply {
                timeInMillis = entry.timestamp
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val start = day.timeInMillis
            val end = start + 24L * 60 * 60 * 1000 - 1

            val twin = withContext(Dispatchers.IO) {
                dao.sameDayTwin(entry.partyId, entry.amount, entry.isGiven, start, end)
            }

            if (twin == null) {
                afterTwinCheck(entry, items)
                return@launch
            }

            MaterialAlertDialogBuilder(this@PartyDetailActivity)
                .setTitle(R.string.twin_entry_title)
                .setMessage(
                    getString(
                        R.string.twin_entry_body,
                        Format.money(twin.amount),
                        getString(if (entry.isGiven) R.string.i_gave else R.string.i_got),
                        Format.dateTime(twin.timestamp)
                    )
                )
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.twin_entry_add) { _, _ -> afterTwinCheck(entry, items) }
                .show()
        }
    }

    /** The credit-limit gate, which used to sit inline in the save button. */
    private fun afterTwinCheck(entry: LedgerEntry, items: List<EntryItem>) {
        val limit = creditLimit
        val projected = currentBalance + (if (entry.isGiven) entry.amount else -entry.amount)

        if (entry.isGiven && limit != null && limit > 0 &&
            projected > limit && currentBalance <= limit
        ) {
            warnOverLimit(entry, items, limit, projected)
        } else {
            saveEntry(entry, items)
        }
    }

    private fun saveEntry(entry: LedgerEntry, items: List<EntryItem>) {
        AppScope.launch {
            // Numbering, the entry and its goods lines all happen inside the
            // DAO's own transaction — see insertEntryNumbered for why the count
            // must not be read out here, and insertEntryWithItems for why the
            // lines are written in the same step.
            val id = dao.insertEntryWithItems(entry, items)
            // Balance as it stands once this entry counts (positive = owed to me).
            val before = currentBalance
            val after = before + if (entry.isGiven) entry.amount else -entry.amount
            // Paid up: the harvest promise has been kept, so it goes (v28).
            if (harvestPromise != null && !Money.isPositive(after)) {
                dao.setHarvestPromise(partyId, null)
                harvestPromise = null
                withContext(Dispatchers.Main) { if (!isFinishing && !isDestroyed) refreshSeasonRecord() }
            }
            withContext(Dispatchers.Main) {
                if (isFinishing || isDestroyed) return@withContext
                // Felt in the hand: the entry is in (2 Oct).
                com.innovation313.roshankhata.ui.Motion.confirm(findViewById(android.R.id.content))
                // Paid down to nothing from owing: celebrate (it includes the
                // card to send), instead of the plain "send this entry" bar.
                val settledNow = !entry.isGiven && Money.isPositive(before) && !Money.isPositive(after)
                if (settledNow) com.innovation313.roshankhata.ui.SettledCelebration.show(this@PartyDetailActivity, partyName)
                else offerEntryShare(entry, after)
                // Sold at the cash (naqd) rate: ask whether the money came now.
                if (entry.isGiven && entry.rateType == RateType.CASH) offerCashReceived(id, entry.amount)
            }
        }
    }

    /**
     * "Fasal bech kar dunga" (v28): the customer's own day. The next two
     * harvest ends are offered (Kharif 30 Nov, Rabi 31 May), or any date;
     * Follow-up then leaves him alone until that day and puts him first on
     * it. No late fee or markup goes with it, ever.
     */
    private fun showHarvestPromiseDialog() {
        val now = System.currentTimeMillis()
        val y = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val ends = listOf(
            SeasonBook.Season(SeasonBook.Crop.RABI, y - 1), SeasonBook.Season(SeasonBook.Crop.KHARIF, y),
            SeasonBook.Season(SeasonBook.Crop.RABI, y), SeasonBook.Season(SeasonBook.Crop.KHARIF, y + 1)
        ).map { it to SeasonBook.harvestEnd(it) }.filter { it.second > now }.sortedBy { it.second }.take(2)

        val labels = ends.map { (s, at) ->
            getString(R.string.harvest_promise_option, SeasonText.name(this, s), Format.dateOnly(at))
        } + getString(R.string.harvest_promise_other_date) +
            listOfNotNull(harvestPromise?.let { getString(R.string.harvest_promise_remove) })

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.harvest_promise_title, partyName))
            .setItems(labels.toTypedArray()) { _, i ->
                when {
                    i < ends.size -> saveHarvestPromise(ends[i].second)
                    i == ends.size -> {
                        val c = java.util.Calendar.getInstance()
                        android.app.DatePickerDialog(this, { _, yy, mm, dd ->
                            val at = java.util.Calendar.getInstance().apply {
                                clear(); set(yy, mm, dd, 23, 59, 59)
                            }.timeInMillis
                            saveHarvestPromise(at)
                        }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH))
                            .apply { datePicker.minDate = now }
                            .show()
                    }
                    else -> saveHarvestPromise(null)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun saveHarvestPromise(at: Long?) {
        harvestPromise = at
        AppScope.launch {
            dao.setHarvestPromise(partyId, at)
            withContext(Dispatchers.Main) { if (!isFinishing && !isDestroyed) refreshSeasonRecord() }
        }
    }

    /** "Rabi 2025-26: cleared 12 days after harvest · Kharif 2026: Rs 4,000 due" — customers only. */
    private fun refreshSeasonRecord() {
        lifecycleScope.launch {
            val party = dao.getParty(partyId)
            val rows = if (party?.isCustomer == true) {
                withContext(Dispatchers.Default) { SeasonBook.book(dao.seasonLinesOf(partyId)) }
            } else emptyList()
            if (isFinishing || isDestroyed) return@launch
            val promise = party?.harvestPromise?.let { getString(R.string.harvest_promise_header, Format.dateOnly(it)) }
            val text = listOfNotNull(promise, SeasonText.record(this@PartyDetailActivity, rows).takeIf { it.isNotEmpty() })
                .joinToString("  ·  ")
            tvSeasonRecord.text = text
            tvSeasonRecord.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    /**
     * "Send this to Aslam on WhatsApp?" — a bar under the list after an entry
     * is saved (2 Oct). Only an offer: ignored, it goes away by itself. Not
     * shown with no phone, for a customer marked "don't offer", or when the
     * owner turned it off in settings. WhatsApp opens with the text ready;
     * the owner presses Send there.
     */
    private fun offerEntryShare(entry: LedgerEntry, balanceAfter: Double) {
        if (partyPhone.isNullOrBlank() || noEntryShare || !BusinessProfile.askShareAfterEntry(this)) return
        val message = Reminder.buildEntryMessage(
            context = this,
            partyName = partyName,
            isGiven = entry.isGiven,
            amount = entry.amount,
            date = entry.timestamp,
            balanceAfter = balanceAfter,
            businessName = BusinessProfile.businessName(this)
        )
        com.google.android.material.snackbar.Snackbar
            .make(findViewById(android.R.id.content), getString(R.string.entry_share_offer, partyName), 8000)
            .setAction(R.string.entry_share_send) { Reminder.sendViaWhatsApp(this, partyPhone, message) }
            .show()
    }

    /**
     * "Was Rs 17,800 received just now?" — after a sale at the cash rate. Yes
     * records the matching "I got" in cash, paired with the sale, so the pair
     * nets to nothing and each can find the other. No writes nothing: the
     * ledger never records money the owner has not said arrived.
     */
    private fun offerCashReceived(saleId: Long, amount: Double) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.cash_received_offer, Format.money(amount)))
            .setMessage(R.string.cash_received_help)
            .setPositiveButton(R.string.cash_received_yes) { _, _ ->
                AppScope.launch { dao.recordCashForSale(saleId) }
            }
            .setNegativeButton(R.string.cash_received_no, null)
            .show()
    }

    /**
     * The limit is advice, not a gate. The owner knows their customer and their
     * own risk better than a number in a database does — so we tell them
     * plainly what this entry will do, and let them decide.
     */
    private fun warnOverLimit(entry: LedgerEntry, items: List<EntryItem>, limit: Double, projected: Double) {
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
            .setPositiveButton(R.string.proceed_anyway) { _, _ -> saveEntry(entry, items) }
            .show()
    }

    /**
     * Change whether this party is a customer or a supplier.
     *
     * Until now this could only be chosen when the party was first created,
     * and never corrected — a contact imported as a customer stayed one
     * forever, however the shop actually deals with them. That is no longer
     * a cosmetic label: the promotion and batch-recall lists include only
     * customers, and a supplier bill warns when its party is not a supplier.
     * A wrong flag therefore decides, silently, who gets messaged and which
     * bills look suspicious.
     *
     * Nothing about the money changes here. The ledger, its entries, the
     * balance and every bill stay exactly as they are — this only corrects
     * what kind of relationship the shop has with this person. That is worth
     * saying on the dialog, because "supplier" next to a balance could easily
     * be read as an offer to move the debt somewhere.
     */
    private fun showPartyTypeDialog() {
        lifecycleScope.launch {
            val party = dao.getParty(partyId) ?: return@launch

            val view = layoutInflater.inflate(R.layout.dialog_party_type, null)
            val rbCustomer: RadioButton = view.findViewById(R.id.rbTypeCustomer)
            val rbSupplier: RadioButton = view.findViewById(R.id.rbTypeSupplier)
            if (party.isCustomer) rbCustomer.isChecked = true else rbSupplier.isChecked = true

            MaterialAlertDialogBuilder(this@PartyDetailActivity)
                .setTitle(R.string.change_party_type)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save) { _, _ ->
                    val nowCustomer = rbCustomer.isChecked
                    if (nowCustomer == party.isCustomer) return@setPositiveButton

                    // AppScope: a deliberate correction the owner made, not a
                    // repeatable command — see AppScope's own comment.
                    AppScope.launch {
                        dao.updateParty(party.copy(isCustomer = nowCustomer))
                        withContext(Dispatchers.Main) {
                            if (!isFinishing && !isDestroyed) {
                                Toast.makeText(
                                    this@PartyDetailActivity,
                                    if (nowCustomer) R.string.now_a_customer
                                    else R.string.now_a_supplier,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
                .show()
        }
    }

    /**
     * The first way to correct a customer since the app shipped.
     *
     * A misspelt name or a wrong number could only ever be fixed by starting
     * a second account, which is exactly how a book ends up holding the same
     * man twice. Father name and village are here for a different reason:
     * they arrived after most customers were already entered, and a field
     * that can only be set at creation is a field the existing hundred can
     * never have.
     *
     * Nothing here can collide: parties carry no unique index on name or
     * phone (only on the QR token, which this does not touch), so a rename is
     * a plain write. Type and credit limit are deliberately absent — each
     * already has its own item in this menu.
     */
    private fun showEditPartyDialog() {
        lifecycleScope.launch {
            val party = dao.getParty(partyId) ?: return@launch

            val view = layoutInflater.inflate(R.layout.dialog_edit_party, null)
            val etName: EditText = view.findViewById(R.id.etEditName)
            val etPhone: EditText = view.findViewById(R.id.etEditPhone)
            val etFather: EditText = view.findViewById(R.id.etEditFatherName)
            val etVillage: EditText = view.findViewById(R.id.etEditVillage)
            val cbNoShare: com.google.android.material.checkbox.MaterialCheckBox =
                view.findViewById(R.id.cbNoEntryShare)
            cbNoShare.isChecked = party.noEntryShare

            etName.setText(party.name)
            etPhone.setText(party.phone)
            etFather.setText(party.fatherName)
            etVillage.setText(party.village)

            MaterialAlertDialogBuilder(this@PartyDetailActivity)
                .setTitle(R.string.edit_details)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save) { _, _ ->
                    val newName = etName.text.toString().trim()
                    if (newName.isEmpty()) {
                        Toast.makeText(
                            this@PartyDetailActivity, R.string.enter_name, Toast.LENGTH_SHORT
                        ).show()
                        return@setPositiveButton
                    }

                    fun typed(e: EditText) = e.text.toString().trim().ifEmpty { null }

                    val updated = party.copy(
                        name = newName,
                        phone = typed(etPhone),
                        fatherName = typed(etFather),
                        village = typed(etVillage),
                        noEntryShare = cbNoShare.isChecked
                    )

                    // AppScope for the write — leaving right after Save must
                    // not cancel it. The header is reloaded afterwards so the
                    // screen shows what was actually saved.
                    AppScope.launch {
                        dao.updateParty(updated)
                        withContext(Dispatchers.Main) {
                            if (!isFinishing && !isDestroyed) loadParty()
                        }
                    }
                }
                .show()
        }
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
                val newLimit = Digits.parse(etLimit.text)
                    ?.takeIf { it > 0 }

                // The read-then-write runs on AppScope — see AppScope's own
                // comment. The limit is a figure the owner typed; losing it to
                // a quick Back press would look exactly like it saved, since
                // this dialog closes immediately either way.
                AppScope.launch {
                    dao.getParty(partyId)?.let { p ->
                        dao.updateParty(p.copy(creditLimit = newLimit))
                    }
                }
                creditLimit = newLimit

                // Already on the main thread, inside the dialog's own click
                // callback, so the toast needs no hop and no guard.
                Toast.makeText(
                    this@PartyDetailActivity,
                    if (newLimit == null) R.string.credit_limit_removed
                    else R.string.credit_limit_saved,
                    Toast.LENGTH_SHORT
                ).show()
            }
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        cameraPath?.let { outState.putString(STATE_CAMERA_PATH, it) }
        scanFlow.saveState(outState, STATE_SCAN_CAMERA_PATH)
        cameraTarget?.let { outState.putString(STATE_CAMERA_TARGET, it.name) }
    }

    /**
     * The customer's QR card: shown big for a counter scan, shareable as a
     * PNG for their phone.
     *
     * Every customer should already have a token — migration 9→10 backfilled
     * the book and new customers are born with one — but a customer restored
     * from an old external backup could arrive without. That case is repaired
     * here, once, and saved: a card issued twice must carry the same code, so
     * the token is never generated without being written back.
     */
    private fun showCustomerQr() {
        lifecycleScope.launch {
            val party = withContext(Dispatchers.IO) { dao.getParty(partyId) } ?: return@launch

            val token = party.qrToken ?: run {
                val fresh = QrTag.newToken()
                // AppScope, and awaited: this token is about to be drawn onto
                // a card the owner may print and hand to a customer. If the
                // write were cancelled the card would exist in the world
                // carrying a token no database has ever heard of, and
                // scanning it would find nobody. Awaited rather than fired
                // and forgotten, so the QR is only ever built from a token
                // that is genuinely saved.
                AppScope.launch {
                    dao.updateParty(party.copy(qrToken = fresh))
                }.join()
                fresh
            }

            val qr = withContext(Dispatchers.IO) { QrImage.of(QrTag.payload(token)) }

            val view = layoutInflater.inflate(R.layout.dialog_customer_qr, null)
            view.findViewById<TextView>(R.id.tvQrPartyName).text = party.name
            view.findViewById<android.widget.ImageView>(R.id.ivQr).setImageBitmap(qr)

            MaterialAlertDialogBuilder(this@PartyDetailActivity)
                .setTitle(R.string.customer_qr)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.share) { _, _ -> shareCustomerQr(qr) }
                .show()
        }
    }

    /**
     * The PNG that goes to the customer is the code alone — no name on it.
     * The name identifies them to anyone the image is forwarded to; the bare
     * code identifies them to nobody, because it only resolves inside this
     * app on this phone.
     */
    private fun shareCustomerQr(qr: android.graphics.Bitmap) {
        try {
            val dir = java.io.File(cacheDir, "receipts").apply { mkdirs() }
            val file = java.io.File(dir, "customer_qr_${partyId}.png")
            java.io.FileOutputStream(file).use {
                qr.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this, "$packageName.fileprovider", file
            )
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_party_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_customer_qr -> {
                showCustomerQr()
                true
            }
            R.id.action_report -> {
                startActivity(
                    Intent(this, ReportActivity::class.java)
                        .putExtra(ReportActivity.EXTRA_PARTY_ID, partyId)
                )
                true
            }
            R.id.action_party_type -> {
                showPartyTypeDialog()
                true
            }
            R.id.action_edit_party -> {
                showEditPartyDialog()
                true
            }
            R.id.action_harvest_promise -> {
                showHarvestPromiseDialog()
                true
            }
            R.id.action_credit_limit -> {
                showCreditLimitDialog()
                true
            }
            R.id.action_select_entries -> {
                enterSelectionMode()
                true
            }
            android.R.id.home -> {
                if (selectionMode) exitSelectionMode() else finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Back handling that works on every Android version. On Android 16+ an app
     * targeting API 36 never receives onBackPressed(), so an override there is
     * silently skipped. This callback goes through the OnBackPressedDispatcher
     * instead, and it is enabled only while there is something for Back to undo,
     * so predictive back (13+) knows when Back will leave the screen.
     */
    private val selectionBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = exitSelectionMode()
    }
}
