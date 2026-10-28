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
