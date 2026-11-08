package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words

/** A row's own id and its uid, for matching rows across copies of the book. */
data class IdUid(val id: Long, val uid: String?)

/**
 * Another copy of this book merged in (21 Oct 2026, the critical review):
 * the phone used at the market, the computer at the counter, and both
 * brought together without either losing its day — where a restore would
 * replace one with the other.
 *
 * Only what this book does not have yet is added, matched by each row's uid
 * (schema 36: the same row has the same uid on every copy) — never by what
 * it says. Rows both have stay exactly as they are here, even when the copy
 * holds them differently (counted as [Plan.changed], so the owner knows);
 * nothing here is changed or removed. A new row comes with what it points
 * at: its customer (matched by name and phone, else added), its products (by
 * name, else added), its goods lines, its payments and the khata and
 * cashbook rows a counter ticket wrote. A number already used here (RK-,
 * S-, R-, PO-, INV-) takes the next free one and keeps the old in its note.
 * Rows in the copy's bin are left out (a book's purged bin must not come
 * back); held tickets too. Payment plans, seasons, schemes, attendance and
 * day closes carry no uid and are not merged.
 *
 * All of it is one transaction ([KhataDao.mergeBook]): it lands whole or
 * not at all. The screens keep a copy of the book first, as before a restore.
 */
object BookMerge {

    enum class Why { TOO_OLD, UNRELATED }

    /** A copy that cannot be merged: too old to carry uids, or sharing no row with this book (another book). */
    class Refused(val why: Why) : IllegalArgumentException("merge refused: $why")

    data class Plan(
        val entries: Int = 0, val cash: Int = 0, val sales: Int = 0, val returns: Int = 0,
        val cheques: Int = 0, val bills: Int = 0, val invoices: Int = 0, val adjustments: Int = 0,
        val orders: Int = 0, val staffPayments: Int = 0, val loyalty: Int = 0, val shifts: Int = 0,
        val parties: Int = 0, val products: Int = 0,
        /** Rows both copies have that the copy holds differently: this book keeps its own. */
        val changed: Int = 0,
        /** New rows whose number was already used here, given the next free one. */
        val renumbered: Int = 0,
        /** The copy's shop name, when it is not this book's (said, not refused: a shop may be renamed). */
        val otherShop: String? = null
    ) {
        val others: Int get() = returns + cheques + bills + invoices + adjustments + orders + staffPayments + loyalty + shifts
        val rows: Int get() = entries + cash + sales + others
        val none: Boolean get() = rows == 0 && parties == 0 && products == 0
    }

    /** What a merge would add, in the app's words, one line each — the phone and the computer say it alike. */
    fun lines(plan: Plan, words: Words): List<String> = buildList {
        add(words.text(W.merge_preview, plan.entries, plan.cash, plan.sales, plan.others, plan.parties, plan.products))
        if (plan.changed > 0) add(words.text(W.merge_changed, plan.changed))
        plan.otherShop?.let { add(words.text(W.merge_other_shop, it)) }
        add(words.text(W.merge_not_carried))
    }

    /** Why a copy cannot be merged, as a word name. */
    fun refusalWord(e: Refused): String = if (e.why == Why.TOO_OLD) W.merge_too_old else W.merge_unrelated

    private fun norm(s: String?) = s.orEmpty().trim().lowercase().replace(Regex("\\s+"), " ")
    private fun phone(s: String?) = s.orEmpty().filter { it.isDigit() }.takeLast(10)
    private fun money(v: Double) = Math.round(v * 100)
    private fun List<IdUid>.byUid(): Map<String, Long> = mapNotNull { r -> r.uid?.let { it to r.id } }.toMap()

    /** A customer of the copy, found here: the same id and name, else the same name and phone, else the one of that name when a phone is missing. */
    // Only customers still in the book are matched, and two blank phones are not "the same phone"
    // (21 Oct 2026, the critical review: two Aslams with no number became one, and a binned
    // customer took the copy's entries and was then purged with them). A name that is not one
    // person here is not guessed: the copy's customer comes in as their own.
    fun matchParty(p: Party, hereAll: List<Party>): Party? {
        val here = hereAll.filter { !it.isDeleted }
        val named = here.filter { norm(it.name) == norm(p.name) }
        return here.firstOrNull { it.id == p.id && norm(it.name) == norm(p.name) }
            ?: named.firstOrNull { phone(p.phone).isNotEmpty() && phone(it.phone) == phone(p.phone) }
            ?: named.singleOrNull()?.takeIf { phone(it.phone).isEmpty() || phone(p.phone).isEmpty() }
    }

    private suspend fun partiesHere(dao: KhataDao): List<Party> {
        val out = ArrayList<Party>()
        var after = 0L
        while (true) {
            val page = dao.partiesPageForBackup(after, 2_000)
            out += page
            if (page.size < 2_000) break
            after = page.last().id
        }
        return out
    }

    private class News(there: BackupCodec.ParsedBackup, val here: Here) {
        fun <T> fresh(rows: List<T>, uid: (T) -> String?, live: (T) -> Boolean, known: Map<String, Long>) =
            rows.filter { r -> val u = uid(r); u != null && u !in known && live(r) }
        val entries = fresh(there.entries, { it.uid }, { !it.isDeleted }, here.entries)
        val cash = fresh(there.cash, { it.uid }, { !it.isDeleted }, here.cash)
        val cheques = fresh(there.cheques, { it.uid }, { !it.isDeleted }, here.cheques)
        val bills = fresh(there.bills, { it.uid }, { !it.isDeleted }, here.bills)
        val invoices = fresh(there.invoices, { it.uid }, { !it.isDeleted }, here.invoices)
        // A posted customer ticket comes only with its khata row (22 Oct 2026, the review: one whose row was in the
        // copy's bin arrived with none, was counted as a walk-in's — its goods out of stock — and its credit nowhere).
        private val liveEntries = there.entries.filter { !it.isDeleted }.map { it.id }.toHashSet()
        val sales = fresh(there.sales, { it.uid }, { s ->
            !s.isDeleted && s.status != SaleStatus.HELD &&
                (s.status != SaleStatus.DONE || s.partyId == null || s.ledgerEntryId == null || s.ledgerEntryId in liveEntries)
        }, here.sales)
        val returns = fresh(there.returns, { it.uid }, { !it.isDeleted }, here.returns)
        val adjustments = fresh(there.adjustments, { it.uid }, { !it.isDeleted }, here.adjustments)
        val orders = fresh(there.orders, { it.uid }, { !it.isDeleted }, here.orders)
        val staffPayments = fresh(there.staffPayments, { it.uid }, { !it.isDeleted }, here.staffPayments)
        val loyalty = fresh(there.loyalty, { it.uid }, { !it.isDeleted }, here.loyalty)
        val shifts = fresh(there.shifts, { it.uid }, { true }, here.shifts)
    }

    private class Here(
        val entries: Map<String, Long>, val cash: Map<String, Long>, val cheques: Map<String, Long>, val bills: Map<String, Long>,
        val invoices: Map<String, Long>, val sales: Map<String, Long>, val returns: Map<String, Long>, val adjustments: Map<String, Long>,
        val orders: Map<String, Long>, val staffPayments: Map<String, Long>, val loyalty: Map<String, Long>, val shifts: Map<String, Long>
    )

    private suspend fun here(dao: KhataDao) = Here(
        dao.entryUids().byUid(), dao.cashUids().byUid(), dao.chequeUids().byUid(), dao.billUids().byUid(),
        dao.invoiceUids().byUid(), dao.saleUids().byUid(), dao.returnUids().byUid(), dao.adjustmentUids().byUid(),
        dao.orderUids().byUid(), dao.staffPaymentUids().byUid(), dao.loyaltyUids().byUid(), dao.shiftUids().byUid()
    )

    /** The customers the new rows point at, and the copy's live ones this book lacks. */
    private fun partiesWanted(n: News, there: BackupCodec.ParsedBackup): Set<Long> =
        (n.entries.map { it.partyId } + n.cheques.map { it.partyId } + n.bills.map { it.partyId } + n.sales.mapNotNull { it.partyId } +
            n.returns.mapNotNull { it.partyId } + n.orders.map { it.partyId } + n.loyalty.map { it.partyId } +
            there.parties.filter { !it.isDeleted }.map { it.id }).toSet()

    /** The products the new rows point at, and the copy's live ones this book lacks. */
    private fun productsWanted(n: News, there: BackupCodec.ParsedBackup): Set<Long> {
        val newEntryIds = n.entries.map { it.id }.toSet()
        val newBillIds = n.bills.map { it.id }.toSet()
        val newSaleIds = n.sales.map { it.id }.toSet()
        val newReturnIds = n.returns.map { it.id }.toSet()
        val newOrderIds = n.orders.map { it.id }.toSet()
        return (n.entries.mapNotNull { it.productId } +
            there.entryItems.filter { it.entryId in newEntryIds }.mapNotNull { it.productId } +
            there.billItems.filter { it.billId in newBillIds }.mapNotNull { it.productId } +
            there.saleItems.filter { it.saleId in newSaleIds }.mapNotNull { it.productId } +
            there.returnItems.filter { it.returnId in newReturnIds }.mapNotNull { it.productId } +
            there.orderItems.filter { it.orderId in newOrderIds }.mapNotNull { it.productId } +
            n.adjustments.map { it.productId } +
            there.products.filter { !it.isDeleted }.map { it.id }).toSet()
    }

    /** What a merge would add, said before anything is written; refuses a copy it cannot merge. */
    suspend fun plan(dao: KhataDao, there: BackupCodec.ParsedBackup, shopHere: String?): Plan {
        if (there.version < 12) throw Refused(Why.TOO_OLD)
        val h = here(dao)
        val sharedRows = there.entries.count { it.uid in h.entries } + there.cash.count { it.uid in h.cash } + there.sales.count { it.uid in h.sales }
        val bookHasRows = h.entries.isNotEmpty() || h.cash.isNotEmpty() || h.sales.isNotEmpty()
        val copyHasRows = there.entries.isNotEmpty() || there.cash.isNotEmpty() || there.sales.isNotEmpty()
        if (sharedRows == 0 && bookHasRows && copyHasRows) throw Refused(Why.UNRELATED)
        val n = News(there, h)
        val partiesHere = partiesHere(dao)
        val wantedParties = partiesWanted(n, there)
        val newParties = there.parties.count { it.id in wantedParties && matchParty(it, partiesHere) == null }
        val wantedProducts = productsWanted(n, there)
        val newProducts = there.products.count { it.id in wantedProducts && dao.productByKey(it.nameKey) == null }
        // Rows both have that the copy holds differently: counted, never applied.
        val entriesHere = HashMap<Long, LedgerEntry>()
        var after = 0L
        while (true) {
            val page = dao.entriesPageForBackup(after, 2_000)
            page.forEach { entriesHere[it.id] = it }
            if (page.size < 2_000) break
            after = page.last().id
        }
        val changedEntries = there.entries.count { t ->
            val id = t.uid?.let { h.entries[it] } ?: return@count false
            val e = entriesHere[id] ?: return@count false
            // Not the note: a merge writes the old number into a renumbered row's note.
            e.isDeleted != t.isDeleted || money(e.amount) != money(t.amount) || e.isGiven != t.isGiven || e.timestamp != t.timestamp
        }
        val cashById = dao.allCashForBackup().associateBy { it.id }
        val changedCash = there.cash.count { t ->
            val c = t.uid?.let { h.cash[it] }?.let { cashById[it] } ?: return@count false
            c.isDeleted != t.isDeleted || money(c.amount) != money(t.amount) || c.isIncome != t.isIncome
        }
        val salesById = dao.allSalesForBackup().associateBy { it.id }
        val changedSales = there.sales.count { t ->
            val s = t.uid?.let { h.sales[it] }?.let { salesById[it] } ?: return@count false
            s.status != t.status || s.isDeleted != t.isDeleted
        }
        return Plan(
            entries = n.entries.size, cash = n.cash.size, sales = n.sales.size, returns = n.returns.size,
            cheques = n.cheques.size, bills = n.bills.size, invoices = n.invoices.size, adjustments = n.adjustments.size,
            orders = n.orders.size, staffPayments = n.staffPayments.size, loyalty = n.loyalty.size, shifts = n.shifts.size,
            parties = newParties, products = newProducts, changed = changedEntries + changedCash + changedSales,
            otherShop = there.businessName?.takeIf { !shopHere.isNullOrBlank() && it.isNotBlank() && norm(it) != norm(shopHere) }
        )
    }

    /**
     * The merge itself — call it inside one transaction ([KhataDao.mergeBook]).
     * Returns what was added.
     */
    suspend fun apply(dao: KhataDao, there: BackupCodec.ParsedBackup, shopHere: String?): Plan {
        val plan = plan(dao, there, shopHere)
        if (plan.none) return plan
        val h = here(dao)
        val n = News(there, h)
        var renumbered = 0

        // ---- Who: customers and suppliers ----
        val partiesHere = partiesHere(dao)
        val wantedParties = partiesWanted(n, there)
        val partyMap = HashMap<Long, Long>()
        // A copy's card QR is kept unless this book already uses it (the same customer renamed here): then a new
        // one, as the token is unique in the book and a clash would stop the whole merge (22 Oct 2026).
        val tokensHere = partiesHere.mapNotNull { it.qrToken }.toMutableSet()
        // The same for the uid (22 Oct 2026, the review): a customer renamed here is not matched by name, and
        // came in a second time under the uid he already has here — two customers, one uid, which the
        // customer page and live sync both read as one.
        val partyUidsHere = partiesHere.mapNotNull { it.uid }.toMutableSet()
        for (p in there.parties) {
            val found = matchParty(p, partiesHere)
            when {
                found != null -> partyMap[p.id] = found.id
                p.id in wantedParties -> {
                    val token = p.qrToken?.takeIf { it !in tokensHere } ?: QrTag.newToken()
                    tokensHere += token
                    val uid = p.uid?.takeIf { it !in partyUidsHere } ?: Uid.next()
                    partyUidsHere += uid
                    partyMap[p.id] = dao.insertParty(p.copy(id = 0, photoPath = null, qrToken = token, uid = uid))
                }
            }
        }

        // ---- What: products (their main product set once all are in), staff ----
        val wantedProducts = productsWanted(n, there)
        val productMap = HashMap<Long, Long>()
        val addedProducts = ArrayList<Pair<Product, Long>>()
        val productUidsHere = dao.allProductsForBackup().mapNotNull { it.uid }.toMutableSet()
        for (p in there.products) {
            if (p.id !in wantedProducts) continue
            val found = dao.productByKey(p.nameKey)
            if (found != null) {
                if (found.isDeleted) dao.restoreProduct(found.id)
                productMap[p.id] = found.id
            } else {
                val uid = p.uid?.takeIf { it !in productUidsHere } ?: Uid.next()
                productUidsHere += uid
                val id = dao.insertProduct(p.copy(id = 0, parentId = null, barcode = p.barcode?.takeIf { dao.productByBarcode(it) == null }, uid = uid))
                productMap[p.id] = id
                addedProducts += p to id
            }
        }
        for ((p, id) in addedProducts) {
            val parent = p.parentId?.let { productMap[it] } ?: continue
            dao.productById(id)?.let { dao.updateProduct(it.copy(parentId = parent)) }
        }
        val staffHere = dao.allStaffForBackup()
        val staffMap = HashMap<Long, Long>()
        val wantedStaff = n.staffPayments.map { it.staffId }.toSet()
        val staffUidsHere = staffHere.mapNotNull { it.uid }.toMutableSet()
        for (s in there.staff) {
            val found = staffHere.firstOrNull { norm(it.name) == norm(s.name) }
            when {
                found != null -> staffMap[s.id] = found.id
                s.id in wantedStaff -> {
                    val uid = s.uid?.takeIf { it !in staffUidsHere } ?: Uid.next()
                    staffUidsHere += uid
                    // On the payroll only: a login comes from this book's owner, never with another book's PIN.
                    staffMap[s.id] = dao.insertStaff(s.copy(id = 0, uid = uid, role = null, pinHash = null, pinSalt = null))
                }
            }
        }

        // ---- Supplier bills and their lines (a batch is a bill line) ----
        val billMap = HashMap<Long, Long>()
        val billItemMap = HashMap<Long, Long>()
        val itemsByBill = there.billItems.groupBy { it.billId }
        for (b in there.bills) {
            val sharedId = b.uid?.let { h.bills[it] } ?: continue
            billMap[b.id] = sharedId
            val mine = dao.billItems(sharedId).sortedBy { it.id }
            val theirs = itemsByBill[b.id].orEmpty().sortedBy { it.id }
            if (mine.size == theirs.size) theirs.zip(mine).forEach { (t, m) -> billItemMap[t.id] = m.id }
            else theirs.forEach { t -> mine.firstOrNull { m -> m.productName == t.productName && m.batchNumber == t.batchNumber }?.let { billItemMap[t.id] = it.id } }
        }
        val newBills = ArrayList<Pair<SupplierBill, Long>>()
        for (b in n.bills) {
            val partyId = partyMap[b.partyId] ?: continue
            val id = dao.insertBill(b.copy(id = 0, partyId = partyId, ledgerEntryId = null))
            billMap[b.id] = id
            newBills += b to id
            for (i in itemsByBill[b.id].orEmpty().sortedBy { it.id }) {
                billItemMap[i.id] = dao.insertBillItem(i.copy(id = 0, billId = id, productId = i.productId?.let { productMap[it] }))
            }
        }

        // ---- Counter tickets (before the khata and cashbook rows that point at them) ----
        val saleMap = HashMap<Long, Long>()
        val saleItemMap = HashMap<Long, Long>()
        val itemsBySale = there.saleItems.groupBy { it.saleId }
        for (s in there.sales) {
            val sharedId = s.uid?.let { h.sales[it] } ?: continue
            saleMap[s.id] = sharedId
            val mine = dao.saleItems(sharedId).associateBy { it.lineNo }
            itemsBySale[s.id].orEmpty().forEach { t -> mine[t.lineNo]?.let { saleItemMap[t.id] = it.id } }
        }
        val newSales = ArrayList<Pair<Sale, Long>>()
        for (s in n.sales.sortedBy { it.id }) {
            var number = s.saleNumber
            var note = s.note
            if (number.isBlank() || dao.saleNumberUsed(number) > 0) {
                note = listOfNotNull(note?.takeIf { it.isNotBlank() }, "↺ ${s.saleNumber}").joinToString(" · ")
                number = SaleNumber.next(dao.highestSaleNumber()); renumbered++
            }
            val id = dao.insertSale(s.copy(id = 0, saleNumber = number, note = note, partyId = s.partyId?.let { partyMap[it] }, ledgerEntryId = null, invoiceId = null))
            saleMap[s.id] = id
            newSales += s to id
            for (i in itemsBySale[s.id].orEmpty().sortedBy { it.lineNo }) {
                saleItemMap[i.id] = dao.insertSaleItem(i.copy(id = 0, saleId = id, productId = i.productId?.let { productMap[it] }, billItemId = i.billItemId?.let { billItemMap[it] }))
            }
        }

        // ---- Khata entries, with their goods lines; then the pairs between them ----
        val entryMap = HashMap<Long, Long>()
        for (e in there.entries) e.uid?.let { h.entries[it] }?.let { entryMap[e.id] = it }
        val newEntries = ArrayList<Pair<LedgerEntry, Long>>()
        for (e in n.entries.sortedBy { it.id }) {
            val partyId = partyMap[e.partyId] ?: continue
            var number = e.entryNumber
            var note = e.note
            if (number.isBlank() || dao.entryNumberUsed(number) > 0) {
                if (number.isNotBlank()) note = listOfNotNull(note?.takeIf { it.isNotBlank() }, "↺ $number").joinToString(" · ")
                number = EntryNumber.next(maxOf(dao.totalEntryCount(), dao.highestEntryNumber())); renumbered++
            }
            val id = dao.insertEntry(e.copy(
                id = 0, partyId = partyId, entryNumber = number, note = note, pairedEntryId = null,
                productId = e.productId?.let { productMap[it] }, billItemId = e.billItemId?.let { billItemMap[it] },
                saleId = e.saleId?.let { saleMap[it] }
            ))
            entryMap[e.id] = id
            newEntries += e to id
        }
        for ((e, id) in newEntries) {
            val paired = e.pairedEntryId?.let { entryMap[it] } ?: continue
            dao.getEntry(id)?.let { dao.updateEntry(it.copy(pairedEntryId = paired)) }
        }
        val newEntryIds = newEntries.associate { (e, id) -> e.id to id }
        val lines = there.entryItems.filter { it.entryId in newEntryIds }.map { i ->
            i.copy(id = 0, entryId = newEntryIds.getValue(i.entryId), productId = i.productId?.let { productMap[it] }, billItemId = i.billItemId?.let { billItemMap[it] })
        }
        if (lines.isNotEmpty()) dao.insertEntryItems(lines)

        // ---- Cashbook ----
        val cashMap = HashMap<Long, Long>()
        for (c in there.cash) c.uid?.let { h.cash[it] }?.let { cashMap[c.id] = it }
        for (c in n.cash.sortedBy { it.id }) cashMap[c.id] = dao.insertCashEntry(c.copy(id = 0, saleId = c.saleId?.let { saleMap[it] }))

        // ---- What pointed forward: a bill's and a ticket's khata row; a ticket's payments ----
        for ((b, id) in newBills) {
            val entry = b.ledgerEntryId?.let { entryMap[it] } ?: continue
            dao.getBill(id)?.let { dao.updateBill(it.copy(ledgerEntryId = entry)) }
        }
        val paymentsBySale = there.salePayments.groupBy { it.saleId }
        for ((s, id) in newSales) {
            val entry = s.ledgerEntryId?.let { entryMap[it] }
            if (entry != null) dao.getSale(id)?.let { dao.updateSale(it.copy(ledgerEntryId = entry)) }
            for (p in paymentsBySale[s.id].orEmpty().sortedBy { it.id }) {
                dao.insertSalePayment(p.copy(id = 0, saleId = id, ledgerEntryId = p.ledgerEntryId?.let { entryMap[it] }, cashEntryId = p.cashEntryId?.let { cashMap[it] }))
            }
        }

        // ---- Cheques ----
        for (c in n.cheques) {
            val partyId = partyMap[c.partyId] ?: continue
            dao.insertCheque(c.copy(id = 0, partyId = partyId, ledgerEntryId = c.ledgerEntryId?.let { entryMap[it] }))
        }

        // ---- Invoices, then the tickets they were made from ----
        val invoiceMap = HashMap<Long, Long>()
        for (i in there.invoices) i.uid?.let { h.invoices[it] }?.let { invoiceMap[i.id] = it }
        val invoiceLines = there.invoiceItems.groupBy { it.invoiceId }
        for (inv in n.invoices.sortedBy { it.id }) {
            var number = inv.invoiceNumber
            if (number.isBlank() || dao.invoiceNumberUsed(number) > 0) {
                number = InvoiceNumber.next(maxOf(dao.totalInvoiceCount(), dao.highestInvoiceNumber())); renumbered++
            }
            val id = dao.insertInvoice(inv.copy(id = 0, invoiceNumber = number, sourceEntryId = inv.sourceEntryId?.let { entryMap[it] }))
            invoiceMap[inv.id] = id
            for (l in invoiceLines[inv.id].orEmpty().sortedBy { it.id }) dao.insertInvoiceItem(l.copy(id = 0, invoiceId = id))
        }
        for ((s, id) in newSales) {
            val invoice = s.invoiceId?.let { invoiceMap[it] } ?: continue
            dao.getSale(id)?.let { dao.updateSale(it.copy(invoiceId = invoice)) }
        }

        // ---- Returns and their lines ----
        val returnLines = there.returnItems.groupBy { it.returnId }
        for (r in n.returns.sortedBy { it.id }) {
            var number = r.returnNumber
            var note = r.note
            if (number.isBlank() || dao.returnNumberUsed(number) > 0) {
                note = listOfNotNull(note?.takeIf { it.isNotBlank() }, "↺ ${r.returnNumber}").joinToString(" · ")
                number = ReturnNumber.next(dao.highestReturnNumber()); renumbered++
            }
            val id = dao.insertReturn(r.copy(
                id = 0, returnNumber = number, note = note, saleId = r.saleId?.let { saleMap[it] }, partyId = r.partyId?.let { partyMap[it] },
                ledgerEntryId = r.ledgerEntryId?.let { entryMap[it] }, refundEntryId = r.refundEntryId?.let { entryMap[it] },
                refundCashId = r.refundCashId?.let { cashMap[it] }
            ))
            val items = returnLines[r.id].orEmpty().map { i ->
                i.copy(id = 0, returnId = id, saleItemId = i.saleItemId?.let { saleItemMap[it] }, productId = i.productId?.let { productMap[it] },
                    billItemId = i.billItemId?.let { billItemMap[it] })
            }
            if (items.isNotEmpty()) dao.insertReturnItems(items)
        }

        // ---- Stock corrections, purchase orders, staff pay, points, shifts ----
        for (a in n.adjustments) {
            val productId = productMap[a.productId] ?: continue
            dao.insertAdjustment(a.copy(id = 0, productId = productId, billItemId = a.billItemId?.let { billItemMap[it] }))
        }
        val orderLines = there.orderItems.groupBy { it.orderId }
        for (o in n.orders.sortedBy { it.id }) {
            val partyId = partyMap[o.partyId] ?: continue
            var number = o.orderNumber
            var note = o.note
            if (number.isBlank() || dao.orderNumberUsed(number) > 0) {
                note = listOfNotNull(note?.takeIf { it.isNotBlank() }, "↺ ${o.orderNumber}").joinToString(" · ")
                number = OrderNumber.next(dao.highestOrderNumber()); renumbered++
            }
            val id = dao.insertOrder(o.copy(id = 0, orderNumber = number, note = note, partyId = partyId, billId = o.billId?.let { billMap[it] }))
            val items = orderLines[o.id].orEmpty().map { it.copy(id = 0, orderId = id, productId = it.productId?.let { p -> productMap[p] }) }
            if (items.isNotEmpty()) dao.insertOrderItems(items)
        }
        for (p in n.staffPayments) {
            val staffId = staffMap[p.staffId] ?: continue
            dao.insertStaffPayment(p.copy(id = 0, staffId = staffId))
        }
        for (l in n.loyalty) {
            val partyId = partyMap[l.partyId] ?: continue
            dao.insertLoyalty(l.copy(id = 0, partyId = partyId, saleId = l.saleId?.let { saleMap[it] }))
        }
        for (s in n.shifts) dao.insertShift(s.copy(id = 0))

        return plan.copy(renumbered = renumbered)
    }

    /** The audit row's detail: what came in. */
    fun detail(done: Plan): String =
        "entries ${done.entries} · cash ${done.cash} · sales ${done.sales} · other ${done.others} · parties ${done.parties} · products ${done.products} · renumbered ${done.renumbered}"
}
