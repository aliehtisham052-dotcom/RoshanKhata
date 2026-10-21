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
    fun out() = Access.resetForTest()

    /** A device's keeping, in memory. */
    private class Prefs : Access.DeviceStore {
        val map = HashMap<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
    }

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

    @Test
    fun aCounterOpensLockedAndOnlyASignInOpensIt() {
        val prefs = Prefs()
        Access.store = prefs
        assertTrue("the owner's own device: the owner", Access.isOwner)
        Access.setCounterMode(true)
        assertTrue("turning it on leaves the owner in", Access.isOwner)
        Access.signOut()
        assertTrue(Access.locked)
        Access.Permission.values().forEach { assertFalse("locked: ${it.name}", Access.can(it)) }
        // The next start of the app on this device: still a counter, still locked.
        Access.store = prefs
        assertTrue(Access.counterMode); assertTrue(Access.locked)
        Access.signIn(staff("Imran", Role.CASHIER, "5678"))
        assertFalse(Access.locked); assertFalse(Access.can(Access.Permission.EDIT_BOOK)); assertFalse(Access.can(Access.Permission.REPORTS))
        Access.signIn(staff("Asad", Role.MANAGER, "1234"))
        assertTrue(Access.can(Access.Permission.EDIT_BOOK)); assertTrue(Access.can(Access.Permission.REPORTS))
        assertFalse(Access.can(Access.Permission.BACKUP))
        Access.signInOwner()
        Access.Permission.values().forEach { assertTrue(it.name, Access.can(it)) }
        Access.setCounterMode(false)
        assertFalse(Access.counterMode); assertNull(prefs.get("access_counter_mode"))
        assertTrue(Access.isOwner)
    }

    @Test
    fun fiveWrongPinsWaitAndTheWaitGrowsAndSurvivesARestart() {
        val prefs = Prefs()
        Access.store = prefs
        val now = 1_000_000L
        var waits = 0
        repeat(4) { assertEquals(false, Access.tryPin("0000", now, { waits++ }) { it == "1234" }) }
        assertEquals(0L, Access.waitMillis(now))
        assertEquals(false, Access.tryPin("0000", now, { waits++ }) { it == "1234" })
        assertEquals(1, waits)
        assertEquals(30_000L, Access.waitMillis(now))
        assertNull("nothing is compared while waiting", Access.tryPin("1234", now + 1_000) { it == "1234" })
        // A restart does not clear it: the device keeps the count.
        Access.store = prefs
        assertEquals(29, Access.waitSeconds(now + 1_000))
        val later = now + 31_000
        repeat(5) { Access.tryPin("0000", later, { waits++ }) { it == "1234" } }
        assertEquals(2, waits)
        assertEquals("the second wait is twice the first", 60_000L, Access.waitMillis(later))
        assertEquals(true, Access.tryPin("1234", later + 61_000) { it == "1234" })
        assertEquals(0L, Access.waitMillis(later + 61_000))
        assertNull(prefs.get("access_pin_misses"))
    }

    @Test
    fun aRecoveryCodeIsEightPlainCharactersAndTypedLooselyStillMatches() {
        val code = RecoveryCode.next()
        assertTrue(code, Regex("[A-Z2-9]{4}-[A-Z2-9]{4}").matches(code))
        assertFalse(code.any { it in "01OIL" })
        assertEquals(RecoveryCode.normal(code), RecoveryCode.normal(" " + code.lowercase().replace("-", " ") + " "))
        val salt = PinHash.salt()
        val pin = Access.OwnerPin(PinHash.hash("2468", salt), salt)
        assertTrue(pin.matches("2468")); assertFalse(pin.matches("2469"))
    }
}
