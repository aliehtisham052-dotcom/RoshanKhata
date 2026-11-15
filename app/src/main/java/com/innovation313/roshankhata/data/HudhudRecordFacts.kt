package com.innovation313.roshankhata.data

import android.content.Context
import com.innovation313.roshankhata.AuditActivity
import com.innovation313.roshankhata.ChequesActivity
import com.innovation313.roshankhata.PlansActivity
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.StaffActivity
import com.innovation313.roshankhata.data.HudhudBook.Answer
import com.innovation313.roshankhata.data.HudhudBook.Mood
import com.innovation313.roshankhata.data.HudhudBook.Row
import com.innovation313.roshankhata.screens.AuditNames
import com.innovation313.roshankhata.screens.auditActionName
import com.innovation313.roshankhata.ui.Format
import kotlinx.coroutines.flow.first
import kotlin.math.abs

/**
 * The records the book keeps on pages of their own (15 Nov 2026, the catalog's group "staff, plans, cheques, audit"): a staff
 * member's pay, what was paid them, who was away, who they are; a customer's payment plan with its instalments, the plans that
 * were closed; a customer's cheques with the day each was written and settled; the audit log's rows with what they touched.
 *
 * Every figure is read by the rule of the screen the card opens: pay by [Payroll] (Staff's month), attendance as Staff marks it
 * (a day with no row is a day present), a plan's progress by the Plans screen's own read, a cheque by the Cheques list.
 * Staff is the owner's screen (STAFF): a manager is told so too, as [HudhudFacts.salaries] does. Amounts on plans and cheques
 * stay with REPORTS like every figure Hudhud adds up. Nothing is written, and no PIN hash or salt is ever read: the staff rows
 * are read only for what a person's page shows.
 */
internal object HudhudRecordFacts {

    private fun reports() = HudhudGate.reports()

    private fun staff() = HudhudGate.staff()

    suspend fun answer(c: Context, dao: KhataDao, ask: Hudhud.Ask.Record, parties: List<PartyWithBalance>): Answer = when (ask.facet) {
        Hudhud.RecordFacet.STAFF_PAY -> staffPay(c, dao, ask.ids, ask.window)
        Hudhud.RecordFacet.STAFF_PAID -> staffPaid(c, dao, ask.ids, ask.window)
        Hudhud.RecordFacet.ATTENDANCE -> attendance(c, dao, ask.ids, ask.window)
        Hudhud.RecordFacet.STAFF_PERSON -> staffPerson(c, dao, ask.ids)
        Hudhud.RecordFacet.PLAN -> plan(c, dao, ask.ids, parties)
        Hudhud.RecordFacet.PLAN_CLOSED -> plansClosed(c, dao, ask.ids, ask.window)
        Hudhud.RecordFacet.CHEQUE -> cheques(c, dao, ask.ids, parties)
    }

    // ------------------------------------------------------------------------------------------------ the staff

    /** The month the question is about: this month, or last month when it was said (Staff's own month, [Payroll.monthRange]). */
    private fun monthOf(window: VoiceQuestion.Window?): Triple<Long, Long, Int> {
        val now = HudhudFacts.now()
        val this_ = Payroll.monthRange(now)
        return if (window == VoiceQuestion.Window.LAST_MONTH) Payroll.monthRange(this_.first - 1) else this_
    }

    /**
     * What a named staff member earns and is still owed this month: the monthly salary, what was earned after the days away,
     * the advances and the salary paid, and what is still to pay — Staff's own month rule ([Payroll.month]).
     */
    suspend fun staffPay(c: Context, dao: KhataDao, ids: List<Long>, window: VoiceQuestion.Window?): Answer {
        val label = c.getString(R.string.hudhud_t_salary)
        if (!staff()) return HudhudFacts.ownerExact(c, label)
        val (from, to, days) = monthOf(window)
        val people = dao.observeStaff().first().filter { it.id in ids }
        if (people.isEmpty()) return Answer(Mood.CALM, label = label, lines = listOf(c.getString(R.string.staff_none)), open = StaffActivity::class.java)
        val marks = dao.observeAttendanceBetween(from, to).first()
        val pays = dao.observeStaffPaymentsBetween(from, to).first()
        val months = people.map { p ->
            p to Payroll.month(p.monthlySalary, days, marks.filter { it.staffId == p.id }.map { it.status }, pays.filter { it.staffId == p.id })
        }
        val title = HudhudFacts.heading(c, R.string.hudhud_t_salary, window ?: VoiceQuestion.Window.THIS_MONTH)
        if (months.size > 1) {
            return Answer(Mood.CALM, label = title,
                rows = months.map { (p, m) -> Row(0, p.name, HudhudBook.money(m.due), c.getString(R.string.staff_salary_line, HudhudBook.money(p.monthlySalary)), open = StaffActivity::class.java) },
                open = StaffActivity::class.java, money = true)
        }
        val (p, m) = months.first()
        val due = m.due
        val state = when {
            due > 0.004 -> c.getString(R.string.staff_due, HudhudBook.money(due))
            due < -0.004 -> c.getString(R.string.staff_overpaid, HudhudBook.money(-due))
            else -> c.getString(R.string.staff_settled)
        }
        return Answer(if (due > 0.004) Mood.CARE else Mood.GOOD, label = p.name + " · " + title, figure = HudhudBook.money(abs(due)),
            lines = listOfNotNull(
                c.getString(R.string.staff_salary_line, HudhudBook.money(p.monthlySalary)),
                c.getString(R.string.staff_money, HudhudBook.money(m.earned), HudhudBook.money(m.advances), HudhudBook.money(m.paid)),
                if (m.absent + m.half + m.leave > 0) c.getString(R.string.staff_counts, m.absent.toString(), m.half.toString(), m.leave.toString()) else null,
                state
            ), open = StaffActivity::class.java, money = true)
    }

    /**
     * What was paid to the staff over a span (this month when none was said): advances and salary, who got what and when, with
     * the note written beside it — the Staff screen's payments, newest first.
     */
    suspend fun staffPaid(c: Context, dao: KhataDao, ids: List<Long>, window: VoiceQuestion.Window?): Answer {
        val title = HudhudFacts.heading(c, R.string.hudhud_t_staff_paid, window ?: VoiceQuestion.Window.THIS_MONTH)
        if (!staff()) return HudhudFacts.ownerExact(c, title)
        val (from, to) = if (window == null) monthOf(null).let { it.first to it.second } else Hudhud.span(window)
        val names = dao.allStaffForBackup().associate { it.id to it.name }
        val pays = dao.observeStaffPaymentsBetween(from, to).first()
            .filter { !it.isDeleted && (ids.isEmpty() || it.staffId in ids) }.sortedByDescending { it.timestamp }
        if (pays.isEmpty()) return Answer(Mood.CALM, label = title, lines = listOf(c.getString(R.string.hudhud_staff_paid_none)), open = StaffActivity::class.java)
        val advances = pays.filter { it.kind == Payroll.ADVANCE }.sumOf { it.amount }
        val salary = pays.filter { it.kind == Payroll.SALARY }.sumOf { it.amount }
        return Answer(Mood.CALM, label = title, figure = HudhudBook.money(pays.sumOf { it.amount }),
            lines = listOfNotNull(
                if (advances > 0.004) HudhudFacts.line(c, R.string.staff_advance, HudhudBook.money(advances)) else null,
                if (salary > 0.004) HudhudFacts.line(c, R.string.hudhud_l_salary_paid, HudhudBook.money(salary)) else null
            ),
            rows = pays.take(HudhudFacts.TOP).map { pay ->
                val kind = c.getString(if (pay.kind == Payroll.ADVANCE) R.string.staff_advance else R.string.hudhud_l_salary_paid)
                val sub = listOfNotNull(kind, HudhudFacts.dayOf(c, pay.timestamp), pay.note?.takeIf { it.isNotBlank() }).joinToString(" · ")
                Row(0, names[pay.staffId] ?: "", HudhudBook.money(pay.amount), sub, open = StaffActivity::class.java)
            },
            open = StaffActivity::class.java, money = true)
    }

    /**
     * Who was away over a span (today when none was said), as Staff marks it: absent, half day, paid leave; a day with no mark is
     * a day present. A single day lists the names; a longer span counts each person's days, and one person's marked days are listed.
     */
    suspend fun attendance(c: Context, dao: KhataDao, ids: List<Long>, window: VoiceQuestion.Window?): Answer {
        val w = window ?: VoiceQuestion.Window.TODAY
        val title = HudhudFacts.heading(c, R.string.staff_attendance, w)
        if (!staff()) return HudhudFacts.ownerExact(c, title)
        val (from, to) = Hudhud.span(w)
        val people = dao.observeStaff().first().filter { ids.isEmpty() || it.id in ids }
        if (people.isEmpty()) return Answer(Mood.CALM, label = title, lines = listOf(c.getString(R.string.staff_none)), open = StaffActivity::class.java)
        val marks = dao.observeAttendanceBetween(from, to).first().filter { m -> people.any { it.id == m.staffId } }
        val oneDay = w == VoiceQuestion.Window.TODAY || w == VoiceQuestion.Window.YESTERDAY
        fun names(status: Int) = people.filter { p -> marks.any { it.staffId == p.id && it.status == status } }.joinToString(", ") { it.name }
        if (oneDay) {
            val absent = people.count { p -> marks.any { it.staffId == p.id && it.status == Payroll.ABSENT } }
            val half = people.count { p -> marks.any { it.staffId == p.id && it.status == Payroll.HALF } }
            val leave = people.count { p -> marks.any { it.staffId == p.id && it.status == Payroll.LEAVE } }
            val present = people.size - absent - half - leave
            val lines = listOfNotNull(
                if (absent > 0) HudhudFacts.line(c, R.string.staff_absent, names(Payroll.ABSENT)) else null,
                if (half > 0) HudhudFacts.line(c, R.string.staff_half, names(Payroll.HALF)) else null,
                if (leave > 0) HudhudFacts.line(c, R.string.staff_leave, names(Payroll.LEAVE)) else null,
                HudhudFacts.line(c, R.string.staff_present, present.toString())
            )
            return Answer(if (absent > 0) Mood.CARE else Mood.CALM, label = title, figure = if (absent > 0) absent.toString() else null,
                lines = if (absent + half + leave == 0) listOf(c.getString(R.string.hudhud_attendance_none)) + lines else lines,
                open = StaffActivity::class.java)
        }
        if (people.size == 1) {
            val p = people.first()
            val mine = marks.filter { it.staffId == p.id }.sortedByDescending { it.day }
            if (mine.isEmpty()) return Answer(Mood.GOOD, label = p.name + " · " + title, lines = listOf(c.getString(R.string.hudhud_attendance_none)), open = StaffActivity::class.java)
            return Answer(Mood.CALM, label = p.name + " · " + title,
                figure = mine.count { it.status != Payroll.PRESENT }.toString(),
                rows = mine.take(HudhudFacts.TOP * 2).map { Row(0, HudhudFacts.dayOf(c, it.day), "", statusWord(c, it.status), open = StaffActivity::class.java) },
                open = StaffActivity::class.java)
        }
        val rows = people.mapNotNull { p ->
            val mine = marks.filter { it.staffId == p.id }
            if (mine.isEmpty()) return@mapNotNull null
            val counts = c.getString(R.string.staff_counts, mine.count { it.status == Payroll.ABSENT }.toString(),
                mine.count { it.status == Payroll.HALF }.toString(), mine.count { it.status == Payroll.LEAVE }.toString())
            Row(0, p.name, "", counts, open = StaffActivity::class.java)
        }
        if (rows.isEmpty()) return Answer(Mood.GOOD, label = title, lines = listOf(c.getString(R.string.hudhud_attendance_none)), open = StaffActivity::class.java)
        return Answer(Mood.CALM, label = title, figure = rows.size.toString(), rows = rows.take(HudhudFacts.TOP + 1), open = StaffActivity::class.java)
    }

    private fun statusWord(c: Context, status: Int): String = c.getString(when (status) {
        Payroll.ABSENT -> R.string.staff_absent
        Payroll.HALF -> R.string.staff_half
        Payroll.LEAVE -> R.string.staff_leave
        else -> R.string.staff_present
    })

    private fun roleWord(c: Context, role: String?): String = c.getString(when (role) {
        Role.MANAGER -> R.string.staff_role_manager
        Role.CASHIER -> R.string.staff_role_cashier
        else -> R.string.staff_role_none
    })

    /**
     * Who a staff member is: his role at the counter, his phone, his salary, the day he was added and whether he is still on the
     * list; with nobody named, everyone on the list with the role each has. The phone is shown, never said aloud ([Answer.secrets]).
     * Read from the staff table for what the Staff screen shows: the PIN's hash and salt are not touched.
     */
    suspend fun staffPerson(c: Context, dao: KhataDao, ids: List<Long>): Answer {
        val label = c.getString(R.string.hudhud_t_staff)
        if (!staff()) return HudhudFacts.ownerExact(c, label)
        val all = dao.allStaffForBackup()
        val list = if (ids.isEmpty()) all.filter { it.isActive } else all.filter { it.id in ids }
        if (list.isEmpty()) return Answer(Mood.CALM, label = label, lines = listOf(c.getString(R.string.staff_none)), open = StaffActivity::class.java)
        if (list.size > 1) {
            return Answer(Mood.CALM, label = label, figure = list.size.toString(),
                rows = list.take(HudhudFacts.TOP + 1).map { Row(0, it.name, "", roleWord(c, it.role), open = StaffActivity::class.java) },
                open = StaffActivity::class.java)
        }
        val p = list.first()
        val phone = p.phone?.takeIf { it.isNotBlank() }
        return Answer(Mood.CALM, label = p.name,
            lines = listOfNotNull(
                HudhudFacts.line(c, R.string.staff_role, roleWord(c, p.role)),
                phone?.let { HudhudFacts.line(c, R.string.hudhud_l_phone, Format.ltr(it)) },
                HudhudFacts.line(c, R.string.staff_salary, HudhudBook.money(p.monthlySalary)),
                HudhudFacts.line(c, R.string.hudhud_l_joined, HudhudFacts.dayOf(c, p.createdAt)),
                if (!p.isActive) c.getString(R.string.hudhud_l_staff_left) else null
            ),
            open = StaffActivity::class.java, money = true, secrets = listOfNotNull(phone))
    }

    // ------------------------------------------------------------------------------------------------ plans

    /**
     * A customer's payment plan: what was agreed, what is paid and what is left (amounts with REPORTS), the next due day, when it
     * started and, when it is complete, when it closed; and his instalments newest first with the last one's day. A plan is a
     * promise, never a debt: nothing here adds to what he owes.
     */
    suspend fun plan(c: Context, dao: KhataDao, ids: List<Long>, parties: List<PartyWithBalance>): Answer {
        val name = parties.firstOrNull { it.id in ids }?.name
        val label = c.getString(R.string.nav_plans)
        val progress = dao.observePlans().first().filter { it.partyId in ids }
        if (progress.isEmpty()) {
            return Answer(Mood.CALM, label = name ?: label, lines = listOf(c.getString(R.string.hudhud_plan_none_for, name ?: "")), open = PlansActivity::class.java)
        }
        val money = reports()
        val made = dao.allPlansForBackup().associateBy { it.id }
        val paid = dao.allInstallmentsForBackup().filter { !it.isDeleted }.groupBy { it.planId }
        val p = progress.first()
        val row = made[p.id]
        val mine = (paid[p.id] ?: emptyList()).sortedByDescending { it.paidAt }
        val lines = listOfNotNull(
            if (progress.size > 1) HudhudFacts.line(c, R.string.nav_plans, progress.size.toString()) else null,
            if (money) c.getString(R.string.plan_paid, HudhudBook.money(p.paidSoFar), HudhudBook.money(p.totalAmount)) else null,
            if (money && !p.isClosed) c.getString(R.string.plan_remaining, HudhudBook.money(p.remaining)) else null,
            p.installmentAmount?.takeIf { money && it > 0.004 }?.let { HudhudFacts.line(c, R.string.plan_installment_hint, HudhudBook.money(it)) },
            if (p.isClosed) c.getString(R.string.plan_closed) else c.getString(R.string.plan_next_due, p.nextDueDate?.let { HudhudFacts.dayOf(c, it) } ?: c.getString(R.string.plan_next_due_none)),
            row?.let { HudhudFacts.line(c, R.string.hudhud_l_plan_started, HudhudFacts.dayOf(c, it.createdAt)) },
            row?.closedAt?.let { HudhudFacts.line(c, R.string.hudhud_l_plan_closed_on, HudhudFacts.dayOf(c, it)) },
            HudhudFacts.line(c, R.string.hudhud_l_instalments, mine.size.toString()),
            mine.firstOrNull()?.let { HudhudFacts.line(c, R.string.hudhud_l_last_instalment, HudhudFacts.dayOf(c, it.paidAt)) },
            p.note?.takeIf { it.isNotBlank() }
        )
        return Answer(if (p.isClosed) Mood.GOOD else Mood.CALM, label = (name ?: p.partyName) + " · " + label,
            figure = if (money && !p.isClosed) HudhudBook.money(p.remaining) else null, lines = lines,
            rows = mine.take(HudhudFacts.TOP).map { Row(0, HudhudFacts.dayOf(c, it.paidAt), if (money) HudhudBook.money(it.amount) else "", it.note?.takeIf { n -> n.isNotBlank() }, open = PlansActivity::class.java) },
            open = PlansActivity::class.java, money = true)
    }

    /** The plans that were completed (over a span when one was said, a named customer's when he was), newest first, with the day each closed. */
    suspend fun plansClosed(c: Context, dao: KhataDao, ids: List<Long>, window: VoiceQuestion.Window?): Answer {
        val label = HudhudFacts.heading(c, R.string.hudhud_t_plans_closed, window)
        val money = reports()
        val made = dao.allPlansForBackup().associateBy { it.id }
        val span = window?.let { Hudhud.span(it) }
        val closed = dao.observePlans().first().filter { it.isClosed && (ids.isEmpty() || it.partyId in ids) }
            .map { it to made[it.id]?.closedAt }
            .filter { (_, at) -> span == null || (at != null && at >= span.first && at < span.second) }
            .sortedByDescending { it.second ?: 0L }
        if (closed.isEmpty()) return Answer(Mood.CALM, label = label, lines = listOf(c.getString(R.string.hudhud_plans_closed_none)), open = PlansActivity::class.java)
        return Answer(Mood.GOOD, label = label, figure = closed.size.toString(),
            rows = closed.take(HudhudFacts.TOP).map { (p, at) ->
                Row(p.partyId, p.partyName, if (money) HudhudBook.money(p.totalAmount) else "", at?.let { HudhudFacts.line(c, R.string.hudhud_l_plan_closed_on, HudhudFacts.dayOf(c, it)) })
            },
            open = PlansActivity::class.java)
    }

    // ------------------------------------------------------------------------------------------------ cheques

    /** A customer's cheques, newest due day first, each with the day it was written and, once it cleared or bounced, the day it settled. */
    suspend fun cheques(c: Context, dao: KhataDao, ids: List<Long>, parties: List<PartyWithBalance>): Answer {
        val name = parties.firstOrNull { it.id in ids }?.name
        val label = c.getString(R.string.cheques)
        val list = dao.observeCheques().first().filter { it.partyId in ids }.sortedByDescending { it.dueDate }
        if (list.isEmpty()) {
            return Answer(Mood.CALM, label = name ?: label, lines = listOf(c.getString(R.string.hudhud_cheques_none_for, name ?: "")), open = ChequesActivity::class.java)
        }
        val money = reports()
        fun count(status: Int, word: Int) = list.count { it.status == status }.takeIf { it > 0 }?.let { HudhudFacts.line(c, word, it.toString()) }
        return Answer(if (list.any { it.status == ChequeStatus.BOUNCED }) Mood.CARE else Mood.CALM, label = (name ?: list.first().partyName) + " · " + label,
            figure = list.size.toString(),
            lines = listOfNotNull(count(ChequeStatus.PENDING, R.string.cheque_pending), count(ChequeStatus.CLEARED, R.string.cheque_cleared),
                count(ChequeStatus.BOUNCED, R.string.cheque_bounced), count(ChequeStatus.CANCELLED, R.string.cheque_cancelled)),
            rows = chequeRows(c, dao, list.take(HudhudFacts.TOP), money), open = ChequesActivity::class.java)
    }

    /**
     * The rows of a list of cheques: number, bank, the day it falls due, the day it was written ([Cheque.createdAt]), the way it
     * stands (cleared, bounced, cancelled) with the day it was settled ([Cheque.settledAt]), and — with REPORTS, like the amount —
     * the note written on it. The Cheques list's own row does not carry these, so each cheque is read from its own table.
     */
    suspend fun chequeRows(c: Context, dao: KhataDao, list: List<ChequeWithParty>, money: Boolean): List<Row> {
        val own = dao.allChequesForBackup().associateBy { it.id }
        return list.map { ch ->
            val row = own[ch.id]
            val status = when (ch.status) {
                ChequeStatus.CLEARED -> c.getString(R.string.cheque_cleared)
                ChequeStatus.BOUNCED -> c.getString(R.string.cheque_bounced)
                ChequeStatus.CANCELLED -> c.getString(R.string.cheque_cancelled)
                else -> null
            }
            val sub = listOfNotNull(
                ch.chequeNumber?.takeIf { it.isNotBlank() }, ch.bankName?.takeIf { it.isNotBlank() },
                c.getString(R.string.hudhud_l_cheque_due, HudhudFacts.dayOf(c, ch.dueDate)),
                row?.createdAt?.let { c.getString(R.string.hudhud_l_written_on, HudhudFacts.dayOf(c, it)) },
                status, row?.settledAt?.takeIf { ch.status != ChequeStatus.PENDING }?.let { c.getString(R.string.hudhud_l_settled_on, HudhudFacts.dayOf(c, it)) },
                row?.note?.takeIf { money && it.isNotBlank() }?.take(NOTE_MAX)
            )
            Row(ch.partyId, ch.partyName, if (money) HudhudBook.money(ch.amount) else "", sub.joinToString(" · "))
        }
    }

    // ------------------------------------------------------------------------------------------------ who did what

    /** Actions the Who did what screen lists that say nothing about the book's records (a sign-in, a PIN wait). */
    private val QUIET = setOf(AuditAction.SIGN_IN, AuditAction.SIGN_OUT, AuditAction.PIN_WAIT)

    /** The actions whose detail is an amount (what [com.innovation313.roshankhata.screens.auditDetail] writes as money). */
    private val AMOUNT = setOf(AuditAction.ENTRY_ADDED, AuditAction.CASH_ADDED, AuditAction.CASH_EDITED, AuditAction.CASH_DELETED)

    /**
     * The newest audit rows given (the caller has asked [HudhudGate.reports]), as the Who did what screen words them
     * ([auditActionName], one source for both): what was done, to what ([AuditLog.refTable]: an entry, a cashbook row, a cheque,
     * a customer…), whose customer it was and how much when the row says so, who did it and when. Free text in a detail is the
     * screen's own; only an amount is told here.
     */
    suspend fun auditRows(c: Context, dao: KhataDao, audit: List<AuditLog>): List<Row> {
        val shown = audit.filter { it.action !in QUIET }.take(HudhudFacts.TOP)
        val names = AuditNames.load(dao, shown)
        val words = AndroidWords(c)
        return shown.map { a ->
            val table = a.refTable?.let { tableWord(c, it) }
            val who = names.entryParty[a.refId] ?: names.party[a.refId]
            val amount = a.detail?.takeIf { a.action in AMOUNT }?.let { d -> AuditNames.amount(d.substringBefore(' '))?.let { HudhudBook.money(it) } }
            val by = c.getString(R.string.audit_by, a.user?.takeIf { it.isNotBlank() } ?: c.getString(R.string.access_owner))
            Row(0, auditActionName(words, a, names), HudhudFacts.whenOf(c, a.timestamp),
                listOfNotNull(table, who, amount, by).joinToString(" \u00B7 "), open = AuditActivity::class.java)
        }
    }

    private fun tableWord(c: Context, table: String): String? = when (table) {
        "transactions" -> R.string.hudhud_l_entries
        "cashbook" -> R.string.hudhud_t_cashbook
        "cheques" -> R.string.cheques
        "parties" -> R.string.hudhud_t_customers
        "invoices" -> R.string.invoices
        "supplier_bills" -> R.string.supplier_bills
        "staff" -> R.string.hudhud_t_staff
        "payment_plans" -> R.string.nav_plans
        else -> null
    }?.let { c.getString(it) }

    /** A cheque's note on a card: the first words of it, the rest is the Cheques screen's. */
    private const val NOTE_MAX = 60
}
