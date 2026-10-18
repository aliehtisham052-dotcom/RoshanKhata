package com.innovation313.roshankhata.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase

/** A key's word in the app's language, for [TradeVocab]. */
fun UnitWords.label(context: Context, key: String): String = label(key, DateWords.appLocale())

/**
 * The list the owner picks from, the active trade's own units first (9 Oct
 * 2026): a dairy sees litre, kg, packet at the top and bag, maund, seer
 * below, where before every shop was handed a pesticide dealer's order.
 * Every unit is still offered — a trade's list is an order, not a fence —
 * and what is saved is the same key as ever.
 */
fun UnitWords.choices(context: Context): List<String> {
    val locale = DateWords.appLocale()
    val lead = TradeVocab.unitKeys(context)
    return (lead + KEYS.filter { it !in lead }).map { label(it, locale) }
}

/**
 * Turn units that were typed by hand as one of the app's own labels into
 * their key, once per opening of a book. One UPDATE per table, touching
 * only rows whose unit is exactly such a label; a key, an empty unit and
 * the owner's own words are not rows it matches. Each table on its own
 * and never fatal: a book must open whether or not this ran.
 *
 * English keys are deliberately not in the list: "bag" is already right,
 * and "Bag" typed by hand is left as the owner wrote it (the app has
 * always compared units without regard to letter case).
 */
fun UnitWords.normalizeStored(db: SupportSQLiteDatabase) {
    val pairs = TO_KEY.filter { (label, key) -> label != key }
    if (pairs.isEmpty()) return
    val quoted = { s: String -> "'" + s.replace("'", "''") + "'" }
    val cases = pairs.entries.joinToString(" ") { (label, key) -> "WHEN ${quoted(label)} THEN ${quoted(key)}" }
    val labels = pairs.keys.joinToString(", ") { quoted(it) }
    for ((table, column) in STORED_IN) {
        runCatching {
            db.execSQL(
                "UPDATE $table SET $column = CASE lower(trim($column)) $cases ELSE $column END " +
                    "WHERE $column IS NOT NULL AND lower(trim($column)) IN ($labels)"
            )
        }
    }
}
