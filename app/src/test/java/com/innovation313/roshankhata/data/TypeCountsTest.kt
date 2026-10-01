package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The stock register's "By type" line. */
class TypeCountsTest {
    @Test
    fun `types group ignoring case and spaces, biggest first, unset last`() {
        val got = InspectorReport.typeCounts(
            listOf("Pesticide", " pesticide ", "Fertilizer", null, "", "PESTICIDE", "Seed", "Fertilizer"),
            "Not set"
        )
        assertEquals(listOf("Pesticide" to 3, "Fertilizer" to 2, "Seed" to 1, "Not set" to 2), got)
    }

    @Test
    fun `no unset group when every product has a type`() {
        assertEquals(listOf("Seed" to 1), InspectorReport.typeCounts(listOf("Seed"), "Not set"))
    }
}
