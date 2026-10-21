package com.innovation313.roshankhata.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.NameSearch

/**
 * The ticket being rung up at the counter (POS Phase 1) — the one state
 * both counters draw, the phone's and the computer's. Everything the screen
 * types lands here as text; the figures are read through [Calc] the way
 * the phone's amount pad reads them (sums, Urdu digits), and the totals are
 * [SaleMath]'s, so what is shown is what [KhataDao.postSale] will write.
 *
 * Compose state, so a screen recomposes as the ticket changes; no Android,
 * no Swing — the same class on both.
 */
class CounterDraft {

    class Line(
        val productId: Long?,
        name: String,
        qty: String,
        unit: String?,
        rate: String,
        discount: String = "",
        val billItemId: Long? = null,
        /** What the shelf holds, where the book knows; null = unknown. In the product's own unit. */
        val onHand: Double? = null,
        /** The product rung up (schema 36): its pack and its tax. Null for a line typed free. */
        val product: Product? = null,
        /** The line's tax rate in percent, from its product; null = none of its own. */
        val taxRate: Double? = null,
        /** Whether the line's price already holds its tax (the book's setting when it was rung up). */
        val taxIncluded: Boolean = false
    ) {
        var name by mutableStateOf(name)
        var qty by mutableStateOf(qty)
        var unit by mutableStateOf(unit)
        var rate by mutableStateOf(rate)
        var discount by mutableStateOf(discount)

        val qtyValue: Double get() = Calc.evalAmount(qty) ?: 0.0
        val rateValue: Double get() = Calc.evalAmount(rate) ?: 0.0
        val discountValue: Double get() = Calc.evalAmount(discount) ?: 0.0
        val total: Double get() = SaleMath.lineTotal(qtyValue, rateValue, discountValue)

        /** The quantity in the product's own unit (a carton of 12 is 12). */
        val baseQty: Double get() = qtyValue * Units.factor(product, unit)

        /** More than the shelf holds, where the shelf is known (in the product's own unit). */
        val shortBy: Double? get() = onHand?.let { if (baseQty > it + 1e-9) baseQty - it else null }

        /** The product is also sold in a pack: the line can switch between its own unit and the pack. */
        val canSwitchUnit: Boolean get() = Units.hasPack(product)

        /** The other unit, for the switch's label. */
        val otherUnit: String? get() = product?.let { p -> if (Units.isPack(p, unit)) p.defaultUnit else p.packUnit }

        /**
         * Between the product's own unit and its pack (schema 36). The rate
         * follows: the pack's price (or the unit rate × the pack size), or
         * back to one unit's. The quantity stays as typed.
         */
        fun switchUnit(unitRate: Double) {
            val p = product ?: return
            if (!Units.hasPack(p)) return
            if (Units.isPack(p, unit)) {
                unit = p.defaultUnit
                rate = Calc.trim(unitRate)
            } else {
                unit = p.packUnit
                rate = Calc.trim(Units.rateFor(p, p.packUnit, unitRate))
            }
        }

        fun toItem() = SaleItem(
            saleId = 0, productId = productId, itemName = name.trim(), quantity = qtyValue,
            unit = unit, rate = rateValue, discount = discountValue, billItemId = billItemId,
            taxRate = taxRate?.takeIf { it > 0.0 }, taxIncluded = if ((taxRate ?: 0.0) > 0.0) taxIncluded else null
        )
    }

    class Payment(method: String, amount: String, reference: String = "") {
        var method by mutableStateOf(method)
        var amount by mutableStateOf(amount)
        var reference by mutableStateOf(reference)
        val amountValue: Double get() = Calc.evalAmount(amount) ?: 0.0
        fun toPayment() = SalePayment(
            saleId = 0, method = method, amount = amountValue, reference = reference.trim().ifBlank { null }
        )
    }

    var partyId by mutableStateOf<Long?>(null)
    var partyName by mutableStateOf<String?>(null)
    val lines = mutableStateListOf<Line>()
    var discount by mutableStateOf("")
    var taxPercent by mutableStateOf("")
    var note by mutableStateOf("")
    val payments = mutableStateListOf<Payment>()
    var roundToWhole by mutableStateOf(false)
    /** The held ticket this draft came off, to drop once it is rung up. */
    var heldId by mutableStateOf<Long?>(null)
    /** The book's setting (schema 36): products' prices already hold their tax. Read by the screen when it opens. */
    var pricesIncludeTax by mutableStateOf(false)

    val hasParty: Boolean get() = partyId != null
    val isEmpty: Boolean get() = lines.isEmpty() && payments.isEmpty() && !hasParty

    val totals: SaleMath.Totals
        get() = SaleMath.totals(
            lines.map { it.toItem() }, Calc.evalAmount(discount) ?: 0.0,
            Calc.evalAmount(taxPercent), roundToWhole
        )

    val paid: Double get() = SaleMath.paid(payments.map { it.toPayment() })

    /** What is still owed on the ticket after the payments typed so far. */
    val remaining: Double get() = LineMath.round(totals.grandTotal - paid)

    /** The first reason the ticket cannot be posted as it stands, or null. */
    val problem: SaleMath.Problem?
        get() = SaleMath.check(lines.map { it.toItem() }, payments.map { it.toPayment() }, totals.grandTotal, hasParty)

    /**
     * The customer's account after this ticket, where it would pass the
     * credit limit the owner set (POS Phase 9): the limit and the balance it
     * would reach, or null when there is no limit or it is kept.
     */
    fun creditOver(balanceNow: Double?, creditLimit: Double?): Pair<Double, Double>? {
        if (!hasParty || creditLimit == null || balanceNow == null) return null
        val after = LineMath.round(balanceNow + remaining)
        return if (after > creditLimit + 0.005) creditLimit to after else null
    }

    /** The ticket's lines that would sell more than the shelf holds. */
    val short: List<Line> get() = lines.filter { it.shortBy != null }

    fun setParty(id: Long?, name: String?) {
        partyId = id
        partyName = name
    }

    /**
     * A product rung up. The same product again adds one to its line rather
     * than opening a second — a scanner beeps once per pack. The rate is the
     * product's counter price (cash) or its credit price when the ticket is
     * on an account and one is set; the owner may change it on the line.
     */
    fun add(product: Product, onHand: Double? = null, qty: Double = 1.0): Line {
        lines.firstOrNull { it.productId == product.id }?.let {
            it.qty = Calc.trim(it.qtyValue + qty)
            return it
        }
        val rate = unitRate(product)
        val line = Line(
            product.id, product.name, Calc.trim(qty), product.defaultUnit, if (rate == 0.0) "" else Calc.trim(rate), onHand = onHand,
            product = product, taxRate = product.taxRate, taxIncluded = pricesIncludeTax
        )
        lines += line
        return line
    }

    /** One of [product]'s own unit at this ticket's price: the counter price, or the credit price on an account. */
    fun unitRate(product: Product): Double =
        (if (hasParty) product.creditPrice ?: product.salePrice else product.salePrice ?: product.creditPrice) ?: 0.0

    /** A line typed free, for a thing not in the products list. */
    fun addFree(name: String, unit: String? = null): Line {
        val line = Line(null, name.trim(), "1", unit, "")
        lines += line
        return line
    }

    fun remove(line: Line) { lines.remove(line) }

    fun addPayment(method: String = PaymentMethod.CASH, amount: Double? = null): Payment {
        val p = Payment(method, amount?.let { Calc.trim(it) } ?: "")
        payments += p
        return p
    }

    /** One cash payment for whatever is still owed. */
    fun payRestInCash() {
        val rest = remaining
        if (rest <= 0.0) return
        payments.firstOrNull { it.method == PaymentMethod.CASH && it.amountValue == 0.0 }
            ?.let { it.amount = Calc.trim(rest); return }
        addPayment(PaymentMethod.CASH, rest)
    }

    fun removePayment(p: Payment) { payments.remove(p) }

    fun clear() {
        partyId = null; partyName = null
        lines.clear(); payments.clear()
        discount = ""; taxPercent = ""; note = ""; heldId = null
    }

    /** A held ticket back on the counter. */
    fun load(held: SaleWithLines, onHandOf: (Long) -> Double? = { null }, productOf: (Long) -> Product? = { null }) {
        clear()
        heldId = held.sale.id
        partyId = held.sale.partyId
        partyName = held.sale.customerName
        note = held.sale.note.orEmpty()
        if (held.sale.discountAmount > 0.0) discount = Calc.trim(held.sale.discountAmount)
        held.sale.taxPercent?.let { taxPercent = Calc.trim(it) }
        for (i in held.items) {
            lines += Line(
                i.productId, i.itemName, Calc.trim(i.quantity), i.unit, Calc.trim(i.rate),
                if (i.discount > 0.0) Calc.trim(i.discount) else "", i.billItemId,
                onHand = i.productId?.let(onHandOf), product = i.productId?.let(productOf),
                taxRate = i.taxRate, taxIncluded = i.taxIncluded == true
            )
        }
    }

    /** The document [KhataDao.postSale] is given; its figures are recomputed there. */
    fun toSale(createdBy: String? = Access.userName, timestamp: Long = System.currentTimeMillis()) = Sale(
        partyId = partyId, customerName = partyName?.trim()?.ifBlank { null },
        timestamp = timestamp, subtotal = totals.subtotal, discountAmount = Calc.evalAmount(discount) ?: 0.0,
        taxPercent = Calc.evalAmount(taxPercent)?.takeIf { it > 0.0 }, taxAmount = totals.tax,
        roundOff = totals.roundOff, grandTotal = totals.grandTotal, paidTotal = paid,
        note = note.trim().ifBlank { null }, createdBy = createdBy
    )

    fun toItems(): List<SaleItem> = lines.map { it.toItem() }
    fun toPayments(): List<SalePayment> = payments.map { it.toPayment() }.filter { it.amount > 0.0 }

    companion object {
        /**
         * The products that answer a counter search: an exact barcode first,
         * then names and companies by the app's one search rule. At most
         * [limit], for a list that fits beside the ticket.
         */
        fun search(products: List<Product>, query: String, limit: Int = 12): List<Product> {
            val q = query.trim()
            if (q.isEmpty()) return products.take(limit)
            products.firstOrNull { it.barcode != null && it.barcode == q }?.let { return listOf(it) }
            val typed = q.lowercase()
            return products.filter {
                NameSearch.matches(it.name, null, typed) || (it.company?.let { c -> NameSearch.matches(c, null, typed) } ?: false)
            }.take(limit)
        }
    }
}

/**
 * The counter's database work, shared by both screens: what a ticket needs
 * read before it is rung up, and the one call that rings it up.
 */
object Counter {

    /** Every live product's stock on hand, by id (null where the units disagree). */
    suspend fun onHand(dao: KhataDao): Map<Long, Double?> =
        Stock.of(dao).associate { it.productId to it.onHand }

    /**
     * The cost a line's profit is measured against, per one of the line's
     * [unit]: the product's own cost, else its weighted average purchase
     * cost, else its last purchase rate ([Cost]) — times the pack size for a pack.
     */
    suspend fun costOf(dao: KhataDao, product: Product?, unit: String? = product?.defaultUnit): Double? =
        Cost.perUnit(dao, product)?.let { LineMath.round(it * Units.factor(product, unit)) }

    /** A line the shelf cannot sell: only expired stock is left of [item] ([fresh] may still go, in its unit). */
    class ExpiredStock(val item: String, val fresh: Double) : IllegalArgumentException("sale refused: expired stock of $item")

    /**
     * Ring the draft up: cost rates filled in per line, each product line's
     * batches chosen first-expiry-first-out ([Fefo]) and an expired batch
     * never sold, the sale posted in one transaction, the held ticket it came
     * off dropped. Returns the sale's id. Throws [ExpiredStock] for a line
     * only expired stock could fill, and IllegalArgumentException with the
     * problem's name if the ticket fails its checks — the screen should have
     * shown [CounterDraft.problem] before offering the button.
     */
    suspend fun post(dao: KhataDao, draft: CounterDraft, createdBy: String? = null, now: Long = System.currentTimeMillis()): Long {
        val products = dao.productsOnce().associateBy { it.id }
        val today = Fefo.today(now)
        val items = ArrayList<SaleItem>()
        val takes = ArrayList<List<Fefo.Take>?>()
        for (item in draft.toItems()) {
            val p = item.productId?.let { products[it] }
            items += item.copy(costRate = p?.let { costOf(dao, it, item.unit) })
            if (p == null) { takes += null; continue }
            val lots = Fefo.lots(dao, p)
            if (item.billItemId != null) {
                // A batch chosen already (a held ticket): never an expired one.
                if (lots.firstOrNull { it.billItemId == item.billItemId }?.expired(today) == true) throw ExpiredStock(item.itemName, 0.0)
                takes += null
                continue
            }
            when (val a = Fefo.allocate(lots, item.quantity, Units.factor(p, item.unit), today)) {
                is Fefo.Allocation.ExpiredOnly -> throw ExpiredStock(item.itemName, a.fresh)
                is Fefo.Allocation.Ok -> takes += a.takes
            }
        }
        val id = dao.postSale(draft.toSale(createdBy), items, draft.toPayments(), draft.roundToWhole, takes)
        draft.heldId?.let { dao.dropHeldSale(it) }
        return id
    }

    /** Park the draft; returns the held row's id. */
    suspend fun hold(dao: KhataDao, draft: CounterDraft, createdBy: String? = null): Long {
        val id = dao.holdSale(draft.toSale(createdBy), draft.toItems())
        draft.heldId?.let { dao.dropHeldSale(it) }
        return id
    }
}
