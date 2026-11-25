package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Urdu numbers from 11 to 99 said aloud on an entry (25 Nov 2026, BC-316): voice entry knew only 1 to 10, 20 and 50, so
 * "pachees hazar" gave no amount at all. The words come from the invoice's own table (NumberWords). Words that are also
 * everyday words or names ("saath" with, "Sattar", اسی, چون, "bara" big) are never read as numbers.
 */
class VoiceUrduTensTest {
    private fun amount(said: String): Double? = VoiceEntry.parse(said, listOf("Ali Raza", "Sattar Khan")).amount

    @Test fun `roman urdu tens before a scale`() {
        assertEquals(25_000.0, amount("Ali Raza ko pachees hazar diye")!!, 0.0)
        assertEquals(25_000.0, amount("Ali Raza ko pachis hazar diye")!!, 0.0)
        assertEquals(1_200.0, amount("Ali Raza ko barah sau diye")!!, 0.0)
        assertEquals(4_500_000.0, amount("Ali Raza ko paintalees lakh diye")!!, 0.0)
        assertEquals(99_000.0, amount("Ali Raza ko ninyanwe hazar diye")!!, 0.0)
    }

    @Test fun `urdu script tens before a scale`() {
        assertEquals(25_000.0, amount("Ali Raza ko پچیس ہزار دیے")!!, 0.0)
        assertEquals(26_000.0, amount("Ali Raza ko چھبیس ہزار دیے")!!, 0.0)
        assertEquals(70_000.0, amount("Ali Raza ko ستر ہزار دیے")!!, 0.0)
        assertEquals(3_500.0, amount("Ali Raza ko پینتیس سو دیے")!!, 0.0)
    }

    @Test fun `every printed word from 11 to 99 is read back as its number`() {
        for ((word, n) in NumberWordsProbe.tens()) {
            assertEquals(word, n * 1000.0, amount("Ali Raza ko $word hazar diye")!!, 0.0)
        }
    }

    @Test fun `words that are also everyday words or names are not numbers`() {
        // "saath" is "with", "Sattar" a name, "bara" big: none of them adds to the amount.
        assertEquals(500.0, amount("Ali Raza ke saath 500 diye")!!, 0.0)
        assertEquals(500.0, amount("Sattar Khan ko 500 diye")!!, 0.0)
        assertEquals(500.0, amount("Ali Raza ko bara 500 diye")!!, 0.0)
        // "sattar hazar" stays unread (the owner types it), never 1,000 for 70,000.
        assertNull(amount("Ali Raza ko sattar hazar diye"))
    }
}

/** The table voice entry reads, less the words it leaves out on purpose. */
private object NumberWordsProbe {
    fun tens(): List<Pair<String, Int>> = com.innovation313.roshankhata.ui.NumberWords.urduTensSpoken()
        .filterKeys { it !in setOf("saath", "sattar", "اسی", "چون") }
        .map { it.key to it.value.toInt() }
}
