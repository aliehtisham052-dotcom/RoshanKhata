package com.innovation313.roshankhata.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Mera Khata's list on the phone (7 Nov 2026): a link opened goes first, a link opened again keeps what it brought,
 * an update changes only its own shop, a removal takes only that one, and the list never passes [MeraKhataStore.MAX].
 * After the security review of the same day: a link is kept only once its page opened (until then it waits apart, at
 * most [MeraKhataStore.MAX_PENDING]), so links that never open cannot push a shop out; the highest page counter seen
 * and an older page's mark are kept with the shop.
 */
@RunWith(AndroidJUnit4::class)
class MeraKhataStoreTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun link(n: Int) = CustomerLink.Link("%032x".format(n), "AAAAAAAAAAAAAAAAAAAAAA")
    /** A shop whose page opened: what the screen keeps after its first answer. */
    private fun opened(n: Int) = MeraKhataStore.Saved(link(n), "sealed$n", "\"e$n\"", n.toLong(), MeraKhataStore.State.OK, seq = n.toLong())

    @Before
    fun clean() {
        context.getSharedPreferences("mera_khata", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun linksKeepTheirOrderAndWhatTheyBrought() {
        assertFalse(MeraKhataStore.any(context))
        MeraKhataStore.confirm(context, opened(1))
        MeraKhataStore.confirm(context, opened(2))
        assertEquals(listOf(link(2), link(1)), MeraKhataStore.all(context).map { it.link })

        val first = MeraKhataStore.all(context).first { it.link == link(1) }
        MeraKhataStore.update(context, first.copy(blob = "sealed", etag = "\"e1\"", askedAt = 5L, state = MeraKhataStore.State.GONE, seq = 77L, older = true))
        MeraKhataStore.add(context, link(1))
        val again = MeraKhataStore.all(context)
        assertEquals(link(1), again[0].link)
        assertEquals("sealed", again[0].blob)
        assertEquals("\"e1\"", again[0].etag)
        assertEquals(MeraKhataStore.State.GONE, again[0].state)
        assertEquals(77L, again[0].seq)
        assertTrue(again[0].older)
        assertEquals("sealed2", again[1].blob)
        assertFalse(again[1].older)
        assertTrue("a shop already kept never waits apart", MeraKhataStore.pending(context).isEmpty())

        MeraKhataStore.remove(context, link(1))
        assertEquals(listOf(link(2)), MeraKhataStore.all(context).map { it.link })
        assertTrue(MeraKhataStore.any(context))
    }

    @Test
    fun aLinkIsKeptOnlyOnceItsPageOpened() {
        MeraKhataStore.add(context, link(7))
        assertTrue(MeraKhataStore.all(context).isEmpty())
        assertEquals(listOf(link(7)), MeraKhataStore.pending(context))
        assertTrue("a phone with a link waiting still opens on Mera Khata", MeraKhataStore.any(context))
        MeraKhataStore.confirm(context, opened(7))
        assertEquals(listOf(link(7)), MeraKhataStore.all(context).map { it.link })
        assertTrue(MeraKhataStore.pending(context).isEmpty())
        // A link that never opens (taken back, or not a page its key opens) goes, and leaves nothing.
        MeraKhataStore.add(context, link(8))
        MeraKhataStore.dropPending(context, link(8))
        assertTrue(MeraKhataStore.pending(context).isEmpty())
        MeraKhataStore.add(context, link(9))
        MeraKhataStore.remove(context, link(9))
        assertTrue(MeraKhataStore.pending(context).isEmpty())
    }

    @Test
    fun linksThatNeverOpenCannotPushAShopOut() {
        repeat(MeraKhataStore.MAX) { MeraKhataStore.confirm(context, opened(it + 1)) }
        repeat(100) { MeraKhataStore.add(context, link(1000 + it)) }
        assertEquals((1..MeraKhataStore.MAX).map { link(it) }.toSet(), MeraKhataStore.all(context).map { it.link }.toSet())
        val waiting = MeraKhataStore.pending(context)
        assertEquals(MeraKhataStore.MAX_PENDING, waiting.size)
        assertEquals(link(1099), waiting.first())
    }

    @Test
    fun theListHoldsAtMostFiftyShops() {
        repeat(MeraKhataStore.MAX + 5) { MeraKhataStore.confirm(context, opened(it + 1)) }
        val all = MeraKhataStore.all(context)
        assertEquals(MeraKhataStore.MAX, all.size)
        assertEquals(link(MeraKhataStore.MAX + 5), all.first().link)
    }
}
