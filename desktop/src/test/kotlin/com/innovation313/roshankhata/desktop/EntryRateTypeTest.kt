package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.RateType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The rate type the entry form saves (25 Oct 2026, the audit): a ticked
 * "paid now" is a cash sale even with no goods line, so Main writes its cash
 * "I got" (it only did when a goods line had a rate, and the customer read as
 * owing money he had paid).
 */
class EntryRateTypeTest {

    @Test
    fun `a ticked paid now with no goods line is a cash sale`() {
        assertEquals(RateType.CASH, entryRateType(isSale = true, goodsHaveRate = false, cashRate = true, paidNow = true, kept = null))
    }

    @Test
    fun `goods with a rate keep the side the owner chose`() {
        assertEquals(RateType.CREDIT, entryRateType(isSale = true, goodsHaveRate = true, cashRate = false, paidNow = false, kept = null))
        assertEquals(RateType.CASH, entryRateType(isSale = true, goodsHaveRate = true, cashRate = true, paidNow = false, kept = null))
    }

    @Test
    fun `no goods rate and no tick keep what the entry had`() {
        assertNull(entryRateType(isSale = true, goodsHaveRate = false, cashRate = true, paidNow = false, kept = null))
        assertEquals(RateType.CREDIT, entryRateType(isSale = true, goodsHaveRate = false, cashRate = false, paidNow = false, kept = RateType.CREDIT))
    }

    @Test
    fun `an I got or a supplier entry never takes a rate type`() {
        assertNull(entryRateType(isSale = false, goodsHaveRate = true, cashRate = true, paidNow = true, kept = null))
    }
}
