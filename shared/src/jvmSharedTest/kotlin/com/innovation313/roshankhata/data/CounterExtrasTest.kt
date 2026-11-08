package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The counter's third night (21 Oct 2026), held without a database: a
 * weighing scale's label read into its product and weight (or price), the
 * item grid's variants under their main product, and loyalty's arithmetic.
 */
class CounterExtrasTest {

    private fun ean(body12: String) = body12 + Barcodes.checkDigit(body12)

    private val tomato = Product(id = 1, name = "Tomato", nameKey = "tomato", normalisedName = "tomato", defaultUnit = "kg", salePrice = 240.0, barcode = "00042")
    private val apple = Product(id = 2, name = "Apple", nameKey = "apple", normalisedName = "apple", defaultUnit = "kg", salePrice = 480.0, barcode = "123")

    @Test
    fun aWeightLabelIsItsProductAndItsKilograms() {
        // 21 · item 00042 · 01250 g · check
        val code = ean("21" + "00042" + "01250")
        val label = ScaleCode.read(code, ScaleCode.Rule())!!
        assertEquals("00042", label.code)
        assertEquals(1.25, label.kilograms!!, 0.0)
        val line = ScaleCode.line(code, ScaleCode.Rule(), listOf(tomato, apple)) { it.salePrice ?: 0.0 }!!
        assertEquals(tomato.id, line.product.id)
        assertEquals(1.25, line.quantity, 0.0)
        assertEquals(300.0, LineMath.round(line.quantity * line.rate), 0.0)
        // The scale code may be kept without its leading zeros: "123" is "00123".
        assertEquals(apple.id, ScaleCode.line(ean("21" + "00123" + "00500"), ScaleCode.Rule(), listOf(tomato, apple)) { 480.0 }!!.product.id)
    }

    @Test
    fun aPriceLabelComesToItsPriceExactly() {
        // The scale printed Rs 500.00 of apples at 480 a kilo: 1.042 kg, the line exactly 500.
        val code = ean("21" + "00123" + "50000")
        val line = ScaleCode.line(code, ScaleCode.Rule(price = true), listOf(apple)) { 480.0 }!!
        assertEquals(1.042, line.quantity, 0.0)
        assertEquals(500.0, SaleMath.lineTotal(line.quantity, line.rate), 0.0)
    }

    @Test
    fun whatIsNotALabelIsLeftAlone() {
        val good = ean("21" + "00042" + "01250")
        val badCheck = good.dropLast(1) + ((good.last() - '0' + 1) % 10)
        assertNull(ScaleCode.read(badCheck, ScaleCode.Rule()))
        assertNull(ScaleCode.read(good, ScaleCode.Rule(prefix = "22")))         // another prefix
        assertNull(ScaleCode.read("2100042", ScaleCode.Rule()))                  // too short
        assertNull(ScaleCode.line(ean("21" + "99999" + "01000"), ScaleCode.Rule(), listOf(tomato)) { 1.0 })  // no such item
        assertNull(ScaleCode.line(ean("21" + "00042" + "00000"), ScaleCode.Rule(), listOf(tomato)) { 1.0 })  // nothing weighed
        assertEquals(ScaleCode.Rule("21", false), ScaleCode.rule("9", null))
        assertEquals(ScaleCode.Rule("24", true), ScaleCode.rule("24", "1"))
        assertEquals(ScaleCode.Rule("24", true, 0), ScaleCode.rule("24", "1", "0"))
        assertEquals("a nonsense setting is the default", 2, ScaleCode.rule("24", "1", "7").decimals)
    }

    @Test
    fun aPriceLabelReadsInTheShopsOwnCurrencyDigits() {
        // 01250 on the label: 12.50 for a cents scale, 1,250 for a whole-rupee one, 1.250 for a dinar one.
        val code = ean("21" + "00042" + "01250")
        assertEquals(12.5, ScaleCode.read(code, ScaleCode.Rule(price = true))!!.price!!, 0.0)
        assertEquals(1250.0, ScaleCode.read(code, ScaleCode.Rule(price = true, decimals = 0))!!.price!!, 0.0)
        assertEquals(1.25, ScaleCode.read(code, ScaleCode.Rule(price = true, decimals = 3))!!.price!!, 0.0)
    }

    @Test
    fun variantsSitUnderTheirMainProductAndALoopIsNotFollowed() {
        val soap = Product(id = 10, name = "Soap", nameKey = "soap", normalisedName = "soap", category = "Bath")
        val rose = Product(id = 11, name = "Soap Rose", nameKey = "soap rose", normalisedName = "soaprose", parentId = 10, variant = "Rose", salePrice = 90.0)
        val lime = Product(id = 12, name = "Soap Lime", nameKey = "soap lime", normalisedName = "soaplime", parentId = 10, variant = "Lime", salePrice = 95.0)
        val big = Product(id = 13, name = "Soap Lime Big", nameKey = "soap lime big", normalisedName = "soaplimebig", parentId = 12, variant = "Lime, big", salePrice = 150.0)
        val loopA = Product(id = 20, name = "A", nameKey = "a", normalisedName = "a", parentId = 21)
        val loopB = Product(id = 21, name = "B", nameKey = "b", normalisedName = "b", parentId = 20)
        val groups = ItemGroups.of(listOf(soap, rose, lime, big, loopA, loopB, tomato))
        val soapGroup = groups.single { it.main.id == 10L }
        assertEquals(listOf("Lime", "Lime, big", "Rose"), soapGroup.variants.map { ItemGroups.label(it) })
        // The main soap has no price of its own: a tap offers only the variants.
        assertEquals(listOf(12L, 13L, 11L), soapGroup.choices.map { it.id })
        // A loop is cut, not followed for ever; every product still has a tile somewhere.
        assertEquals(setOf(1L, 2L, 10L, 11L, 12L, 13L, 20L, 21L).minus(setOf(2L)), groups.flatMap { listOf(it.main) + it.variants }.map { it.id }.toSet())
        assertEquals(listOf("Bath"), ItemGroups.categories(listOf(soap, rose, tomato)))
        assertEquals(1, ItemGroups.of(listOf(soap, rose, tomato), "Bath").size)
    }

    @Test
    fun loyaltyEarnsOnTheTicketAndNeverSpendsMoreThanItIsWorth() {
        val rule = Loyalty.rule("2", "1")!!
        assertEquals(20.0, rule.earned(1_000.0), 0.0)
        assertEquals(50.0, rule.value(50.0), 0.0)
        assertEquals(300.0, rule.maxSpend(500.0, 300.0), 0.0)   // the ticket caps it
        assertEquals(120.0, rule.maxSpend(120.0, 300.0), 0.0)   // the points cap it
        assertNull(Loyalty.rule("2", null))
        assertNull(Loyalty.rule("0", "1"))
        assertTrue(Loyalty.ofSale(listOf(LoyaltyEntry(partyId = 1, points = -40.0), LoyaltyEntry(partyId = 1, points = 12.5))) == (40.0 to 12.5))
    }

    @Test
    fun spentPointsAreADiscountTheCashiersCapDoesNotCount() {
        val draft = CounterDraft()
        draft.loyalty = LoyaltyRule(pointsPer100 = 1.0, pointValue = 1.0)
        draft.add(apple, qty = 1.0)
        draft.setParty(5, "Ali")
        draft.pointsBalance = 100.0
        draft.discount = "20"
        assertEquals(100.0, draft.pointsMax, 0.0)
        draft.usePoints(true)
        assertEquals(120.0, draft.totals.discount, 0.0)
        assertEquals(20.0, draft.handDiscount, 0.0)
        assertEquals(360.0, draft.totals.grandTotal, 0.0)
        assertEquals(120.0, draft.toSale(createdBy = null).discountAmount, 0.0)
        // Another customer: the points go back to none.
        draft.setParty(6, "Bilal")
        assertEquals(0.0, draft.pointsUsed, 0.0)
        assertEquals(460.0, draft.totals.grandTotal, 0.0)
    }
}
