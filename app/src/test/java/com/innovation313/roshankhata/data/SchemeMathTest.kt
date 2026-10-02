package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SchemeMathTest {
    private fun scheme(kind: String, measure: String = SchemeMath.BY_VALUE, end: Long = 100) = Scheme(
        name = "Rabi", startDate = 0, endDate = end, measure = measure, rewardKind = kind,
        target1 = 300000.0, reward1 = 1.0, target2 = 500000.0, reward2 = 2.0
    )

    @Test
    fun `between slabs - the lower slab is earned, the next is shown with what is left`() {
        val p = SchemeMath.progress(scheme(SchemeMath.PERCENT), 420000.0)
        assertEquals(300000.0, p.achieved!!.target, 0.0)
        assertEquals(80000.0, p.toNext!!, 0.0)
        assertEquals(4200.0, p.earned!!, 0.0)
    }

    @Test
    fun `top slab reached - no next, percent of the whole purchase`() {
        val p = SchemeMath.progress(scheme(SchemeMath.PERCENT), 510000.0)
        assertNull(p.next)
        assertEquals(10200.0, p.earned!!, 0.0)
    }

    @Test
    fun `units reward is the slab's own figure, below the first slab nothing`() {
        assertEquals(2.0, SchemeMath.progress(scheme(SchemeMath.UNITS, SchemeMath.BY_QTY), 600000.0).earned!!, 0.0)
        assertNull(SchemeMath.progress(scheme(SchemeMath.AMOUNT), 1000.0).earned)
    }

    @Test
    fun `status follows the dates and the claim`() {
        val s = scheme(SchemeMath.AMOUNT, end = 100)
        val reached = SchemeMath.progress(s, 300000.0)
        assertEquals(SchemeMath.Status.RUNNING, SchemeMath.status(s, reached, 50))
        assertEquals(SchemeMath.Status.CLAIM_DUE, SchemeMath.status(s, reached, 200))
        assertEquals(SchemeMath.Status.MISSED, SchemeMath.status(s, SchemeMath.progress(s, 10.0), 200))
        assertEquals(SchemeMath.Status.CLAIMED, SchemeMath.status(s.copy(claimedAmount = 1.0), reached, 200))
    }
}
