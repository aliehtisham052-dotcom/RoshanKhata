package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words

/**
 * What a restore would take away (21 Oct 2026, the critical review).
 *
 * A restore replaces the whole book with the copy, so whatever was written
 * here after that copy was made is gone — the phone↔computer whole-copy rule
 * makes this easy to do by accident (a morning's sales on the computer, then
 * the phone's older book sent over it). Before anything is replaced, the
 * screens ask this and say it in figures: entries, sales, cashbook rows …
 * that are here and not in the copy, and entries the copy holds in an older
 * form.
 *
 * Rows are matched by their uid when the copy carries them (format 12, schema
 * 36: the same row has the same uid on every copy of the book). An older copy
 * has none, so its rows are matched by what they say — whose, how much, which
 * way and when to the millisecond for an entry; the number for a sale, a
 * return or an invoice. Only live rows here count: what is already in the bin
 * is not a loss the owner would notice.
 */
object RestoreCheck {

    data class Loss(
        /** Live entries here that the copy does not have. */
        val entries: Int,
        /** Entries both have, that the copy holds differently (an edit or a delete made here since). */
        val changed: Int,
        val cash: Int,
        val sales: Int,
        val returns: Int,
        val cheques: Int,
        val bills: Int,
        val invoices: Int,
        val parties: Int,
        /** The newest entry, sale or cashbook row here, and in the copy; null when there is none. */
        val newestHere: Long?,
        val newestThere: Long?,
        /**
         * The count could not be made (21 Oct 2026). It used to read as "nothing
         * lost" and the replace went ahead unticked; now it is said, and the
         * owner ticks that they accept not knowing.
         */
        val unknown: Boolean = false,
        /**
         * Everything else made here since the copy (22 Oct 2026, the review):
         * stock counts and corrections, day closes, shifts, points, purchase
         * orders, and tickets or returns voided here that the copy holds posted.
         */
        val others: Int = 0
    ) {
        val total: Int get() = entries + changed + cash + sales + returns + cheques + bills + invoices + parties + others
        val none: Boolean get() = !unknown && total == 0
    }

    /** One entry of the copy as [of] compares it (17 Nov 2026, L001): what it says, never its goods. */
    data class EntryMark(
        val uid: String?, val partyId: Long, val amount: Double, val isGiven: Boolean, val timestamp: Long,
        val note: String?, val isDeleted: Boolean
    )

    /** One cashbook row of the copy as [of] compares it. */
    data class CashMark(val uid: String?, val amount: Double, val isIncome: Boolean, val timestamp: Long, val category: String, val isDeleted: Boolean)

    /** One counter ticket of the copy as [of] compares it. */
    data class SaleMark(val uid: String?, val saleNumber: String, val status: String, val isDeleted: Boolean, val timestamp: Long)

    /**
     * What [of] reads of the copy's big tables (17 Nov 2026, the audit's L001): a copy read whole has its rows, a big
     * one read a page at a time ([BackupCodec.ParsedBackup.paged]) keeps only these marks of them — the same fields
     * the comparison has always read, so both say the same figures. The small tables are read from the copy as before.
     */
    class CopyMarks(val entries: List<EntryMark>, val cash: List<CashMark>, val sales: List<SaleMark>, val seals: Int) {
        /** Built a row at a time, in the file's order, by the paged read. */
        class Builder {
            private val entries = ArrayList<EntryMark>()
            private val cash = ArrayList<CashMark>()
            private val sales = ArrayList<SaleMark>()
            private var seals = 0
            fun entry(e: LedgerEntry) { entries += CopyMarks.markOf(e) }
            fun cash(c: CashEntry) { cash += CopyMarks.markOf(c) }
            fun sale(s: Sale) { sales += CopyMarks.markOf(s) }
            fun seal() { seals++ }
            fun build() = CopyMarks(entries, cash, sales, seals)
        }

        companion object {
            fun markOf(e: LedgerEntry) = EntryMark(e.uid, e.partyId, e.amount, e.isGiven, e.timestamp, e.note, e.isDeleted)
            fun markOf(c: CashEntry) = CashMark(c.uid, c.amount, c.isIncome, c.timestamp, c.category, c.isDeleted)
            fun markOf(s: Sale) = SaleMark(s.uid, s.saleNumber, s.status, s.isDeleted, s.timestamp)

            /** The copy's marks: its paged read's, else made from its lists. */
            fun of(there: BackupCodec.ParsedBackup): CopyMarks = there.paged?.marks ?: CopyMarks(
                there.entries.map { markOf(it) }, there.cash.map { markOf(it) }, there.sales.map { markOf(it) }, there.bookSeals.size
            )
        }
    }

    /** What is said when the count could not be made. */
    val UNKNOWN = Loss(0, 0, 0, 0, 0, 0, 0, 0, 0, null, null, unknown = true)

    private const val PAGE = 2_000

    private fun money(v: Double) = Math.round(v * 100).toString()
    private fun nameKey(name: String?) = name.orEmpty().trim().lowercase().replace(Regex("\\s+"), " ")

    suspend fun of(dao: KhataDao, there: BackupCodec.ParsedBackup): Loss {
        val byUid = there.version >= 12
        // The copy's big tables as marks (17 Nov 2026, L001): its rows' own fields for a copy read whole, the marks a big
        // copy kept for a copy read a page at a time — the comparisons below read only these.
        val marks = CopyMarks.of(there)

        // Parties here, paged as a backup reads them.
        val partiesHere = ArrayList<Party>()
        var after = 0L
        while (true) {
            val page = dao.partiesPageForBackup(after, PAGE)
            partiesHere += page
            if (page.size < PAGE) break
            after = page.last().id
        }
        val nameHere = partiesHere.associate { it.id to nameKey(it.name) }
        val nameThere = there.parties.associate { it.id to nameKey(it.name) }

        fun keyOf(uid: String?, partyId: Long, amount: Double, isGiven: Boolean, timestamp: Long, names: Map<Long, String>): String =
            if (byUid && uid != null) uid
            else "${names[partyId]}|${money(amount)}|${isGiven}|${timestamp}"
        fun entryKey(e: LedgerEntry, names: Map<Long, String>): String = keyOf(e.uid, e.partyId, e.amount, e.isGiven, e.timestamp, names)
        fun markKey(e: EntryMark, names: Map<Long, String>): String = keyOf(e.uid, e.partyId, e.amount, e.isGiven, e.timestamp, names)

        val thereEntries = HashMap<String, EntryMark>(marks.entries.size * 2)
        for (e in marks.entries) thereEntries[markKey(e, nameThere)] = e

        var lostEntries = 0
        var changed = 0
        var newestHere: Long? = null
        after = 0L
        while (true) {
            val page = dao.entriesPageForBackup(after, PAGE)
            for (e in page) {
                if (e.isDeleted) {
                    // Binned here since the copy, live there: a restore brings it back (22 Oct 2026, the review).
                    if (byUid && e.uid != null && thereEntries[e.uid]?.isDeleted == false) changed++
                    continue
                }
                if (newestHere == null || e.timestamp > newestHere) newestHere = e.timestamp
                val t = thereEntries[entryKey(e, nameHere)]
                when {
                    t == null -> lostEntries++
                    byUid && (t.isDeleted || money(t.amount) != money(e.amount) || t.isGiven != e.isGiven ||
                        t.note.orEmpty() != e.note.orEmpty() || t.timestamp != e.timestamp) -> changed++
                }
            }
            if (page.size < PAGE) break
            after = page.last().id
        }

        val cashKeys = marks.cash.map { c -> if (byUid && c.uid != null) c.uid else "${money(c.amount)}|${c.isIncome}|${c.timestamp}|${c.category}" }.toHashSet()
        val cashHere = dao.allCashForBackup().filter { !it.isDeleted }
        val lostCash = cashHere.count { c -> (if (byUid && c.uid != null) c.uid else "${money(c.amount)}|${c.isIncome}|${c.timestamp}|${c.category}") !in cashKeys }
        cashHere.maxOfOrNull { it.timestamp }?.let { if (newestHere == null || it > newestHere!!) newestHere = it }

        val salesHere = dao.allSalesForBackup().filter { !it.isDeleted && it.status != SaleStatus.HELD }
        val saleKeys = marks.sales.map { s -> if (byUid && s.uid != null) s.uid else s.saleNumber }.toHashSet()
        val lostSales = salesHere.count { s -> (if (byUid && s.uid != null) s.uid else s.saleNumber) !in saleKeys }
        salesHere.maxOfOrNull { it.timestamp }?.let { if (newestHere == null || it > newestHere!!) newestHere = it }

        val returnKeys = there.returns.map { r -> if (byUid && r.uid != null) r.uid else r.returnNumber }.toHashSet()
        val lostReturns = dao.allReturnsForBackup().count { r -> !r.isDeleted && (if (byUid && r.uid != null) r.uid else r.returnNumber) !in returnKeys }

        fun chequeKey(c: Cheque, names: Map<Long, String>) =
            if (byUid && c.uid != null) c.uid else "${names[c.partyId]}|${money(c.amount)}|${c.dueDate}|${c.chequeNumber.orEmpty()}"
        val chequeKeys = there.cheques.map { chequeKey(it, nameThere) }.toHashSet()
        val lostCheques = dao.allChequesForBackup().count { !it.isDeleted && chequeKey(it, nameHere) !in chequeKeys }

        fun billKey(b: SupplierBill, names: Map<Long, String>) =
            if (byUid && b.uid != null) b.uid else "${names[b.partyId]}|${money(b.totalAmount)}|${b.billDate}|${b.billNumber.orEmpty()}"
        val billKeys = there.bills.map { billKey(it, nameThere) }.toHashSet()
        val lostBills = dao.allBillsForBackup().count { !it.isDeleted && billKey(it, nameHere) !in billKeys }

        val invoiceKeys = there.invoices.map { i -> if (byUid && i.uid != null) i.uid else i.invoiceNumber }.toHashSet()
        val lostInvoices = dao.allInvoicesForBackup().count { i -> !i.isDeleted && (if (byUid && i.uid != null) i.uid else i.invoiceNumber) !in invoiceKeys }

        // What else the copy lacks or holds older (22 Oct 2026, the review: a 10:00 copy said "nothing lost",
        // then silently un-voided an 11:00 void and dropped an 11:00 stock count).
        var others = 0
        if (byUid) {
            fun <T> missing(here: List<T>, copy: List<T>, uid: (T) -> String?, live: (T) -> Boolean): Int {
                val keys = copy.mapNotNull(uid).toHashSet()
                return here.count { r -> live(r) && uid(r).let { u -> u != null && u !in keys } }
            }
            others += missing(dao.allAdjustmentsForBackup(), there.adjustments, { it.uid }, { !it.isDeleted })
            others += missing(dao.allOrdersForBackup(), there.orders, { it.uid }, { !it.isDeleted })
            others += missing(dao.allLoyaltyForBackup(), there.loyalty, { it.uid }, { !it.isDeleted })
            others += missing(dao.allShiftsForBackup(), there.shifts, { it.uid }, { true })
            // 8 Nov 2026 review (M6): what restoreAll also wipes and a copy may lack — products, plans, staff,
            // schemes (by uid), the seals (by count) and the book's own settings (by key). Each was lost in silence.
            others += missing(dao.allProductsForBackup(), there.products, { it.uid }, { !it.isDeleted })
            others += missing(dao.allPlansForBackup(), there.plans, { it.uid }, { !it.isDeleted })
            others += missing(dao.allStaffForBackup(), there.staff, { it.uid }, { true })
            others += missing(dao.allSchemesForBackup(), there.schemes, { it.uid }, { !it.isDeleted })
            others += maxOf(0, dao.allSealsForBackup().size - marks.seals)
            val settingsThere = there.settings.map { it.key }.toHashSet()
            others += dao.allSettingsForBackup().count { st ->
                st.key !in settingsThere && st.key !in BookSettings.ACCESS_KEYS && !Waris.isSecret(st.key) && !st.value.isNullOrEmpty()
            }
            val salesThere = marks.sales.filter { it.uid != null }.associateBy { it.uid!! }
            others += salesHere.count { s -> s.uid?.let { salesThere[it] }?.let { t -> t.status != s.status } == true }
            val returnsThere = there.returns.filter { it.uid != null }.associateBy { it.uid!! }
            others += dao.allReturnsForBackup().count { r -> !r.isDeleted && r.uid?.let { returnsThere[it] }?.let { t -> t.status != r.status } == true }
        }
        val closesThere = there.dayCloses.associateBy { it.day }
        others += dao.allDayClosesForBackup().count { c -> closesThere[c.day]?.let { t -> t.closedAt != c.closedAt } ?: true }

        val partyNamesThere = there.parties.filter { !it.isDeleted }.map { nameKey(it.name) }.toHashSet()
        val lostParties = partiesHere.count { !it.isDeleted && nameKey(it.name) !in partyNamesThere }

        val newestThere = listOfNotNull(
            marks.entries.filter { !it.isDeleted }.maxOfOrNull { it.timestamp },
            marks.cash.filter { !it.isDeleted }.maxOfOrNull { it.timestamp },
            marks.sales.filter { !it.isDeleted }.maxOfOrNull { it.timestamp }
        ).maxOrNull()

        return Loss(
            entries = lostEntries, changed = changed, cash = lostCash, sales = lostSales, returns = lostReturns,
            cheques = lostCheques, bills = lostBills, invoices = lostInvoices, parties = lostParties,
            newestHere = newestHere, newestThere = newestThere, others = others
        )
    }

    /**
     * The loss in the app's words, one line each, for the confirmation — the
     * phone and the computer say it alike. [date] writes a moment as the
     * screen does.
     */
    fun lines(loss: Loss, words: Words, date: (Long) -> String): List<String> = buildList {
        if (loss.unknown) add(words.text(W.restore_loss_unknown))
        if (loss.entries > 0) add(words.text(W.restore_loss_entries, loss.entries))
        if (loss.changed > 0) add(words.text(W.restore_loss_changed, loss.changed))
        if (loss.sales > 0) add(words.text(W.restore_loss_sales, loss.sales))
        if (loss.returns > 0) add(words.text(W.restore_loss_returns, loss.returns))
        if (loss.cash > 0) add(words.text(W.restore_loss_cash, loss.cash))
        if (loss.cheques > 0) add(words.text(W.restore_loss_cheques, loss.cheques))
        if (loss.bills > 0) add(words.text(W.restore_loss_bills, loss.bills))
        if (loss.invoices > 0) add(words.text(W.restore_loss_invoices, loss.invoices))
        if (loss.parties > 0) add(words.text(W.restore_loss_parties, loss.parties))
        if (loss.others > 0) add(words.text(W.restore_loss_other, loss.others))
        loss.newestHere?.let { here ->
            add(words.text(W.restore_loss_newest, date(here), loss.newestThere?.let(date) ?: "—"))
        }
    }
}
