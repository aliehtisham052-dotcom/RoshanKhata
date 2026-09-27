package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The rules of the rate offer on the add-entry screen. See [RateOffer]. */
class RateOfferTest {

    private fun decide(
        isGiven: Boolean = true,
        isCustomer: Boolean = true,
        credit: Double? = 4800.0,
        cash: Double? = 4500.0,
        productUnit: String? = "bori",
        type: String = RateType.CREDIT,
        qty: Double? = 2.0,
        unit: String = "bori"
    ) = RateOffer.decide(isGiven, isCustomer, credit, cash, productUnit, type, qty, unit)

    @Test
    fun `udhar and naqd each use their own price`() {
        assertEquals(RateOffer.Result.Offer(4800.0, 9600.0), decide(type = RateType.CREDIT))
        assertEquals(RateOffer.Result.Offer(4500.0, 9000.0), decide(type = RateType.CASH))
    }

    @Test
    fun `udhar with no udhar rate is not set - never the cash price in disguise`() {
        assertEquals(RateOffer.Result.NotSet, decide(credit = null, type = RateType.CREDIT))
        assertEquals(RateOffer.Result.NotSet, decide(cash = null, type = RateType.CASH))
        assertEquals(RateOffer.Result.NotSet, decide(credit = 0.0, type = RateType.CREDIT))
    }

    @Test
    fun `nothing is offered on a supplier's account, on money in, or with no rates`() {
        assertEquals(RateOffer.Result.Hidden, decide(isCustomer = false))
        assertEquals(RateOffer.Result.Hidden, decide(isGiven = false))
        assertEquals(RateOffer.Result.Hidden, decide(credit = null, cash = null))
    }

    @Test
    fun `a rate per bori is not applied to kilograms`() {
        assertEquals(RateOffer.Result.UnitMismatch("bori", "kg"), decide(qty = 25.0, unit = "kg"))
    }

    @Test
    fun `units compare trimmed and case-blind, and a blank unit is fine`() {
        assertEquals(RateOffer.Result.Offer(4800.0, 9600.0), decide(unit = " Bori "))
        assertEquals(RateOffer.Result.Offer(4800.0, 9600.0), decide(unit = ""))
        assertEquals(RateOffer.Result.Offer(4800.0, 9600.0), decide(productUnit = null, unit = "bag"))
    }

    @Test
    fun `no quantity yet offers nothing but keeps the choice visible`() {
        assertEquals(RateOffer.Result.NeedQuantity, decide(qty = null))
        assertEquals(RateOffer.Result.NeedQuantity, decide(qty = 0.0))
    }

    @Test
    fun `the total is exact to the paisa`() {
        assertEquals(RateOffer.Result.Offer(1950.1, 5850.3), decide(credit = 1950.1, qty = 3.0, productUnit = null, unit = ""))
    }
}
