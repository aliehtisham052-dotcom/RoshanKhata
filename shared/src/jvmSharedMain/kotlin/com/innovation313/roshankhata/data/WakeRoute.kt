package com.innovation313.roshankhata.data

/**
 * Which of the phone's shops a wake-up from the relay is for (8 Nov 2026 review L010). A phone keeps one book per
 * shop, but Firebase knows only the phone: every book whose relay holds this phone's address rings the same phone.
 * Until now a wake-up ran rounds for the open shop alone, so a parcel for a shop that was not open waited until the
 * owner opened it.
 *
 * The relay knows its own book; when its message names it ([named], the "k" field), only that shop is woken. A relay
 * that does not name it yet (the Worker as deployed on 16 Nov 2026) leaves the phone to guess: the open shop first
 * (as before), then the shops that are not open and whose owner turned the relay on — at most [MAX_OTHERS], since
 * Firebase gives a message about twenty seconds. Every decision here is pure; the phone opens each shop's own file
 * ([PushWake]) one shop at a time.
 */
object WakeRoute {

    /**
     * A shop that is not open, as the wake-up reads it from its own file: [book] its live-sync book id (null when
     * it does not sync), [relayOn] whether its owner turned the relay on (only then can a wake-up be for it).
     */
    data class Shop(val id: Long, val book: String?, val relayOn: Boolean)

    /** Shops not on screen woken by one unnamed message: Firebase's time for a message holds about three rounds. */
    const val MAX_OTHERS = 3

    /** A live-sync book id: 32 lower-case hex letters ([LiveSync.newHex] of 16 bytes). Anything else names nothing. */
    fun validBook(text: String?): Boolean = text != null && text.length == 32 && text.all { it in '0'..'9' || it in 'a'..'f' }

    /** The book a message names, or null when it names none (or names it in a shape no book has). */
    fun named(field: String?): String? = field?.trim()?.lowercase()?.takeIf { validBook(it) }

    /**
     * Whether the open shop's rounds run for a message naming [named] ([WakeRoute.named]): an unnamed message is
     * the open shop's first, as before; a named one only when it names the open shop's book ([openBook]).
     */
    fun forOpen(named: String?, openBook: String?): Boolean = openBook != null && (named == null || named == openBook)

    /**
     * The shops not on screen to wake, in order: the one [named] (none when this phone has no such shop, or it is
     * the open one), else every shop whose relay is on and whose book is not the open one's, lowest id first, at most
     * [MAX_OTHERS]. A book held by two shops (a copied file) is woken once.
     */
    fun others(shops: List<Shop>, named: String?, openBook: String?): List<Shop> {
        val syncing = shops.filter { it.relayOn && validBook(it.book) && it.book != openBook }.sortedBy { it.id }.distinctBy { it.book }
        if (named != null) return syncing.filter { it.book == named }
        return syncing.take(MAX_OTHERS)
    }

    /**
     * Whether [named] still needs the shops that are not open to be read at all: an unnamed message always does;
     * a named one not when it names the open shop.
     */
    fun readOthers(named: String?, openBook: String?): Boolean = named == null || named != openBook
}
