package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The live-sync reason codes both platforms put in the owner's language (23 Oct 2026). */
class LiveWhyTest {

    /** Says each word's name back, so a test can see which word was chosen. */
    private val names = object : Words {
        override fun text(name: String, vararg args: Any): String = "<$name>"
        override fun plural(name: String, count: Int, vararg args: Any): String = "<$name>"
        override fun list(name: String): List<String> = emptyList()
    }

    @Test
    fun `the engine's and the relays' known codes have words`() {
        assertEquals(W.live_why_other_computer, LiveWhy.wordFor("another computer"))
        assertEquals(W.live_why_other_book, LiveWhy.wordFor("another book"))
        assertEquals(W.live_why_unknown_device, LiveWhy.wordFor("unknown device"))
        assertEquals(W.live_why_unreachable, LiveWhy.wordFor("unreachable"))
        assertEquals(W.live_why_error, LiveWhy.wordFor(LiveWhy.ERROR))
        for (day in listOf("day-limit", "day-bytes")) assertEquals(day, W.live_why_day_limit, LiveWhy.wordFor(day))
        for (full in listOf("full", "new-books", "too-many-devices", "book-full")) assertEquals(full, W.live_why_relay_full, LiveWhy.wordFor(full))
        assertEquals("<${W.live_why_relay_full}>", LiveWhy.text(names, "book-full"))
    }

    @Test
    fun `a code not known is shown as it is`() {
        assertNull(LiveWhy.wordFor("round 503"))
        assertEquals("round 503", LiveWhy.text(names, "round 503"))
        assertEquals("schema 39", LiveWhy.text(names, "schema 39"))
    }

    @Test
    fun `every capacity refusal the client knows is worded`() {
        // LiveCloud.CAPACITY are the refusals that fall over to the next relay; each reaches a screen as a reason.
        for (why in LiveCloud.CAPACITY) assertTrue(why, LiveWhy.wordFor(why) != null)
    }
}
