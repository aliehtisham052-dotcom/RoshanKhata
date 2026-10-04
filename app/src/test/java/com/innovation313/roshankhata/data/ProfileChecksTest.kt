package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileChecksTest {

    @Test fun ibanValidWithAndWithoutSpaces() {
        assertFalse(ProfileChecks.ibanLooksWrong("PK36SCBL0000001123456702"))
        assertFalse(ProfileChecks.ibanLooksWrong("pk36 scbl 0000 0011 2345 6702"))
    }

    @Test fun ibanWrongShapes() {
        assertTrue(ProfileChecks.ibanLooksWrong("bvbb"))
        assertTrue(ProfileChecks.ibanLooksWrong("PK36SCBL000000112345670"))   // 23
        assertTrue(ProfileChecks.ibanLooksWrong("GB36SCBL0000001123456702"))  // not PK
    }

    @Test fun plainAccountNumberIsNotJudged() {
        assertFalse(ProfileChecks.ibanLooksWrong("0123456789012"))
        assertFalse(ProfileChecks.ibanLooksWrong(""))
    }

    @Test fun mobileForms() {
        assertFalse(ProfileChecks.mobileLooksWrong("03001234567"))
        assertFalse(ProfileChecks.mobileLooksWrong("0300-1234567"))
        assertFalse(ProfileChecks.mobileLooksWrong("+92 300 1234567"))
        assertFalse(ProfileChecks.mobileLooksWrong("923001234567"))
        assertFalse(ProfileChecks.mobileLooksWrong(""))
        assertTrue(ProfileChecks.mobileLooksWrong("999"))
        assertTrue(ProfileChecks.mobileLooksWrong("0521234567"))
    }

    @Test fun initials() {
        assertEquals("BT", ProfileChecks.initials("Bhatti Traders"))
        assertEquals("R", ProfileChecks.initials("roshan"))
        assertEquals("AB", ProfileChecks.initials("  al   bhatti  and sons "))
    }
    @Test
    fun otherCountriesGetTheGeneralShapeChecks() {
        // A UK or Gulf IBAN is fine outside the Pakistani rule, a 7-15 digit mobile too.
        assertFalse(ProfileChecks.ibanLooksWrong("GB29 NWBK 6016 1331 9268 19", pakistani = false))
        assertFalse(ProfileChecks.ibanLooksWrong("AE07 0331 2345 6789 0123 456", pakistani = false))
        assertTrue(ProfileChecks.ibanLooksWrong("GB29 NWBK", pakistani = false))
        assertFalse(ProfileChecks.mobileLooksWrong("+971501234567", pakistani = false))
        assertFalse(ProfileChecks.mobileLooksWrong("9876543210", pakistani = false))
        assertTrue(ProfileChecks.mobileLooksWrong("12345", pakistani = false))
        // The Pakistani rule is unchanged for a rupee shop.
        assertTrue(ProfileChecks.ibanLooksWrong("GB29 NWBK 6016 1331 9268 19"))
    }
}
