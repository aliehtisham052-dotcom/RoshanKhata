package com.innovation313.roshankhata.data

import java.util.Locale
import kotlin.random.Random

/**
 * A book the size of a wholesaler's, written into a book so the app can be measured against it
 * (P0 of the scaling work, 7 Oct 2026, on the phone; shared on 3 Nov 2026, the speed work, so the
 * computer is measured against the very same book).
 *
 * The owner's own book is about 1,200 customers. A ledger that is quick at that size tells nothing about
 * one at ten thousand, and "it will never slow down" is a claim that has to be MEASURED before it is
 * made. So: [seed] writes [CUSTOMERS] customers with [ENTRIES_EACH] lines each — 300,000 lines, the first
 * customer [HEAVY_ENTRIES] — and, since 3 Nov 2026, what a real shop's book also holds, because a book of
 * bare amounts measured the khata and nothing else (Sale Insights, stock, the counter and the bell read
 * goods): [PRODUCTS] products, goods on a third of the customers' sales, two bills from every supplier
 * with batches and expiry days, [CASH_ROWS] cashbook rows and [TICKETS] walk-in counter tickets. Every
 * table is written by the clock, as a shop writes it: the 7 Oct seed wrote a customer's lines side by side,
 * and a read by customer then found them together on disk — on a shop's real book they lie among everyone
 * else's, and the same read took twice as long (measured on 3 Nov 2026).
 *
 * Every seeded row can be found again from [MARK] — in a customer's or a product's name, in a cashbook
 * row's or a ticket's note — or through a row that carries it (a customer's lines and their goods, a
 * supplier's bills and their lines, a ticket's lines and payments), so [clear] takes the whole seed out
 * and leaves every row of the owner's own exactly as it was. Nothing is seeded into a book that syncs:
 * three hundred thousand made-up rows would go to the shop's other devices.
 *
 * Names are built from the parts a Pakistani shop's book is made of — a given name, often a trade or a
 * word of address — so the search folds, the ranks and the duplicates behave the way they do on real
 * names rather than on "User 4817". No place or person of the owner's is among them (the repository is
 * public).
 */
object BigBookSeed {
    const val CUSTOMERS = 10_000
    const val ENTRIES_EACH = 30
    /** The first seeded customer's lines: one account with years of dealings, the customer screen's hard case. */
    const val HEAVY_ENTRIES = 5_000
    const val PRODUCTS = 300
    const val CASH_ROWS = 20_000
    const val TICKETS = 20_000
    /** A thin space and a tag the owner never types. */
    const val MARK = " (bb)"

    private const val DAY = 24L * 60 * 60 * 1000
    /** Ids are given from this far above the highest in use, so a row the app writes meanwhile never meets one. */
    private const val ID_GAP = 1_000L
    /** A line's place among all the lines, in the low bits of its sort key under the moment it was written. */
    private const val LINE_BITS = 20
    private const val LINE_LIMIT = 1 shl LINE_BITS

    private val FIRST = listOf(
        "Muhammad", "Ali", "Asghar", "Bilal", "Tousif", "Yaseen", "Abbas", "Amir", "Amin", "Aslam",
        "Nazeer", "Shahid", "Zahid", "Naeem", "Rana", "Chaudhry", "Malik", "Mian", "Imran", "Kamran",
        "Salim", "Khalid", "Tariq", "Umar", "Usman", "Waqas", "Faisal", "Saleem", "Riaz", "Ijaz"
    )
    private val SECOND = listOf(
        "", "Bhatti", "Butt", "Cheema", "Gujjar", "Main Bazar", "Spray Wala", "Thallay Wala", "Chak 12",
        "Dokandar", "Loader", "Manager", "College", "Nehar Wala", "Mistri", "Ustad", "Sahib", "Bhai", "Khan", "Kiryana"
    )

    /** What a shop of any trade sells, with the unit it is counted in (a [UnitWords] key). */
    private val KINDS = listOf(
        "Urea" to "bag", "DAP" to "bag", "Wheat Seed" to "bag", "Spray" to "bottle", "Cooking Oil" to "litre",
        "Sugar" to "kg", "Rice" to "kg", "Flour" to "bag", "Soap" to "piece", "Tea" to "packet",
        "Cattle Feed" to "bag", "Paint" to "litre", "Wire" to "metre", "Tablet" to "box", "Syrup" to "bottle"
    )

    private fun name(i: Int, rnd: Random): String {
        val first = FIRST[rnd.nextInt(FIRST.size)]
        val second = SECOND[rnd.nextInt(SECOND.size)]
        val tail = if (second.isEmpty()) "" else " $second"
        return "$first$tail $i$MARK"
    }

    private class Item(val id: Long, val name: String, val unit: String, val price: Double, val cost: Double?)

    /**
     * Writes the big book into [db]. Returns how many customers it added. Safe to call twice: it clears
     * its own earlier seed first. Refuses a book that syncs (see the class).
     */
    suspend fun seed(
        db: KhataDatabase,
        customers: Int = CUSTOMERS,
        entriesEach: Int = ENTRIES_EACH,
        progress: ((done: Int) -> Unit)? = null
    ): Int {
        val sql = db.syncSql()
        check(!LiveSync.state(sql).on) { "this book syncs with other devices; the big book is not written into it" }
        clear(db)
        val dao = db.khataDao()
        val now = System.currentTimeMillis()

        // Goods first, from their own random numbers, so the khata below is the same book it was before goods came.
        val goods = Random(3131)
        val items = (0 until PRODUCTS).map { i ->
            val (kind, unit) = KINDS[i % KINDS.size]
            val name = "$kind ${i / KINDS.size + 1}$MARK"
            val price = (goods.nextInt(2, 100) * 50).toDouble()
            val cost = if (goods.nextInt(3) != 0) LineMath.round(price * 0.85) else null
            val id = dao.insertProduct(
                Product(
                    name = name, nameKey = ProductName.key(name), normalisedName = ProductName.normalised(name),
                    defaultUnit = unit, salePrice = price, costPrice = cost,
                    lowStockLevel = if (goods.nextInt(3) == 0) 10.0 else null
                )
            )
            Item(id, name, unit, price, cost)
        }

        // The customers, and when each of their lines was written, drawn in the 7 Oct seed's order (the same
        // khata); then the lines are written in the order a shop writes them — by the clock, each customer's among
        // everyone else's (3 Nov 2026). Written customer by customer, one customer's lines sat side by side in the
        // file, and every read by customer looked quicker than a real shop's book lets it be.
        val rnd = Random(313)
        val lineCount = if (customers <= 0) 0 else HEAVY_ENTRIES + (customers - 1) * entriesEach
        require(lineCount < LINE_LIMIT) { "the big book holds fewer than $LINE_LIMIT lines" }
        val partyIds = LongArray(customers)
        val customerAt = BooleanArray(customers)
        val whoOf = IntArray(lineCount)
        val whenOf = LongArray(lineCount)
        val fiftiesOf = IntArray(lineCount)
        val givenOf = BooleanArray(lineCount)
        val notedOf = BooleanArray(lineCount)
        var lines = 0
        var done = 0
        val batch = 500
        var start = 0
        val suppliers = ArrayList<Long>()
        while (start < customers) {
            val end = minOf(start + batch, customers)
            val parties = (start until end).map { i ->
                val name = name(i, rnd)
                val phone = "03%02d%07d".format(Locale.ROOT, rnd.nextInt(0, 50), rnd.nextInt(0, 10_000_000))
                val isCustomer = rnd.nextInt(10) != 0
                val createdAt = now - rnd.nextLong(0, 900 * DAY)
                Party(
                    name = name, phone = phone, isCustomer = isCustomer, createdAt = createdAt,
                    // One customer in twenty named a day to pay, some already passed.
                    harvestPromise = if (isCustomer && goods.nextInt(20) == 0) now + goods.nextLong(-20 * DAY, 20 * DAY) else null
                )
            }
            val ids = dao.insertParties(parties)
            for ((k, id) in ids.withIndex()) {
                val i = start + k
                partyIds[i] = id
                customerAt[i] = parties[k].isCustomer
                if (!parties[k].isCustomer) suppliers += id
                var t = now - rnd.nextLong(0, 700 * DAY)
                val count = if (i == 0) HEAVY_ENTRIES else entriesEach
                repeat(count) {
                    t += if (count > entriesEach) rnd.nextLong(1, 4 * 60 * 60 * 1000) else rnd.nextLong(DAY / 4, 20 * DAY)
                    fiftiesOf[lines] = rnd.nextInt(1, 400)
                    givenOf[lines] = rnd.nextInt(5) != 0
                    notedOf[lines] = rnd.nextInt(4) == 0
                    whoOf[lines] = i
                    whenOf[lines] = minOf(t, now)
                    lines++
                }
            }
            done = end
            progress?.invoke(done)
            start = end
        }
        // By the clock, two lines of one moment in the order they were drawn: the moment above, the line below.
        val byClock = LongArray(lines) { (whenOf[it] shl LINE_BITS) or it.toLong() }
        byClock.sort()
        var nextEntry = highest(sql, "transactions") + ID_GAP
        var at = 0
        while (at < lines) {
            val until = minOf(at + 5_000, lines)
            val entries = ArrayList<LedgerEntry>(until - at)
            val goodsLines = ArrayList<EntryItem>()
            while (at < until) {
                val e = (byClock[at] and (LINE_LIMIT - 1).toLong()).toInt()
                val entryId = nextEntry++
                var amount = fiftiesOf[e] * 50.0
                // A third of a customer's sales carry their goods, as the khata writes them.
                if (customerAt[whoOf[e]] && givenOf[e] && goods.nextInt(3) == 0) {
                    var sum = 0.0
                    repeat(1 + goods.nextInt(2)) { n ->
                        val p = items[goods.nextInt(items.size)]
                        val qty = (1 + goods.nextInt(10)).toDouble()
                        sum += qty * p.price
                        goodsLines += EntryItem(entryId = entryId, lineNo = n, itemName = p.name, quantity = qty, unit = p.unit, rate = p.price, productId = p.id)
                    }
                    amount = LineMath.round(sum)
                }
                at++
                entries += LedgerEntry(
                    id = entryId, partyId = partyIds[whoOf[e]], amount = amount, isGiven = givenOf[e],
                    note = if (notedOf[e]) "Urea 2 bori" else null,
                    entryNumber = "BB-%06d".format(Locale.ROOT, at), timestamp = whenOf[e]
                )
            }
            dao.restoreEntries(entries)
            if (goodsLines.isNotEmpty()) dao.restoreEntryItems(goodsLines)
        }

        // Two bills from every supplier, by their day, with batches: some expired, some within the warning
        // window, most later.
        var nextBill = highest(sql, "supplier_bills") + ID_GAP
        val billDays = suppliers.flatMap { s -> List(2) { s to now - goods.nextLong(0, 700 * DAY) } }.sortedBy { it.second }
        val bills = ArrayList<SupplierBill>()
        val billLines = ArrayList<BillItem>()
        var lot = 0
        for ((s, day) in billDays) {
            val billId = nextBill++
            var total = 0.0
            repeat(1 + goods.nextInt(4)) {
                val p = items[goods.nextInt(items.size)]
                val qty = (20 + goods.nextInt(181)).toDouble()
                val rate = LineMath.round(p.price * 0.8)
                total += qty * rate
                lot++
                billLines += BillItem(
                    billId = billId, productName = p.name, batchNumber = "BB-L%05d".format(Locale.ROOT, lot),
                    expiryDate = if (goods.nextBoolean()) now + goods.nextLong(-30 * DAY, 400 * DAY) else null,
                    quantity = qty, unit = p.unit, rate = rate, productId = p.id
                )
            }
            bills += SupplierBill(id = billId, partyId = s, totalAmount = LineMath.round(total), billDate = day, createdAt = day)
        }
        for (part in bills.chunked(1_000)) dao.restoreBills(part)
        for (part in billLines.chunked(1_000)) dao.restoreBillItems(part)

        // Walk-in tickets over the counter, each paid in cash into the cashbook as the counter writes it, and the
        // cashbook's own rows (the shop's expenses, money in that was not a ticket): the cashbook by the clock too.
        val ticketAt = LongArray(TICKETS) { now - goods.nextLong(0, 700 * DAY) }.also { it.sort() }
        val otherAt = LongArray(CASH_ROWS) { now - goods.nextLong(0, 700 * DAY) }.also { it.sort() }
        var nextCash = highest(sql, "cashbook") + ID_GAP
        val ticketCash = LongArray(TICKETS)
        val otherCash = LongArray(CASH_ROWS)
        run {
            var a = 0
            var b = 0
            while (a < TICKETS || b < CASH_ROWS) {
                if (b >= CASH_ROWS || (a < TICKETS && ticketAt[a] <= otherAt[b])) ticketCash[a++] = nextCash++
                else otherCash[b++] = nextCash++
            }
        }
        var nextSale = highest(sql, "sales") + ID_GAP
        var ticket = 0
        while (ticket < TICKETS) {
            val sales = ArrayList<Sale>()
            val saleLines = ArrayList<SaleItem>()
            val payments = ArrayList<SalePayment>()
            val cash = ArrayList<CashEntry>()
            repeat(minOf(1_000, TICKETS - ticket)) {
                val saleId = nextSale++
                val cashId = ticketCash[ticket]
                val moment = ticketAt[ticket]
                ticket++
                var sum = 0.0
                repeat(1 + goods.nextInt(3)) { n ->
                    val p = items[goods.nextInt(items.size)]
                    val qty = (1 + goods.nextInt(5)).toDouble()
                    sum += qty * p.price
                    saleLines += SaleItem(saleId = saleId, lineNo = n, productId = p.id, itemName = p.name, quantity = qty, unit = p.unit, rate = p.price, costRate = p.cost)
                }
                val total = LineMath.round(sum)
                val number = "BB-S-%06d".format(Locale.ROOT, ticket)
                sales += Sale(
                    id = saleId, saleNumber = number, timestamp = moment, subtotal = total, grandTotal = total, paidTotal = total,
                    status = SaleStatus.DONE, note = "Test$MARK"
                )
                payments += SalePayment(saleId = saleId, method = PaymentMethod.CASH, amount = total, cashEntryId = cashId)
                cash += CashEntry(
                    id = cashId, amount = total, isIncome = true, category = CashCategory.SALE, note = "$number$MARK",
                    timestamp = moment, paymentMethod = PaymentMethod.CASH, saleId = saleId
                )
            }
            dao.restoreSales(sales)
            dao.restoreSaleItems(saleLines)
            dao.restoreSalePayments(payments)
            dao.restoreCash(cash)
        }
        val outWords = listOf("Rent", "Electricity", "Wages", "Fuel", "Tea")
        for (part in (0 until CASH_ROWS).chunked(1_000)) {
            dao.restoreCash(part.map { j ->
                val income = goods.nextInt(10) < 3
                CashEntry(
                    id = otherCash[j], amount = (goods.nextInt(1, 200) * 50).toDouble(), isIncome = income,
                    category = if (income) "Other" else outWords[goods.nextInt(outWords.size)], note = "Test$MARK",
                    timestamp = otherAt[j], paymentMethod = PaymentMethod.CASH
                )
            })
        }
        return done
    }

    private suspend fun highest(sql: SyncSql, table: String): Long =
        sql.read { rows("SELECT COALESCE(MAX(id), 0) FROM `$table`", types = "I").first()[0] as Long? ?: 0L }

    /**
     * Takes every seeded row back out, children first, in one transaction; then the customers and their
     * lines as before. The owner's own book is untouched: every statement names the mark, or a seeded row.
     */
    suspend fun clear(db: KhataDatabase) {
        val mark = "%$MARK"
        val seededParties = "SELECT id FROM parties WHERE name LIKE ?"
        val seededProducts = "SELECT id FROM products WHERE name LIKE ?"
        val seededSales = "SELECT id FROM sales WHERE note LIKE ?"
        db.syncSql().write {
            exec("DELETE FROM entry_items WHERE entryId IN (SELECT id FROM transactions WHERE partyId IN ($seededParties))", listOf(mark))
            exec("DELETE FROM bill_items WHERE billId IN (SELECT id FROM supplier_bills WHERE partyId IN ($seededParties))", listOf(mark))
            exec("DELETE FROM supplier_bills WHERE partyId IN ($seededParties)", listOf(mark))
            exec("DELETE FROM sale_payments WHERE saleId IN ($seededSales)", listOf(mark))
            exec("DELETE FROM sale_items WHERE saleId IN ($seededSales)", listOf(mark))
            exec("DELETE FROM cashbook WHERE note LIKE ?", listOf(mark))
            exec("DELETE FROM sales WHERE note LIKE ?", listOf(mark))
            // What the app may have written against a seeded product or supplier while the big book was in.
            exec("DELETE FROM stock_adjustments WHERE productId IN ($seededProducts)", listOf(mark))
            exec("DELETE FROM purchase_order_items WHERE orderId IN (SELECT id FROM purchase_orders WHERE partyId IN ($seededParties))", listOf(mark))
            exec("DELETE FROM purchase_orders WHERE partyId IN ($seededParties)", listOf(mark))
            exec("DELETE FROM products WHERE name LIKE ?", listOf(mark))
        }
        db.khataDao().purgeSeeded(mark)
    }
}
