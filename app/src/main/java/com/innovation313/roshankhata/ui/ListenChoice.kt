package com.innovation313.roshankhata.ui

/**
 * Where speech is understood, and who said so (25 Nov 2026, the owner's decision D10): pure, no Android, so a plain
 * test holds it (ListenChoiceTest).
 *
 * The rule in one line: **audio goes online only after the owner's yes.** Where the phone has the language's offline
 * pack the words are made out on the phone, silently; where it has none the owner is asked once (Google's online
 * recogniser, "your voice goes to Google", or type instead) and the answer is kept for this phone; the voice settings'
 * switch changes it later. Until a yes there is no online path at all — not with internet, not on a fallback, not through
 * the phone's voice-search box (which is the same online service).
 *
 * What a phone remembers is a [Mode]:
 *  - [Mode.UNDECIDED] nothing chosen yet. Behaves as "phone only" for every online gate; the first microphone use that
 *    finds no pack asks.
 *  - [Mode.PHONE] "type instead" (or the switch on): never online. A phone with the pack still listens on itself.
 *  - [Mode.ONLINE] the owner said yes to Google's online recogniser when the phone cannot hear the language itself.
 *    A phone that has the pack still listens on itself first.
 */
object ListenChoice {

    enum class Mode { UNDECIDED, PHONE, ONLINE }

    /** What the microphone does when the phone cannot hear the language itself. */
    enum class Lacking {
        /** Go online: the owner said yes, and there is internet. */
        ONLINE,
        /** Ask once: nothing was chosen yet and there is internet to use. */
        ASK,
        /** Say why and point at typing (and, where the phone can, at downloading the language). */
        TYPE
    }

    /** What the phone's voice-search box (Khata's voice entry) does. */
    enum class Box {
        /** The phone has the pack: the box is asked to prefer it (`EXTRA_PREFER_OFFLINE`), nothing online by choice. */
        LOCAL,
        /** The owner said yes: the box as it always was. */
        ONLINE,
        /** Ask once, then [ONLINE] or [TYPE]. */
        ASK,
        /** No voice entry: the entry is typed. */
        TYPE
    }

    /** Whether any online recogniser (or the voice-search box) may be used now: a yes, and a network. */
    fun mayGoOnline(mode: Mode, networkUp: Boolean): Boolean = mode == Mode.ONLINE && networkUp

    /** Whether the owner keeps listening on this phone: everything but a yes (what the voice settings' switch shows). */
    fun phoneOnly(mode: Mode): Boolean = mode != Mode.ONLINE

    /**
     * No pack on the phone for the language. The only way to [Lacking.ASK] is nothing chosen, a network to go online
     * with and a recogniser that exists ([canHear]): asking where a yes could not be used would be a question about
     * nothing, so offline it is [Lacking.TYPE] and the question waits for a day there is internet.
     */
    fun lacking(mode: Mode, networkUp: Boolean, canHear: Boolean): Lacking = when {
        mode == Mode.ONLINE && networkUp -> Lacking.ONLINE
        mode == Mode.UNDECIDED && networkUp && canHear -> Lacking.ASK
        else -> Lacking.TYPE
    }

    /** The voice-search box: [packInstalled] is what Android 13+ said (anything else, an error included, is false). */
    fun box(packInstalled: Boolean, mode: Mode, networkUp: Boolean, canHear: Boolean): Box =
        if (packInstalled) Box.LOCAL
        else when (lacking(mode, networkUp, canHear)) {
            Lacking.ONLINE -> Box.ONLINE
            Lacking.ASK -> Box.ASK
            Lacking.TYPE -> Box.TYPE
        }

    /** The owner's answer to the question: yes to Google's online recogniser, or type instead. */
    fun answered(yesOnline: Boolean): Mode = if (yesOnline) Mode.ONLINE else Mode.PHONE

    /** The voice settings' switch ("Only listen on this phone"): on is [Mode.PHONE]; off is a yes, asked first, never silent. */
    fun afterSwitch(on: Boolean): Mode = if (on) Mode.PHONE else Mode.ONLINE

    /**
     * What a phone remembers, from what it stored. [stored] is this feature's own value (a [Mode] name); before it
     * existed the one stored thing was the 16 Nov switch ([legacySwitch], null when it was never touched): on was a
     * choice for the phone; off, and never touched, are nothing chosen — an off made before the question existed was
     * not a yes to "your voice goes to Google", so it is asked once like the rest.
     */
    fun fromStored(stored: String?, legacySwitch: Boolean?): Mode {
        val named = Mode.values().firstOrNull { it.name == stored }
        return when {
            named != null -> named
            legacySwitch == true -> Mode.PHONE
            else -> Mode.UNDECIDED
        }
    }
}
