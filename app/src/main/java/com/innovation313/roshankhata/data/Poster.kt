package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.R

/**
 * The occasions a shop posts on its WhatsApp Status, each with its own
 * wording in the app's language and its own colours.
 *
 * Text and geometric ornament only — a crescent and star for Eid and
 * Ramazan, an eight-point star otherwise. No people and no animals: that is
 * the owner's standing caution for anything the app draws (a qualified
 * scholar has the final word), and it also keeps every poster about the shop.
 *
 * No dates are printed. Eid follows the moon sighting where the shop is, so
 * the owner posts it on the day; the app does not guess the day for him.
 */
enum class PosterOccasion(
    val key: String,
    val title: Int,
    val message: Int,
    val top: Int,
    val bottom: Int,
    val crescent: Boolean
) {
    NEW_STOCK("new_stock", R.string.poster_new_stock, R.string.poster_new_stock_msg, 0xFF1B5E3A.toInt(), 0xFF0F3D26.toInt(), false),
    EID_FITR("eid_fitr", R.string.poster_eid_fitr, R.string.poster_eid_fitr_msg, 0xFF0E4D4A.toInt(), 0xFF072B2A.toInt(), true),
    EID_ADHA("eid_adha", R.string.poster_eid_adha, R.string.poster_eid_adha_msg, 0xFF5A1E2B.toInt(), 0xFF2E0F17.toInt(), true),
    RAMAZAN("ramazan", R.string.poster_ramazan, R.string.poster_ramazan_msg, 0xFF14244A.toInt(), 0xFF0A1430.toInt(), true),
    CLOSED("closed", R.string.poster_closed, R.string.poster_closed_msg, 0xFF3A3A38.toInt(), 0xFF1E1E1D.toInt(), false),
    HOURS("hours", R.string.poster_hours, R.string.poster_hours_msg, 0xFF5C4A16.toInt(), 0xFF33290C.toInt(), false),
    THANKS("thanks", R.string.poster_thanks, R.string.poster_thanks_msg, 0xFF24553F.toInt(), 0xFF12301F.toInt(), false),
    /** The owner's own words: both lines start empty. */
    CUSTOM("custom", 0, 0, 0xFF1B5E3A.toInt(), 0xFF134228.toInt(), false);

    companion object {
        fun byKey(key: String?): PosterOccasion = values().firstOrNull { it.key == key } ?: NEW_STOCK
    }
}
