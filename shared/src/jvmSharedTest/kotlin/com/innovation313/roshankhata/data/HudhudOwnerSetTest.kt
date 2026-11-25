package com.innovation313.roshankhata.data

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The owner's own kind of questions (14 Nov 2026): the two short lists he sent (owner_set.tsv, 200 sentences) and his
 * 1,000 sentences, fifty meanings asked twenty ways each (owner_1000.tsv). Each sentence carries the readings that
 * answer it truly — a figure from the book, the person or product it names, or the help page of a screen Hudhud has
 * no figure for. The floors are what this build reads right: a change that reads fewer of them fails here, so a
 * better reading of one meaning can never quietly cost another. Names and goods are made up (lesson 80).
 */
class HudhudOwnerSetTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val names = listOf("Ali Raza", "Bashir Ahmad", "Tariq Butt", "Naeem Khan", "Saleem Bhai", "Kamran Traders")
        .mapIndexed { i, n -> Hudhud.Name(i + 1L, n) }
    private val goods = listOf("Urea", "DAP", "Sugar", "Paracetamol", "Atta", "Ghee")
        .mapIndexed { i, n -> Hudhud.Name(100L + i, n) }

    /**
     * The app's own help pages, in Roman Urdu, English and Urdu, read from strings.xml as the app reads them
     * ([HudhudRealPages], shared with the how-to pages' test). The three tongues and their order are what the floors
     * below were measured with (14 Nov 2026): change them and the floors are measured again.
     */
    private val topics: List<Hudhud.Topic> by lazy {
        HudhudRealPages.topics(listOf("values-b+ur+Latn", "values", "values-ur"))
    }

    private fun lines(resource: String): List<String> =
        javaClass.getResourceAsStream(resource)?.bufferedReader(Charsets.UTF_8)?.readLines().orEmpty()
            .filter { it.isNotBlank() && !it.startsWith("#") }

    /** How many of [rows] (id, sentence, …, accepted) are read as one of their accepted readings; misses are printed. */
    private fun score(title: String, rows: List<List<String>>): Int {
        var right = 0
        for (r in rows) {
            val got = HudhudShown.of(Hudhud.understand(r[1], names, topics, null, goods))
            if (r.last().split('|').any { got.startsWith(it) }) right++ else println("  miss ${r[0]}  ${r[1]}  => $got")
        }
        println("$title: $right of ${rows.size} right (${right * 100 / rows.size}%)")
        return right
    }

    @Test fun `the owner's two short lists are read as he means them`() {
        val rows = lines("/hudhud/owner_set.tsv").map { it.split('\t') }
        if (rows.isEmpty()) return
        val right = score("The owner's short lists", rows)
        assertTrue("owner's short lists: $right of ${rows.size} is below the floor", right >= SHORT_FLOOR)
    }

    @Test fun `the owner's 1000 sentences are read as he means them`() {
        val rows = lines("/hudhud/owner_1000.tsv").map { it.split('\t') }
        if (rows.isEmpty()) return
        val right = score("The owner's 1,000 sentences", rows)
        assertTrue("owner's 1,000 sentences: $right is below the floor", right >= THOUSAND_FLOOR)
    }

    private companion object {
        /**
         * What this build reads right (14 Nov 2026); raise it with every improvement, never lower it. Measured with the
         * pages in the app's own order (pages.tsv is held to HudhudBook.PAGES by the catalog check): 143 and 726 —
         * the short list's floor is the figure itself, the thousand's keeps the margin of two it always had.
         */
        const val SHORT_FLOOR = 159
        const val THOUSAND_FLOOR = 812
    }
}
