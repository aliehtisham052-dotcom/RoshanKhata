package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.PaymentNotice.Opening
import com.innovation313.roshankhata.data.PaymentNotice.PartyNow
import com.innovation313.roshankhata.data.PaymentNotice.Pace.Say
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The door a payment notice opens and what is heard (7 Nov 2026, the security review of "Paisa aaya ki awaaz"): whose
 * notices are read (P2, P8), the one-time ticket (P3), the customer checked again when the notice is opened (P7), how
 * often a payment is said (P5), and only offline voices (P6). Every name and number below is made up.
 */
class PaymentDoorTest {

    // ------------------------------------------------------------------ whose notices are read (P2, P8)

    @Test fun chatsEmailsPostsAndCallsAreAPersonsWords() {
        for (category in listOf("msg", "email", "social", "call", "missed_call"))
            assertTrue(category, PaymentNotice.isPersonal(category, null, emptySet()))
    }

    @Test fun aMessagingStyleIsAPersonsWords() {
        assertTrue(PaymentNotice.isPersonal(null, "android.app.Notification\$MessagingStyle", emptySet()))
        assertTrue(PaymentNotice.isPersonal("status", null, setOf("android.title", "android.messages")))
        assertTrue(PaymentNotice.isPersonal(null, null, setOf("android.messagingUser")))
    }

    @Test fun aWalletsNoticeIsNotAPersonsWords() {
        assertFalse(PaymentNotice.isPersonal("status", "android.app.Notification\$BigTextStyle", setOf("android.title", "android.text")))
        assertFalse(PaymentNotice.isPersonal(null, null, emptySet()))
    }

    @Test fun anOngoingNoticeIsPassedOver() {
        assertTrue(PaymentNotice.passedOver(ongoing = true, category = null, template = null, keys = emptySet()))
        assertTrue(PaymentNotice.passedOver(ongoing = false, category = "msg", template = null, keys = emptySet()))
        assertFalse(PaymentNotice.passedOver(ongoing = false, category = "status", template = null, keys = emptySet()))
    }

    // ------------------------------------------------------------------ the one-time ticket (P3)

    /** A store in memory, as the app's preferences file would hold it. */
    private class Memory : PaymentTickets.Store {
        val rows = LinkedHashMap<String, String>()
        override fun all(): Map<String, String> = HashMap(rows)
        override fun save(put: Map<String, String>, drop: Collection<String>) {
            drop.forEach { rows.remove(it) }
            rows.putAll(put)
        }
    }

    private fun ticket(at: Long = 1_000_000L, id: Int = 7001) = PaymentTicket(
        noticeId = id, issuedAt = at, business = 1L, amount = 5000.0, note = "Received on JazzCash, Ref 012345678901",
        method = "wallet", reference = "012345678901", sender = "Ahmed Ali", partyId = 12L, partyUid = "u-12",
        partyName = "Ahmed Ali"
    )

    @Test fun aTicketOpensOnce() {
        val book = PaymentTickets(Memory())
        val t = ticket()
        book.issue("k1", t)
        assertEquals(t, book.redeem("k1", t.issuedAt + 60_000L))
        assertNull(book.redeem("k1", t.issuedAt + 61_000L))
    }

    @Test fun aTicketPastItsDayIsRefusedAndGone() {
        val store = Memory()
        val book = PaymentTickets(store)
        val t = ticket()
        book.issue("k1", t)
        assertNull(book.redeem("k1", t.issuedAt + PaymentTickets.TTL_MS + 1))
        assertTrue(store.rows.isEmpty())
    }

    @Test fun aClockSetBackDoesNotKeepATicketAlive() {
        val book = PaymentTickets(Memory())
        val t = ticket(at = 10 * PaymentTickets.TTL_MS)
        book.issue("k1", t)
        assertNull(book.redeem("k1", t.issuedAt - PaymentTickets.TTL_MS - 1))
    }

    @Test fun anUnknownKeyOpensNothing() {
        val book = PaymentTickets(Memory())
        book.issue("k1", ticket())
        assertNull(book.redeem("k2", 1_000_000L))
        assertNull(book.redeem("", 1_000_000L))
        assertNull(book.redeem(null, 1_000_000L))
    }

    @Test fun atMostMaxTicketsAreKeptTheOldestGoFirst() {
        val store = Memory()
        val book = PaymentTickets(store)
        for (i in 0 until PaymentTickets.MAX + 5) book.issue("k$i", ticket(at = 1_000_000L + i * 1_000L, id = 7001 + i))
        assertEquals(PaymentTickets.MAX, store.rows.size)
        assertFalse(store.rows.containsKey("k0"))
        assertTrue(store.rows.containsKey("k${PaymentTickets.MAX + 4}"))
    }

    @Test fun aNewTicketLetsGoOfStaleOnes() {
        val store = Memory()
        val book = PaymentTickets(store)
        book.issue("old", ticket(at = 0L))
        book.issue("new", ticket(at = PaymentTickets.TTL_MS + 1))
        assertEquals(setOf("new"), store.rows.keys)
    }

    @Test fun clearingSaysWhichNoticesToTakeAway() {
        val store = Memory()
        val book = PaymentTickets(store)
        book.issue("a", ticket(id = 7001))
        book.issue("b", ticket(id = 7002))
        assertEquals(setOf(7001, 7002), book.clear().map { it.noticeId }.toSet())
        assertTrue(store.rows.isEmpty())
    }

    @Test fun aTicketsLineReadsBackAndCannotBeSplitByItsText() {
        val t = ticket().copy(note = "a\u001Fb\nc", sender = null, partyId = null, partyUid = null, partyName = null, narrow = true)
        assertEquals(t.copy(note = "abc"), PaymentTicket.of(t.line()))
    }

    @Test fun anythingElseIsNoTicket() {
        assertNull(PaymentTicket.of(""))
        assertNull(PaymentTicket.of("t1\u001F7001"))
        assertNull(PaymentTicket.of(ticket().copy(amount = 0.0).line()))
        assertNull(PaymentTicket.of(ticket().line().replaceFirst("t1", "t9")))
    }

    // ------------------------------------------------------------------ what the tap opens (P3, P7)

    @Test fun aReferenceAlreadyInTheBookIsSaidNotFormedAgain() {
        val o = PaymentNotice.opening(ticket(), PartyNow("u-12", "Ahmed Ali", false), PaymentRefUse(123L, "Ahmed Ali"))
        assertEquals(Opening.Already(123L, "Ahmed Ali"), o)
        assertEquals(Opening.Already(456L, null), PaymentNotice.opening(ticket(), null, PaymentRefUse(456L, null)))
    }

    @Test fun theMatchedCustomerOpens() {
        assertEquals(Opening.Customer(12L), PaymentNotice.opening(ticket(), PartyNow("u-12", "Ahmed Ali", false), null))
    }

    @Test fun aBinnedCustomerIsNotOpened() {
        assertEquals(Opening.Choose("Ahmed Ali"), PaymentNotice.opening(ticket(), PartyNow("u-12", "Ahmed Ali", true), null))
    }

    @Test fun anotherCustomerUnderTheSameIdIsNotOpened() {
        assertEquals(Opening.Choose("Ahmed Ali"), PaymentNotice.opening(ticket(), PartyNow("u-99", "Bilal Khan", false), null))
        assertEquals(Opening.Choose("Ahmed Ali"), PaymentNotice.opening(ticket(), null, null))
    }

    @Test fun withoutAUidTheNameDecides() {
        val t = ticket().copy(partyUid = null)
        assertEquals(Opening.Customer(12L), PaymentNotice.opening(t, PartyNow(null, " ahmed ali", false), null))
        assertEquals(Opening.Choose("Ahmed Ali"), PaymentNotice.opening(t, PartyNow(null, "Bilal Khan", false), null))
    }

    @Test fun noCustomerOpensTheListNarrowedOnlyWhenSeveralCameNear() {
        val none = ticket().copy(partyId = null, partyUid = null, partyName = null)
        assertEquals(Opening.Choose(null), PaymentNotice.opening(none, null, null))
        assertEquals(Opening.Choose("Ahmed Ali"), PaymentNotice.opening(none.copy(narrow = true), null, null))
    }

    // ------------------------------------------------------------------ how often, and how (P5, P6)

    @Test fun threeAMinuteAreSaidThenOneChimeThenQuiet() {
        val pace = PaymentNotice.Pace()
        assertEquals(listOf(Say.VOICE, Say.VOICE, Say.VOICE, Say.CHIME, Say.QUIET, Say.QUIET), (0L until 6L).map { pace.next(it * 1_000L) })
    }

    @Test fun aMinuteLaterThereIsRoomAgain() {
        val pace = PaymentNotice.Pace()
        repeat(5) { pace.next(0L) }
        assertEquals(Say.VOICE, pace.next(60_000L))
    }

    @Test fun aFloodGetsAtMostOneChimeAMinute() {
        val pace = PaymentNotice.Pace()
        val heard = (0L until 600L).map { pace.next(it * 1_000L) }
        assertTrue(heard.chunked(60).all { minute -> minute.count { it == Say.CHIME } <= 1 && minute.count { it == Say.VOICE } <= 3 })
    }

    @Test fun lockedOrAppLockedItIsOnlyTheChime() {
        val pace = PaymentNotice.Pace()
        assertEquals(Say.CHIME, pace.next(0L, aloud = false))
        assertEquals(Say.VOICE, pace.next(1_000L, aloud = true))
    }

    @Test fun aClockSetBackDoesNotSilenceThePhone() {
        val pace = PaymentNotice.Pace()
        repeat(4) { pace.next(100_000L) }
        assertEquals(Say.VOICE, pace.next(1_000L))
    }

    private data class V(val name: String, val facts: PaymentNotice.VoiceFacts)

    private fun v(name: String, lang: String, country: String, network: Boolean = false, missing: Boolean = false, q: Int = 300) =
        V(name, PaymentNotice.VoiceFacts(lang, country, network, missing, q))

    @Test fun onlyAVoiceOnThePhoneSpeaks() {
        val voices = listOf(v("net", "ur", "PK", network = true, q = 500), v("missing", "ur", "PK", missing = true), v("local", "ur", "IN", q = 200))
        assertEquals("local", PaymentNotice.offlineVoice(voices, "ur", "PK") { it.facts }?.name)
    }

    @Test fun noOfflineVoiceMeansTheChime() {
        val voices = listOf(v("net", "ur", "PK", network = true), v("english", "en", "US"))
        assertNull(PaymentNotice.offlineVoice(voices, "ur", "PK") { it.facts })
    }

    @Test fun theCountrysVoiceThenTheBestSounding() {
        val voices = listOf(v("in-high", "ur", "IN", q = 500), v("pk-low", "ur", "PK", q = 100), v("pk-high", "ur", "PK", q = 400))
        assertEquals("pk-high", PaymentNotice.offlineVoice(voices, "ur", "PK") { it.facts }?.name)
        assertEquals("in-high", PaymentNotice.offlineVoice(voices, "ur", "") { it.facts }?.name)
    }
}
