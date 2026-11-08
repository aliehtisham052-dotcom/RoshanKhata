package com.innovation313.roshankhata.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.Calendar

/**
 * First expiry, first out (21 Oct 2026, the critical review): a counter sale
 * of a product kept in batches takes the batch that runs out soonest, and
 * never one that has run out.
 *
 * What is left of each batch is worked out here, not in SQL: the bill line
 * that brought it, less what was sold from it (khata and walk-in), plus what
 * came back, less what was written off — each in its own unit, a pack turned
 * into the product's own unit ([Units]). A movement in a unit the app cannot
 * convert is left out of that batch's count rather than guessed.
 *
 * This is the one rule for what is left of a batch (Marhala 3, 25 Oct 2026, the
 * audit's "three places count what is left three ways"): the counter, the Expiry
 * page and its write-off, the stock register and its PDF, the bell and Home's
 * chip, and the batch a khata sale is tagged to all read it ([lotsOf]). Until
 * then the register took off only tagged sales in the same unit (a batch written
 * off, returned or sold in packs still showed its stock) and the Expiry list and
 * the bell only khata sales or nothing at all.
 *
 * The rule at the counter ([allocate]): dated batches soonest first, then
 * undated ones; an expired batch is never sold. When the fresh batches cannot
 * cover the line but expired stock is still on the books, the line is refused
 * — the shelf has nothing sellable, and the owner writes the old lot off
 * (Expiry → Write off) or corrects the count. When there are no batches, or
 * they are used up, the rest goes out untagged, as every sale did before.
 */
object Fefo {

    /** One batch's movement in one unit; + out, − back (see KhataDao.batchMovesOfProduct). */
    data class Move(val billItemId: Long, val qty: Double, val unit: String?)

    /**
     * A batch with what is left of it, in [unit]: the product's own unit once a pack is turned into it, else
     * the unit its bill line was written in (a unit the app cannot convert is counted in itself).
     */
    data class Lot(val billItemId: Long, val batchNumber: String?, val expiryDate: Long?, val left: Double, val unit: String? = null) {
        fun expired(today: Long): Boolean = expiryDate != null && expiryDate < today
    }

    /** One bill line as the rule reads it: what came, in what unit, expiring when. */
    data class Batch(val id: Long, val batchNumber: String?, val expiryDate: Long?, val quantity: Double, val unit: String?)

    /** One part of a line: so much from this batch (null = untagged), in the line's own unit. */
    data class Take(val billItemId: Long?, val qty: Double)

    sealed class Allocation {
        data class Ok(val takes: List<Take>) : Allocation()
        /** Only expired stock could cover it: [fresh] is what may still be sold, in the line's unit. */
        data class ExpiredOnly(val fresh: Double) : Allocation()
    }

    /** Midnight today: a batch whose date is before it has expired. */
    fun today(now: Long = System.currentTimeMillis()): Long = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /**
     * [product]'s batches, what is left of each, in the order they are sold from.
     * [shelf] is every product's stock on hand read once ([Counter.onHand]) by a
     * caller that asks for many products (a ticket, the Expiry page — 23 Oct 2026,
     * the review: each call read the whole book's stock again); without it the
     * shelf is read here. Either way the same figure caps the batches.
     */
    suspend fun lots(dao: KhataDao, product: Product, shelf: Map<Long, Double?>? = null): List<Lot> {
        val batches = dao.batchesOfProduct(product.id)
        if (batches.isEmpty()) return emptyList()
        val moves = dao.batchMovesOfProduct(product.id).groupBy { it.billItemId }
        return lotsOf(product, batches.map { Batch(it.id, it.batchNumber, it.expiryDate, it.quantity, it.unit) }, moves,
            if (shelf != null) shelf[product.id] else onHand(dao, product))
    }

    /**
     * The one rule, with nothing read: each of [product]'s [batches] less what moved against it ([moves], by
     * bill line: + out, − back), each in its own unit, a pack turned into the product's own unit ([Units]) —
     * a movement in a unit the batch is not counted in is left out rather than guessed — then soonest expiry
     * first and never more than [onHand] ([capByShelf]). [product] null (a bill line no product is linked to):
     * no packs and no shelf, only the batch's own movements.
     */
    fun lotsOf(product: Product?, batches: List<Batch>, moves: Map<Long, List<Move>>, onHand: Double?): List<Lot> =
        batches.map { b ->
            val (inBase, baseUnit) = Units.toBase(product, b.quantity, b.unit)
            var left = inBase
            for (m in moves[b.id].orEmpty()) {
                val (q, u) = Units.toBase(product, m.qty, m.unit)
                // Only what is counted in the batch's own unit (after packs) is subtracted.
                if (Units.norm(u) == null || Units.norm(baseUnit) == null || Units.norm(u) == Units.norm(baseUnit)) left -= q
            }
            Lot(b.id, b.batchNumber, b.expiryDate, left, baseUnit)
        }.sortedWith(compareBy<Lot, Long?>(nullsLast()) { it.expiryDate }.thenBy { it.billItemId })
            .let { capByShelf(it, onHand) }

    /**
     * Every live batch of the book with what is left of it, by bill line ([lotsOf] for every product at once:
     * four queries and the shelf, never a pair per product). [rows] when the caller has read them already.
     */
    suspend fun allLots(dao: KhataDao, shelf: Map<Long, Double?>? = null, rows: List<InspectorBatch>? = null): Map<Long, Lot> {
        val batches = rows ?: dao.batchRowsOnce()
        if (batches.isEmpty()) return emptyMap()
        val moves = dao.allBatchMoves().groupBy { it.billItemId }
        val products = dao.productsOnce().associateBy { it.id }
        val onHand = shelf ?: runCatching { Counter.onHand(dao) }.getOrNull()
        val out = HashMap<Long, Lot>(batches.size * 2)
        for ((pid, rs) in batches.groupBy { it.productId }) {
            // A binned product's batches still count by its packs, as the counter's own write-off reads them.
            val product = pid?.let { products[it] ?: dao.productById(it) }
            val cap = if (product != null) onHand?.get(product.id) else null
            lotsOf(product, rs.map { Batch(it.itemId, it.batchNumber, it.expiryDate, it.quantity, it.unit) }, moves, cap)
                .forEach { out[it.billItemId] = it }
        }
        return out
    }

    /**
     * The stock register's batches: every batch that still holds something, in the register's order, each a
     * copy whose [InspectorBatch.quantity] is what is LEFT of it in its [InspectorBatch.unit] (so
     * [InspectorBatch.remaining] reads the one rule; nothing sold is left to take off).
     */
    suspend fun register(dao: KhataDao, shelf: Map<Long, Double?>? = null): List<InspectorBatch> {
        val rows = dao.batchRowsOnce()
        val lots = allLots(dao, shelf, rows)
        return rows.mapNotNull { b ->
            lots[b.itemId]?.takeIf { it.left > EPS }?.let { l -> b.copy(quantity = l.left, unit = l.unit ?: b.unit, soldFromBatch = 0.0) }
        }
    }

    /**
     * The batches dated on or before [cutoff] that still hold something, soonest first, each a copy whose
     * [ExpiringBatch.quantity] is what is LEFT of it in its [ExpiringBatch.unit]: the Expiry page, the bell
     * and the badges. A batch sold out, sent back or written off is not on it.
     */
    suspend fun expiring(dao: KhataDao, cutoff: Long, shelf: Map<Long, Double?>? = null): List<ExpiringBatch> {
        val dated = dao.expiringBatchesOnce(cutoff)
        if (dated.isEmpty()) return emptyList()
        val lots = allLots(dao, shelf)
        return dated.mapNotNull { b ->
            lots[b.itemId]?.takeIf { it.left > EPS }?.let { l -> b.copy(quantity = l.left, unit = l.unit ?: b.unit) }
        }
    }

    /** How many batches [expiring] holds, again whenever stock moves ([KhataDao.observeBatchMoves]): the badges. */
    fun observeExpiringCount(dao: KhataDao, cutoff: Long): Flow<Int> =
        dao.observeBatchMoves().map {
            try {
                expiring(dao, cutoff).size
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                0
            }
        }.distinctUntilChanged()

    /**
     * [product]'s batches for a khata sale to be tagged to, in the order they are sold from, each with what is
     * left of it ([BatchOption.remaining], in [BatchOption.unit]) by the one rule — what the counter would say.
     */
    suspend fun options(dao: KhataDao, product: Product, shelf: Map<Long, Double?>? = null): List<BatchOption> =
        lots(dao, product, shelf).map { l -> BatchOption(l.billItemId, l.batchNumber, l.expiryDate, l.left, l.unit, 0.0) }

    /**
     * What is left of one batch in the unit its bill line was written in — a return to the supplier is written
     * in it, at the bill's rate — or null when the line is gone. A pack line's left is turned back into packs.
     */
    suspend fun leftInBillUnit(dao: KhataDao, billItemId: Long): Double? {
        val item = dao.getBillItem(billItemId) ?: return null
        val product = item.productId?.let { dao.productById(it) }
        val lot = (if (product != null) lots(dao, product).firstOrNull { it.billItemId == billItemId } else allLots(dao)[billItemId])
            ?: return null
        return lot.left / Units.factor(product, item.unit)
    }

    /** What the shelf holds of [product] by the books (every movement, tagged or not); null when it cannot be said. */
    private suspend fun onHand(dao: KhataDao, product: Product): Double? =
        runCatching { Stock.of(dao).firstOrNull { it.productId == product.id }?.onHand }.getOrNull()

    /**
     * The batches never hold more than the shelf (21 Oct 2026, the critical
     * review). A khata sale written without a batch takes stock but no batch,
     * so the batches alone said more was left than there was: the counter
     * "sold" from a lot that was gone, and writing an expired lot off took the
     * stock below zero. What the batches hold beyond the shelf is taken from
     * them soonest-expiry first — the order the shop sells in.
     */
    fun capByShelf(lots: List<Lot>, onHand: Double?): List<Lot> {
        if (onHand == null) return lots
        var excess = lots.sumOf { it.left.coerceAtLeast(0.0) } - onHand.coerceAtLeast(0.0)
        if (excess <= EPS) return lots
        return lots.map { l ->
            if (excess <= EPS || l.left <= EPS) l
            else {
                val cut = minOf(excess, l.left)
                excess -= cut
                l.copy(left = l.left - cut)
            }
        }
    }

    /**
     * Splits [qty] (in a unit worth [factor] of the product's own) over [lots].
     * See the class note for the rule.
     */
    fun allocate(lots: List<Lot>, qty: Double, factor: Double, today: Long): Allocation {
        if (lots.isEmpty()) return Allocation.Ok(listOf(Take(null, qty)))
        val need = qty * factor
        var rest = need
        val takes = ArrayList<Take>()
        var freshLeft = 0.0
        for (l in lots) {
            if (l.left <= EPS || l.expired(today)) continue
            freshLeft += l.left
            if (rest <= EPS) continue
            val t = minOf(rest, l.left)
            takes += Take(l.billItemId, t / factor)
            rest -= t
        }
        if (rest > EPS) {
            val expiredLeft = lots.filter { it.expired(today) && it.left > EPS }.sumOf { it.left }
            if (expiredLeft > EPS) return Allocation.ExpiredOnly(freshLeft / factor)
            takes += Take(null, rest / factor)
        }
        return Allocation.Ok(takes)
    }

    /**
     * [line] written as one line per batch it came from. The parts' quantities
     * add up to the line's, and their money — total, discount and [tax] — to
     * the paisa, so the ticket the customer paid is exactly the ticket kept.
     * When it cannot be split that exactly (a fractional quantity at an odd
     * rate), it is kept whole, tagged to the first batch: the money stays
     * right and the batch count is out by that line, which is the lesser harm.
     */
    fun split(line: SaleItem, takes: List<Take>, tax: Double?): List<SaleItem> {
        val whole = listOf(line.copy(billItemId = takes.firstOrNull()?.billItemId ?: line.billItemId, taxAmount = tax))
        if (takes.size <= 1) return whole
        val qty = line.quantity
        val parts = takes.dropLast(1).map { Math.round(it.qty * 1000.0) / 1000.0 }
        val lastQty = Math.round((qty - parts.sum()) * 1000.0) / 1000.0
        val qtys = parts + lastQty
        if (qtys.any { it <= 0.0 }) return whole
        val total = line.lineTotal
        val totals = qtys.dropLast(1).map { LineMath.round(total * it / qty) }.let { it + LineMath.round(total - it.sum()) }
        val taxes = tax?.let { t -> qtys.dropLast(1).map { LineMath.round(t * it / qty) }.let { it + LineMath.round(t - it.sum()) } }
        val out = ArrayList<SaleItem>()
        for (i in qtys.indices) {
            val discount = LineMath.round(qtys[i] * line.rate - totals[i])
            if (discount < 0.0) return whole
            val part = line.copy(quantity = qtys[i], discount = discount, billItemId = takes[i].billItemId, taxAmount = taxes?.get(i))
            if (Math.abs(part.lineTotal - totals[i]) > 0.0001) return whole
            out += part
        }
        return out
    }

    /** One batch (a supplier bill line) with its product and what is left of it; null when the line is no product's. */
    suspend fun lotOf(dao: KhataDao, billItemId: Long, shelf: Map<Long, Double?>? = null): Pair<Product, Lot>? {
        val item = dao.getBillItem(billItemId) ?: return null
        val product = item.productId?.let { dao.productById(it) } ?: return null
        val lot = lots(dao, product, shelf).firstOrNull { it.billItemId == billItemId } ?: return null
        return product to lot
    }

    /**
     * What is left of a batch, written off (an expired lot thrown out): one
     * adjustment of that batch with the reason expired, in the product's own
     * unit, logged as every adjustment is. Returns how much went, or null
     * when the batch is not a product's.
     */
    suspend fun writeOff(dao: KhataDao, billItemId: Long, note: String?, user: String? = Access.userName, allowedBy: String? = null): Double? {
        val (product, lot) = lotOf(dao, billItemId) ?: return null
        if (lot.left <= EPS) return 0.0
        // In the unit the batch is counted in (Marhala 3): a lot billed in a unit the product's own cannot be
        // turned into is written off in that unit, so its own count reaches nothing, never another unit's figure.
        dao.adjustStock(product.id, -lot.left, AdjustReason.EXPIRED, note, user, billItemId = billItemId, unit = lot.unit ?: product.defaultUnit, allowedBy = allowedBy)
        return lot.left
    }

    private const val EPS = 1e-9
}
