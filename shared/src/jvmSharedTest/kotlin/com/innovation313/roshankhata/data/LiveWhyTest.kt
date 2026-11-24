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
        override fun text(name: String, vararg args: Any): String =
            if (args.isEmpty()) "<$name>" else "<$name|" + args.joinToString("|") + ">"
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
        assertNull(LiveWhy.skew("round 503"))
        assertNull(LiveWhy.skewText(names, "round 503"))
        assertNull(LiveWhy.skewText(names, "unreachable"))
    }

    // This build is made to look like schema 46 / protocol 1, so the tests do not move when the schema is bumped.
    private fun skew(why: String) = LiveWhy.skew(why, ourSchema = 46, ourProtocol = 1)
    private fun said(why: String, device: String? = null, computer: Boolean = false) =
        LiveWhy.skewText(names, why, device, computer, ourSchema = 46, ourProtocol = 1)

    @Test
    fun `a higher schema number than ours means the other is newer, so this one is updated`() {
        val s = skew("schema 47")!!
        assertEquals(LiveWhy.Update.THIS, s.update)
        assertEquals(LiveWhy.SCHEMA, s.kind)
        assertEquals(47, s.theirs)
        assertEquals(46, s.ours)
        assertEquals(
            "<${W.live_skew_this}|<${W.live_skew_other_device}>|<${W.live_skew_kind_schema}|47>|<${W.live_skew_kind_schema}|46>>",
            said("schema 47")
        )
    }

    @Test
    fun `a lower schema number than ours means the other is older, so the other one is updated`() {
        val s = skew("schema 45")!!
        assertEquals(LiveWhy.Update.OTHER, s.update)
        assertEquals(45, s.theirs)
        assertEquals(
            "<${W.live_skew_other}|<${W.live_skew_other_device}>|<${W.live_skew_kind_schema}|45>|<${W.live_skew_kind_schema}|46>>",
            said("schema 45")
        )
    }

    @Test
    fun `the protocol number is compared to the protocol, never to the schema`() {
        // Protocol 2 is above our protocol 1 (and far below schema 46): the other is newer.
        assertEquals(LiveWhy.Update.THIS, skew("protocol 2")!!.update)
        assertEquals(LiveWhy.PROTOCOL, skew("protocol 2")!!.kind)
        assertEquals(LiveWhy.Update.OTHER, LiveWhy.skew("protocol 1", ourSchema = 46, ourProtocol = 2)!!.update)
        assertEquals(
            "<${W.live_skew_this}|<${W.live_skew_other_device}>|<${W.live_skew_kind_protocol}|2>|<${W.live_skew_kind_protocol}|1>>",
            said("protocol 2")
        )
        // 46 is not "newer" than protocol 1's own number.
        assertEquals(LiveWhy.Update.OTHER, LiveWhy.skew("protocol 1", ourSchema = 46, ourProtocol = 3)!!.update)
    }

    @Test
    fun `the wire's refusal has no number and asks both devices to be updated`() {
        val s = skew(LiveWire.WIRE_OTHER)!!
        assertEquals(LiveWhy.Update.EITHER, s.update)
        assertEquals(LiveWhy.WIRE, s.kind)
        assertNull(s.theirs)
        assertEquals("<${W.live_skew_unknown}|<${W.live_skew_other_device}>||>", said(LiveWire.WIRE_OTHER))
    }

    @Test
    fun `a refusal with no readable number, or the same number, asks both`() {
        assertEquals(LiveWhy.Update.EITHER, skew("schema")!!.update)
        assertEquals(LiveWhy.Update.EITHER, skew("schema x")!!.update)
        assertEquals(LiveWhy.Update.EITHER, skew("schema 46")!!.update)
        assertEquals(LiveWhy.Update.EITHER, skew("protocol 1")!!.update)
    }

    @Test
    fun `the computer's window and the phone's card word the same refusal with their own keys`() {
        assertEquals(W.live_skew_pc_this, said("schema 47", computer = true)!!.substring(1).substringBefore('|'))
        assertEquals(W.live_skew_pc_other, said("schema 45", computer = true)!!.substring(1).substringBefore('|'))
        assertEquals(W.live_skew_pc_unknown, said(LiveWire.WIRE_OTHER, computer = true)!!.substring(1).substringBefore('|'))
        assertEquals(W.live_skew_this, said("schema 47")!!.substring(1).substringBefore('|'))
        assertEquals(W.live_skew_other, said("schema 45")!!.substring(1).substringBefore('|'))
        assertEquals(W.live_skew_unknown, said(LiveWire.WIRE_OTHER)!!.substring(1).substringBefore('|'))
    }

    @Test
    fun `a named device is put in the sentence, a blank one reads as the other device`() {
        assertTrue(said("schema 45", "Ali's phone", computer = true)!!.contains("Ali's phone"))
        assertTrue(said("schema 45", "   ")!!.contains("<${W.live_skew_other_device}>"))
        assertTrue(said("schema 45", null)!!.contains("<${W.live_skew_other_device}>"))
    }

    @Test
    fun `every refusal the engine can make reaches a sentence that names a device to update`() {
        // skewOf / oursFor are the engine's own words; both carry the OTHER device's number into the screen.
        val newer = LiveSync.Parcel("b", "d", 0, 0, emptyList(), false, schema = KHATA_DB_VERSION + 1)
        val older = LiveSync.Parcel("b", "d", 0, 0, emptyList(), false, schema = KHATA_DB_VERSION - 1)
        val proto = LiveSync.Parcel("b", "d", 0, 0, emptyList(), false, protocol = LiveSync.PROTOCOL + 1)
        assertEquals(LiveWhy.Update.THIS, LiveWhy.skew(LiveSync.skewOf(newer)!!)!!.update)
        assertEquals(LiveWhy.Update.OTHER, LiveWhy.skew(LiveSync.skewOf(older)!!)!!.update)
        assertEquals(LiveWhy.Update.THIS, LiveWhy.skew(LiveSync.skewOf(proto)!!)!!.update)
        // What this device tells the sender is its own number: on the sender's screen that is the OTHER device's.
        assertEquals(LiveWhy.Update.OTHER, LiveWhy.skew(LiveSync.oursFor("schema ${KHATA_DB_VERSION + 1}"), ourSchema = KHATA_DB_VERSION + 1)!!.update)
        assertNull(LiveWhy.skew(LiveSync.skewOf(LiveSync.Parcel("b", "d", 0, 0, emptyList(), false)) ?: "none"))
        // text() words it too (the relay line on the computer, a Failed card on the phone).
        assertTrue(LiveWhy.text(names, "schema ${KHATA_DB_VERSION + 1}").startsWith("<${W.live_skew_this}|"))
        assertEquals(W.live_skew_this, LiveWhy.wordFor("schema ${KHATA_DB_VERSION + 5}"))
        assertEquals(W.live_skew_other, LiveWhy.wordFor("schema ${KHATA_DB_VERSION - 5}"))
    }

    @Test
    fun `every capacity refusal the client knows is worded`() {
        // LiveCloud.CAPACITY are the refusals that fall over to the next relay; each reaches a screen as a reason.
        for (why in LiveCloud.CAPACITY) assertTrue(why, LiveWhy.wordFor(why) != null)
    }
}
