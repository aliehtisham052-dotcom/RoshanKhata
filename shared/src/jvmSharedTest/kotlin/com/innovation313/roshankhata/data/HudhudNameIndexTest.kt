package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import com.innovation313.roshankhata.data.Hudhud.Facet
import com.innovation313.roshankhata.ui.NameSearch
import com.innovation313.roshankhata.ui.TextMemo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the readers make of the book's names is made once (26 Nov 2026, BC-353): a question against a shop of 1,000 names read
 * every name again for every word, 1.3 s at the middle on CI's emulator. The memos are keyed by a name's own letters and the
 * prepared book by the names it was made from, so the reading must follow the book the moment it changes — a rename, a name
 * added or removed, a list changed in place — and the quick answers must be the slow ones. Every name is made up (lesson 80).
 */
class HudhudNameIndexTest {

    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    private val goods = listOf(Hudhud.Name(100, "Urea"))

    private fun read(s: String, names: List<Hudhud.Name>, language: String? = "ur-Latn") =
        Hudhud.understand(s, names, emptyList(), language, goods = goods)

    private fun book() = arrayListOf(
        Hudhud.Name(1, "Ali Raza"), Hudhud.Name(2, "Bashir Ahmad"), Hudhud.Name(3, "Tariq Butt"), Hudhud.Name(4, "Naeem Khan")
    )

    @Test fun `a renamed customer is read under his new name at once, the old name names nobody`() {
        val before = book()
        assertEquals(Ask.Person(listOf(2L), Facet.PHONE), read("Bashir ka number", before))
        val after = book().also { it[1] = Hudhud.Name(2, "Qadeer Sheikh") }
        assertEquals(Ask.Person(listOf(2L), Facet.PHONE), read("Qadeer ka number", after))
        assertNotEquals(Ask.Person(listOf(2L), Facet.PHONE), read("Bashir ka number", after))
        // And back: the first book is read as it was, whatever the prepared slots hold.
        assertEquals(Ask.Person(listOf(2L), Facet.PHONE), read("Bashir ka number", before))
    }

    @Test fun `a list changed in place is a new book`() {
        val names = book()
        assertEquals(Ask.Person(listOf(2L), Facet.PHONE), read("Bashir ka number", names))
        names[1] = Hudhud.Name(2, "Qadeer Sheikh")
        assertNotEquals(Ask.Person(listOf(2L), Facet.PHONE), read("Bashir ka number", names))
        names.add(Hudhud.Name(5, "Bashir Mirza"))
        assertEquals(Ask.Person(listOf(5L), Facet.PHONE), read("Bashir ka number", names))
        names.removeAt(names.size - 1)
        assertNotEquals(Ask.Person(listOf(5L), Facet.PHONE), read("Bashir ka number", names))
    }

    @Test fun `a customer added across scripts is met the moment he is in the book, and gone the moment he is not`() {
        val without = book().filter { it.id != 2L }
        val with = book()
        assertNotEquals(Ask.Person(listOf(2L), Facet.PHONE), read("بشیر کا نمبر", without, "ur"))
        assertEquals(Ask.Person(listOf(2L), Facet.PHONE), read("بشیر کا نمبر", with, "ur"))
        assertNotEquals(Ask.Person(listOf(2L), Facet.PHONE), read("بشیر کا نمبر", without, "ur"))
    }

    @Test fun `many books in turn - more than the prepared slots - each read as its own`() {
        val first = listOf("Kashif", "Luqman", "Moiz", "Nabeel", "Owais", "Rameez", "Sarmad", "Taimoor", "Umair")
        val books = first.mapIndexed { k, w -> listOf(Hudhud.Name(10L + k, "$w Depot"), Hudhud.Name(50, "Ali Raza")) }
        repeat(2) {
            for ((k, b) in books.withIndex()) {
                assertEquals(Ask.Person(listOf(10L + k), Facet.PHONE), read("${first[k]} ka number", b))
                // Another book's man is nobody here.
                assertNotEquals(Ask.Person(listOf(10L + (k + 1) % 9), Facet.PHONE), read("${first[(k + 1) % 9]} ka number", b))
            }
        }
    }

    @Test fun `the quick strong-hit answer is the scored one, for every word against every book`() {
        val names = listOf("Ali Raza", "Bashir Ahmad", "Rana Matyky", "Tallay Wali", "B", "Chacho Zahid U.S", "Abbas Sabzi Mandi",
            "علی رضا", "बशीर अहमद", "আলী রাজা", "Al-Noor Traders", "Yaseen", "Asghar Spray Wala")
        val words = listOf("ali", "raz", "raza", "bashir", "ba", "b", "matyky", "mate", "talewali", "tallay", "zah", "u", "s",
            "abbas", "sabzi", "علی", "बशीर", "আলী", "noor", "al", "yasin", "yas", "asgar", "spray", "wal", "wali", "xyz", "")
        for (w in words) for (k in 0..names.size) {
            val book = names.take(k)
            val slow = NameSearch.scoreSpoken(book, listOf(w)) { it }.any { it.strongHits > 0 }
            assertEquals("'$w' in $book", slow, NameSearch.hasStrongHit(book, listOf(w)) { it })
        }
        for (p in listOf("ali", "raza", "ab", "abbas", "abbasi", "bas", "x", "talewali")) for (s in listOf("ali", "al", "abb", "abbas", "bas", "talewa", "x")) {
            assertEquals("$p / $s", NameSearch.wordScoreForTest(p, s) >= 80, NameSearch.strongWord(p, s))
        }
    }

    @Test fun `a memo past its cap starts again and still answers`() {
        val memo = TextMemo<String>(3)
        var made = 0
        val upper = { k: String -> made++; k.uppercase() }
        for (k in listOf("a", "b", "c")) assertEquals(k.uppercase(), memo.get(k, upper))
        assertEquals(3, made)
        assertEquals("A", memo.get("a", upper))
        assertEquals(3, made)
        assertEquals("D", memo.get("d", upper))
        assertTrue(memo.size <= 3)
        assertEquals("B", memo.get("b", upper))
    }
}
