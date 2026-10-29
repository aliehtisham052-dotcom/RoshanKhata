package com.innovation313.roshankhata.data

import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.assertTrue

/**
 * Takes a real database file back to an older schema, for the migration
 * tests: today's tables with exactly what a version added taken out again.
 *
 * Robolectric's SQLite predates ALTER TABLE … DROP COLUMN (3.35), so a
 * column is removed the way every SQLite supports: rebuild the table from
 * its own CREATE statement minus that column, copy the rows across, put the
 * indices back. A table with rows keeps them.
 */
object SchemaRewind {

    fun sqlOf(raw: SQLiteDatabase, type: String, table: String): List<String> =
        raw.rawQuery(
            "SELECT sql FROM sqlite_master WHERE type = ? AND tbl_name = ? AND sql IS NOT NULL",
            arrayOf(type, table)
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    /**
     * Rebuilds [table] without [columns]. Each column's declaration is cut out
     * of the CREATE statement by name, whatever its type or NOT NULL, and the
     * test fails loudly if a name was not found — a rewind that silently left
     * a column in place would make the migration under test "add" a column
     * that already exists and fail for the wrong reason.
     */
    fun stripColumns(raw: SQLiteDatabase, table: String, columns: List<String>) {
        val create = sqlOf(raw, "table", table).single()
        val indices = sqlOf(raw, "index", table)
        var older = create
        for (col in columns) {
            val m = Regex(", `$col` [A-Z]+( NOT NULL)?( DEFAULT [^,)]+)?").find(older)
                ?: error("could not find column $col in: $create")
            older = older.removeRange(m.range)
        }
        val keep = raw.rawQuery("PRAGMA table_info($table)", null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val col = c.getString(c.getColumnIndexOrThrow("name"))
                    if (col !in columns) add("`$col`")
                }
            }
        }.joinToString(", ")
        raw.execSQL("ALTER TABLE $table RENAME TO ${table}_newer")
        raw.execSQL(older)
        raw.execSQL("INSERT INTO $table ($keep) SELECT $keep FROM ${table}_newer")
        raw.execSQL("DROP TABLE ${table}_newer")
        // Indices on the dropped columns are gone with them; the rest come back.
        indices.filter { idx -> columns.none { idx.contains("`$it`") } }.forEach { raw.execSQL(it) }
    }

    /** Undo version 40: the documents' kind, status and source on invoices, and the source's index. */
    fun undo40(raw: SQLiteDatabase) {
        raw.execSQL("PRAGMA foreign_keys = OFF")
        raw.execSQL("PRAGMA legacy_alter_table = ON")
        for (idx in Schema40.newIndices) raw.execSQL("DROP INDEX IF EXISTS `$idx`")
        for ((table, cols) in Schema40.newColumns) stripColumns(raw, table, cols)
        for ((table, cols) in Schema40.newColumns) {
            val now = raw.rawQuery("PRAGMA table_info($table)", null).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
            }
            assertTrue("rewind left ${cols.filter { it in now }} on $table", cols.none { it in now })
        }
    }

    /** Undo version 39: the payment reference on transactions and cashbook. Undoes 40 first. */
    fun undo39(raw: SQLiteDatabase) {
        undo40(raw)
        raw.execSQL("PRAGMA foreign_keys = OFF")
        raw.execSQL("PRAGMA legacy_alter_table = ON")
        for ((table, cols) in Schema39.newColumns) stripColumns(raw, table, cols)
        for ((table, cols) in Schema39.newColumns) {
            val now = raw.rawQuery("PRAGMA table_info($table)", null).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
            }
            assertTrue("rewind left ${cols.filter { it in now }} on $table", cols.none { it in now })
        }
    }

    /**
     * Undo version 38: the uid indices on all 27 tables, and the uid column on
     * the 15 tables that gained it there. Undoes 39 first.
     */
    fun undo38(raw: SQLiteDatabase) {
        undo39(raw)
        raw.execSQL("PRAGMA foreign_keys = OFF")
        raw.execSQL("PRAGMA legacy_alter_table = ON")
        for (idx in Schema38.newIndices) raw.execSQL("DROP INDEX IF EXISTS `$idx`")
        for ((table, cols) in Schema38.newColumns) stripColumns(raw, table, cols)
        for ((table, cols) in Schema38.newColumns) {
            val now = raw.rawQuery("PRAGMA table_info($table)", null).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
            }
            assertTrue("rewind left ${cols.filter { it in now }} on $table", cols.none { it in now })
        }
    }

    /** Undo version 37: the batch on a stock adjustment. Undoes 38 first. */
    fun undo37(raw: SQLiteDatabase) {
        undo38(raw)
        raw.execSQL("PRAGMA foreign_keys = OFF")
        raw.execSQL("PRAGMA legacy_alter_table = ON")
        for ((table, cols) in Schema37.newColumns) stripColumns(raw, table, cols)
    }

    /**
     * Undo version 36: its three tables (book settings, loyalty, shifts), the
     * products' packs/tax/variants, the sale lines' tax, the audit seal and
     * every uid. Undoes 37 first.
     */
    fun undo36(raw: SQLiteDatabase) {
        undo37(raw)
        raw.execSQL("PRAGMA foreign_keys = OFF")
        raw.execSQL("PRAGMA legacy_alter_table = ON")
        for (t in Schema36.newTables) raw.execSQL("DROP TABLE IF EXISTS $t")
        for ((table, cols) in Schema36.newColumns) stripColumns(raw, table, cols)
        for ((table, cols) in Schema36.newColumns) {
            val now = raw.rawQuery("PRAGMA table_info($table)", null).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
            }
            assertTrue("rewind left ${cols.filter { it in now }} on $table", cols.none { it in now })
        }
    }

    /** Undo version 35 (stock adjustments, purchase orders): three tables. Undoes 36 first. */
    fun undo35(raw: SQLiteDatabase) {
        undo36(raw)
        raw.execSQL("PRAGMA foreign_keys = OFF")
        for (t in Schema35.newTables) raw.execSQL("DROP TABLE IF EXISTS $t")
    }

    /** Undo version 34 (staff logins): three columns on staff. Undoes 35 first. */
    fun undo34(raw: SQLiteDatabase) {
        undo35(raw)
        raw.execSQL("PRAGMA foreign_keys = OFF")
        raw.execSQL("PRAGMA legacy_alter_table = ON")
        for ((table, cols) in Schema34.newColumns) stripColumns(raw, table, cols)
    }

    /** Undo version 33 (returns): its two tables. Undoes 34 first. */
    fun undo33(raw: SQLiteDatabase) {
        undo34(raw)
        raw.execSQL("PRAGMA foreign_keys = OFF")
        for (t in Schema33.newTables) raw.execSQL("DROP TABLE IF EXISTS $t")
    }

    /** Undo version 32 (the counter): its four tables and its seven columns. Undoes 33 first. */
    fun undo32(raw: SQLiteDatabase) {
        undo33(raw)
        raw.execSQL("PRAGMA foreign_keys = OFF")
        raw.execSQL("PRAGMA legacy_alter_table = ON")
        for (t in Schema32.newTables) raw.execSQL("DROP TABLE IF EXISTS $t")
        for ((table, cols) in Schema32.newColumns) stripColumns(raw, table, cols)
        for ((table, cols) in Schema32.newColumns) {
            val now = raw.rawQuery("PRAGMA table_info($table)", null).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
            }
            assertTrue("rewind left ${cols.filter { it in now }} on $table", cols.none { it in now })
        }
    }
}
