package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import com.innovation313.roshankhata.data.Hudhud.GoodsFacet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A product's other names (schema 45, the audit's T34) reach the assistant as more names of the same product: the phone's
 * Hudhud sheet hands one [Hudhud.Name] per name, all with the product's id. Asked in the script the shop wrote, the product is
 * found and found ONCE — two names of one product never make a choice between the product and itself. Names are invented.
 */
class HudhudOtherNamesTest {
    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf(Hudhud.Name(1L, "Ali Raza"), Hudhud.Name(2L, "Bashir Ahmad"))
    private val topics = listOf("intro_cashbook", "screen_rates").map { Hudhud.Topic(it, it, it) }

    /** What HudhudSheet passes: the product's own name, then each other name with the same id. */
    private val goods = listOf(
        Hudhud.Name(100L, "Urea"), Hudhud.Name(100L, "یوریا"), Hudhud.Name(100L, "यूरिया"),
        Hudhud.Name(101L, "DAP")
    )

    private fun read(s: String) = Hudhud.understand(s, names, topics, null, goods = goods)

    @Test fun `a product asked by an other name in another script is that product`() {
        for (s in listOf("یوریا کا ریٹ کیا ہے", "यूरिया का रेट क्या है", "یوریا کتنی بچی ہے")) {
            val ask = read(s)
            assertTrue("$s -> $ask", ask is Ask.Goods && ask.ids == listOf(100L))
        }
    }

    @Test fun `the product's own name still finds it once beside its other names`() {
        val ask = read("Urea ka rate kya hai")
        assertEquals(Ask.Goods(listOf(100L), GoodsFacet.PRICE), ask)
    }

    @Test fun `without the other names the other script finds nothing`() {
        val bare = Hudhud.understand("یوریا کا ریٹ کیا ہے", names, topics, null, goods = listOf(Hudhud.Name(100L, "Urea"), Hudhud.Name(101L, "DAP")))
        assertTrue("$bare", !(bare is Ask.Goods && 100L in bare.ids))
    }
}
