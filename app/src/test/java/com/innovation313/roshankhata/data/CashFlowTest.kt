package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.CashFlow.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Each rule of the cash-flow outlook, with figures small enough to check by eye. */
class CashFlowTest {

    private val day = 24L * 60 * 60 * 1000
    private val today = 100 * day

    @Test
    fun `next instalment, never more than what is left, minus a cheque already given`() {
        val (items, _) = CashFlow.items(
            plans = listOf(CashFlow.Plan(1, "Aslam", left = 12_000.0, instalment = 5_000.0, due = today + 3 * day),
                CashFlow.Plan(2, "Bilal", left = 2_000.0, instalment = 5_000.0, due = today + 4 * day),
                CashFlow.Plan(3, "Kamran", left = 6_000.0, instalment = null, due = today + 5 * day)),
            cheques = listOf(CashFlow.Cheque(3, "Kamran", 4_000.0, today + 1 * day, toReceive = true)),
            harvests = emptyList(), bills = emptyList(), owedToSupplier = emptyMap()
        )
        val plan = items.filter { it.kind == Kind.PLAN }.associate { it.name to it.amount }
        assertEquals(5_000.0, plan["Aslam"]!!, 0.001)
        assertEquals(2_000.0, plan["Bilal"]!!, 0.001)
        assertEquals(2_000.0, plan["Kamran"]!!, 0.001) // 6,000 left − 4,000 cheque
        assertEquals(4_000.0, items.single { it.kind == Kind.CHEQUE_IN }.amount, 0.001)
    }

    @Test
    fun `a plan without a date is not a promise of a day`() {
        val (items, _) = CashFlow.items(listOf(CashFlow.Plan(1, "A", 5_000.0, 1_000.0, null)), emptyList(), emptyList(), emptyList(), emptyMap())
        assertEquals(0, items.size)
    }

    @Test
    fun `what is owed to a supplier sits on the newest bills, after cheques written`() {
        val bills = listOf(
            CashFlow.Bill(9, "Supplier", 10_000.0, billDate = 1 * day, dueDate = today + 20 * day, isPaidInFull = false),
            CashFlow.Bill(9, "Supplier", 8_000.0, billDate = 5 * day, dueDate = today + 10 * day, isPaidInFull = false),
            CashFlow.Bill(9, "Supplier", 6_000.0, billDate = 9 * day, dueDate = null, isPaidInFull = false)
        )
        val (items, undated) = CashFlow.items(emptyList(),
            listOf(CashFlow.Cheque(9, "Supplier", 2_000.0, today + 2 * day, toReceive = false)),
            emptyList(), bills, mapOf(9L to 15_000.0))
        // 15,000 owed − 2,000 cheque = 13,000: newest 6,000 (no date) then 7,000 of the 8,000 bill.
        assertEquals(1, undated)
        val billItems = items.filter { it.kind == Kind.BILL }
        assertEquals(1, billItems.size)
        assertEquals(7_000.0, billItems.single().amount, 0.001)
        assertEquals(today + 10 * day, billItems.single().date)
    }

    @Test
    fun `harvest promise only when there is no plan, for the balance`() {
        val (items, _) = CashFlow.items(
            plans = listOf(CashFlow.Plan(1, "A", 5_000.0, 1_000.0, today + 1 * day)),
            cheques = emptyList(),
            harvests = listOf(CashFlow.Harvest(1, "A", today + 30 * day, 5_000.0), CashFlow.Harvest(2, "B", today + 30 * day, 9_000.0)),
            bills = emptyList(), owedToSupplier = emptyMap()
        )
        assertEquals(listOf("B"), items.filter { it.kind == Kind.HARVEST }.map { it.name })
    }

    @Test
    fun `the first short week is flagged, broken promises are not relied on, late bills weigh on week one`() {
        val items = listOf(
            CashFlow.Item(today - 3 * day, 1, "Late payer", 9_000.0, Kind.PLAN),   // broken promise
            CashFlow.Item(today - 1 * day, 9, "Supplier", 4_000.0, Kind.BILL),     // late bill: due now
            CashFlow.Item(today + 2 * day, 2, "A", 5_000.0, Kind.PLAN),
            CashFlow.Item(today + 9 * day, 9, "Supplier", 8_000.0, Kind.BILL),
            CashFlow.Item(today + 20 * day, 3, "B", 10_000.0, Kind.CHEQUE_IN)
        )
        val o = CashFlow.outlook(items, 0, today, 30)
        assertEquals(1, o.overdue.count { it.kind.isIn })
        assertEquals(5, o.weeks.size)                 // 7+7+7+7+2 days
        assertEquals(4_000.0, o.weeks[0].going, 0.001)
        assertEquals(5_000.0, o.weeks[0].coming, 0.001)
        // week 2: in 5,000 vs out 12,000 → 7,000 short
        assertEquals(o.weeks[1], o.gapWeek)
        assertEquals(7_000.0, o.gap, 0.001)
        assertEquals(15_000.0, o.coming, 0.001)
        assertEquals(12_000.0, o.going, 0.001)
    }

    @Test
    fun `no warning when what comes in covers what goes out`() {
        val o = CashFlow.outlook(listOf(CashFlow.Item(today + 1 * day, 1, "A", 5_000.0, Kind.PLAN),
            CashFlow.Item(today + 2 * day, 9, "S", 5_000.0, Kind.BILL)), 0, today, 30)
        assertNull(o.gapWeek)
    }
}
