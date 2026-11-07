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
 */
@RunWith(AndroidJUnit4::class)
class MeraKhataStoreTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun link(n: Int) = CustomerLink.Link("%032x".format(n), "AAAAAAAAAAAAAAAAAAAAAA")

    @Before
    fun clean() {
        context.getSharedPreferences("mera_khata", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun linksKeepTheirOrderAndWhatTheyBrought() {
        assertFalse(MeraKhataStore.any(context))
        MeraKhataStore.add(context, link(1))
        MeraKhataStore.add(context, link(2))
        assertEquals(listOf(link(2), link(1)), MeraKhataStore.all(context).map { it.link })

        val first = MeraKhataStore.all(context).first { it.link == link(1) }
        MeraKhataStore.update(context, first.copy(blob = "sealed", etag = "\"e1\"", askedAt = 5L, state = MeraKhataStore.State.GONE))
        MeraKhataStore.add(context, link(1))
        val again = MeraKhataStore.all(context)
        assertEquals(link(1), again[0].link)
        assertEquals("sealed", again[0].blob)
        assertEquals("\"e1\"", again[0].etag)
        assertEquals(MeraKhataStore.State.GONE, again[0].state)
        assertEquals(null, again[1].blob)

        MeraKhataStore.remove(context, link(1))
        assertEquals(listOf(link(2)), MeraKhataStore.all(context).map { it.link })
        assertTrue(MeraKhataStore.any(context))
    }

    @Test
    fun theListHoldsAtMostFiftyShops() {
        repeat(MeraKhataStore.MAX + 5) { MeraKhataStore.add(context, link(it + 1)) }
        val all = MeraKhataStore.all(context)
        assertEquals(MeraKhataStore.MAX, all.size)
        assertEquals(link(MeraKhataStore.MAX + 5), all.first().link)
    }
}
