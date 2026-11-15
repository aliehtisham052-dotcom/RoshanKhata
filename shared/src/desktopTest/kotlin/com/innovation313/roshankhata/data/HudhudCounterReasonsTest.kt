package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The reasons Hudhud calls goods lost are the app's own (HudhudCounter keeps literals so it compiles beside the plain harness). */
class HudhudCounterReasonsTest {
    @Test fun `lost reasons are the inventory's damage, expiry and theft`() {
        assertEquals(setOf(AdjustReason.DAMAGE, AdjustReason.EXPIRED, AdjustReason.THEFT), HudhudCounter.LOST_REASONS)
    }
}
