package com.innovation313.roshankhata

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
import com.innovation313.roshankhata.data.BusinessProfile
import com.innovation313.roshankhata.data.DateWords
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.Payroll
import com.innovation313.roshankhata.data.Staff
import com.innovation313.roshankhata.data.StaffAttendance
import com.innovation313.roshankhata.data.StaffPayment
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.ScreenInsets
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_staff)
        ScreenInsets.on(this)
        findViewById<View>(R.id.btnPrevMonth).setOnClickListener { shiftMonth(-1) }
        findViewById<View>(R.id.btnNextMonth).setOnClickListener { shiftMonth(1) }
        findViewById<View>(R.id.btnAddStaff).setOnClickListener { editStaff(null) }
        load()
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

    private fun money(v: Double) = Format.ltr(Format.money(v))

    private fun render(staff: List<Staff>, marks: List<StaffAttendance>, pays: List<StaffPayment>, from: Long, to: Long, days: Int) {
        findViewById<View>(R.id.tvStaffEmpty).visibility = if (staff.isEmpty()) View.VISIBLE else View.GONE
        val list = findViewById<LinearLayout>(R.id.staffList)
        list.removeAllViews()
        for (person in staff) {
            val mine = marks.filter { it.staffId == person.id }
            val m = Payroll.month(person.monthlySalary, days, mine.map { it.status }, pays.filter { it.staffId == person.id })
            val card = layoutInflater.inflate(R.layout.item_staff, list, false)
            card.findViewById<TextView>(R.id.tvStaffName).text = person.name
            card.findViewById<TextView>(R.id.tvStaffSalary).text = getString(R.string.staff_salary_line, money(person.monthlySalary))
            card.findViewById<TextView>(R.id.tvStaffCounts).text =
                getString(R.string.staff_counts, m.absent.toString(), m.half.toString(), m.leave.toString())
            card.findViewById<TextView>(R.id.tvStaffMoney).text =
                getString(R.string.staff_money, money(m.earned), money(m.advances), money(m.paid))
            val due = card.findViewById<TextView>(R.id.tvStaffDue)
            when {
                m.due > 0.004 -> { due.text = getString(R.string.staff_due, money(m.due)); due.setTextColor(ContextCompat.getColor(this, R.color.bal_owed_to_me)) }
                m.due < -0.004 -> { due.text = getString(R.string.staff_overpaid, money(-m.due)); due.setTextColor(ContextCompat.getColor(this, R.color.bal_i_owe)) }
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
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.staff_day_for, person.name + " \u00b7 " + DateWords.format("d MMM", d)))
            .setSingleChoiceItems(labels, current) { dialog, which ->
                dialog.dismiss()
                lifecycleScope.launch {
                    if (which == 0) dao.clearAttendance(person.id, d)
                    else dao.markAttendance(StaffAttendance(staffId = person.id, day = d, status = which + 1))
                }
            }
            .setNeutralButton(R.string.staff_change_date) { _, _ ->
                val c = Calendar.getInstance().apply { timeInMillis = d }
                DatePickerDialog(this, { _, y, mo, dd ->
                    val picked = Calendar.getInstance().apply { set(y, mo, dd, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                    markDay(person, marks, from, to, picked)
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).apply {
                    datePicker.minDate = from
                    datePicker.maxDate = to - 1
                }.show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun field(hint: Int, type: Int, value: String? = null) = EditText(this).apply {
        setHint(hint); inputType = type; value?.let { setText(it) }
    }

    private fun box(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val pad = (20 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad / 2, pad, 0)
        views.forEach { addView(it) }
    }

    /** An advance or a salary payment, handed over now. */
    private fun handOver(person: Staff, kind: Int) {
        val amount = field(R.string.staff_amount, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val note = field(R.string.staff_note, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        MaterialAlertDialogBuilder(this)
            .setTitle(person.name + " \u00b7 " + getString(if (kind == Payroll.ADVANCE) R.string.staff_advance else R.string.staff_pay))
            .setView(box(amount, note))
            .setPositiveButton(R.string.save) { _, _ ->
                val v = amount.text.toString().replace(",", "").toDoubleOrNull()
                if (v == null || v <= 0) {
                    Toast.makeText(this, R.string.staff_invalid, Toast.LENGTH_LONG).show(); return@setPositiveButton
                }
                lifecycleScope.launch {
                    dao.insertStaffPayment(StaffPayment(staffId = person.id, amount = v, kind = kind,
                        note = note.text.toString().trim().ifEmpty { null }))
                    com.innovation313.roshankhata.ui.Motion.savedTick(this@StaffActivity)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun editStaff(person: Staff?) {
        val name = field(R.string.staff_name, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS, person?.name)
        val phone = field(R.string.staff_phone, InputType.TYPE_CLASS_PHONE, person?.phone)
        val salary = field(R.string.staff_salary, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
            person?.monthlySalary?.let { Format.plain(it) })
        MaterialAlertDialogBuilder(this)
            .setTitle(if (person == null) R.string.staff_add else R.string.staff_edit)
            .setView(box(name, phone, salary))
            .setPositiveButton(R.string.save) { _, _ ->
                val n = name.text.toString().trim()
                val s = salary.text.toString().replace(",", "").toDoubleOrNull()
                if (n.isEmpty() || s == null || s <= 0) {
                    Toast.makeText(this, R.string.staff_invalid, Toast.LENGTH_LONG).show(); return@setPositiveButton
                }
                val p = phone.text.toString().trim().ifEmpty { null }
                lifecycleScope.launch {
                    if (person == null) dao.insertStaff(Staff(name = n, phone = p, monthlySalary = s))
                    else dao.updateStaff(person.copy(name = n, phone = p, monthlySalary = s))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun manage(person: Staff) {
        MaterialAlertDialogBuilder(this)
            .setTitle(person.name)
            .setItems(arrayOf(getString(R.string.staff_edit), getString(R.string.staff_remove))) { _, which ->
                if (which == 0) editStaff(person)
                else MaterialAlertDialogBuilder(this)
                    .setMessage(getString(R.string.staff_remove_confirm, person.name))
                    .setPositiveButton(R.string.staff_remove) { _, _ ->
                        lifecycleScope.launch { dao.updateStaff(person.copy(isActive = false)) }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
            .show()
    }

    /** The month's slip as text, to WhatsApp or anywhere: every figure on its own line. */
    private fun sendSlip(person: Staff, m: Payroll.Month, from: Long) {
        val due = when {
            m.due > 0.004 -> getString(R.string.staff_due, money(m.due))
            m.due < -0.004 -> getString(R.string.staff_overpaid, money(-m.due))
            else -> getString(R.string.staff_settled)
        }
        val shop = BusinessProfile.businessName(this)?.trim().orEmpty()
        val text = listOf(
            shop,
            getString(R.string.staff_slip_title, person.name, DateWords.format("MMMM yyyy", from)),
            getString(R.string.staff_salary_line, money(person.monthlySalary)),
            getString(R.string.staff_counts, m.absent.toString(), m.half.toString(), m.leave.toString()),
            getString(R.string.staff_money, money(m.earned), money(m.advances), money(m.paid)),
            due
        ).filter { it.isNotBlank() }.joinToString("\n")
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
        }, getString(R.string.staff_slip)))
    }
}
