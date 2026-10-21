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

    /** Undo version 33 (returns): its two tables. */
    fun undo33(raw: SQLiteDatabase) {
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
