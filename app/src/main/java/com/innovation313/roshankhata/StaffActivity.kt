package com.innovation313.roshankhata

import com.innovation313.roshankhata.data.get

import com.innovation313.roshankhata.ui.BrandDatePickerDialog
import com.innovation313.roshankhata.ui.ChoiceSheet
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.data.AppScope
import com.innovation313.roshankhata.data.BusinessProfile
import com.innovation313.roshankhata.data.DateWords
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.Payroll
import com.innovation313.roshankhata.data.Staff
import com.innovation313.roshankhata.data.StaffAttendance
import com.innovation313.roshankhata.data.StaffPayment
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.ScreenInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Staff & salary (v29): a month at a time. Each person's card shows the days
 * marked absent, half or on leave, what they earned, the advances they took,
 * the salary already paid, and what is still to pay. Owner's phone only
 * (ViewerMode.OWNER_ONLY): a helper may be on this list himself.
 */
class StaffActivity : BaseActivity() {

    private val dao by lazy { KhataDatabase.get(this).khataDao() }
    private var monthAt = System.currentTimeMillis()
    private var job: Job? = null

    /** The staff forms while open (M90, 8 Nov 2026): a payment (which 1, arg = person*8 + kind) or a person (2, arg = id, 0 new). The PIN is never kept. */
    private val staffDraft = com.innovation313.roshankhata.ui.FormDraft("staff_form")
    private var staffReopen: com.innovation313.roshankhata.ui.FormDraft.Saved? = null
    private var lastStaff: List<Staff> = emptyList()

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        staffDraft.save(outState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        staffReopen = staffDraft.saved(savedInstanceState)
        setContentView(R.layout.activity_staff)
        // How this works, the first time it opens on this phone (1 Nov 2026).
        com.innovation313.roshankhata.ui.FeatureIntro.showOnce(this, com.innovation313.roshankhata.ui.FeatureIntro.Topic.STAFF)
        ScreenInsets.on(this)
        findViewById<View>(R.id.btnPrevMonth).setOnClickListener { shiftMonth(-1) }
        findViewById<View>(R.id.btnNextMonth).setOnClickListener { shiftMonth(1) }
        findViewById<View>(R.id.btnAddStaff).setOnClickListener { editStaff(null) }
        findViewById<View>(R.id.btnAudit).setOnClickListener { startActivity(android.content.Intent(this, AuditActivity::class.java)) }
        findViewById<View>(R.id.btnOwnerPin).setOnClickListener { com.innovation313.roshankhata.ui.AccessUi.ownerPin(this) { refreshAccessButtons() } }
        findViewById<View>(R.id.btnCounterMode).setOnClickListener {
            com.innovation313.roshankhata.ui.AccessUi.counterMode(this, !com.innovation313.roshankhata.data.Access.counterMode) { refreshAccessButtons() }
        }
        refreshAccessButtons()
        load()
    }

    /** The owner's two buttons say what is set: the PIN (set or change) and counter mode (on or off). */
    private fun refreshAccessButtons() {
        lifecycleScope.launch {
            val has = com.innovation313.roshankhata.data.Access.ownerPin(dao) != null
            findViewById<TextView>(R.id.tvOwnerPinTitle).setText(if (has) R.string.access_owner_pin_change else R.string.access_owner_pin_set)
            findViewById<TextView>(R.id.tvCounterModeTitle).setText(
                if (com.innovation313.roshankhata.data.Access.counterMode) R.string.access_counter_on_label else R.string.access_counter_off_label
            )
        }
    }

    private fun shiftMonth(by: Int) {
        monthAt = Calendar.getInstance().apply { timeInMillis = monthAt; set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, by) }.timeInMillis
        load()
    }

    private fun load() {
        val (from, to, days) = Payroll.monthRange(monthAt)
        findViewById<TextView>(R.id.tvMonth).text = DateWords.format("MMMM yyyy", from)
        job?.cancel()
        job = lifecycleScope.launch {
            combine(dao.observeStaff(), dao.observeAttendanceBetween(from, to), dao.observeStaffPaymentsBetween(from, to)) { s, a, p ->
                Triple(s, a, p)
            }.collect { (staff, marks, pays) -> render(staff, marks, pays, from, to, days) }
        }
    }

    private fun render(staff: List<Staff>, marks: List<StaffAttendance>, pays: List<StaffPayment>, from: Long, to: Long, days: Int) {
        findViewById<View>(R.id.tvStaffEmpty).visibility = if (staff.isEmpty()) View.VISIBLE else View.GONE
        lastStaff = staff
        staffReopen?.let { r ->
            staffReopen = null
            if (!isFinishing && !isDestroyed) {
                if (r.which == 1) staff.firstOrNull { it.id == r.arg / 8 }?.let { handOver(it, (r.arg % 8).toInt(), r) }
                else if (r.arg == 0L) editStaff(null, r)
                else staff.firstOrNull { it.id == r.arg }?.let { editStaff(it, r) }
            }
        }
        val list = findViewById<LinearLayout>(R.id.staffList)
        list.removeAllViews()
        for (person in staff) {
            val mine = marks.filter { it.staffId == person.id }
            val m = Payroll.month(person.monthlySalary, days, mine.map { it.status }, pays.filter { it.staffId == person.id })
            val card = layoutInflater.inflate(R.layout.item_staff, list, false)
            card.findViewById<TextView>(R.id.tvStaffName).text = person.name
            card.findViewById<TextView>(R.id.tvStaffSalary).text = getString(R.string.staff_salary_line, Format.moneyLtr(person.monthlySalary))
            card.findViewById<TextView>(R.id.tvStaffCounts).text =
                getString(R.string.staff_counts, m.absent.toString(), m.half.toString(), m.leave.toString())
            card.findViewById<TextView>(R.id.tvStaffMoney).text =
                getString(R.string.staff_money, Format.moneyLtr(m.earned), Format.moneyLtr(m.advances), Format.moneyLtr(m.paid))
            val due = card.findViewById<TextView>(R.id.tvStaffDue)
            when {
                m.due > 0.004 -> { due.text = getString(R.string.staff_due, Format.moneyLtr(m.due)); due.setTextColor(ContextCompat.getColor(this, R.color.bal_owed_to_me)) }
                m.due < -0.004 -> { due.text = getString(R.string.staff_overpaid, Format.moneyLtr(-m.due)); due.setTextColor(ContextCompat.getColor(this, R.color.bal_i_owe)) }
                else -> { due.text = getString(R.string.staff_settled); due.setTextColor(ContextCompat.getColor(this, R.color.ink)) }
            }
            card.findViewById<View>(R.id.btnStaffAttendance).setOnClickListener { markDay(person, mine, from, to) }
            card.findViewById<View>(R.id.btnStaffAdvance).setOnClickListener { handOver(person, Payroll.ADVANCE) }
            card.findViewById<View>(R.id.btnStaffPay).setOnClickListener { handOver(person, Payroll.SALARY) }
            card.findViewById<View>(R.id.btnStaffSlip).setOnClickListener { sendSlip(person, m, from) }
            card.setOnLongClickListener { manage(person); true }
            list.addView(card)
        }
    }

    /** Pick the day (today, or the 1st when looking at another month), then its mark. */
    private fun markDay(person: Staff, marks: List<StaffAttendance>, from: Long, to: Long, day: Long? = null) {
        val today = Payroll.dayOf(System.currentTimeMillis())
        val d = day ?: if (today in from until to) today else from
        val labels = arrayOf(getString(R.string.staff_present), getString(R.string.staff_absent),
            getString(R.string.staff_half), getString(R.string.staff_leave))
        val current = (marks.firstOrNull { it.day == d }?.status ?: Payroll.PRESENT) - 1
        // Present, absent, half day, leave - each with its own tile (10 Oct);
        // "Change date" stays, as the sheet's second button.
        val choices = listOf(
            ChoiceSheet.Option(labels[0], icon = R.drawable.ic_check_circle,
                iconTint = R.color.bal_i_owe, tile = R.color.summary_give_bg),
            ChoiceSheet.Option(labels[1], icon = R.drawable.ic_close,
                iconTint = R.color.bal_owed_to_me, tile = R.color.summary_get_bg),
            ChoiceSheet.Option(labels[2], glyph = "\u00bd", glyphSp = 18f,
                iconTint = R.color.backup_ink, tile = R.color.tile_bills_bg),
            ChoiceSheet.Option(labels[3], icon = R.drawable.ic_calendar,
                iconTint = R.color.tile_insights_fg, tile = R.color.tile_insights_bg)
        )
        ChoiceSheet.show(
            this, getString(R.string.staff_day_for, person.name + " \u00b7 " + DateWords.format("d MMM", d)),
            choices, current,
            secondary = ChoiceSheet.Action(getString(R.string.staff_change_date)) {
                val c = Calendar.getInstance().apply { timeInMillis = d }
                BrandDatePickerDialog(this, { _, y, mo, dd ->
                    val picked = Calendar.getInstance().apply { set(y, mo, dd, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                    markDay(person, marks, from, to, picked)
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).apply {
                    datePicker.minDate = from
                    datePicker.maxDate = to - 1
                }.show()
            }
        ) { which ->
            lifecycleScope.launch {
                if (which == 0) dao.clearAttendance(person.id, d)
                else dao.markAttendance(StaffAttendance(staffId = person.id, day = d, status = which + 1))
            }
        }
    }

    private fun field(hint: Int, type: Int, value: String? = null) = com.innovation313.roshankhata.ui.FormBox.style(EditText(this)).apply {
        setHint(hint); inputType = type; value?.let { setText(it) }
    }

    /** The boxes in one column, the dialog's margins and room between them (FormBox, 30 Oct 2026). */
    private fun box(vararg views: View) = com.innovation313.roshankhata.ui.FormBox.column(this, *views)

    /**
     * A money box in a dialog built here gets the app's calculator pad
     * (21 Oct), as the add-entry form has: its running total and the pad go
     * in right under it, and the whole body scrolls — with the pad it is
     * taller than a short phone's dialog. [padShown]: whether the pad starts
     * out showing (the money box is the first box, so it has the focus).
     */
    private fun withPad(body: LinearLayout, money: EditText, padShown: Boolean): View {
        com.innovation313.roshankhata.ui.CalcPad.moneyBox(money)
        val at = body.indexOfChild(money)
        val result = com.innovation313.roshankhata.ui.CalcPad.resultLine(this)
        val pad = com.innovation313.roshankhata.ui.CalcPad.inflatePad(body)
        pad.visibility = if (padShown) View.VISIBLE else View.GONE
        body.addView(result, at + 1)
        body.addView(pad, at + 2)
        val scroll = android.widget.ScrollView(this)
        scroll.addView(body)
        // Kept on the scroll so the dialog can dock it above its buttons once shown (CalcPad.dock).
        scroll.setTag(R.id.calc_pad_view, pad)
        com.innovation313.roshankhata.ui.CalcPad.bindAll(
            root = scroll, pad = pad, fields = listOf(money), results = mapOf(money to result)
        )
        return scroll
    }

    /** An advance or a salary payment, handed over now. */
    private fun handOver(person: Staff, kind: Int, restore: com.innovation313.roshankhata.ui.FormDraft.Saved? = null) {
        val amount = field(R.string.staff_amount, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val note = field(R.string.staff_note, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        val form = withPad(box(amount, note), amount, padShown = true)
        com.innovation313.roshankhata.ui.FormDraft.fill(listOf(amount, note), restore?.texts)
        staffDraft.open(1, { person.id * 8 + kind }) { listOf(amount, note) }
        MaterialAlertDialogBuilder(this)
            .setTitle(person.name + " \u00b7 " + getString(if (kind == Payroll.ADVANCE) R.string.staff_advance else R.string.staff_pay))
            .setView(form)
            // Save is wired after show() (5 Nov 2026), as Close the day's: a builder's button closes the dialog
            // whatever its listener decides, so an amount the form refused threw the note away with it.
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .show().also { dialog ->
                com.innovation313.roshankhata.ui.CalcPad.dock(dialog, form, form.getTag(R.id.calc_pad_view) as View)
                com.innovation313.roshankhata.ui.KeyboardFollow.dialog(dialog)
                com.innovation313.roshankhata.ui.FormGuard.onSave(dialog) {
                    val v = com.innovation313.roshankhata.ui.Calc.evalAmount(amount.text.toString())
                    if (v == null || v <= 0) {
                        Toast.makeText(this@StaffActivity, R.string.staff_invalid, Toast.LENGTH_LONG).show()
                        amount.requestFocus()
                        return@onSave false
                    }
                    val payment = StaffPayment(staffId = person.id, amount = v, kind = kind,
                        note = note.text.toString().trim().ifEmpty { null })
                    // AppScope, not the screen's own scope (5 Nov 2026), as every other money write: Back right after
                    // Save closes the screen and cancelled a write still on its way. A failed write is said by AppScope's
                    // handler ("not saved"); the card is shown only while there is still a screen to show it on.
                    AppScope.launch {
                        dao.insertStaffPayment(payment)
                        withContext(Dispatchers.Main) {
                            if (!isFinishing && !isDestroyed) {
                                com.innovation313.roshankhata.ui.SavedCard.show(this@StaffActivity, getString(R.string.saved))
                            }
                        }
                    }
                    true
                }
                // Back, a tap beside it or Cancel threw a typed payment away in silence (5 Nov 2026, the supplier
                // bill's flaw). The form opens blank: an amount or a note asks first.
                dialog.setOnDismissListener { staffDraft.closed(this@StaffActivity) }
                com.innovation313.roshankhata.ui.FormGuard.guard(
                    activity = this@StaffActivity,
                    dialog = dialog,
                    typed = { amount.text.isNotBlank() || note.text.isNotBlank() }
                )
            }
    }

    private fun editStaff(person: Staff?, restore: com.innovation313.roshankhata.ui.FormDraft.Saved? = null) {
        val name = field(R.string.staff_name, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS, person?.name)
        val phone = field(R.string.staff_phone, InputType.TYPE_CLASS_PHONE, person?.phone)
        val salary = field(R.string.staff_salary, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
            person?.monthlySalary?.let { Format.plain(it) })
        // Counter login (POS Phase 5): a role and a PIN, set by the owner only.
        val mayEditLogin = com.innovation313.roshankhata.data.Access.can(com.innovation313.roshankhata.data.Access.Permission.STAFF)
        val roles = listOf(null, com.innovation313.roshankhata.data.Role.MANAGER, com.innovation313.roshankhata.data.Role.CASHIER)
        val roleLabels = listOf(getString(R.string.staff_role_none), getString(R.string.staff_role_manager), getString(R.string.staff_role_cashier))
        val roleBox = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@StaffActivity, android.R.layout.simple_spinner_dropdown_item, roleLabels)
            setSelection(roles.indexOf(person?.role).coerceAtLeast(0))
        }
        val roleLabel = android.widget.TextView(this).apply { setText(R.string.staff_role); textSize = 12f }
        val pin = field(R.string.staff_pin, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        val pinNote = android.widget.TextView(this).apply { setText(R.string.staff_pin_keep); textSize = 12f; visibility = if (person?.pinHash != null) View.VISIBLE else View.GONE }
        val views = if (mayEditLogin) arrayOf<View>(name, phone, salary, roleLabel, roleBox, pin, pinNote) else arrayOf<View>(name, phone, salary)
        val form = withPad(box(*views), salary, padShown = false)
        // The form as it opened (blank, or the person as saved; the PIN box is always blank), to tell what the
        // owner changed from what he only looked at.
        val opened = listOf(name, phone, salary).map { it.text.toString() }
        val openedRole = roleBox.selectedItemPosition
        com.innovation313.roshankhata.ui.FormDraft.fill(listOf(name, phone, salary), restore?.texts)
        staffDraft.open(2, { person?.id ?: 0L }) { listOf(name, phone, salary) }
        MaterialAlertDialogBuilder(this)
            .setTitle(if (person == null) R.string.staff_add else R.string.staff_edit)
            .setView(form)
            // Save is wired after show() (5 Nov 2026), as Close the day's: a builder's button closes the dialog
            // whatever its listener decides, so a refused name, salary or PIN — or a name already taken, or the
            // owner PIN question cancelled — threw the typed person away.
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .show().also { dialog ->
                com.innovation313.roshankhata.ui.CalcPad.dock(dialog, form, form.getTag(R.id.calc_pad_view) as View)
                com.innovation313.roshankhata.ui.KeyboardFollow.dialog(dialog)
                // Set while a save is on its way (the name is looked up, then written), and kept once it is written:
                // a second tap, or a second answer from the owner PIN question, saves nothing twice. A flag, not a
                // disabled button (lesson 10). Cleared when the name is refused or the write failed, for another try.
                var saving = false
                dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)?.setOnClickListener {
                    if (saving) return@setOnClickListener
                    val n = name.text.toString().trim()
                    val s = com.innovation313.roshankhata.ui.Calc.evalAmount(salary.text.toString())
                    if (n.isEmpty() || s == null || s <= 0) {
                        Toast.makeText(this@StaffActivity, R.string.staff_invalid, Toast.LENGTH_LONG).show()
                        (if (n.isEmpty()) name else salary).requestFocus()
                        return@setOnClickListener
                    }
                    val p = phone.text.toString().trim().ifEmpty { null }
                    val role = if (mayEditLogin) roles[roleBox.selectedItemPosition] else person?.role
                    val typedPin = pin.text.toString()
                    val needsPin = mayEditLogin && role != null && (person?.pinHash == null || typedPin.isNotEmpty())
                    if (needsPin && !com.innovation313.roshankhata.data.PinHash.valid(typedPin)) {
                        Toast.makeText(this@StaffActivity, R.string.staff_pin_invalid, Toast.LENGTH_LONG).show()
                        pin.requestFocus()
                        return@setOnClickListener
                    }
                    val login: (Staff) -> Staff = { st ->
                        if (!mayEditLogin) st
                        else if (role == null) st.copy(role = null, pinHash = null, pinSalt = null)
                        else if (typedPin.isNotEmpty()) { val salt = com.innovation313.roshankhata.data.PinHash.salt(); st.copy(role = role, pinSalt = salt, pinHash = com.innovation313.roshankhata.data.PinHash.hash(typedPin, salt)) }
                        else st.copy(role = role)
                    }
                    val save: () -> Unit = save@{
                        if (saving) return@save
                        saving = true
                        lifecycleScope.launch {
                            // The look-up and the write run on AppScope (5 Nov 2026): a failure is said as every other
                            // AppScope write's is ("not saved", AppFailures) — on the screen's own scope it crashed the
                            // app — and a turned screen cannot cancel them half way. This screen only waits for them.
                            var taken = false
                            val write = AppScope.launch {
                                // Two staff of one name would share a shift and a drawer (23 Oct 2026, the review).
                                taken = dao.staffNameTaken(n, person?.id ?: 0L)
                                if (!taken) {
                                    if (person == null) dao.insertStaff(login(Staff(name = n, phone = p, monthlySalary = s)))
                                    else dao.updateStaff(login(person.copy(name = n, phone = p, monthlySalary = s)))
                                }
                            }
                            write.join()
                            if (write.isCancelled) {
                                // Not written, and already said: the form stays as it was, for another try.
                                saving = false
                            } else if (taken) {
                                Toast.makeText(this@StaffActivity, R.string.staff_name_taken, Toast.LENGTH_LONG).show()
                                name.requestFocus()
                                saving = false
                            } else {
                                // Written: the form closes, and Save stays spent.
                                dialog.dismiss()
                            }
                        }
                    }
                    // A staff login without an owner PIN would let anyone sign in as the owner: set it first.
                    if (mayEditLogin && role != null) com.innovation313.roshankhata.ui.AccessUi.requireOwnerPin(this@StaffActivity) { save() } else save()
                }
                // Back, a tap beside it or Cancel threw a typed person (or a correction) away in silence (5 Nov 2026,
                // the supplier bill's flaw): a changed name, phone, salary or role, or a PIN typed, asks first.
                dialog.setOnDismissListener { staffDraft.closed(this@StaffActivity) }
                com.innovation313.roshankhata.ui.FormGuard.guard(
                    activity = this@StaffActivity,
                    dialog = dialog,
                    typed = {
                        listOf(name, phone, salary).map { it.text.toString() } != opened ||
                            roleBox.selectedItemPosition != openedRole || pin.text.isNotEmpty()
                    }
                )
            }
    }

    private fun manage(person: Staff) {
        ChoiceSheet.show(
            this, person.name,
            listOf(ChoiceSheet.edit(getString(R.string.staff_edit)), ChoiceSheet.delete(getString(R.string.staff_remove)))
        ) { which ->
            if (which == 0) editStaff(person)
            else MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.staff_remove_confirm, person.name))
                .setPositiveButton(R.string.staff_remove) { _, _ ->
                    lifecycleScope.launch { dao.updateStaff(person.copy(isActive = false)) }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    /** The month's slip as text, to WhatsApp or anywhere: every figure on its own line. */
    private fun sendSlip(person: Staff, m: Payroll.Month, from: Long) {
        val due = when {
            m.due > 0.004 -> getString(R.string.staff_due, Format.moneyLtr(m.due))
            m.due < -0.004 -> getString(R.string.staff_overpaid, Format.moneyLtr(-m.due))
            else -> getString(R.string.staff_settled)
        }
        val shop = BusinessProfile.businessName(this)?.trim().orEmpty()
        val text = listOf(
            shop,
            getString(R.string.staff_slip_title, person.name, DateWords.format("MMMM yyyy", from)),
            getString(R.string.staff_salary_line, Format.moneyLtr(person.monthlySalary)),
            getString(R.string.staff_counts, m.absent.toString(), m.half.toString(), m.leave.toString()),
            getString(R.string.staff_money, Format.moneyLtr(m.earned), Format.moneyLtr(m.advances), Format.moneyLtr(m.paid)),
            due
        ).filter { it.isNotBlank() }.joinToString("\n")
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
        }, getString(R.string.staff_slip)))
    }
}
