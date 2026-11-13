package com.innovation313.roshankhata.data

import android.content.Context
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.ShopCountry

/**
 * The occasions a shop posts on its WhatsApp Status, each with its own
 * wording in the app's language and its own colours.
 *
 * Each is drawn in its own premium design (ui/PosterDesigns.kt): crescents,
 * lanterns, a mosque, a clock, a wax seal, geometric ornament. No people and
 * no animals: that is the owner's standing caution for anything the app draws
 * (a qualified scholar has the final word), and it also keeps every poster
 * about the shop. Eid ul-Adha shows a mosque, not the Kaaba, for the same reason.
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
    /** A discount: the figure on a gold coin (12 Oct). */
    OFFER("offer", R.string.poster_offer, R.string.poster_offer_msg, 0xFF8E1B1B.toInt(), 0xFF4A0A0A.toInt(), false),
    JUMMA("jumma", R.string.poster_jumma, R.string.poster_jumma_msg, 0xFF0E4A34.toInt(), 0xFF06261B.toInt(), false),
    /** 14 August, Pakistan's Independence Day: the flag waving, fireworks. */
    AZADI("azadi", R.string.poster_azadi, R.string.poster_azadi_msg, 0xFF0B5A2F.toInt(), 0xFF053019.toInt(), false),
    /** The owner's own words: both lines start empty. */
    CUSTOM("custom", 0, 0, 0xFF1B5E3A.toInt(), 0xFF134228.toInt(), false);

    /** Whether this occasion is for the shop's country: 14 August is Pakistan's (21 Oct 2026). */
    fun offeredIn(country: ShopCountry): Boolean = this != AZADI || country.pakistan

    /** Whether the occasion starts with words of its own (the shop's own announcement starts empty). */
    val hasWords: Boolean get() = this != CUSTOM

    /**
     * The headline this occasion starts with, in [context]'s language, for a shop of [trade] ([TradeVocab.key]).
     * Only New stock changes with the trade (13 Nov 2026, [NewStockWords]); every other occasion says the same
     * to every shop.
     */
    fun titleIn(context: Context, trade: String): String = when (this) {
        CUSTOM -> ""
        NEW_STOCK -> NewStockWords.title(context, trade)
        else -> context.getString(title)
    }

    /** The line under the headline, the same way. */
    fun messageIn(context: Context, trade: String): String = when (this) {
        CUSTOM -> ""
        NEW_STOCK -> NewStockWords.message(context, trade)
        else -> context.getString(message)
    }

    companion object {
        fun byKey(key: String?): PosterOccasion = values().firstOrNull { it.key == key } ?: NEW_STOCK

        /** The occasions a shop in [country] is offered, in order. */
        fun offered(country: ShopCountry = ShopCountry.current): List<PosterOccasion> = values().filter { it.offeredIn(country) }
    }
}

/**
 * The New stock poster's words by the kind of shop (13 Nov 2026). "Stock" is a word for goods: a restaurant has a
 * menu, a salon, a tailor (stitching, alterations) and a fuel station have something to offer, a baker's counter is
 * fresh today, a clothier's or a furniture shop's is a collection. The three lines also say three different things
 * now (the corner label: it is new; the
 * headline: what kind of new; the line under it: what to do) where they said "just arrived", "new stock has
 * arrived" and "fresh stock is in" one after another.
 *
 * Sindhi and Bengali wording of the new lines is for a native reader to look at.
 */
object NewStockWords {
    enum class Family { GOODS, FRESH, COLLECTION, MENU, SERVICE }

    /** The trade ([TradeVocab.key], lower case) says the family; a trade not named here sells goods, as before. */
    fun family(trade: String): Family = when (trade.trim().lowercase()) {
        "dairy", "bakery", "meat", "produce" -> Family.FRESH
        "clothing", "footwear", "cosmetics", "furniture" -> Family.COLLECTION
        "restaurant" -> Family.MENU
        "salon", "tailor", "fuel" -> Family.SERVICE
        else -> Family.GOODS
    }

    /** The label in the poster's corner: "JUST ARRIVED" for what arrives, the short "NEW" for a menu or a service. */
    fun kicker(context: Context, trade: String): String = context.getString(when (family(trade)) {
        Family.MENU, Family.SERVICE -> R.string.poster_kicker_new_short
        else -> R.string.poster_kicker_new_stock
    })

    fun title(context: Context, trade: String): String = context.getString(when (family(trade)) {
        Family.GOODS -> R.string.poster_new_stock
        Family.FRESH -> R.string.poster_new_fresh
        Family.COLLECTION -> R.string.poster_new_collection
        Family.MENU -> R.string.poster_new_menu
        Family.SERVICE -> R.string.poster_new_service
    })

    fun message(context: Context, trade: String): String = context.getString(when (family(trade)) {
        Family.GOODS, Family.COLLECTION -> R.string.poster_new_stock_msg
        Family.FRESH -> R.string.poster_new_fresh_msg
        Family.MENU -> R.string.poster_new_menu_msg
        Family.SERVICE -> R.string.poster_new_service_msg
    })
}
