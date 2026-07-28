package com.innovation313.roshankhata.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.innovation313.roshankhata.ui.NameSearch

/**
 * A product the shop actually deals in.
 *
 * WHY THIS TABLE EXISTS. Until now a product was a piece of free text written
 * twice: [BillItem.productName] on the way in, [LedgerEntry.itemName] on the
 * way out, with nothing joining them. So "urea", "Urea" and "Urea 50kg" were
 * three unrelated strings, and three questions an agri dealer must be able to
 * answer had no answer at all:
 *
 *  - how much of this is left, bought minus sold
 *  - who bought this last season, so they can be told it is in again
 *  - which customers received a batch, when that batch turns out to be bad
 *
 * The last one is not convenience. A licensed pesticide dealer records batch
 * numbers when stock arrives; if the trail stops at the counter, a recall
 * cannot reach the farmers who bought it.
 *
 * NOTHING IS TAKEN AWAY. The free text stays exactly where it is and keeps
 * working; [LedgerEntry.productId] and [BillItem.productId] are nullable and
 * every row written before today keeps a null. This table is an anchor that
 * text can be tied to, not a replacement for it, and an owner who never opens
 * a product screen loses nothing.
 */
@Entity(
    tableName = "products",
    indices = [
        Index(value = ["nameKey"], unique = true),
        Index(value = ["normalisedName"])
    ]
)
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** As the dealer writes it, and what they will always be shown. */
    val name: String,

    /**
     * The name reduced to the one thing that is unarguably the same name:
     * itself, trimmed, in lower case. UNIQUE, so "Urea" and "urea " cannot
     * become two products.
     *
     * See [ProductName.key].
     */
    val nameKey: String,

    /**
     * The name run through the app's own spelling-folding. Indexed, and
     * deliberately NOT unique.
     *
     * The fold is built for Pakistani names and treats g and j, v and b, q and
     * k as the same sound. That is right for finding a customer and wrong for
     * refusing a product: two genuinely different brands that happen to fold
     * together would make the second one impossible to create, and the owner
     * would be told their real product does not exist. So this column only
     * ever SUGGESTS — "did you mean Urea?" — and never blocks.
     */
    val normalisedName: String,

    /**
     * Pesticide, seed, fertiliser, feed — the dealer's own words, not a fixed
     * list. Left free text on purpose: seasons will be hung off this later,
     * and a list decided here would be a list that does not fit their shop.
     */
    val category: String? = null,

    /** Bottle, litre, kg, bag, packet — what this is normally counted in. */
    val defaultUnit: String? = null,

    val note: String? = null,

    val createdAt: Long = System.currentTimeMillis(),

    /**
     * Soft delete, like everything else here. A product is never removed from
     * the table, because ledger entries and bill items point at it by id and a
     * hard delete would leave them pointing at nothing.
     */
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null
)

/**
 * The two keys a product name is looked up by, in one place.
 *
 * [key] is what uniqueness rests on. [normalised] is what similarity rests on,
 * and it deliberately delegates to [NameSearch.fold] rather than growing a
 * second folder of its own — two definitions of "the same name" in one app is
 * how a search box and a matcher end up disagreeing with each other.
 */
object ProductName {

    /** Exact identity: trimmed, lower case, inner spacing evened out. */
    fun key(name: String): String =
        name.trim().lowercase().replace(Regex("\\s+"), " ")

    /** Similarity only. Never used to refuse a product. */
    fun normalised(name: String): String =
        key(name).split(' ').joinToString("") { NameSearch.fold(it) }
}
