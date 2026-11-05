package com.innovation313.roshankhata.data

/**
 * What version 44 adds (4 Nov 2026, Kitab ki Muhar — the book's seal), as the SQL both migrations run — see [Schema32]:
 *
 *  - book_seals: one row a day while the owner keeps the seal on ([BookSeals]) — the khata entries written since the
 *    seal before (their 32-byte fingerprints in id order), the audit log's newest seal, the digest chained to the seal
 *    before, and the time stamps three independent Time Stamp Authorities gave that digest.
 *
 * No uid and not in [SyncSchema]: each device seals its own view of the book, as each keeps its own audit chain. The
 * backup carries the table (format 19), so a restored book keeps its proofs. Every book written before has none.
 */
object Schema44 {
    const val TABLE = "book_seals"

    val statements: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `book_seals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sealedAt` INTEGER NOT NULL, " +
            "`fromId` INTEGER NOT NULL, `toId` INTEGER NOT NULL, `leafCount` INTEGER NOT NULL, `leaves` BLOB, `auditHead` TEXT, " +
            "`prevDigest` TEXT, `digest` TEXT NOT NULL, `tokens` TEXT)",
        "CREATE INDEX IF NOT EXISTS `index_book_seals_sealedAt` ON `book_seals` (`sealedAt`)"
    )

    val newTables = listOf(TABLE)
    val newIndices = listOf("index_book_seals_sealedAt")
}
