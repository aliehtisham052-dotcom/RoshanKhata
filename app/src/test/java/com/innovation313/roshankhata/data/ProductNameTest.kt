package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * What counts as the same product, and what does not.
 *
 * Two keys exist because they answer two different questions, and the tests
 * below are mostly about keeping them from being confused with each other:
 * [ProductName.key] decides whether the database REFUSES a name, and
 * [ProductName.normalised] only ever decides whether the app SUGGESTS one.
 *
 * The dangerous mistake would be to let the loose one do the refusing. The
 * folding treats g and j, and v and b, as the same sound — right for a
 * Pakistani name, wrong for a brand. Those cases are asserted here so that if
 * anyone ever puts a unique index on the folded column, this test fails and
 * says why.
 */
class ProductNameTest {

    // ---------- key: exact identity, and what uniqueness rests on ----------

    @Test
    fun `case and surrounding space do not make a second product`() {
        assertEquals(ProductName.key("Urea"), ProductName.key("urea"))
        assertEquals(ProductName.key("Urea"), ProductName.key("  Urea  "))
        assertEquals(ProductName.key("Urea"), ProductName.key("UREA"))
    }

    @Test
    fun `inner spacing is evened out, so a stray double space is not a new product`() {
        assertEquals(ProductName.key("Zinc  Sulphate"), ProductName.key("Zinc Sulphate"))
        assertEquals(ProductName.key("Zinc\tSulphate"), ProductName.key("Zinc Sulphate"))
    }

    @Test
    fun `a size or pack in the name IS a different product`() {
        // Deliberate. A dealer who writes the pack size means the pack size,
        // and merging them would make a stock count meaningless.
        assertNotEquals(ProductName.key("Urea"), ProductName.key("Urea 50kg"))
    }

    @Test
    fun `genuinely different products keep different keys`() {
        assertNotEquals(ProductName.key("Urea"), ProductName.key("DAP"))
        assertNotEquals(ProductName.key("Confidor"), ProductName.key("Karate"))
    }

    // ---------- normalised: similarity only, never a refusal ----------

    @Test
    fun `spelling differences that carry no sound fold together`() {
        // The whole point of the loose key: the owner typing it the other way
        // round should still be shown what they already have.
        assertEquals(
            ProductName.normalised("Bavistin"),
            ProductName.normalised("Bavisteen")
        )
    }

    @Test
    fun `the folded key is NOT safe to enforce uniqueness on`() {
        // g and j fold together, and so do v and b. These are two names a
        // shopkeeper could plausibly hold at once, and the unique index must
        // never be moved onto this column — the second product would become
        // impossible to create and the owner would be told it does not exist.
        assertEquals(ProductName.normalised("Gold"), ProductName.normalised("Jold"))
        assertNotEquals(ProductName.key("Gold"), ProductName.key("Jold"))
    }

    @Test
    fun `a multi-word name folds to one comparable run`() {
        // Words are folded individually and run together, so the spacing a
        // supplier used cannot decide whether two names look alike.
        assertEquals(
            ProductName.normalised("Zinc Sulphate"),
            ProductName.normalised("ZincSulphate")
        )
    }

    @Test
    fun `folding is stable under the same case and spacing changes as the exact key`() {
        assertEquals(ProductName.normalised("  UREA  "), ProductName.normalised("urea"))
    }

    // ---------- both, together ----------

    @Test
    fun `every name produces non-empty keys, so no row can be written blank`() {
        for (name in listOf("Urea", "DAP", "Confidor 200SL", "Zinc Sulphate")) {
            assertEquals(
                "key must be stable for $name",
                ProductName.key(name),
                ProductName.key(ProductName.key(name))
            )
            assertNotEquals("$name folded to nothing", "", ProductName.normalised(name))
        }
    }
}
