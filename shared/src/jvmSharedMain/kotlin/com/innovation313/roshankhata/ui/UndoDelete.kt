package com.innovation313.roshankhata.ui

/**
 * An entry just binned on the entry screen, waiting for its customer's
 * screen to offer "Undo" (2 Oct). The entry screen closes on delete, so the
 * offer is made where the owner lands. In memory only: it lives for this
 * one return, and the Recycle Bin remains the way back after that.
 */
object UndoDelete {
    class Pending(val partyId: Long, val entryIds: List<Long>)

    @Volatile var pending: Pending? = null

    /** Hand over (and forget) what was binned for [partyId], if anything. */
    fun take(partyId: Long): Pending? = pending?.takeIf { it.partyId == partyId }?.also { pending = null }
}
