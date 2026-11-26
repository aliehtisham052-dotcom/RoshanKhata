package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** BC-408: one name, several products — a total only when every shelf is known and in one unit. */
class HudhudGoodsTotalTest {
    @Test fun `bags of three Ureas add up`() =
        assertEquals(120.0 to "bag", HudhudGoodsTotal.of(listOf(50.0 to "bag", 40.0 to "bag", 30.0 to "bag")))

    @Test fun `a bag beside a kilogram is no total`() =
        assertNull(HudhudGoodsTotal.of(listOf(50.0 to "bag", 40.0 to "kg")))

    @Test fun `a product never traded is no total`() =
        assertNull(HudhudGoodsTotal.of(listOf(50.0 to "bag", null to null)))

    @Test fun `one product is not added up`() =
        assertNull(HudhudGoodsTotal.of(listOf(50.0 to "bag")))

    @Test fun `no unit on any is still one unit`() =
        assertEquals(7.0 to null, HudhudGoodsTotal.of(listOf(3.0 to null, 4.0 to null)))
}
