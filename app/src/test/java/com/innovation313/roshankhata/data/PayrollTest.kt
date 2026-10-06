package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The month's salary sums, on examples a shop would recognise. */
class PayrollTest {

    private fun pay(kind: Int, amount: Double, deleted: Boolean = false) =
        StaffPayment(staffId = 1, amount = amount, kind = kind, isDeleted = deleted)

    @Test
    fun `a full month present earns the full salary`() {
        val m = Payroll.month(30_000.0, 30, emptyList(), emptyList())
        assertEquals(30_000.0, m.earned, 0.001)
        assertEquals(30_000.0, m.due, 0.001)
    }

    @Test
    fun `two days absent and one half day in a 30-day month`() {
        // 30000 x (30 - 2 - 0.5) / 30 = 27500
        val m = Payroll.month(30_000.0, 30, listOf(Payroll.ABSENT, Payroll.ABSENT, Payroll.HALF), emptyList())
        assertEquals(27_500.0, m.earned, 0.001)
        assertEquals(2, m.absent); assertEquals(1, m.half)
    }

    @Test
    fun `paid leave is a day worked`() {
        val m = Payroll.month(31_000.0, 31, listOf(Payroll.LEAVE, Payroll.LEAVE), emptyList())
        assertEquals(31_000.0, m.earned, 0.001)
        assertEquals(2, m.leave)
    }

    @Test
    fun `advances and salary paid come off what is due, deleted ones do not`() {
        val m = Payroll.month(30_000.0, 30, emptyList(),
            listOf(pay(Payroll.ADVANCE, 5_000.0), pay(Payroll.SALARY, 20_000.0), pay(Payroll.ADVANCE, 9_999.0, deleted = true)))
        assertEquals(5_000.0, m.advances, 0.001)
        assertEquals(20_000.0, m.paid, 0.001)
        assertEquals(5_000.0, m.due, 0.001)
    }

    @Test
    fun `handing over more than earned shows as a negative due, never hidden`() {
        val m = Payroll.month(10_000.0, 30, emptyList(), listOf(pay(Payroll.ADVANCE, 12_000.0)))
        assertEquals(-2_000.0, m.due, 0.001)
    }

    @Test
    fun `a month absent every day earns nothing, not less than nothing`() {
        val m = Payroll.month(9_000.0, 30, List(31) { Payroll.ABSENT }, emptyList())
        assertEquals(0.0, m.earned, 0.001)
    }
}
