package com.innovation313.roshankhata.data

/**
 * What a backup's big tables point at, and nothing else (17 Nov 2026, the audit's L001): the ids and pointers of its
 * entries, goods lines, counter tickets and their lines and payments, as numbers. A restore that reads a big file a
 * page at a time ([BackupCodec.readPages]) cannot hold the rows to check them; it keeps these instead, and the list
 * path feeds the very same keys from its lists ([BackupCodec]'s parseRoot), so the two can never judge a file apart.
 *
 * Two rules live here: [orphans] — a row that points at something the file does not carry is a hole, and a file with
 * holes is refused — and [pairKept] — a cash-sale pairing whose other half is not in the file is cleared, not counted
 * as a hole (3 Nov 2026 audit, C1). Plain Kotlin on purpose: the shared harness runs its test.
 */
class BigKeys {
    private var entryIds = LongArray(1024)
    private var entryCount = 0
    private var sorted = true
    private val saleIds = HashSet<Long>()

    // How many rows point at each id: a hole is counted once per row, as the lists counted it.
    private val entryParty = HashMap<Long, Int>()
    private val entryProduct = HashMap<Long, Int>()
    private val entryBillItem = HashMap<Long, Int>()
    private val itemEntry = HashMap<Long, Int>()
    private val itemProduct = HashMap<Long, Int>()
    private val itemBillItem = HashMap<Long, Int>()
    private val lineSale = HashMap<Long, Int>()

    /** How many entries were taken. */
    val entries: Int get() = entryCount

    private fun bump(m: HashMap<Long, Int>, id: Long) { m[id] = (m[id] ?: 0) + 1 }

    /** One khata entry: its id, its party and the product and bill line it may name. */
    fun entry(id: Long, partyId: Long, productId: Long?, billItemId: Long?) {
        if (entryCount == entryIds.size) entryIds = entryIds.copyOf(entryIds.size * 2)
        if (entryCount > 0 && id < entryIds[entryCount - 1]) sorted = false
        entryIds[entryCount++] = id
        bump(entryParty, partyId)
        productId?.let { bump(entryProduct, it) }
        billItemId?.let { bump(entryBillItem, it) }
    }

    /** One goods line: the entry it belongs to and the product and bill line it may name. */
    fun entryItem(entryId: Long, productId: Long?, billItemId: Long?) {
        bump(itemEntry, entryId)
        productId?.let { bump(itemProduct, it) }
        billItemId?.let { bump(itemBillItem, it) }
    }

    /** One counter ticket. */
    fun sale(id: Long) { saleIds += id }

    /** One line or payment of a ticket, by the ticket it belongs to. */
    fun saleLine(saleId: Long) = bump(lineSale, saleId)

    /** Whether the file carries the entry [id]. */
    fun hasEntry(id: Long): Boolean {
        if (!sorted) {
            java.util.Arrays.sort(entryIds, 0, entryCount)
            sorted = true
        }
        return java.util.Arrays.binarySearch(entryIds, 0, entryCount, id) >= 0
    }

    /** A cash-sale pairing as the restore writes it: kept when its other half is in the file, else cleared (C1). */
    fun pairKept(paired: Long?): Long? = paired?.takeIf { hasEntry(it) }

    /**
     * The rows of the big tables that point at nothing the file carries: an entry's party (always named), product or
     * bill line; a goods line's entry, product or bill line; a ticket's line or payment's ticket. [partyIds],
     * [productIds] and [billItemIds] are the file's own (its parties, products and bill lines).
     */
    fun orphans(partyIds: Set<Long>, productIds: Set<Long>, billItemIds: Set<Long>): Int {
        fun missing(m: Map<Long, Int>, has: (Long) -> Boolean) = m.entries.sumOf { (id, n) -> if (has(id)) 0 else n }
        return missing(entryParty) { it in partyIds } +
            missing(entryProduct) { it in productIds } +
            missing(entryBillItem) { it in billItemIds } +
            missing(itemEntry) { hasEntry(it) } +
            missing(itemProduct) { it in productIds } +
            missing(itemBillItem) { it in billItemIds } +
            missing(lineSale) { it in saleIds }
    }
}
