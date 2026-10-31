package com.innovation313.roshankhata.data

import org.junit.Assert.assertTrue
import org.junit.Test

/** The counter's product search (31 Oct 2026): a pharmacy finds a medicine by its generic name too. */
class CounterSearchTest {

    private fun p(id: Long, name: String, technical: String? = null) =
        Product(id = id, name = name, nameKey = name.lowercase(), normalisedName = name.lowercase(), technicalName = technical)

    @Test
    fun `a product is found by its name, company or technical name`() {
        val panadol = p(1, "Panadol", "Paracetamol 500 mg")
        val brufen = p(2, "Brufen", "Ibuprofen 400 mg")
        val list = listOf(panadol, brufen)
        // The app's one fuzzy search rule decides the rest; what matters here is that the generic name is read.
        assertTrue(panadol in CounterDraft.search(list, "paracetamol"))
        assertTrue(brufen in CounterDraft.search(list, "ibuprofen"))
        assertTrue(brufen in CounterDraft.search(list, "brufen"))
    }
}
