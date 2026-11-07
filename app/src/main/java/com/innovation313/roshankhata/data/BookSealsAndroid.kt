package com.innovation313.roshankhata.data

import android.content.Context

/*
 * The book's seal on the phone (4 Nov 2026, Kitab ki Muhar): the shared [BookSeals] run once a calendar day per
 * business, from where the day's safety copy is taken (KhataActivity.startDailyCopy), off the main thread. Never on a
 * helper's read-only phone (its writes are refused), never for a book closed under it (lesson 49), and never for
 * another business than the one it started for (7 Nov 2026, the security review's M6): a run notes its business first
 * and writes nothing once another is open, or its book was closed.
 */

private const val SEAL_PREFS = "book_seal"
private const val SEAL_DAY = "day"

/** Whether a run that began for [business] on [db] may still write: the same book, still open. */
private fun sameBookOpen(app: Context, db: KhataDatabase, business: Long): Boolean =
    KhataDatabase.isOpen(db) && Businesses.active(app).id == business

/** Once per calendar day per business: today's seal when due, then the witnesses still owed. Blocking; call on IO. */
suspend fun BookSeals.maybeToday(context: Context) {
    val app = context.applicationContext
    if (ViewerMode.isOn(app)) return
    val business = Businesses.active(app).id
    val key = "${SEAL_DAY}_b$business"
    val prefs = app.getSharedPreferences(SEAL_PREFS, Context.MODE_PRIVATE)
    val today = DayMath.dayNumber(System.currentTimeMillis())
    if (prefs.getLong(key, Long.MIN_VALUE) == today) return
    try {
        val db = KhataDatabase.get(app)
        val alive = { sameBookOpen(app, db, business) }
        if (!alive()) return
        val dao = db.khataDao()
        if (!isOn(dao)) return
        daily(dao, alive = alive)
        // A run cut short by a switch of business is not today's: it is run again when this business is open.
        if (alive()) prefs.edit().putLong(key, today).apply()
    } catch (_: Exception) {
        // As the safety copy: a seal that could not be made today is tried again tomorrow; the app goes on.
    }
}

/**
 * Just after the switch was turned on (the Backup screen): the first seal's witnesses asked at once, so the owner sees
 * their answers, and today marked as done. On AppScope: leaving the screen does not cut the requests short.
 */
suspend fun BookSeals.witnessHere(context: Context) {
    val app = context.applicationContext
    if (ViewerMode.isOn(app)) return
    val business = Businesses.active(app).id
    try {
        val db = KhataDatabase.get(app)
        val alive = { sameBookOpen(app, db, business) }
        if (!alive()) return
        witness(db.khataDao(), alive = alive)
        if (alive()) {
            app.getSharedPreferences(SEAL_PREFS, Context.MODE_PRIVATE).edit()
                .putLong("${SEAL_DAY}_b$business", DayMath.dayNumber(System.currentTimeMillis())).apply()
        }
    } catch (_: Exception) {
        // Tried again with the day's seal.
    }
}
