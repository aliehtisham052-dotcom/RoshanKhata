package com.innovation313.roshankhata.data

import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class SealCoverTest {
    private val c = SealCover(firstTo = 10, lastTo = 40)

    @Test fun entriesBeforeSealingBeganAreNotCovered() {
        assertFalse(c.covers(10)); assertFalse(c.covers(1)); assertFalse(c.waiting(10))
    }

    @Test fun entriesInTheSealedRangeAreCovered() {
        assertTrue(c.covers(11)); assertTrue(c.covers(40)); assertFalse(c.waiting(40))
    }

    @Test fun newerEntriesWaitForTheNextSeal() {
        assertTrue(c.waiting(41)); assertFalse(c.covers(41))
    }
}
