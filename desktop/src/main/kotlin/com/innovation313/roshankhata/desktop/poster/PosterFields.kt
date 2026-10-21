// GENERATED from app/src/main/java/com/innovation313/roshankhata/data/PosterFields.kt
// by scripts/gen_desktop_poster.py. Do not edit: change the phone's file and re-run.
package com.innovation313.roshankhata.desktop.poster

import com.innovation313.roshankhata.desktop.invoice.Context
import java.util.Calendar

/**
 * What the owner chose for each poster beyond its two lines (12 Oct, the
 * approved screen mockup): what arrived, why the shop is closed and when it
 * opens, the timings and weekly holiday, the Ramazan hours, the Eid
 * holidays, and what the poster shows (logo, phone, address, the mark).
 *
 * Kept per shop in the poster's own preferences as one small JSON object,
 * so each business remembers its own. Times are minutes after midnight;
 * days are [Calendar] day numbers (Sunday = 1 … Saturday = 7).
 */
data class PosterFields(
    // New stock
    /** Up to four things that arrived; empty = the trade's usual three. */
    val items: List<String> = emptyList(),
    /** The owner's own photo of the stock takes the crate's place. */
    val usePhoto: Boolean = false,

    // Closed
    /** A [Reason] key, [CUSTOM_REASON] for [reasonText], or "" for none. */
    val reason: String = "",
    val reasonText: String = "",
    /** A [Reopen] key, or "" for not said. */
    val reopen: String = Reopen.TOMORROW.key,
    val reopenAt: Int = 9 * 60,
    val urgentPhone: Boolean = true,

    // Hours
    val opens: Int = 9 * 60,
    val closes: Int = 20 * 60,
    val holidays: Set<Int> = setOf(ShopCountry.current.weeklyOff),
    val prayerBreak: Boolean = true,
    val breakFrom: Int = 13 * 60,
    val breakTo: Int = 14 * 60 + 30,

    // Ramazan
    val ramazanHours: Boolean = false,
    val dayFrom: Int = 10 * 60,
    val dayTo: Int = 17 * 60,
    /** True: the night opening starts "after Taraweeh" rather than at [nightFrom]. */
    val afterTaraweeh: Boolean = true,
    val nightFrom: Int = 21 * 60 + 30,
    val nightTo: Int = 60,

    // Eid ul-Fitr
    /** Days the shop is closed for Eid; 0 = not on the poster. */
    val eidDays: Int = 0,

    // Offer
    /** The discount, in percent. */
    val offerPercent: Int = 20,
    /** "Only for 3 days"; 0 = not on the poster. */
    val offerDays: Int = 3,

    // What the poster shows
    val showLogo: Boolean = true,
    val showPhone: Boolean = true,
    val showAddress: Boolean = true,
    val showMark: Boolean = true,
    /** The shop's email under the call button (12 Oct). */
    val showEmail: Boolean = true,
    /** The number is on WhatsApp too: the call button carries a chat mark. */
    val onWhatsapp: Boolean = true
) {
    /** Why a shop closes, as the owner picks it; each has its own words in every language. */
    enum class Reason(val key: String) { FRIDAY("friday"), EID("eid"), WEDDING("wedding"), BEREAVEMENT("bereavement"), STRIKE("strike"), HOLIDAY("holiday") }

    /** When it opens again. A weekday means the next one after today. */
    enum class Reopen(val key: String, val weekday: Int = 0) {
        TOMORROW("tomorrow"), DAY_AFTER("day_after"),
        MONDAY("mon", Calendar.MONDAY), TUESDAY("tue", Calendar.TUESDAY), WEDNESDAY("wed", Calendar.WEDNESDAY),
        THURSDAY("thu", Calendar.THURSDAY), FRIDAY("fri", Calendar.FRIDAY), SATURDAY("sat", Calendar.SATURDAY),
        SUNDAY("sun", Calendar.SUNDAY);

        companion object {
            fun of(key: String?) = values().firstOrNull { it.key == key }
        }
    }


    companion object {
        const val CUSTOM_REASON = "custom"
        const val MAX_ITEMS = 4
        private const val PREFS = "poster"

    }
}
