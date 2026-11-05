package com.innovation313.roshankhata.data

import android.content.Context

/*
 * The book's seal on the phone (4 Nov 2026, Kitab ki Muhar): the shared [BookSeals] run once a calendar day per
 * business, from where the day's safety copy is taken (KhataActivity.startDailyCopy), off the main thread. Never on a
 * helper's read-only phone (its writes are refused), never for a book closed under it (lesson 49).
 */

private const val SEAL_PREFS = "book_seal"
private const val SEAL_DAY = "day"

/** Once per calendar day per business: today's seal when due, then the witnesses still owed. Blocking; call on IO. */
suspend fun BookSeals.maybeToday(context: Context) {
    val app = context.applicationContext
    if (ViewerMode.isOn(app)) return
    val business = Businesses.active(app)
    val key = "${SEAL_DAY}_b${business.id}"
    val prefs = app.getSharedPreferences(SEAL_PREFS, Context.MODE_PRIVATE)
    val today = DayMath.dayNumber(System.currentTimeMillis())
    if (prefs.getLong(key, Long.MIN_VALUE) == today) return
    try {
        val db = KhataDatabase.get(app)
        val dao = db.khataDao()
        if (!isOn(dao)) return
        daily(dao, alive = { KhataDatabase.isOpen(db) })
        prefs.edit().putLong(key, today).apply()
    } catch (_: Exception) {
        // As the safety copy: a seal that could not be made today is tried again tomorrow; the app goes on.
    }
}

/**
 * Just after the switch was turned on (the Backup screen): the first seal's witnesses asked at once, so the owner sees
 * them stamped, and today marked as done. On AppScope: leaving the screen does not cut the requests short.
 */
suspend fun BookSeals.witnessHere(context: Context) {
    val app = context.applicationContext
    if (ViewerMode.isOn(app)) return
    try {
        val db = KhataDatabase.get(app)
        witness(db.khataDao(), alive = { KhataDatabase.isOpen(db) })
        app.getSharedPreferences(SEAL_PREFS, Context.MODE_PRIVATE).edit()
            .putLong("${SEAL_DAY}_b${Businesses.active(app).id}", DayMath.dayNumber(System.currentTimeMillis())).apply()
    } catch (_: Exception) {
        // Tried again with the day's seal.
    }
}
