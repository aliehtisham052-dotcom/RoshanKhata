package com.innovation313.roshankhata.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.innovation313.roshankhata.data.EntryItem
import com.innovation313.roshankhata.data.EntryLines
import com.innovation313.roshankhata.data.LineDraft
import com.innovation313.roshankhata.data.Product
import com.innovation313.roshankhata.data.RateOffer
import com.innovation313.roshankhata.ui.Calc

/**
 * The goods on the computer's entry form (Marhala 4, part 6: the phone's list on dialog_add_entry, which the
 * computer did not have — one line, the phone's others only shown): items added one by one, an item pulled
 * back into the boxes to correct and put back in its own place, removed, and re-priced when Credit / Cash is
 * switched. The phone's rules (LineDraft, EntryLines, RateOffer), held apart from the screen so they are tested.
 */
class GoodsList(initial: List<LineDraft> = emptyList()) {
    val lines = mutableStateListOf<LineDraft>().apply { addAll(initial) }

    /** Where the item in the boxes came from, so it goes back to the same place rather than to the end. */
    var editingIndex by mutableStateOf<Int?>(null)
        private set

    /**
     * The item being corrected, as it was: what the boxes do not show stays with it — the dealer's spray
     * advice (crop, pest, dose), the product while the name is the same, the batch it was drawn from. (The
     * phone dropped the advice on a pull-back; the audit's C3 was the same loss on the computer.)
     */
    var pulled by mutableStateOf<LineDraft?>(null)
        private set

    /** [d] into the list, where the pulled item was or at the end; the boxes are free again. */
    fun add(d: LineDraft) {
        val at = editingIndex
        if (at != null && at <= lines.size) lines.add(at, d) else lines += d
        editingIndex = null
        pulled = null
    }

    /** Item [i] out of the list into the boxes; null when there is none. */
    fun pullBack(i: Int): LineDraft? {
        if (i !in lines.indices) return null
        val d = lines.removeAt(i)
        editingIndex = i
        pulled = d
        return d
    }

    fun remove(i: Int) {
        if (i !in lines.indices) return
        lines.removeAt(i)
        editingIndex = editingIndex?.let { if (i < it) it - 1 else it }
    }

    /** The item pulled back goes back to its place unchanged (the owner left the correction); nothing when none was. */
    fun putBack() {
        pulled?.let { add(it) }
    }

    /** Every item at the other rate type: only a rate that came from the product moves (LineDraft.repriced). */
    fun reprice(rateType: String, isCustomer: Boolean) {
        for (i in lines.indices) lines[i] = lines[i].repriced(rateType, isCustomer)
    }

    /** The list with the boxes' item in its place, as Save writes it. */
    fun all(editor: LineDraft?): List<LineDraft> {
        val all = lines.toMutableList()
        val at = editingIndex
        if (editor != null) {
            if (at != null && at <= all.size) all.add(at, editor) else all.add(editor)
        }
        return all
    }

    companion object {
        /**
         * A saved entry's lines on the form: every rate the owner's own (agreed at the sale; a chip switch
         * while editing must not move it to today's price), the free mark and the advice kept.
         */
        fun of(items: List<EntryItem>): GoodsList = GoodsList(items.map { draftOf(it) })

        fun draftOf(it: EntryItem): LineDraft = LineDraft(
            itemName = it.itemName, quantity = it.quantity, unit = it.unit, rate = it.rate,
            productId = it.productId, billItemId = it.billItemId, rateEdited = true, isBonus = it.isBonus,
            crop = it.crop, pest = it.pest, dose = it.dose
        )
    }
}

/**
 * The boxes' item as a line ([GoodsList.all], Save), or null when nothing about goods is in them. What the
 * boxes do not show comes from the item pulled back ([was]): its advice always, its product and batch while
 * the name is the same. An old free item comes back a plain one with no rate (the phone retired Free, 30 Sep).
 */
fun boxesLine(
    name: String, quantity: Double?, unit: String?, rate: Double?, rateTyped: Boolean,
    product: Product?, batchId: Long?, was: LineDraft?
): LineDraft? {
    val n = name.trim().ifEmpty { null }
    if (n == null && quantity == null && product == null && batchId == null) return null
    val same = was != null && was.itemName?.trim() == n
    return LineDraft(
        itemName = n, quantity = quantity, unit = unit, rate = rate,
        productId = product?.id ?: was?.productId?.takeIf { same },
        billItemId = batchId ?: was?.billItemId?.takeIf { same },
        creditPrice = product?.creditPrice, cashPrice = product?.salePrice, productUnit = product?.defaultUnit,
        rateEdited = rateTyped,
        crop = was?.crop, pest = was?.pest, dose = was?.dose
    )
}

/**
 * What the amount box does as the items change (the phone's refreshTotal): it follows the items' total while
 * the owner has not written a figure of his own over goods ([own]); a figure the items wrote ([byItems]) goes
 * when the last item goes; a figure typed before any goods is replaced by the first total (it cannot be a
 * discount on items not yet written). Returns the text to put in the box, or null to leave it.
 */
fun followedAmount(all: List<LineDraft>, written: String, own: Boolean, byItems: Boolean): String? {
    if (all.isEmpty()) return if (byItems && written.isNotEmpty()) "" else null
    val total = EntryLines.total(all) ?: return null
    // A total of 0 (a rate of 0) is not an amount: the box would read empty and be written again, for ever.
    if (own || total <= 0.0) return null
    val figure = Calc.evalAmount(written)
    return if (figure == null || EntryLines.compare(total, figure) !is EntryLines.AmountCheck.Equal) Calc.trim(total) else null
}

/**
 * The rate the boxes' item is offered when the owner has not typed one (the phone's refreshRateSuggestion):
 * a sale takes the product's price for the chosen rate type (none when it is not set, or per another unit);
 * a customer's return takes the price he was last charged, when it was per the same unit — never a guess.
 */
fun offeredRate(
    isSale: Boolean, isReturn: Boolean, product: Product?, rateType: String, typedUnit: String,
    lastAny: com.innovation313.roshankhata.data.LastRate?
): Double? = when {
    isSale && product != null -> (RateOffer.price(true, product.creditPrice, product.salePrice, product.defaultUnit, rateType, typedUnit) as? RateOffer.Result.Offer)?.rate
    isReturn -> lastAny?.takeIf { typedUnit.isEmpty() || it.unit == null || it.unit.equals(typedUnit, ignoreCase = true) }?.rate
    else -> null
}
