package com.innovation313.roshankhata

import com.innovation313.roshankhata.data.get

import com.innovation313.roshankhata.data.quantity
import com.innovation313.roshankhata.data.string

import com.innovation313.roshankhata.ui.BrandDatePickerDialog
import com.innovation313.roshankhata.ui.ChoiceSheet
import com.innovation313.roshankhata.data.Digits

import com.innovation313.roshankhata.ui.Calc

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.innovation313.roshankhata.data.AppScope
import com.innovation313.roshankhata.data.Cheque
import com.innovation313.roshankhata.data.ChequeStatus
import com.innovation313.roshankhata.data.ChequeWithParty
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.ui.ChequeAdapter
import com.innovation313.roshankhata.ui.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Cheque register.
 *
 * The central rule: a cheque is NOT money until it clears. It is held here,
 * outside the ledger, and only posted to the balance the moment the owner
 * confirms the funds actually arrived. Booking a cheque as payment on the day
 * it is handed over is how a bounced cheque quietly corrupts a ledger — the
 * balance says paid, the money never came, and it surfaces weeks later.
 */
class ChequesActivity : BaseActivity() {

    private lateinit var adapter: ChequeAdapter
    private lateinit var tvNoCheques: TextView
    private lateinit var tvDueSummary: TextView

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    private var parties: List<PartyWithBalance> = emptyList()

    // ---------- The cheque's picture (schema 43, 4 Nov 2026) ----------
    //
    // A bill photo's rules, as the cashbook's receipts: the scanner, the camera or the gallery (BillScanFlow), the
    // picture kept in the bills folder (BillPhoto). On a new cheque's form it waits ([pendingPhoto]) until Save; a
    // cheque already written — pending, cleared, bounced (the bank's memo) or cancelled — changes it from its menu
    // ([photoForCheque]), the old picture going once nothing points at it.

    private var pendingPhoto: String? = null
    private var photoButton: MaterialButton? = null
    private var photoForCheque: Long? = null

    private val photoFlow = com.innovation313.roshankhata.ui.BillScanFlow(
        activity = this,
        onPhoto = { uri, temp -> keepPhoto(uri, temp) },
        onNothing = { photoForCheque = null }
    )

    /** A new cheque's form while it is open (M90, 8 Nov 2026): the boxes, the direction and the due day survive a rotation or a kill. */
    private val chequeDraft = com.innovation313.roshankhata.ui.FormDraft("cheque_form")
    private var chequeReopen: com.innovation313.roshankhata.ui.FormDraft.Saved? = null

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        chequeDraft.save(outState)
        photoFlow.saveState(outState, STATE_PHOTO_CAMERA)
        pendingPhoto?.let { outState.putString(STATE_PHOTO_PENDING, it) }
        photoForCheque?.let { outState.putLong(STATE_PHOTO_CHEQUE, it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        photoFlow.restoreState(savedInstanceState, STATE_PHOTO_CAMERA)
        pendingPhoto = savedInstanceState?.getString(STATE_PHOTO_PENDING)
        photoForCheque = savedInstanceState?.getLong(STATE_PHOTO_CHEQUE, -1L)?.takeIf { it > 0 }
        chequeReopen = chequeDraft.saved(savedInstanceState)
        setContentView(R.layout.activity_cheques)
        // How this works, the first time it opens on this phone (1 Nov 2026).
        com.innovation313.roshankhata.ui.FeatureIntro.showOnce(this, com.innovation313.roshankhata.ui.FeatureIntro.Topic.CHEQUES)

        // Edge-to-edge, the mechanism proven on the Home screen.
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)

        tvNoCheques = findViewById(R.id.tvNoCheques)
        tvDueSummary = findViewById(R.id.tvDueSummary)

        adapter = ChequeAdapter { cheque -> showChequeActions(cheque) }
        val rv: RecyclerView = findViewById(R.id.rvCheques)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        findViewById<ExtendedFloatingActionButton>(R.id.fabAddCheque).setOnClickListener {
            showChequeDialog()
        }

        observe()
    }

    private fun observe() {
        lifecycleScope.launch {
            dao.observeCheques().collectLatest { list ->
                adapter.submit(list)
                tvNoCheques.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        // The subtitle should describe what is actually on the screen.
        //
        // It used to count only cheques falling due and say "Nothing due yet"
        // otherwise — which read as nonsense once a cheque had been cleared.
        // There was nothing "yet" about it: the cheque was settled, and the
        // header was still talking as though something were pending.
        lifecycleScope.launch {
            combine(
                dao.observeCheques(),
                dao.observeDueChequeCount(System.currentTimeMillis())
            ) { all, dueCount -> all to dueCount }
                .collectLatest { (all, dueCount) ->
                    val pending = all.count { it.status == ChequeStatus.PENDING }

                    tvDueSummary.text = when {
                        all.isEmpty() -> getString(R.string.due_summary_empty)
                        dueCount > 0 -> Digits.quantity(resources, 
                            R.plurals.due_summary, dueCount, dueCount
                        )
                        pending > 0 -> Digits.string(resources, R.string.due_summary_pending, pending)
                        else -> getString(R.string.due_summary_all_settled)
                    }
                }
        }

        lifecycleScope.launch {
            dao.observePartiesWithBalance().collectLatest {
                parties = it
                // The form that a rotation or a killed process closed comes back once the customers are known.
                chequeReopen?.takeIf { parties.isNotEmpty() && !isFinishing && !isDestroyed }?.let { r -> chequeReopen = null; showChequeDialog(null, r) }
            }
        }
    }

    /**
     * One dialog for writing a cheque down and for correcting it.
     *
     * There was no way to correct one before: a wrong digit in the amount, a
     * mistyped cheque number, the wrong due date, and the only way out was to
     * delete the cheque and write it again. The fields are identical either
     * way, so the same dialog does both — [existing] null means a new cheque.
     */
    private fun showChequeDialog(existing: ChequeWithParty? = null, restore: com.innovation313.roshankhata.ui.FormDraft.Saved? = null) {
        if (parties.isEmpty()) {
            Toast.makeText(this, R.string.select_party_first, Toast.LENGTH_LONG).show()
            return
        }

        val view = layoutInflater.inflate(R.layout.dialog_add_cheque, null)
        com.innovation313.roshankhata.ui.TextFit.relax(view)
        val etParty: AutoCompleteTextView = view.findViewById(R.id.etChequeParty)
        val rbReceived: RadioButton = view.findViewById(R.id.rbReceived)
        val rbIssued: RadioButton = view.findViewById(R.id.rbIssued)
        val etAmount: EditText = view.findViewById(R.id.etChequeAmount)
        val btnDate: MaterialButton = view.findViewById(R.id.btnPickDueDate)
        val etNumber: EditText = view.findViewById(R.id.etChequeNumber)
        val etBank: EditText = view.findViewById(R.id.etBankName)
        val etNote: EditText = view.findViewById(R.id.etChequeNote)

        // A new cheque's picture (schema 43); one written changes its picture from its menu.
        val btnPhoto: MaterialButton = view.findViewById(R.id.btnChequePhoto)
        var written = false
        if (existing == null) {
            btnPhoto.visibility = View.VISIBLE
            photoButton = btnPhoto
            btnPhoto.setText(if (pendingPhoto != null) R.string.photo_attached else R.string.attach_photo)
            btnPhoto.setOnClickListener {
                if (pendingPhoto == null) { photoForCheque = null; photoFlow.chooseSource(R.string.cheque_photo); return@setOnClickListener }
                ChoiceSheet.show(this, getString(R.string.cheque_photo), listOf(
                    ChoiceSheet.photo(getString(R.string.view_photo)),
                    ChoiceSheet.camera(getString(R.string.change_photo)),
                    ChoiceSheet.delete(getString(R.string.remove_photo))
                )) { which ->
                    when (which) {
                        0 -> pendingPhoto?.let { com.innovation313.roshankhata.ui.KeptPhoto.show(this, it, getString(R.string.cheque_photo)) }
                        1 -> { photoForCheque = null; photoFlow.chooseSource(R.string.cheque_photo) }
                        else -> { com.innovation313.roshankhata.data.BillPhoto.delete(pendingPhoto); pendingPhoto = null; btnPhoto.setText(R.string.attach_photo) }
                    }
                }
            }
        }

        // The app's own calculator pad for the amount (21 Oct), shown while
        // the amount has the focus; party, number, bank and note keep the
        // phone's keyboard.
        com.innovation313.roshankhata.ui.CalcPad.bindAll(
            root = view,
            pad = view.findViewById(R.id.chequeKeypad),
            fields = listOf(etAmount),
            results = mapOf(etAmount to view.findViewById<TextView>(R.id.tvChequeAmountResult))
        )

        etParty.setAdapter(
            ArrayAdapter(
                this,
                R.layout.item_dropdown_row,
                parties.map { it.name }
            )
        )

        // arg = the due day * 2 (0: none) + 1 when "received" (M90): the two choices a text box does not hold.
        var dueDate: Long? = existing?.dueDate ?: restore?.let { r -> (r.arg / 2).takeIf { it > 0 } }
        if (existing == null && restore != null) {
            com.innovation313.roshankhata.ui.FormDraft.fill(listOf(etParty, etAmount, etNumber, etBank, etNote), restore.texts)
            if (restore.arg % 2L == 0L) rbIssued.isChecked = true else rbReceived.isChecked = true
            dueDate?.let { btnDate.text = getString(R.string.due_date_set, Format.dateOnly(it)) }
        }

        if (existing != null) {
            // setText(_, false) — filtering the dropdown to the name already
            // in the box would leave the owner one silent tap from an empty
            // list.
            etParty.setText(existing.partyName, false)
            etAmount.setText(Format.plain(existing.amount))
            if (existing.isReceived) rbReceived.isChecked = true else rbIssued.isChecked = true
            etNumber.setText(existing.chequeNumber.orEmpty())
            etBank.setText(existing.bankName.orEmpty())
            etNote.setText(existing.note.orEmpty())
            btnDate.text = getString(R.string.due_date_set, Format.dateOnly(existing.dueDate))
        }

        btnDate.setOnClickListener {
            // Open the picker on the date the cheque already carries, not on
            // today — correcting a date should start from the one being
            // corrected.
            val cal = Calendar.getInstance().apply { dueDate?.let { timeInMillis = it } }
            BrandDatePickerDialog(
                this,
                { _, year, month, day ->
                    val picked = Calendar.getInstance().apply {
                        set(year, month, day, 0, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    dueDate = picked.timeInMillis
                    btnDate.text = getString(
                        R.string.due_date_set,
                        Format.dateOnly(picked.timeInMillis)
                    )
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // The form as it opened — blank and "received" for a new cheque, the cheque's own values for an edit — to
        // tell what the owner changed from what he only looked at.
        val opened = if (existing == null && restore != null) List(5) { "" } else listOf(etParty, etAmount, etNumber, etBank, etNote).map { it.text.toString() }
        val openedReceived = if (existing == null && restore != null) true else rbReceived.isChecked
        if (existing == null) chequeDraft.open(0, { (dueDate ?: 0L) * 2 + (if (rbReceived.isChecked) 1L else 0L) }) { listOf(etParty, etAmount, etNumber, etBank, etNote) }
        val openedDue = if (existing == null && restore != null) null else dueDate

        MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) R.string.add_cheque else R.string.edit_cheque)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setOnDismissListener {
                if (existing == null) {
                    chequeDraft.closed(this)
                    photoButton = null
                    // A closed form's picture goes; one written went with its cheque.
                    if (!written && !isChangingConfigurations) { com.innovation313.roshankhata.data.BillPhoto.delete(pendingPhoto); pendingPhoto = null }
                }
            }
            // Save is wired after show() (5 Nov 2026), as Close the day's: a builder's button closes the dialog
            // whatever its listener decides, so a party, amount or day the form refused threw the rest away.
            .setPositiveButton(R.string.save, null)
            .show().also { form ->
                com.innovation313.roshankhata.ui.CalcPad.dock(form, view, view.findViewById(R.id.chequeKeypad))
                com.innovation313.roshankhata.ui.KeyboardFollow.dialog(form)
                com.innovation313.roshankhata.ui.FormGuard.onSave(form) {
                    val partyName = etParty.text.toString().trim()
                    val party = parties.firstOrNull { it.name.equals(partyName, ignoreCase = true) }

                    if (party == null) {
                        Toast.makeText(this@ChequesActivity, R.string.select_party_first, Toast.LENGTH_LONG).show()
                        etParty.requestFocus()
                        return@onSave false
                    }

                    val amount = Calc.evalAmount(etAmount.text.toString())
                    if (amount == null) {
                        Toast.makeText(this@ChequesActivity, R.string.enter_valid_amount, Toast.LENGTH_SHORT).show()
                        etAmount.requestFocus()
                        return@onSave false
                    }

                    val due = dueDate
                    if (due == null) {
                        Toast.makeText(this@ChequesActivity, R.string.pick_date_first, Toast.LENGTH_LONG).show()
                        return@onSave false
                    }

                    val number = etNumber.text.toString().trim().ifEmpty { null }
                    val bank = etBank.text.toString().trim().ifEmpty { null }
                    val note = etNote.text.toString().trim().ifEmpty { null }
                    // Read here on the main thread, as the form stands at Save, not inside the write.
                    val received = rbReceived.isChecked
                    // A new cheque's picture goes with it; the form lets go of it before the dismiss runs.
                    val photo = if (existing == null) pendingPhoto else null
                    if (existing == null) { written = true; pendingPhoto = null }

                    // AppScope, not lifecycleScope — see AppScope's own comment.
                    // This dialog is already gone by the time the write finishes;
                    // a quick Back press right after Save must not be able to
                    // cancel a cheque that looked saved.
                    AppScope.launch {
                        if (existing == null) {
                            dao.insertCheque(
                                Cheque(
                                    partyId = party.id,
                                    amount = amount,
                                    isReceived = received,
                                    chequeNumber = number,
                                    bankName = bank,
                                    dueDate = due,
                                    note = note,
                                    photoPath = photo
                                )
                            )
                        } else {
                            // copy() off the stored row, so status, settledAt,
                            // ledgerEntryId and createdAt survive untouched. Only
                            // what the dialog shows is what the dialog may change.
                            dao.getCheque(existing.id)?.let { stored ->
                                dao.updateCheque(
                                    stored.copy(
                                        partyId = party.id,
                                        amount = amount,
                                        isReceived = received,
                                        chequeNumber = number,
                                        bankName = bank,
                                        dueDate = due,
                                        note = note
                                    )
                                )
                            }
                        }
                    }
                    true
                }
                // Back, a tap beside it or Cancel threw a typed cheque (or a correction) away in silence (5 Nov 2026,
                // the supplier bill's flaw): any box, the way, the day or a new cheque's waiting picture asks first.
                com.innovation313.roshankhata.ui.FormGuard.guard(
                    activity = this@ChequesActivity,
                    dialog = form,
                    typed = {
                        listOf(etParty, etAmount, etNumber, etBank, etNote).map { it.text.toString() } != opened ||
                            rbReceived.isChecked != openedReceived || dueDate != openedDue ||
                            (existing == null && pendingPhoto != null)
                    },
                    onDiscard = {
                        // A new cheque's picture goes with its form, as the bill form's kept photo does.
                        if (existing == null) {
                            com.innovation313.roshankhata.data.BillPhoto.delete(pendingPhoto)
                            pendingPhoto = null
                        }
                    }
                )
            }
    }

    private fun showChequeActions(cheque: ChequeWithParty) {
        // Its picture first (schema 43, 4 Nov 2026): seen, taken, changed or taken away, whatever the cheque's
        // state — a bounced cheque's picture, with the bank's memo, is the one most worth keeping. A helper's
        // read-only phone sees it and changes nothing.
        val readOnly = com.innovation313.roshankhata.data.ViewerMode.isOn(this)
        val photo = cheque.photoPath?.takeIf { it.isNotBlank() }
        val photoActions = ArrayList<Pair<ChoiceSheet.Option, () -> Unit>>()
        if (photo != null) photoActions += ChoiceSheet.photo(getString(R.string.view_photo)) to {
            com.innovation313.roshankhata.ui.KeptPhoto.show(this, photo, getString(R.string.cheque_photo))
        }
        if (!readOnly) {
            photoActions += ChoiceSheet.camera(getString(if (photo == null) R.string.attach_photo else R.string.change_photo)) to {
                com.innovation313.roshankhata.ui.AccessUi.edit(this, dao) { photoForCheque = cheque.id; photoFlow.chooseSource(R.string.cheque_photo) }
            }
            if (photo != null) photoActions += ChoiceSheet.stop(getString(R.string.remove_photo)) to {
                com.innovation313.roshankhata.ui.AccessUi.edit(this, dao) { removeChequePhoto(cheque.id) }
            }
        }
        val delete = ChoiceSheet.delete(getString(R.string.delete_cheque)) to {
            com.innovation313.roshankhata.ui.AccessUi.edit(this, dao) { lifecycleScope.launch { AppScope.launch { dao.softDeleteCheque(cheque.id) }.join() } }
        }

        if (cheque.status != ChequeStatus.PENDING) {
            // Bounced and cancelled never created a ledger entry — the cheque
            // simply stopped being expected — so putting one back to pending
            // moves no money and undoes a mis-tap cleanly. Cleared is a
            // different animal: it posted an entry, and taking that back is
            // its own job, not a line in this menu.
            val canReopen =
                cheque.status == ChequeStatus.BOUNCED || cheque.status == ChequeStatus.CANCELLED
            val settled = ArrayList<Pair<ChoiceSheet.Option, () -> Unit>>()
            if (canReopen && !readOnly) settled += ChoiceSheet.Option(getString(R.string.cheque_reopen), icon = R.drawable.ic_swap,
                iconTint = R.color.tile_insights_fg, tile = R.color.tile_insights_bg) to { reopen(cheque) }
            settled += photoActions
            if (!readOnly) settled += delete
            if (settled.isEmpty()) return
            ChoiceSheet.show(this, cheque.partyName, settled.map { it.first }) { which -> settled.getOrNull(which)?.second?.invoke() }
            return
        }

        // Edit sits first, and only here. A pending cheque has posted nothing
        // to the ledger, so its details are still only a record of what was
        // written on paper and can be corrected freely. Once cleared, the
        // amount is also an entry in the books, and letting the two drift
        // apart would leave the cheque saying one thing and the khata another.
        val options = ArrayList<Pair<ChoiceSheet.Option, () -> Unit>>()
        if (!readOnly) {
            options += ChoiceSheet.edit(getString(R.string.edit_cheque)) to { com.innovation313.roshankhata.ui.AccessUi.edit(this, dao) { showChequeDialog(cheque) } }
            options += ChoiceSheet.done(getString(R.string.mark_cleared)) to { confirmCleared(cheque) }
            options += ChoiceSheet.Option(getString(R.string.mark_bounced), icon = R.drawable.ic_close,
                iconTint = R.color.bal_owed_to_me, tile = R.color.summary_get_bg) to { confirmBounced(cheque) }
            options += ChoiceSheet.stop(getString(R.string.mark_cancelled)) to { settle(cheque, ChequeStatus.CANCELLED, R.string.cheque_marked_cancelled) }
        }
        options += photoActions
        if (!readOnly) options += delete
        if (options.isEmpty()) return
        ChoiceSheet.show(this, getString(R.string.cheque_actions), options.map { it.first }) { which -> options.getOrNull(which)?.second?.invoke() }
    }

    /**
     * A picture back from the scanner, the camera or the gallery (schema 43): copied into the bills folder, then the
     * new cheque's form's, or the written cheque's it was taken for. [temp] is the camera's own file, deleted once read.
     */
    private fun keepPhoto(uri: android.net.Uri, temp: java.io.File?) {
        val chequeId = photoForCheque
        photoForCheque = null
        lifecycleScope.launch {
            val path = withContext(Dispatchers.IO) { com.innovation313.roshankhata.data.BillPhoto.save(this@ChequesActivity, uri) }
            temp?.delete()
            if (path == null) {
                Toast.makeText(this@ChequesActivity, R.string.bill_photo_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            if (chequeId == null) {
                com.innovation313.roshankhata.data.BillPhoto.delete(pendingPhoto)
                pendingPhoto = path
                photoButton?.setText(R.string.photo_attached)
                return@launch
            }
            val app = applicationContext
            val done = AppScope.async {
                runCatching { com.innovation313.roshankhata.data.BillPhoto.purgingOrphans(app, dao) { dao.setChequePhotoPath(chequeId, path) } }.isSuccess
            }.await()
            if (!done) com.innovation313.roshankhata.data.BillPhoto.delete(path)
            if (!isFinishing && !isDestroyed) Toast.makeText(this@ChequesActivity, if (done) R.string.photo_saved else R.string.photo_save_failed, Toast.LENGTH_SHORT).show()
        }
    }

    /** The cheque's picture taken away; the file goes once no row of any shop on this phone points at it. */
    private fun removeChequePhoto(id: Long) {
        val app = applicationContext
        lifecycleScope.launch {
            val done = AppScope.async {
                runCatching { com.innovation313.roshankhata.data.BillPhoto.purgingOrphans(app, dao) { dao.setChequePhotoPath(id, null) } }.isSuccess
            }.await()
            if (done && !isFinishing && !isDestroyed) Toast.makeText(this@ChequesActivity, R.string.photo_removed, Toast.LENGTH_SHORT).show()
        }
    }

    /** Back to waiting. settledAt clears with the status — a pending cheque was never settled. */
    private fun reopen(cheque: ChequeWithParty) {
        lifecycleScope.launch {
            // Only a bounced or cancelled cheque that never posted money goes back (25 Oct 2026, the audit).
            var done = false
            AppScope.launch { done = dao.settleCheque(cheque.id, null) }.join()
            if (done) Toast.makeText(this@ChequesActivity, R.string.cheque_reopened, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * The only path by which a cheque becomes money. Guarded by a confirmation
     * that says plainly what it will do, because it moves a real balance.
     */
    private fun confirmCleared(cheque: ChequeWithParty) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.mark_cleared)
            .setMessage(getString(R.string.cleared_confirm, cheque.partyName))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.mark_cleared) { _, _ ->
                // Its row stays where it is until the stamp has landed (3 Nov 2026, the owner's mockup).
                adapter.hold()
                postCleared(cheque)
            }
            .show()
    }

    /**
     * Runs on [AppScope] — this is the one cheque action that creates NEW
     * money (a ledger entry), unlike mark-bounced or mark-cancelled below,
     * which only change the cheque's own status and lose nothing if
     * interrupted. A quick Back press right after "Mark cleared" must not be
     * able to cancel the entry partway through. See AppScope's own comment.
     * The toast hops back to the main thread and is shown only if this
     * Activity is still the one on screen.
     */
    private fun postCleared(cheque: ChequeWithParty) {
        AppScope.launch {
            // A cheque received from a customer is money coming IN — it reduces
            // what they owe, so it is an "I Got". A cheque we issued is money
            // going OUT. Getting this backwards would invert the balance.
            val note = buildString {
                append(
                    getString(
                        R.string.cheque_note_prefix,
                        cheque.chequeNumber?.let { "#$it" }.orEmpty()
                    ).trim()
                )
                cheque.bankName?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
            }
            // The entry and the cheque's new status in ONE transaction, and only while it is still
            // pending (21 Oct 2026): two quick taps, or a tap after the computer's copy cleared it,
            // posted the money twice. Null: nothing was posted.
            var posted: Long? = null
            try {
                posted = dao.clearCheque(cheque.id, note)
            } finally {
                // Written: the CLEARED stamp lands on its row, then the list moves on. Not written (or a
                // failure, which AppScope's handler still reports): the list moves on at once.
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) {
                    if (!isFinishing && !isDestroyed) {
                        if (posted == null) adapter.releaseNow()
                        else {
                            adapter.stamp(cheque.id, ChequeStatus.CLEARED)
                            Toast.makeText(
                                this@ChequesActivity,
                                R.string.cheque_cleared_posted,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }

    /**
     * A bounced cheque posts nothing. The debt was never paid — it simply
     * stayed owed, and the ledger already says so.
     */
    private fun confirmBounced(cheque: ChequeWithParty) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.mark_bounced)
            .setMessage(R.string.bounced_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.mark_bounced) { _, _ ->
                settle(cheque, ChequeStatus.BOUNCED, R.string.cheque_marked_bounced)
            }
            .show()
    }

    private fun settle(cheque: ChequeWithParty, status: Int, messageRes: Int) {
        // Its row stays where it is until the stamp has landed (3 Nov 2026, the owner's mockup).
        adapter.hold()
        lifecycleScope.launch {
            // Only while it is still pending (25 Oct 2026, the audit): a cheque the computer cleared
            // meanwhile is never marked bounced here, so its payment cannot be posted twice.
            var done = false
            try {
                AppScope.launch { done = dao.settleCheque(cheque.id, status) }.join()
            } finally {
                if (!isDestroyed) {
                    if (done) {
                        adapter.stamp(cheque.id, status)
                        Toast.makeText(this@ChequesActivity, messageRes, Toast.LENGTH_SHORT).show()
                    } else adapter.releaseNow()
                }
            }
        }
    }

    companion object {
        private const val STATE_PHOTO_CAMERA = "photo_camera_path"
        private const val STATE_PHOTO_PENDING = "photo_pending"
        private const val STATE_PHOTO_CHEQUE = "photo_cheque"
    }
}
