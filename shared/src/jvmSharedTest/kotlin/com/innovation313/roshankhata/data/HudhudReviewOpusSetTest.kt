package com.innovation313.roshankhata.data

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A second regression set (15 Nov 2026, review O: 1,167 everyday sentences written fresh in the nine languages, none of them from
 * review set D or the owner's lists; review_o.tsv). The 1,074 judged right are held to the very reading they have, as
 * [HudhudReviewSetTest] holds set D: a change that turns one into another reading fails here and names it. If the new reading is as
 * right, change that row's last column. The 93 that were not (MISS) are only listed — 41 of them name a product in Urdu or Hindi
 * script while the book keeps it in Latin letters, which no rule reads yet. Names and goods are invented (lesson 80).
 */
class HudhudReviewOpusSetTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf("Ali Raza", "Bashir Ahmad", "Tariq Butt", "Naeem Khan", "Saleem Bhai", "Kamran Traders")
        .mapIndexed { i, n -> Hudhud.Name(i + 1L, n) }
    private val goods = listOf("Urea", "DAP", "Sugar", "Paracetamol", "Atta", "Ghee")
        .mapIndexed { i, n -> Hudhud.Name(100L + i, n) }

    private val rows: List<List<String>> by lazy {
        javaClass.getResourceAsStream("/hudhud/review_o.tsv")?.bufferedReader(Charsets.UTF_8)?.readLines().orEmpty()
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
        println("Review set O: $held held, ${changed.size} changed, $promoted of the others now read differently")
        changed.forEach(::println)
        assertTrue("sentences that changed their reading:\n" + changed.joinToString("\n"), changed.isEmpty())
        assertTrue("fewer than 1074 held rows", held >= 1074)
    }
}
