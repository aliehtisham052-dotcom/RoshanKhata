package com.innovation313.roshankhata.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migrations.
 *
 * Until now the database was built with fallbackToDestructiveMigration(), which
 * means every version bump WIPED the user's data and started over. That was
 * tolerable while the app existed only on the developer's own phone. It is not
 * tolerable for a shopkeeper whose entire book of debts lives here: an app
 * update must never be the thing that destroys their records.
 *
 * So each step from version 1 onwards is written out explicitly. Adding a
 * column or a table does not touch a single existing row — the ledger a
 * shopkeeper has been keeping for a year survives the upgrade untouched.
 *
 * These must be kept in step with the entities. If a future change alters a
 * column rather than adding one, it needs a migration of its own, and the
 * entity change alone is not enough.
 */

/**
 * v1 -> v2: Zakat calculator.
 *
 * Two columns on the ledger: whether an entry is Qarz-e-Hasna, and how likely
 * the debt is to be recovered. Both get the same defaults the entity declares,
 * so existing entries read back exactly as they did before.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE transactions ADD COLUMN isQarzeHasna INTEGER NOT NULL DEFAULT 0"
        )
        db.execSQL(
            "ALTER TABLE transactions ADD COLUMN recovery INTEGER NOT NULL DEFAULT ${Recovery.CERTAIN}"
        )
    }
}

/**
 * v2 -> v3: goods on an entry (item, quantity, unit).
 *
 * All nullable — an entry recorded before this release simply has no goods
 * attached, which is the truth about it and not a gap to be filled in.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN itemName TEXT")
        db.execSQL("ALTER TABLE transactions ADD COLUMN quantity REAL")
        db.execSQL("ALTER TABLE transactions ADD COLUMN unit TEXT")
    }
}

/**
 * v3 -> v4: cheque register.
 *
 * A new table. The index on partyId is created explicitly — Room checks for it
 * on open, and a missing index makes it declare the schema corrupt even though
 * every row is intact.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cheques (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                partyId INTEGER NOT NULL,
                amount REAL NOT NULL,
                isReceived INTEGER NOT NULL,
                chequeNumber TEXT,
                bankName TEXT,
                dueDate INTEGER NOT NULL,
                status INTEGER NOT NULL,
                settledAt INTEGER,
                ledgerEntryId INTEGER,
                note TEXT,
                createdAt INTEGER NOT NULL,
                isDeleted INTEGER NOT NULL,
                deletedAt INTEGER,
                FOREIGN KEY(partyId) REFERENCES parties(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cheques_partyId ON cheques(partyId)")
    }
}

/**
 * v4 -> v5: per-party credit limit.
 *
 * Nullable, and it must stay that way. A default limit would mean warning the
 * owner against a threshold they never chose.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE parties ADD COLUMN creditLimit REAL")
    }
}

/**
 * v5 -> v6: cashbook.
 *
 * Deliberately has no partyId and no foreign key — cashbook entries belong to
 * no customer, which is the whole point of keeping them in a separate table.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cashbook (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                amount REAL NOT NULL,
                isIncome INTEGER NOT NULL,
                category TEXT NOT NULL,
                note TEXT,
                timestamp INTEGER NOT NULL,
                isDeleted INTEGER NOT NULL,
                deletedAt INTEGER
            )
            """.trimIndent()
        )
    }
}

/**
 * v6 -> v7: payment plans and their instalments.
 *
 * Two tables. Instalments cascade from their plan, and plans cascade from their
 * party — delete a customer and the arrangement goes with them, which is right,
 * because the arrangement was only ever about that customer.
 *
 * Note what these tables do NOT contain: any money owed. The debt lives in the
 * ledger. A plan is a promise about how it will be cleared, and nothing here
 * changes anybody's balance.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS payment_plans (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                partyId INTEGER NOT NULL,
                totalAmount REAL NOT NULL,
                installmentAmount REAL,
                note TEXT,
                nextDueDate INTEGER,
                createdAt INTEGER NOT NULL,
                isClosed INTEGER NOT NULL,
                closedAt INTEGER,
                isDeleted INTEGER NOT NULL,
                deletedAt INTEGER,
                FOREIGN KEY(partyId) REFERENCES parties(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_payment_plans_partyId ON payment_plans(partyId)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS installments (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                planId INTEGER NOT NULL,
                amount REAL NOT NULL,
                ledgerEntryId INTEGER NOT NULL,
                paidAt INTEGER NOT NULL,
                note TEXT,
                isDeleted INTEGER NOT NULL,
                FOREIGN KEY(planId) REFERENCES payment_plans(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_installments_planId ON installments(planId)"
        )
    }
}

/**
 * v7 -> v8: supplier bills, with batch numbers and expiry dates.
 *
 * Two tables. Items cascade from their bill, bills cascade from their supplier.
 *
 * Note what these tables do NOT contain: a balance. What is owed to a supplier
 * lives in the ledger, like every other debt in this app. A bill holds the
 * paperwork — bill number, batches, expiry, what was in the delivery — and
 * points at the ledger entry for the money. If it kept its own running total,
 * the two would drift and there would be two different answers to "what do I
 * owe them?", with no way to tell which was true.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS supplier_bills (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                partyId INTEGER NOT NULL,
                billNumber TEXT,
                totalAmount REAL NOT NULL,
                billDate INTEGER NOT NULL,
                dueDate INTEGER,
                ledgerEntryId INTEGER,
                isPaidInFull INTEGER NOT NULL,
                note TEXT,
                createdAt INTEGER NOT NULL,
                isDeleted INTEGER NOT NULL,
                deletedAt INTEGER,
                FOREIGN KEY(partyId) REFERENCES parties(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_supplier_bills_partyId ON supplier_bills(partyId)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS bill_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                billId INTEGER NOT NULL,
                productName TEXT NOT NULL,
                batchNumber TEXT,
                expiryDate INTEGER,
                quantity REAL NOT NULL,
                unit TEXT,
                rate REAL,
                note TEXT,
                isDeleted INTEGER NOT NULL,
                FOREIGN KEY(billId) REFERENCES supplier_bills(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bill_items_billId ON bill_items(billId)")
    }
}

/** Every migration, in order. Register all of them or Room will not find the path. */
val ALL_MIGRATIONS = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8
)
