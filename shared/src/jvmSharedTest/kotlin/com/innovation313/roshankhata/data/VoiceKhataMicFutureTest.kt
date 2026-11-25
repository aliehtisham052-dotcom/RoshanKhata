package com.innovation313.roshankhata.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Khata microphone's two "later" messages (26 Nov 2026, BC-361): a sentence of money still to move makes no entry
 * (`VoiceQuestion.saysFuture`); KhataActivity then says the shop's own payment to come ("Kal Ali ko 500 dunga") with
 * `voice_future_shop` — write it when you pay — and the customer's promise ("Ali kal 500 dega") with
 * `voice_future_promise` — set his promised day. The choice is `shopWillPay` inside `saysFuture`; this holds both
 * halves for the same sentences, so the shop's words never get the customer's message or an entry.
 */
class VoiceKhataMicFutureTest {
    /** What KhataActivity shows: null (an entry), "promise" or "shop". */
    private fun said(heard: String): String? = when {
        !VoiceQuestion.saysFuture(heard) -> null
        VoiceQuestion.shopWillPay(heard) -> "shop"
        else -> "promise"
    }

    @Test fun `the shop's own payment to come gets the shop's message`() {
        for (s in listOf(
            "Kal Ali ko 500 dunga", "main kal Ali ko 500 doonga", "I will pay Ali 500 tomorrow", "we will give Ali 500",
            "کل علی کو 500 دوں گا", "कल अली को 500 दूँगा"
        )) assertTrue("'$s' read as ${said(s)}", said(s) == "shop")
    }

    @Test fun `the customer's promise keeps the promised-day message`() {
        for (s in listOf("Ali kal 500 dega", "Ali will pay 500 tomorrow", "علی کل 500 دے گا", "अली कल 500 देगा")) {
            assertTrue("'$s' read as ${said(s)}", said(s) == "promise")
        }
    }

    @Test fun `money that moved today is still an entry`() {
        for (s in listOf("Ali ko 500 diye", "Ali ne 500 diye", "I gave Ali 500")) {
            assertFalse("'$s' was refused as later money", VoiceQuestion.saysFuture(s))
        }
    }
}
