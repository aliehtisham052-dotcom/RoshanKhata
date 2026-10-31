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
    // Maund and seer are South Asia's (31 Oct 2026, the review): an Egyptian or Indonesian shop was offered them,
    // in Indonesian as the English words. They stay keys, so an old entry still reads; they are just not offered.
    val southAsia = ShopCountry.current in setOf(ShopCountry.PAKISTAN, ShopCountry.INDIA, ShopCountry.BANGLADESH, ShopCountry.NEPAL)
    return (lead + KEYS.filter { it !in lead && (southAsia || it !in SOUTH_ASIAN_UNITS) }).map { label(it, locale) }
}

/** The units only South Asian shops weigh by (UnitWords.KEYS keeps them for every book). */
private val SOUTH_ASIAN_UNITS = setOf("maund", "seer")

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
    // The statements are shared with the computer (UnitWords.repairStored, 23 Oct 2026): one read first,
    // then one transaction kept out of live sync's log.
    repairStored(
        needed = { sql -> db.query(sql).use { c -> c.moveToFirst() && c.getLong(0) != 0L } },
        inTransaction = { body ->
            db.beginTransaction()
            try {
                body()
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        },
        exec = { sql -> db.execSQL(sql) }
    )
}
