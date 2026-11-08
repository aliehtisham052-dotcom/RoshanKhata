package com.innovation313.roshankhata.ui

import android.content.Context
import com.innovation313.roshankhata.data.BookSeals
import com.innovation313.roshankhata.data.Businesses
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.ViewerMode

/**
 * Home's one-time offer of the book seal (Kitab ki Muhar, 8 Nov 2026): shown while the seal is off for the open book,
 * until the owner says Not now or turns it on (from the card or on the Backup screen). Kept per book in this phone's
 * preferences, never in the book or a backup.
 */
object SealOffer {
    private const val PREFS = "seal_offer"

    private fun key(context: Context): String = "done_b" + Businesses.active(context).id

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Whether to show the card now; a book whose seal is on counts the offer as answered. */
    suspend fun shouldShow(context: Context, dao: KhataDao): Boolean {
        if (ViewerMode.isOn(context)) return false
        if (prefs(context).getBoolean(key(context), false)) return false
        if (BookSeals.isOn(dao)) { markDone(context); return false }
        return true
    }

    fun markDone(context: Context) {
        prefs(context).edit().putBoolean(key(context), true).apply()
    }
}
