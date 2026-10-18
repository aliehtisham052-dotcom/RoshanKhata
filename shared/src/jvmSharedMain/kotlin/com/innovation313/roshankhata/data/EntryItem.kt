package com.innovation313.roshankhata.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * One line of goods on a ledger entry — "2 bori Urea at 4,800".
 *
 * From database version 20 this table is THE ONLY place the app records what
 * goods moved on an entry. The five goods columns still sitting on
 * `transactions` (itemName, quantity, unit, productId, billItemId) are legacy:
 * migration 19→20 copied every one of them into a line here, and from then on
 * nothing writes them and nothing reads them. Two places holding the same fact
 * drift apart the first time only one is updated; one place cannot.
 * LegacyGoodsWritesTest fails the build if new code writes them again.
 *
 * THE LEDGER IS STILL THE MONEY. A line records goods and the rate they went
 * at; it never decides a balance. The balance is `transactions.amount` and
 * nothing else — an owner may write 19,000 against lines that add up to
 * 19,200 (a discount, a round-off), and the book must hold what he wrote. So
 * no query anywhere may SUM an entry's amount while joined to its lines: a
 * three-line entry would be counted three times, and the wrong total would
 * look entirely believable. EntryItemsDaoTest asserts it.
 *
 * The foreign key cascades. Every way an entry is erased for good — emptying
 * the Recycle Bin, the 30-day purge, deleting one entry forever, the wipe
 * before a restore, a customer deleted for good — takes its lines with it, so
 * a line can never be left pointing at nothing, and can never attach itself
 * to some later entry. (A new table can carry a real foreign key without
 * rebuilding anything; `transactions` itself is not touched.)
 *
 * A soft delete (Recycle Bin) does NOT touch lines: they stay, hidden by every
 * query's `t.isDeleted = 0`, and come back with their entry on restore.
 */
@Entity(
    tableName = "entry_items",
    foreignKeys = [
        ForeignKey(
            entity = LedgerEntry::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("entryId"), Index("productId"), Index("billItemId")]
)
data class EntryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    /**
     * Where this line sits on its entry, from 0. The order printed on a
     * receipt is the order the owner typed — never whatever order SQLite
     * happens to return rows in.
     */
    val lineNo: Int = 0,
    /** As the owner typed it. Never rewritten by any backfill. */
    val itemName: String? = null,
    val quantity: Double? = null,
    /** Free text: bori, bag, litre, kg… Stock only adds like to like. */
    val unit: String? = null,
    /**
     * The rate this line actually went at, per [unit]. Fixed at the moment of
     * the sale and never recalculated: a product's price changing next month
     * must not change what this customer was charged. Null on every line
     * copied from before rates were recorded — never derived as amount ÷
     * quantity, because an old amount may have covered more than these goods.
     */
    val rate: Double? = null,
    /** A free bag from a company scheme: leaves the shelf, costs nothing. */
    val isBonus: Boolean = false,
    val productId: Long? = null,
    val billItemId: Long? = null,

    /**
     * The dealer's advice with this line (v25): the crop, the pest or
     * disease, and the dose as he told it ("250 ml per acre, morning").
     * Free text, all optional; printed on the receipt so the farmer has it in
     * writing — and the shop has a record if a crop is lost later.
     */
    val crop: String? = null,
    val pest: String? = null,
    val dose: String? = null
) {
    companion object {
        /**
         * The one rule for turning an entry's legacy goods columns into a line.
         *
         * Used by restore when an older backup file (format 6 or below, which
         * has no lines) is brought back. Migration 19→20 applies the SAME rule
         * in SQL — the WHERE clause there is this predicate, word for word —
         * so a book migrated on the phone and the same book restored from an
         * old file end up with exactly the same lines.
         *
         * A line exists when anything about goods was recorded: a name, a
         * quantity, a product link or a batch link. A bare money entry gets
         * none.
         */
        fun fromLegacy(entry: LedgerEntry): EntryItem? =
            ofGoods(
                itemName = entry.itemName,
                quantity = entry.quantity,
                unit = entry.unit,
                productId = entry.productId,
                billItemId = entry.billItemId
            )?.copy(entryId = entry.id)

        /**
         * A line from what the add-entry form holds, or null when nothing
         * about goods was filled in. The same predicate as [fromLegacy] — one
         * definition of "this entry moved goods" for the form, the migration
         * and an old backup alike. entryId is left 0: the DAO sets it from
         * the id the insert actually returns.
         */
        fun ofGoods(
            itemName: String?,
            quantity: Double?,
            unit: String?,
            productId: Long?,
            billItemId: Long?,
            rate: Double? = null
        ): EntryItem? {
            val hasGoods = !itemName.isNullOrBlank() ||
                quantity != null ||
                productId != null ||
                billItemId != null
            if (!hasGoods) return null
            return EntryItem(
                entryId = 0,
                lineNo = 0,
                itemName = itemName,
                quantity = quantity,
                unit = unit,
                rate = rate,
                isBonus = false,
                productId = productId,
                billItemId = billItemId
            )
        }
    }
}

/**
 * An entry with its lines, for the screens that show both.
 *
 * Room fills [items] with a second query inside one transaction, and a Flow of
 * this re-emits when EITHER table changes — so an edited line repaints its row
 * just as an edited amount does.
 */
data class EntryWithItems(
    @Embedded val entry: LedgerEntry,
    @Relation(parentColumn = "id", entityColumn = "entryId")
    val items: List<EntryItem>
) {
    /**
     * Room does not promise an order for a relation; the receipt needs one.
     * A function, not a property, so Room never mistakes it for a column.
     */
    fun orderedItems(): List<EntryItem> = items.sortedWith(compareBy({ it.lineNo }, { it.id }))
}

/** A customer's last price for a product, and the unit it was per. */
data class LastRate(val rate: Double, val unit: String?)

/** Which of a product's two prices an entry was charged at. */
object RateType {
    const val CREDIT = "CREDIT"
    const val CASH = "CASH"
}

/**
 * Money arithmetic for goods lines, in ONE place.
 *
 * A Double cannot hold most decimal rates exactly: 3 × 1,950.10 comes out as
 * 5850.299999999999. Written into the amount box or summed across lines, that
 * residue becomes a figure the owner never typed. Everything here multiplies
 * in BigDecimal and rounds to the paisa, half-up, the way a person rounds.
 */
object LineMath {

    fun round(value: Double): Double =
        BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()

    /**
     * What one line comes to. Zero for a bonus line, whatever its rate. Null
     * when the quantity or rate is missing — unknown is not zero.
     */
    fun lineTotal(quantity: Double?, rate: Double?, isBonus: Boolean = false): Double? {
        if (isBonus) return 0.0
        if (quantity == null || rate == null) return null
        return BigDecimal.valueOf(quantity)
            .multiply(BigDecimal.valueOf(rate))
            .setScale(2, RoundingMode.HALF_UP)
            .toDouble()
    }

    /** The lines added up, or null if any line's total is unknown. */
    fun linesTotal(items: List<EntryItem>): Double? {
        var sum = BigDecimal.ZERO
        for (item in items) {
            val t = lineTotal(item.quantity, item.rate, item.isBonus) ?: return null
            sum = sum.add(BigDecimal.valueOf(t))
        }
        return sum.setScale(2, RoundingMode.HALF_UP).toDouble()
    }
}
