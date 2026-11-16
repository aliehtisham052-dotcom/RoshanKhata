package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A name of the book made of the words of a question (16 Nov 2026, audit T38 / BC-036): a product "DELETE ALL", a customer
 * "Aaj Ki Sale". The readers that run before the name search took the name's own words as the question ("DELETE ALL ka rate"
 * opened the delete page, "DELETE ALL ka stock" the low stock, "Aaj Ki Sale ka balance" the day's sale). [Hudhud.understand]
 * now reads such a sentence again with the name said whole stood in by a word that means nothing, and takes that reading when
 * it asks something OF the name. Every test goes in at [Hudhud.understand] with the shipped sense and the app's real pages
 * (lesson 145). Invented names only.
 */
class HudhudWholeNameTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val people = listOf(
        "Ali Raza", "Bashir Ahmad", "Aaj Ki Sale", "آج کی سیل", "आज की सेल", "Tariq Butt",
        "আজকের বিক্রি", "مبيعات اليوم", "فروش امروز", "Penjualan Hari Ini", "اڄ جو وڪرو"
    )
        .mapIndexed { i, n -> Hudhud.Name(i + 1L, n) }
    private val goods = listOf("Urea", "DELETE ALL", "Low Stock Wala").mapIndexed { i, n -> Hudhud.Name(100L + i, n) }

    private fun read(lang: String, said: String, book: List<Hudhud.Name> = people, items: List<Hudhud.Name> = goods) =
        Hudhud.understand(said, book, HudhudRealPages.forApp(lang), lang, items, null)

    private fun inner(a: Hudhud.Ask): Hudhud.Ask = when (a) {
        is Hudhud.Ask.Guessed -> a.ask
        is Hudhud.Ask.Now -> a.ask
        else -> a
    }

    @Test fun `a product named like a command is asked its facts in nine languages`() {
        val price = listOf(
            "en" to "DELETE ALL ka rate", "en" to "what is the price of DELETE ALL", "en" to "DELETE ALL's price",
            "ur-Latn" to "DELETE ALL ki qeemat kya hai", "ur" to "DELETE ALL کا ریٹ کیا ہے", "hi" to "DELETE ALL का रेट क्या है",
            "bn" to "DELETE ALL এর দাম কত", "ar" to "كم سعر DELETE ALL", "fa" to "قیمت DELETE ALL چند است",
            "sd" to "DELETE ALL جي قيمت ڪيتري آهي", "in" to "berapa harga DELETE ALL"
        )
        for ((lang, said) in price) {
            val a = inner(read(lang, said))
            assertTrue("[$lang] $said read as $a", a is Hudhud.Ask.Goods && a.ids == listOf(101L) && a.facet == Hudhud.GoodsFacet.PRICE)
        }
        val stock = listOf("ur-Latn" to "DELETE ALL ka stock kitna hai", "en" to "how much DELETE ALL stock is left", "ur" to "DELETE ALL کا اسٹاک کتنا ہے")
        for ((lang, said) in stock) {
            val a = inner(read(lang, said))
            assertTrue("[$lang] $said read as $a", a is Hudhud.Ask.Goods && a.ids == listOf(101L) && a.facet == Hudhud.GoodsFacet.STOCK)
        }
        // A product whose name says the low stock: its own shelf, never the shop's low-stock list.
        val low = inner(read("ur-Latn", "Low Stock Wala ka rate"))
        assertTrue("read as $low", low is Hudhud.Ask.Goods && low.ids == listOf(102L))
    }

    @Test fun `a customer named like a figure is asked his account and his facts`() {
        assertEquals(Hudhud.Ask.Parties(listOf(3L)), inner(read("ur-Latn", "Aaj Ki Sale ka balance")))
        assertEquals(Hudhud.Ask.Parties(listOf(3L)), inner(read("en", "Aaj Ki Sale ka hisaab batao")))
        assertEquals(Hudhud.Ask.Person(listOf(3L), Hudhud.Facet.PHONE), inner(read("ur-Latn", "Aaj Ki Sale ka number")))
        assertEquals(Hudhud.Ask.Person(listOf(4L), Hudhud.Facet.PHONE), inner(read("ur", "آج کی سیل کا نمبر")))
        assertEquals(Hudhud.Ask.Person(listOf(5L), Hudhud.Facet.PHONE), inner(read("hi", "आज की सेल का नंबर")))
        // The other languages, each with a customer named "today's sale" in its own words.
        for ((lang, said, id) in listOf(
            Triple("bn", "আজকের বিক্রির ফোন নম্বর", 7L), Triple("ar", "رقم هاتف مبيعات اليوم", 8L), Triple("fa", "شماره تلفن فروش امروز", 9L),
            Triple("in", "nomor telepon Penjualan Hari Ini", 10L), Triple("sd", "اڄ جو وڪرو جو نمبر", 11L)
        )) {
            assertEquals("[$lang] $said", Hudhud.Ask.Person(listOf(id), Hudhud.Facet.PHONE), inner(read(lang, said)))
        }
    }

    @Test fun `an entry said to such a customer names him and keeps the words he said`() {
        val a = read("ur-Latn", "Aaj Ki Sale ko 500 diye")
        assertTrue("read as $a", a is Hudhud.Ask.Entry && a.ids == listOf(3L) && a.amount == 500.0)
        assertTrue("words ${(a as Hudhud.Ask.Entry).nameWords}", "qqbookone" !in a.nameWords)
    }

    /** The guard: with nothing asked OF the name, the sentence's own question stands as it does in a book without him. */
    @Test fun `the question a name is made of stays the question when nothing is asked of him`() {
        val plain = people.filter { it.id in listOf(1L, 2L, 6L) }
        val noCmd = goods.filter { it.id == 100L }
        for ((lang, said) in listOf(
            "ur-Latn" to "aaj ki sale kitni hai", "ur-Latn" to "aaj ki sale", "ur" to "آج کی سیل کتنی ہے", "hi" to "आज की सेल कितनी है",
            "en" to "DELETE ALL", "en" to "low stock wala maal kaun sa hai", "en" to "sale ka hisaab",
            "bn" to "আজকের বিক্রি কত", "ar" to "كم مبيعات اليوم", "fa" to "فروش امروز چقدر است", "in" to "berapa penjualan hari ini",
            "sd" to "اڄ جو وڪرو ڪيترو آهي", "ur-Latn" to "Aaj Ki Sale aur Ali Raza ka balance",
            // A reading of the rest alone that names nobody never replaces the whole sentence's (the profit word, read without
            // the name's "sale", was this month's profit).
            "ur-Latn" to "aaj ki sale mein kitna munafa hua"
        )) {
            assertEquals("[$lang] $said", read(lang, said, plain, noCmd), read(lang, said))
        }
    }

    /** A word that links to the name ("ka", "ke") says the sentence is about him, with no fact word beside it. */
    @Test fun `a linking word alone names him`() {
        assertEquals(Hudhud.Ask.Parties(listOf(3L)), inner(read("ur-Latn", "Aaj Ki Sale ka batao")))
        val a = inner(read("ur-Latn", "DELETE ALL ke bare mein batao"))
        assertTrue("read as $a", a is Hudhud.Ask.Goods && a.ids == listOf(101L) && a.facet == Hudhud.GoodsFacet.OVERVIEW)
        val b = inner(read("ur-Latn", "Low Stock Wala maal kab aaya"))
        assertTrue("read as $b", b is Hudhud.Ask.Goods && b.ids == listOf(102L) && b.facet == Hudhud.GoodsFacet.LAST_IN)
    }

    /** The guard: a whole plain name in a sentence that asks something else is that something else, as before. */
    @Test fun `a delete order about a customer is still the delete page`() {
        assertEquals(Hudhud.Ask.Help("faq_delete"), read("ur-Latn", "Ali Raza ka khata delete kar do"))
        assertEquals(Hudhud.Ask.Help("faq_delete"), read("ur-Latn", "Aaj Ki Sale ka khata delete kar do"))
    }

    /** The guard: a one-word everyday name ("Sale", "Note") never takes the question its word asks. */
    @Test fun `a one-word everyday name does not swallow a question`() {
        val withSale = people + Hudhud.Name(20L, "Sale") + Hudhud.Name(21L, "Note")
        for ((lang, said) in listOf(
            "ur-Latn" to "aaj ki sale kitni hai", "en" to "sale ka hisaab", "en" to "my notes", "ur-Latn" to "kis kis ka note hai",
            "en" to "this month's sale"
        )) {
            assertEquals("[$lang] $said", read(lang, said), read(lang, said, withSale))
        }
    }
}
