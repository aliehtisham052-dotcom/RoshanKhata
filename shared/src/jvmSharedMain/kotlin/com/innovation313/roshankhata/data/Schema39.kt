package com.innovation313.roshankhata.data

/**
 * What version 39 adds (28 Oct 2026, the payment reference), as the SQL both
 * migrations run — see [Schema32]:
 *
 *  - transactions.paymentRef and cashbook.paymentRef: the reference of a
 *    payment that did not come as cash — a Raast or bank transfer's
 *    transaction id, a JazzCash or Easypaisa TID, a cheque's number.
 *
 * Why: a customer who says "I sent it on Tuesday" can be answered only if
 * the entry that took his money carries the id his wallet or bank gave it.
 * The counter's sale_payments have had `reference` since [Schema32]; the
 * khata's "I got" rows and the cashbook's rows, where most of a shop's
 * payments are written, had nowhere for it. Free text, null where nothing
 * was recorded (every row written before, and every cash payment).
 */
object Schema39 {
    val statements: List<String> = listOf(
        "ALTER TABLE transactions ADD COLUMN paymentRef TEXT",
        "ALTER TABLE cashbook ADD COLUMN paymentRef TEXT"
    )
    val newColumns: Map<String, List<String>> = mapOf(
        "transactions" to listOf("paymentRef"),
        "cashbook" to listOf("paymentRef")
    )
}
