package com.innovation313.roshankhata.data

/**
 * One audit row for a change to what is already written (21 Oct 2026, the
 * critical review): an entry edited or deleted, a cashbook row, a customer
 * deleted, a day closed — with the signed-in name, so the log answers "who".
 * Never fails the change it records (a viewer's book refuses every write,
 * and a log that cannot be written must not undo the owner's work).
 */
suspend fun KhataDao.audit(action: String, refTable: String? = null, refId: Long? = null, detail: String? = null) {
    runCatching { log(action, Access.userName, refTable, refId, detail) }
}

/** "1,250.00" with no currency sign: the log is read in any language. */
fun auditAmount(v: Double): String = String.format(java.util.Locale.ENGLISH, "%,.2f", v)

/**
 * The tables the bin's audit rows name (BIN_RESTORED / BIN_PURGED), and the one word each platform passes to
 * [KhataDao.restoreFromBin] and [KhataDao.purgeFromBin] (17 Nov 2026, BC-222). The strings are the tables' own names,
 * as the rows written before this already say them.
 */
object BinTable {
    const val PARTIES = "parties"
    const val ENTRIES = "transactions"
    const val CASH = "cashbook"
    const val CHEQUES = "cheques"
    const val BILLS = "supplier_bills"
    const val DOCUMENTS = "invoices"
    const val SCHEMES = "schemes"
}

/**
 * A product spreadsheet brought in, as its IMPORT row says it (17 Nov 2026, BC-224): refTable "products", detail
 * "<added> <updated>" in plain digits, so Who did what words it in the reader's language ([parse]). Rows written by the
 * computer before this name no table and carry the sentence in the language of the day; they are shown as written.
 */
object ProductImportNote {
    const val TABLE = "products"

    fun detail(added: Int, updated: Int): String = "$added $updated"

    /** (added, updated), or null for any other text. */
    fun parse(detail: String?): Pair<Int, Int>? {
        val parts = detail?.trim()?.split(' ') ?: return null
        if (parts.size != 2) return null
        val added = parts[0].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val updated = parts[1].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        return added to updated
    }
}
