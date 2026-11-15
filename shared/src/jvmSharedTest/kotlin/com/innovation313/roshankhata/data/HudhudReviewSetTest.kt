package com.innovation313.roshankhata.data

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A regression set (15 Nov 2026, kept from the review that wrote 1,115 everyday sentences in the nine languages:
 * who asks, what, which days, in a shopkeeper's own words; review_d.tsv). The 865 that were read as meant are held
 * to the very reading they have: a change that turns one into another reading fails here and names it, so a fix for
 * one kind of sentence can never quietly cost another. If the new reading is as right, change that row's last column.
 * The 250 that were not (MISS, GUESS, NOW) are only listed: when a fix reads one as meant, promote it by writing
 * "OK" in its first status column. Nothing here is the owner's: names and goods are the invented ones (lesson 80).
 */
class HudhudReviewSetTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf("Ali Raza", "Bashir Ahmad", "Tariq Butt", "Naeem Khan", "Saleem Bhai", "Kamran Traders")
        .mapIndexed { i, n -> Hudhud.Name(i + 1L, n) }
    private val goods = listOf("Urea", "DAP", "Sugar", "Paracetamol", "Atta", "Ghee")
        .mapIndexed { i, n -> Hudhud.Name(100L + i, n) }

    private val rows: List<List<String>> by lazy {
        javaClass.getResourceAsStream("/hudhud/review_d.tsv")?.bufferedReader(Charsets.UTF_8)?.readLines().orEmpty()
            .filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }
    }

    @Test fun `the sentences read as meant keep the reading they have`() {
        if (rows.isEmpty()) return
        val pages = HashMap<String, List<Hudhud.Topic>>()
        val changed = ArrayList<String>()
        var held = 0
        var promoted = 0
        for (r in rows) {
            val (lang, status, said) = r
            val prev = r.getOrNull(3)?.takeIf { it.isNotBlank() }
            val expected = r[4]
            val topics = pages.getOrPut(lang) { HudhudRealPages.forApp(lang) }
            val before = prev?.let { Hudhud.understand(it, names, topics, lang, goods, null) }
            val got = HudhudShown.of(Hudhud.understand(said, names, topics, lang, goods, before))
            if (status == "OK") {
                held++
                if (got != expected) changed += "  [$lang] $said  =>  $got  (was $expected)"
            } else if (got != expected) promoted++
        }
        println("Review set D: $held held, ${changed.size} changed, $promoted of the others now read differently")
        changed.forEach(::println)
        assertTrue("sentences that changed their reading:\n" + changed.joinToString("\n"), changed.isEmpty())
        assertTrue("fewer than 865 held rows", held >= 865)
    }
}
