package com.innovation313.roshankhata.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
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
    val holidays: Set<Int> = setOf(Calendar.SUNDAY),
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
    val showMark: Boolean = true
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

    fun toJson(): JSONObject = JSONObject().apply {
        put("items", JSONArray(items))
        put("usePhoto", usePhoto)
        put("reason", reason); put("reasonText", reasonText)
        put("reopen", reopen); put("reopenAt", reopenAt); put("urgentPhone", urgentPhone)
        put("opens", opens); put("closes", closes); put("holidays", JSONArray(holidays.sorted()))
        put("prayerBreak", prayerBreak); put("breakFrom", breakFrom); put("breakTo", breakTo)
        put("ramazanHours", ramazanHours); put("dayFrom", dayFrom); put("dayTo", dayTo)
        put("afterTaraweeh", afterTaraweeh); put("nightFrom", nightFrom); put("nightTo", nightTo)
        put("eidDays", eidDays)
        put("offerPercent", offerPercent); put("offerDays", offerDays)
        put("showLogo", showLogo); put("showPhone", showPhone); put("showAddress", showAddress); put("showMark", showMark)
    }

    companion object {
        const val CUSTOM_REASON = "custom"
        const val MAX_ITEMS = 4
        private const val PREFS = "poster"

        /** Anything missing or unreadable falls back to its default, field by field. */
        fun fromJson(o: JSONObject?): PosterFields {
            val d = PosterFields()
            if (o == null) return d
            fun ints(key: String, def: Set<Int>): Set<Int> = o.optJSONArray(key)?.let { a ->
                (0 until a.length()).map { a.optInt(it) }.filter { it in Calendar.SUNDAY..Calendar.SATURDAY }.toSet()
            } ?: def
            fun mins(key: String, def: Int) = o.optInt(key, def).takeIf { it in 0 until 24 * 60 } ?: def
            return PosterFields(
                items = o.optJSONArray("items")?.let { a -> (0 until a.length()).map { a.optString(it).trim() }.filter { it.isNotEmpty() }.take(MAX_ITEMS) } ?: d.items,
                usePhoto = o.optBoolean("usePhoto", d.usePhoto),
                reason = o.optString("reason", d.reason),
                reasonText = o.optString("reasonText", d.reasonText),
                reopen = o.optString("reopen", d.reopen),
                reopenAt = mins("reopenAt", d.reopenAt),
                urgentPhone = o.optBoolean("urgentPhone", d.urgentPhone),
                opens = mins("opens", d.opens), closes = mins("closes", d.closes),
                holidays = ints("holidays", d.holidays),
                prayerBreak = o.optBoolean("prayerBreak", d.prayerBreak),
                breakFrom = mins("breakFrom", d.breakFrom), breakTo = mins("breakTo", d.breakTo),
                ramazanHours = o.optBoolean("ramazanHours", d.ramazanHours),
                dayFrom = mins("dayFrom", d.dayFrom), dayTo = mins("dayTo", d.dayTo),
                afterTaraweeh = o.optBoolean("afterTaraweeh", d.afterTaraweeh),
                nightFrom = mins("nightFrom", d.nightFrom), nightTo = mins("nightTo", d.nightTo),
                eidDays = o.optInt("eidDays", d.eidDays).coerceIn(0, 10),
                offerPercent = o.optInt("offerPercent", d.offerPercent).coerceIn(1, 90),
                offerDays = o.optInt("offerDays", d.offerDays).coerceIn(0, 30),
                showLogo = o.optBoolean("showLogo", d.showLogo), showPhone = o.optBoolean("showPhone", d.showPhone),
                showAddress = o.optBoolean("showAddress", d.showAddress), showMark = o.optBoolean("showMark", d.showMark)
            )
        }

        private fun key(shopKey: String) = "fields_$shopKey"

        fun load(context: Context, shopKey: String): PosterFields {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(shopKey), null)
            return try { fromJson(raw?.let { JSONObject(it) }) } catch (e: Exception) { PosterFields() }
        }

        fun save(context: Context, shopKey: String, f: PosterFields) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(key(shopKey), f.toJson().toString()).apply()
        }
    }
}
