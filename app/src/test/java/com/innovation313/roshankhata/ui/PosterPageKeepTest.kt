package com.innovation313.roshankhata.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BC-239: the poster screen kept its finished pages by how lately each was asked for, and the page of the design on
 * show, drawn first, was the one pushed out when a sixth page arrived. [PosterPageKeep.letGo] keeps by nearness to
 * the chosen design, whose own page never goes.
 */
class PosterPageKeepTest {

    @Test
    fun `nothing goes while there is room`() {
        assertEquals(emptyList<Int>(), PosterPageKeep.letGo(listOf(5, 4, 6, 7, 3), chosen = 5, room = 5))
        assertEquals(emptyList<Int>(), PosterPageKeep.letGo(emptyList(), chosen = 0, room = 5))
    }

    @Test
    fun `the chosen design's page stays though it was drawn first`() {
        // The order the pager asked in on the device: the chosen design (5), its neighbours, then a sixth page.
        val kept = listOf(5, 4, 6, 7, 3, 2)
        val gone = PosterPageKeep.letGo(kept, chosen = 5, room = 5)
        assertEquals("one page too many, one goes", 1, gone.size)
        assertFalse("the page on show was let go", 5 in gone)
        assertEquals("the farthest from the chosen design goes", listOf(2), gone)
    }

    @Test
    fun `the farthest pages go first, on either side`() {
        val gone = PosterPageKeep.letGo(listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10), chosen = 5, room = 5)
        assertEquals(6, gone.size)
        assertEquals(setOf(0, 1, 2, 8, 9, 10), gone.toSet())
        val atTheEnd = PosterPageKeep.letGo(listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10), chosen = 10, room = 3)
        assertEquals(setOf(0, 1, 2, 3, 4, 5, 6, 7), atTheEnd.toSet())
    }

    @Test
    fun `the chosen page stays even with room for one, and when it is not among the kept`() {
        assertEquals(setOf(1, 9), PosterPageKeep.letGo(listOf(1, 5, 9), chosen = 5, room = 1).toSet())
        assertEquals(setOf(1, 9), PosterPageKeep.letGo(listOf(1, 5, 9), chosen = 5, room = 0).toSet())
        // Not kept yet (its page is still being drawn): the others are judged by nearness all the same.
        val gone = PosterPageKeep.letGo(listOf(0, 4, 6, 10), chosen = 5, room = 2)
        assertEquals(setOf(0, 10), gone.toSet())
    }

    @Test
    fun `the screen's own room holds what the pager binds`() {
        // The chosen design and two either side are bound at once; they must all be kept.
        assertTrue(PosterPageKeep.ROOM >= 5)
        assertEquals(emptyList<Int>(), PosterPageKeep.letGo(listOf(3, 4, 5, 6, 7), chosen = 5))
    }
}
