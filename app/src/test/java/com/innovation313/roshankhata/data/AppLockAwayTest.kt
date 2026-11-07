package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * When App Lock asks again (7 Nov 2026, the review's L2: a shop's khata link, opened on a phone left on the counter,
 * cleared the time away, and Back or Recents then showed the book without App Lock). Only the book's screens count;
 * once due, the lock stays due until it is answered; a process started afresh starts due; Mera Khata asks the same
 * rule without counting, and keeps a task of its own.
 */
class AppLockAwayTest {

    private val away = AppLock.Away
    private val min = 60_000L
    private val on = { true }
    private val off = { false }

    @Before
    fun aFreshProcess() {
        away.resetForTest()
    }

    /** The owner unlocked at [t] (the lock screen over a book screen), as the app does it. */
    private fun unlockedAt(t: Long) {
        assertTrue(away.bookStarted(t, on))
        away.bookStopped(t)
        away.unlocked(t)
        assertFalse(away.bookStarted(t, on))
    }

    @Test
    fun `a process started afresh asks before any of the book shows`() {
        assertTrue("a screen Android brings back after ending the app asks first", away.bookStarted(1_000L, on))
        // Every one of the book's screens asks while it is due, not only the first.
        assertTrue(away.bookStarted(1_001L, on))
        away.bookStopped(1_002L); away.bookStopped(1_002L)
        away.unlocked(1_003L)
        assertFalse(away.bookStarted(1_004L, on))
        assertEquals(1, away.unlocks)
    }

    @Test
    fun `moving between the book's screens is not away, five minutes out of it is`() {
        unlockedAt(0L)
        // Home opens the khata: the new screen starts before the old one stops.
        assertFalse(away.bookStarted(10 * min, on))
        away.bookStopped(10 * min)
        // Out for four minutes: no question.
        away.bookStopped(20 * min)
        assertFalse(away.bookStarted(24 * min, on))
        // Out for six: asked, and asked again by every screen until answered.
        away.bookStopped(25 * min)
        assertTrue(away.bookStarted(31 * min, on))
        away.bookStopped(31 * min)
        assertTrue(away.bookStarted(31 * min + 1, on))
        away.bookStopped(31 * min + 1)
        away.unlocked(32 * min)
        assertFalse(away.bookStarted(32 * min, on))
    }

    @Test
    fun `a khata link opened meanwhile neither clears the time away nor shows the customer's own list`() {
        unlockedAt(0L)
        away.bookStopped(0L)
        // Mera Khata opened from a shop's link six minutes later: it asks, and counts nothing.
        assertTrue(away.due(6 * min, on))
        assertTrue(away.due(6 * min + 1, on))
        // Back or Recents to the book: still asked.
        assertTrue(away.bookStarted(7 * min, on))
    }

    @Test
    fun `Mera Khata opened from inside the book asks nothing, and its own unlock starts the time away`() {
        unlockedAt(0L)
        // Opened from the More sheet: the book's screen is still on when it starts.
        assertFalse(away.due(1 * min, on))
        away.bookStopped(1 * min)
        // Unlocked from Mera Khata with none of the book on screen: the time away starts there.
        assertTrue(away.due(10 * min, on))
        away.unlocked(10 * min)
        assertFalse(away.due(11 * min, on))
        assertFalse(away.bookStarted(12 * min, on))
        away.bookStopped(12 * min)
        away.unlocked(13 * min)
        assertTrue("an unlock long ago in Mera Khata is not a pass for the book", away.bookStarted(30 * min, on))
    }

    @Test
    fun `with App Lock off nothing asks, and the lock turned on later keeps the session`() {
        assertFalse(away.bookStarted(0L, off))
        assertFalse(away.due(0L, off))
        away.bookStopped(1 * min)
        assertFalse(away.bookStarted(3 * min, on))
        away.bookStopped(3 * min)
        assertTrue(away.bookStarted(9 * min, on))
    }

    @Test
    fun `Mera Khata keeps a task of its own, so Back from a shop's link never lands on the book`() {
        val manifest = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml")).first { it.isFile }.readText()
        val mera = Regex("""<activity\s[^>]*android:name="\.MeraKhataActivity"[^>]*>""").find(manifest)?.value
            ?: error("MeraKhataActivity is not in the manifest")
        assertTrue(mera, mera.contains("android:taskAffinity=\"\""))
    }
}
