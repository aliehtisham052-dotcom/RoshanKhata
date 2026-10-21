package com.innovation313.roshankhata.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Who may do what at the counter (POS Phase 5), and the PIN that opens the rest. */
class AccessTest {

    private fun staff(name: String, role: String?, pin: String?): Staff {
        val salt = PinHash.salt()
        return Staff(id = name.hashCode().toLong(), name = name, monthlySalary = 1.0, role = role,
            pinSalt = pin?.let { salt }, pinHash = pin?.let { PinHash.hash(it, salt) })
    }

    @After
    fun out() = Access.signOut()

    @Test
    fun theOwnerMayDoEverythingAManagerAllButTheBookACashierOnlyRingUp() {
        Access.Permission.values().forEach { assertTrue(it.name, Access.can(it)) }
        Access.signIn(staff("Asad", Role.MANAGER, "1234"))
        assertTrue(Access.can(Access.Permission.VOID)); assertTrue(Access.can(Access.Permission.PRICE_EDIT))
        assertFalse(Access.can(Access.Permission.BACKUP)); assertFalse(Access.can(Access.Permission.STAFF)); assertFalse(Access.can(Access.Permission.SETTINGS))
        assertEquals("Asad", Access.userName)
        Access.signIn(staff("Imran", Role.CASHIER, "5678"))
        Access.Permission.values().forEach { assertFalse(it.name, Access.can(it)) }
        Access.signOut()
        assertNull(Access.userName)
    }

    @Test
    fun aCashiersDiscountIsCappedAndAManagersIsNot() {
        Access.signIn(staff("Imran", Role.CASHIER, "5678"))
        assertFalse(Access.discountNeedsOverride(500.0, 10_000.0))
        assertTrue(Access.discountNeedsOverride(501.0, 10_000.0))
        assertTrue(Access.discountNeedsOverride(1.0, 10.0))
        Access.signOut()
        assertFalse(Access.discountNeedsOverride(9_000.0, 10_000.0))
    }

    @Test
    fun anOverrideNeedsAManagersOwnPin() {
        val asad = staff("Asad", Role.MANAGER, "1234")
        val imran = staff("Imran", Role.CASHIER, "5678")
        assertEquals("Asad", Access.override(listOf(asad, imran), asad.id, "1234", null))
        assertNull(Access.override(listOf(asad, imran), asad.id, "1235", null))
        assertNull("a cashier's PIN opens nothing", Access.override(listOf(asad, imran), imran.id, "5678", null))
        assertNull("no owner PIN on this device", Access.override(listOf(asad), null, "0000", null))
        assertEquals(Access.OWNER, Access.override(listOf(asad), null, "0000") { it == "0000" })
        assertTrue(Access.pinMatches(asad, "1234")); assertFalse(Access.pinMatches(asad, "4321"))
        assertFalse(Access.pinMatches(staff("None", Role.CASHIER, null), ""))
    }
}
