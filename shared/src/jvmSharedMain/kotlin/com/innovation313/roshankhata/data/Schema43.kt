package com.innovation313.roshankhata.data

/**
 * What version 43 adds (4 Nov 2026, the competitor study's last three points), as the SQL both migrations run —
 * see [Schema32]:
 *
 *  - parties.note: the owner's private note on a customer or supplier ("pays on the 10th", "his brother runs the
 *    shop on Mill Road") — the shop's memory of a person, kept with the book and its backups, never printed on a
 *    statement, a reminder or the customer's card ([PartyNote]).
 *  - cashbook.photoPath: a picture of the receipt or slip behind money in or out (an electricity bill paid, a
 *    deposit slip).
 *  - cheques.photoPath: a picture of the cheque itself — what was written on it, and the bank's memo when it
 *    bounced.
 *
 * The two photo paths follow a bill photo's rules: a file in the app's own bills folder on this device, never
 * travelling in a live sync ([SyncSchema] marks them local), carried by the text backup as a path and by a Drive
 * backup's image archive as a file, and deleted with the last row that points at it. Every row written before has
 * none (null).
 */
object Schema43 {
    val statements: List<String> = listOf(
        "ALTER TABLE parties ADD COLUMN note TEXT",
        "ALTER TABLE cashbook ADD COLUMN photoPath TEXT",
        "ALTER TABLE cheques ADD COLUMN photoPath TEXT"
    )
    val newColumns: Map<String, List<String>> = mapOf(
        "parties" to listOf("note"),
        "cashbook" to listOf("photoPath"),
        "cheques" to listOf("photoPath")
    )
}
