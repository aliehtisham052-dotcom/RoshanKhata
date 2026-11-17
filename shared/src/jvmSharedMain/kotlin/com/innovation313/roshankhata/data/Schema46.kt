package com.innovation313.roshankhata.data

/**
 * What version 46 adds (17 Nov 2026, the audit's L108 and L002), as the SQL both migrations run — see [Schema32]:
 *
 *  - cashbook.createdBy: who wrote a cashbook row, as transactions.createdBy has said for khata entries since 8 Nov
 *    2026 (M44): the signed-in name ([Access.userName]); null for the owner and on every row written before. A
 *    cashier's shift drawer counts the cash he moved by hand in the khata and the cashbook as well as at the counter
 *    ([ShiftMath.byHand]); a row with no name is counted in no shift, as no such row was before.
 *  - sales.heldUid: the uid of the held ticket a ticket was rung up (or parked again) from — the first one it was
 *    parked as, so a ticket parked, resumed and parked again keeps one name. Two devices that each resumed one parked
 *    ticket and rang it up before either heard of the other both posted it; with the name on the sale, [KhataDao.postSale]
 *    and [KhataDao.holdSale] refuse a ticket whose held ticket was already rung up anywhere ([CounterRefused.Why.HELD_GONE]),
 *    and a live sync that brings a second sale of one held ticket keeps it as a conflict for the bell's money row
 *    ([LiveSync]'s counter checks). Null on every sale written before, and on every sale not rung up from a held one.
 *    Indexed: both checks look a held ticket up by it.
 *
 * Both columns travel in a live sync (the column list is read from the table; heldUid is a uid, not this device's row
 * number, so it needs no pointer of its own) and in the backup (format 21). **Live sync needs both devices at 46.**
 */
object Schema46 {
    val statements: List<String> = listOf(
        "ALTER TABLE cashbook ADD COLUMN createdBy TEXT",
        "ALTER TABLE sales ADD COLUMN heldUid TEXT",
        "CREATE INDEX IF NOT EXISTS `index_sales_heldUid` ON `sales` (`heldUid`)"
    )
    val newColumns: Map<String, List<String>> = mapOf(
        "cashbook" to listOf("createdBy"),
        "sales" to listOf("heldUid")
    )
    val newIndices: List<String> = listOf("index_sales_heldUid")
}
