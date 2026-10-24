package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.words.W

/**
 * The record that wrote a khata entry ([KhataDao.entryOwner]) and the words
 * that send the owner there (21 Oct 2026, the critical review).
 */
object EntryOwner {
    fun wordFor(owner: String): String = when (owner) {
        "bill" -> W.entry_linked_bill
        "plan" -> W.entry_linked_plan
        "cheque" -> W.entry_linked_cheque
        "sale" -> W.entry_linked_sale
        else -> W.entry_linked_return
    }
}
