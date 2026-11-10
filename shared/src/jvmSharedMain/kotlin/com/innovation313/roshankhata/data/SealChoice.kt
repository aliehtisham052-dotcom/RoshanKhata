package com.innovation313.roshankhata.data

/**
 * Which one seal a shop's papers carry (10 Nov 2026, the owner): the Roshan Khata seal by default; the shop's own stamp
 * picture only when the owner chooses it (and has one); or none. Never both - one paper, one seal, in one place.
 * Kept per business on each device (the shop's pictures are per device too); a copy restored elsewhere starts at APP.
 */
enum class SealChoice {
    APP, OWN, NONE;

    /** What is printed: the shop's own stamp falls back to the Roshan Khata seal when no stamp picture is kept. */
    fun printed(hasStamp: Boolean): SealChoice = if (this == OWN && !hasStamp) APP else this

    companion object {
        fun parse(value: String?): SealChoice = entries.firstOrNull { it.name == value } ?: APP
    }
}
