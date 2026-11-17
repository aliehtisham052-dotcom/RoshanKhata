package com.innovation313.roshankhata.data

/**
 * Who a new khata entry is written by (17 Nov 2026, the audit's L108): the name the caller gave, else — for an entry
 * the counter did not write — the name signed in ([Access.userName], null for the owner). A ticket's or a return's
 * own rows ([saleId] set) carry exactly their ticket's writer, never the person who happens to be signed in when a held
 * ticket is rung up. [KhataDao.insertEntryNumbered] asks this for every entry this device writes; a restore, a merge
 * and a live sync write a row as it came.
 */
object EntryWriter {
    fun of(given: String?, saleId: Long?, signedIn: String?): String? =
        given?.takeIf { it.isNotBlank() } ?: if (saleId == null) signedIn else null
}

/**
 * A shift's drawer, every part of it (21 Oct 2026; the by-hand parts 17 Nov 2026, the audit's L108): the float it was
 * opened with, the cash taken on the person's own counter tickets and given back on their own returns, and the cash they
 * moved by hand in the khata and the cashbook (an "I got" in cash, money in or out). What it should hold is summed from
 * those, never stored until the close. One figure on both platforms: the shared [ShiftMath.drawer] makes it and the
 * shared shift screen shows it.
 *
 * [handIn] and [handOut] are 0 for the owner's shift: the owner's rows carry no name, and neither does any row written
 * before names were kept (cashbook rows before schema 46, khata entries before 8 Nov 2026), so a row with no name cannot
 * be told to be his — it is counted in no shift, as no such row ever was.
 */
data class ShiftDrawer(
    val float: Double,
    val taken: Double,
    val back: Double,
    val handIn: Double = 0.0,
    val handOut: Double = 0.0
) {
    /** The parts unrounded; [ShiftMath] rounds the sum to the shop's coin. */
    val sum: Double get() = float + taken - back + handIn - handOut
}
