package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.AnswerCard.Mood
import com.innovation313.roshankhata.data.AnswerCard.Row
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.WP
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
 * Staff is the owner's screen (STAFF): a manager is told so too, as [AnswerFacts.salaries] does. Amounts on plans and cheques
 * stay with REPORTS like every figure Hudhud adds up. Nothing is written, and no PIN hash or salt is ever read: the staff rows
 * are read only for what a person's page shows.
 */
internal object AnswerRecordFacts {

    private fun reports() = AnswerGate.reports()

    private fun staff() = AnswerGate.staff()

    suspend fun answer(c: AnswerEnv, dao: KhataDao, ask: Hudhud.Ask.Record, parties: List<PartyWithBalance>): AnswerCard = when (ask.facet) {
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
    private fun monthOf(c: AnswerEnv, window: VoiceQuestion.Window?): Triple<Long, Long, Int> {
        val now = c.now()
        val this_ = Payroll.monthRange(now)
        return if (window == VoiceQuestion.Window.LAST_MONTH) Payroll.monthRange(this_.first - 1) else this_
    }

    /**
     * What a named staff member earns and is still owed this month: the monthly salary, what was earned after the days away,
     * the advances and the salary paid, and what is still to pay — Staff's own month rule ([Payroll.month]).
     */
    suspend fun staffPay(c: AnswerEnv, dao: KhataDao, ids: List<Long>, window: VoiceQuestion.Window?): AnswerCard {
        val label = c.getString(W.hudhud_t_salary)
        if (!staff()) return AnswerFacts.ownerExact(c, label)
        val (from, to, days) = monthOf(c, window)
        val people = activeStaff(dao).filter { it.id in ids }
        if (people.isEmpty()) return AnswerCard(Mood.CALM, label = label, lines = listOf(c.getString(W.staff_none)), open = ScreenId.STAFF)
        val marks = dao.observeAttendanceBetween(from, to).first()
        val pays = dao.observeStaffPaymentsBetween(from, to).first()
        val months = people.map { p ->
            p to Payroll.month(p.monthlySalary, days, marks.filter { it.staffId == p.id }.map { it.status }, pays.filter { it.staffId == p.id })
        }
        val title = AnswerFacts.heading(c, W.hudhud_t_salary, window ?: VoiceQuestion.Window.THIS_MONTH)
        if (months.size > 1) {
            return AnswerCard(Mood.CALM, label = title,
                rows = months.map { (p, m) -> Row(0, p.name, AnswerBook.money(m.due), c.getString(W.staff_salary_line, AnswerBook.money(p.monthlySalary)), open = ScreenId.STAFF) },
                open = ScreenId.STAFF, money = true)
        }
        val (p, m) = months.first()
        val due = m.due
        val state = when {
            due > Money.halfCoin -> c.getString(W.staff_due, AnswerBook.money(due))
            due < -Money.halfCoin -> c.getString(W.staff_overpaid, AnswerBook.money(-due))
            else -> c.getString(W.staff_settled)
        }
        return AnswerCard(if (due > Money.halfCoin) Mood.CARE else Mood.GOOD, label = p.name + " · " + title, figure = AnswerBook.money(abs(due)),
            lines = listOfNotNull(
                c.getString(W.staff_salary_line, AnswerBook.money(p.monthlySalary)),
                c.getString(W.staff_money, AnswerBook.money(m.earned), AnswerBook.money(m.advances), AnswerBook.money(m.paid)),
                if (m.absent + m.half + m.leave > 0) c.getString(W.staff_counts, m.absent.toString(), m.half.toString(), m.leave.toString()) else null,
                state
            ), open = ScreenId.STAFF, money = true)
    }

    /**
     * What was paid to the staff over a span (this month when none was said): advances and salary, who got what and when, with
     * the note written beside it — the Staff screen's payments, newest first.
     */
    suspend fun staffPaid(c: AnswerEnv, dao: KhataDao, ids: List<Long>, window: VoiceQuestion.Window?): AnswerCard {
        val title = AnswerFacts.heading(c, W.hudhud_t_staff_paid, window ?: VoiceQuestion.Window.THIS_MONTH)
        if (!staff()) return AnswerFacts.ownerExact(c, title)
        val (from, to) = if (window == null) monthOf(c, null).let { it.first to it.second } else Hudhud.span(window, c.now())
        val names = dao.staffCardsOnce().associate { it.id to it.name }
        val pays = dao.observeStaffPaymentsBetween(from, to).first()
            .filter { !it.isDeleted && (ids.isEmpty() || it.staffId in ids) }.sortedByDescending { it.timestamp }
        if (pays.isEmpty()) return AnswerCard(Mood.CALM, label = title, lines = listOf(c.getString(W.hudhud_staff_paid_none)), open = ScreenId.STAFF)
        val advances = pays.filter { it.kind == Payroll.ADVANCE }.sumOf { it.amount }
        val salary = pays.filter { it.kind == Payroll.SALARY }.sumOf { it.amount }
        return AnswerCard(Mood.CALM, label = title, figure = AnswerBook.money(pays.sumOf { it.amount }),
            lines = listOfNotNull(
                if (advances > Money.halfCoin) AnswerFacts.line(c, W.staff_advance, AnswerBook.money(advances)) else null,
                if (salary > Money.halfCoin) AnswerFacts.line(c, W.hudhud_l_salary_paid, AnswerBook.money(salary)) else null
            ),
            rows = pays.take(AnswerFacts.TOP).map { pay ->
                val kind = c.getString(if (pay.kind == Payroll.ADVANCE) W.staff_advance else W.hudhud_l_salary_paid)
                val sub = listOfNotNull(kind, AnswerFacts.dayOf(c, pay.timestamp), pay.note?.takeIf { it.isNotBlank() }).joinToString(" · ")
                Row(0, names[pay.staffId] ?: "", AnswerBook.money(pay.amount), sub, open = ScreenId.STAFF)
            },
            open = ScreenId.STAFF, money = true)
    }

    /**
     * Who was away over a span (today when none was said), as Staff marks it: absent, half day, paid leave; a day with no mark is
     * a day present. A single day lists the names; a longer span counts each person's days, and one person's marked days are listed.
     */
    suspend fun attendance(c: AnswerEnv, dao: KhataDao, ids: List<Long>, window: VoiceQuestion.Window?): AnswerCard {
        val w = window ?: VoiceQuestion.Window.TODAY
        val title = AnswerFacts.heading(c, W.staff_attendance, w)
        if (!staff()) return AnswerFacts.ownerExact(c, title)
        val (from, to) = Hudhud.span(w, c.now())
        val people = activeStaff(dao).filter { ids.isEmpty() || it.id in ids }
        if (people.isEmpty()) return AnswerCard(Mood.CALM, label = title, lines = listOf(c.getString(W.staff_none)), open = ScreenId.STAFF)
        val marks = dao.observeAttendanceBetween(from, to).first().filter { m -> people.any { it.id == m.staffId } }
        val oneDay = w == VoiceQuestion.Window.TODAY || w == VoiceQuestion.Window.YESTERDAY
        fun names(status: Int) = people.filter { p -> marks.any { it.staffId == p.id && it.status == status } }.joinToString(", ") { it.name }
        if (oneDay) {
            val absent = people.count { p -> marks.any { it.staffId == p.id && it.status == Payroll.ABSENT } }
            val half = people.count { p -> marks.any { it.staffId == p.id && it.status == Payroll.HALF } }
            val leave = people.count { p -> marks.any { it.staffId == p.id && it.status == Payroll.LEAVE } }
            val present = people.size - absent - half - leave
            val lines = listOfNotNull(
                if (absent > 0) AnswerFacts.line(c, W.staff_absent, names(Payroll.ABSENT)) else null,
                if (half > 0) AnswerFacts.line(c, W.staff_half, names(Payroll.HALF)) else null,
                if (leave > 0) AnswerFacts.line(c, W.staff_leave, names(Payroll.LEAVE)) else null,
                AnswerFacts.line(c, W.staff_present, present.toString())
            )
            return AnswerCard(if (absent > 0) Mood.CARE else Mood.CALM, label = title, figure = if (absent > 0) absent.toString() else null,
                lines = if (absent + half + leave == 0) listOf(c.getString(W.hudhud_attendance_none)) + lines else lines,
                open = ScreenId.STAFF)
        }
        if (people.size == 1) {
            val p = people.first()
            val mine = marks.filter { it.staffId == p.id }.sortedByDescending { it.day }
            if (mine.isEmpty()) return AnswerCard(Mood.GOOD, label = p.name + " · " + title, lines = listOf(c.getString(W.hudhud_attendance_none)), open = ScreenId.STAFF)
            return AnswerCard(Mood.CALM, label = p.name + " · " + title,
                figure = mine.count { it.status != Payroll.PRESENT }.toString(),
                rows = mine.take(AnswerFacts.TOP * 2).map { Row(0, AnswerFacts.dayOf(c, it.day), "", statusWord(c, it.status), open = ScreenId.STAFF) },
                open = ScreenId.STAFF)
        }
        val rows = people.mapNotNull { p ->
            val mine = marks.filter { it.staffId == p.id }
            if (mine.isEmpty()) return@mapNotNull null
            val counts = c.getString(W.staff_counts, mine.count { it.status == Payroll.ABSENT }.toString(),
                mine.count { it.status == Payroll.HALF }.toString(), mine.count { it.status == Payroll.LEAVE }.toString())
            Row(0, p.name, "", counts, open = ScreenId.STAFF)
        }
        if (rows.isEmpty()) return AnswerCard(Mood.GOOD, label = title, lines = listOf(c.getString(W.hudhud_attendance_none)), open = ScreenId.STAFF)
        return AnswerCard(Mood.CALM, label = title, figure = rows.size.toString(), rows = rows.take(AnswerFacts.TOP + 1), open = ScreenId.STAFF)
    }

    /**
     * The staff still on the list, by name — the Staff screen's own list ([KhataDao.observeStaff]: active, ORDER BY name), read
     * without the PIN's hash and salt ([KhataDao.staffCardsOnce], audit T25).
     */
    private suspend fun activeStaff(dao: KhataDao): List<KhataDao.StaffCard> =
        dao.staffCardsOnce().filter { it.isActive }.sortedBy { it.name }

    private fun statusWord(c: AnswerEnv, status: Int): String = c.getString(when (status) {
        Payroll.ABSENT -> W.staff_absent
        Payroll.HALF -> W.staff_half
        Payroll.LEAVE -> W.staff_leave
        else -> W.staff_present
    })

    private fun roleWord(c: AnswerEnv, role: String?): String = c.getString(when (role) {
        Role.MANAGER -> W.staff_role_manager
        Role.CASHIER -> W.staff_role_cashier
        else -> W.staff_role_none
    })

    /**
     * Who a staff member is: his role at the counter, his phone, his salary, the day he was added and whether he is still on the
     * list; with nobody named, everyone on the list with the role each has. The phone is shown, never said aloud ([AnswerCard.secrets]).
     * Read from the staff table for what the Staff screen shows: the PIN's hash and salt are not touched.
     */
    suspend fun staffPerson(c: AnswerEnv, dao: KhataDao, ids: List<Long>): AnswerCard {
        val label = c.getString(W.hudhud_t_staff)
        if (!staff()) return AnswerFacts.ownerExact(c, label)
        val all = dao.staffCardsOnce()
        val list = if (ids.isEmpty()) all.filter { it.isActive } else all.filter { it.id in ids }
        if (list.isEmpty()) return AnswerCard(Mood.CALM, label = label, lines = listOf(c.getString(W.staff_none)), open = ScreenId.STAFF)
        if (list.size > 1) {
            return AnswerCard(Mood.CALM, label = label, figure = list.size.toString(),
                rows = list.take(AnswerFacts.TOP + 1).map { Row(0, it.name, "", roleWord(c, it.role), open = ScreenId.STAFF) },
                open = ScreenId.STAFF)
        }
        val p = list.first()
        val phone = p.phone?.takeIf { it.isNotBlank() }
        return AnswerCard(Mood.CALM, label = p.name,
            lines = listOfNotNull(
                AnswerFacts.line(c, W.staff_role, roleWord(c, p.role)),
                phone?.let { AnswerFacts.line(c, W.hudhud_l_phone, Format.ltr(it)) },
                AnswerFacts.line(c, W.staff_salary, AnswerBook.money(p.monthlySalary)),
                AnswerFacts.line(c, W.hudhud_l_joined, AnswerFacts.dayOf(c, p.createdAt)),
                if (!p.isActive) c.getString(W.hudhud_l_staff_left) else null
            ),
            open = ScreenId.STAFF, money = true, secrets = listOfNotNull(phone))
    }

    // ------------------------------------------------------------------------------------------------ plans

    /**
     * A customer's payment plan: what was agreed, what is paid and what is left (amounts with REPORTS), the next due day, when it
     * started and, when it is complete, when it closed; and his instalments newest first with the last one's day. A plan is a
     * promise, never a debt: nothing here adds to what he owes.
     */
    suspend fun plan(c: AnswerEnv, dao: KhataDao, ids: List<Long>, parties: List<PartyWithBalance>): AnswerCard {
        val name = parties.firstOrNull { it.id in ids }?.name
        val label = c.getString(W.nav_plans)
        val progress = dao.observePlans().first().filter { it.partyId in ids }
        if (progress.isEmpty()) {
            return AnswerCard(Mood.CALM, label = name ?: label, lines = listOf(c.getString(W.hudhud_plan_none_for, name ?: "")), open = ScreenId.PLANS)
        }
        val money = reports()
        val made = dao.allPlansForBackup().associateBy { it.id }
        val paid = dao.allInstallmentsForBackup().filter { !it.isDeleted }.groupBy { it.planId }
        val p = progress.first()
        val row = made[p.id]
        val mine = (paid[p.id] ?: emptyList()).sortedByDescending { it.paidAt }
        val lines = listOfNotNull(
            if (progress.size > 1) AnswerFacts.line(c, W.nav_plans, progress.size.toString()) else null,
            if (money) c.getString(W.plan_paid, AnswerBook.money(p.paidSoFar), AnswerBook.money(p.totalAmount)) else null,
            if (money && !p.isClosed) c.getString(W.plan_remaining, AnswerBook.money(p.remaining)) else null,
            p.installmentAmount?.takeIf { money && it > Money.halfCoin }?.let { AnswerFacts.line(c, W.plan_installment_hint, AnswerBook.money(it)) },
            if (p.isClosed) c.getString(W.plan_closed) else c.getString(W.plan_next_due, p.nextDueDate?.let { AnswerFacts.dayOf(c, it) } ?: c.getString(W.plan_next_due_none)),
            row?.let { AnswerFacts.line(c, W.hudhud_l_plan_started, AnswerFacts.dayOf(c, it.createdAt)) },
            row?.closedAt?.let { AnswerFacts.line(c, W.hudhud_l_plan_closed_on, AnswerFacts.dayOf(c, it)) },
            AnswerFacts.line(c, W.hudhud_l_instalments, mine.size.toString()),
            mine.firstOrNull()?.let { AnswerFacts.line(c, W.hudhud_l_last_instalment, AnswerFacts.dayOf(c, it.paidAt)) },
            p.note?.takeIf { it.isNotBlank() }
        )
        return AnswerCard(if (p.isClosed) Mood.GOOD else Mood.CALM, label = (name ?: p.partyName) + " · " + label,
            figure = if (money && !p.isClosed) AnswerBook.money(p.remaining) else null, lines = lines,
            rows = mine.take(AnswerFacts.TOP).map { Row(0, AnswerFacts.dayOf(c, it.paidAt), if (money) AnswerBook.money(it.amount) else "", it.note?.takeIf { n -> n.isNotBlank() }, open = ScreenId.PLANS) },
            open = ScreenId.PLANS, money = true)
    }

    /** The plans that were completed (over a span when one was said, a named customer's when he was), newest first, with the day each closed. */
    suspend fun plansClosed(c: AnswerEnv, dao: KhataDao, ids: List<Long>, window: VoiceQuestion.Window?): AnswerCard {
        val label = AnswerFacts.heading(c, W.hudhud_t_plans_closed, window)
        val money = reports()
        val made = dao.allPlansForBackup().associateBy { it.id }
        val span = window?.let { Hudhud.span(it, c.now()) }
        val closed = dao.observePlans().first().filter { it.isClosed && (ids.isEmpty() || it.partyId in ids) }
            .map { it to made[it.id]?.closedAt }
            .filter { (_, at) -> span == null || (at != null && at >= span.first && at < span.second) }
            .sortedByDescending { it.second ?: 0L }
        if (closed.isEmpty()) return AnswerCard(Mood.CALM, label = label, lines = listOf(c.getString(W.hudhud_plans_closed_none)), open = ScreenId.PLANS)
        return AnswerCard(Mood.GOOD, label = label, figure = closed.size.toString(),
            rows = closed.take(AnswerFacts.TOP).map { (p, at) ->
                Row(p.partyId, p.partyName, if (money) AnswerBook.money(p.totalAmount) else "", at?.let { AnswerFacts.line(c, W.hudhud_l_plan_closed_on, AnswerFacts.dayOf(c, it)) })
            },
            open = ScreenId.PLANS)
    }

    // ------------------------------------------------------------------------------------------------ cheques

    /** A customer's cheques, newest due day first, each with the day it was written and, once it cleared or bounced, the day it settled. */
    suspend fun cheques(c: AnswerEnv, dao: KhataDao, ids: List<Long>, parties: List<PartyWithBalance>): AnswerCard {
        val name = parties.firstOrNull { it.id in ids }?.name
        val label = c.getString(W.cheques)
        val list = dao.observeCheques().first().filter { it.partyId in ids }.sortedByDescending { it.dueDate }
        if (list.isEmpty()) {
            return AnswerCard(Mood.CALM, label = name ?: label, lines = listOf(c.getString(W.hudhud_cheques_none_for, name ?: "")), open = ScreenId.CHEQUES)
        }
        val money = reports()
        fun count(status: Int, word: String) = list.count { it.status == status }.takeIf { it > 0 }?.let { AnswerFacts.line(c, word, it.toString()) }
        return AnswerCard(if (list.any { it.status == ChequeStatus.BOUNCED }) Mood.CARE else Mood.CALM, label = (name ?: list.first().partyName) + " · " + label,
            figure = list.size.toString(),
            lines = listOfNotNull(count(ChequeStatus.PENDING, W.cheque_pending), count(ChequeStatus.CLEARED, W.cheque_cleared),
                count(ChequeStatus.BOUNCED, W.cheque_bounced), count(ChequeStatus.CANCELLED, W.cheque_cancelled)),
            rows = chequeRows(c, dao, list.take(AnswerFacts.TOP), money), open = ScreenId.CHEQUES)
    }

    /**
     * The rows of a list of cheques: number, bank, the day it falls due, the day it was written ([Cheque.createdAt]), the way it
     * stands (cleared, bounced, cancelled) with the day it was settled ([Cheque.settledAt]), and — with REPORTS, like the amount —
     * the note written on it. The Cheques list's own row does not carry these, so each cheque is read from its own table.
     */
    suspend fun chequeRows(c: AnswerEnv, dao: KhataDao, list: List<ChequeWithParty>, money: Boolean): List<Row> {
        val own = dao.allChequesForBackup().associateBy { it.id }
        return list.map { ch ->
            val row = own[ch.id]
            val status = when (ch.status) {
                ChequeStatus.CLEARED -> c.getString(W.cheque_cleared)
                ChequeStatus.BOUNCED -> c.getString(W.cheque_bounced)
                ChequeStatus.CANCELLED -> c.getString(W.cheque_cancelled)
                else -> null
            }
            val sub = listOfNotNull(
                ch.chequeNumber?.takeIf { it.isNotBlank() }, ch.bankName?.takeIf { it.isNotBlank() },
                c.getString(W.hudhud_l_cheque_due, AnswerFacts.dayOf(c, ch.dueDate)),
                row?.createdAt?.let { c.getString(W.hudhud_l_written_on, AnswerFacts.dayOf(c, it)) },
                status, row?.settledAt?.takeIf { ch.status != ChequeStatus.PENDING }?.let { c.getString(W.hudhud_l_settled_on, AnswerFacts.dayOf(c, it)) },
                row?.note?.takeIf { money && it.isNotBlank() }?.take(NOTE_MAX)
            )
            Row(ch.partyId, ch.partyName, if (money) AnswerBook.money(ch.amount) else "", sub.joinToString(" · "))
        }
    }

    // ------------------------------------------------------------------------------------------------ who did what

    /** Actions the Who did what screen lists that say nothing about the book's records (a sign-in, a PIN wait). */
    private val QUIET = setOf(AuditAction.SIGN_IN, AuditAction.SIGN_OUT, AuditAction.PIN_WAIT)

    /** The actions whose detail is an amount (what [com.innovation313.roshankhata.screens.auditDetail] writes as money). */
    private val AMOUNT = setOf(AuditAction.ENTRY_ADDED, AuditAction.CASH_ADDED, AuditAction.CASH_EDITED, AuditAction.CASH_DELETED)

    /**
     * The newest audit rows given (the caller has asked [AnswerGate.reports]), as the Who did what screen words them
     * ([auditActionName], one source for both): what was done, to what ([AuditLog.refTable]: an entry, a cashbook row, a cheque,
     * a customer…), whose customer it was and how much when the row says so, who did it and when. Free text in a detail is the
     * screen's own; only an amount is told here.
     */
    suspend fun auditRows(c: AnswerEnv, dao: KhataDao, audit: List<AuditLog>): List<Row> {
        val shown = audit.filter { it.action !in QUIET }.take(AnswerFacts.TOP)
        val names = AuditNames.load(dao, shown)
        val words = c.words
        return shown.map { a ->
            val table = a.refTable?.let { tableWord(c, it) }
            val who = names.entryParty[a.refId] ?: names.party[a.refId]
            val amount = a.detail?.takeIf { a.action in AMOUNT }?.let { d -> AuditNames.amount(d.substringBefore(' '))?.let { AnswerBook.money(it) } }
            val by = c.getString(W.audit_by, a.user?.takeIf { it.isNotBlank() } ?: c.getString(W.access_owner))
            Row(0, auditActionName(words, a, names), AnswerFacts.whenOf(c, a.timestamp),
                listOfNotNull(table, who, amount, by).joinToString(" \u00B7 "), open = ScreenId.AUDIT)
        }
    }

    private fun tableWord(c: AnswerEnv, table: String): String? = when (table) {
        "transactions" -> W.hudhud_l_entries
        "cashbook" -> W.hudhud_t_cashbook
        "cheques" -> W.cheques
        "parties" -> W.hudhud_t_customers
        "invoices" -> W.invoices
        "supplier_bills" -> W.supplier_bills
        "staff" -> W.hudhud_t_staff
        "payment_plans" -> W.nav_plans
        else -> null
    }?.let { c.getString(it) }

    /** A cheque's note on a card: the first words of it, the rest is the Cheques screen's. */
    private const val NOTE_MAX = 60
}
