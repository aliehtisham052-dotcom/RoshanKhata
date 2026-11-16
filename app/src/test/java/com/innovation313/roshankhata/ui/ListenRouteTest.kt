package com.innovation313.roshankhata.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where Hudhud's ears make out the words (16 Nov 2026, audit T14): with "Only listen on this phone (never online)" on, no
 * phone, network or Android version may reach the online recogniser; with it off, listening is exactly as before.
 */
class ListenRouteTest {

    private val sdks = listOf(24, 30, 31, 32, 33, 35)

    /** What the ears do, as HudhudEars.begin runs it: [ListenRoute.first] with [HudhudEars.online]'s rule. */
    private fun first(sdk: Int, onDevice: Boolean, network: Boolean, phoneOnly: Boolean) =
        ListenRoute.first(sdk, onDevice, ListenRoute.mayGoOnline(network, phoneOnly), phoneOnly)

    @Test
    fun onThisPhoneOnlyNothingIsEverOnline() {
        assertFalse(ListenRoute.mayGoOnline(networkUp = true, phoneOnly = true))
        assertFalse(ListenRoute.mayGoOnline(networkUp = false, phoneOnly = true))
        for (sdk in sdks) for (onDevice in listOf(true, false)) for (network in listOf(true, false)) {
            val way = first(sdk, onDevice, network, phoneOnly = true)
            assertTrue("$sdk $onDevice $network -> $way", way != ListenRoute.Way.ONLINE)
            // NOT_HERE goes through startOnline with online = false: it says why and opens nothing.
        }
        // After an Android 13 phone says what it has: never online, whatever it has.
        for (installed in listOf(true, false)) for (downloadable in listOf(true, false)) {
            val next = ListenRoute.afterSupport(installed, downloadable, online = ListenRoute.mayGoOnline(true, true))
            assertTrue(next != ListenRoute.Next.GO_ONLINE)
        }
        assertEquals(ListenRoute.Next.LISTEN_HERE, ListenRoute.supportUnknown(ListenRoute.mayGoOnline(true, true)))
    }

    @Test
    fun theRuleItselfNeverSaysOnlineOnThisPhoneOnly() {
        // Even handed "online" by a caller that forgot the switch (lesson 129: this guard alone decides it).
        for (sdk in sdks) for (onDevice in listOf(true, false)) {
            val way = ListenRoute.first(sdk, onDevice, online = true, phoneOnly = true)
            assertTrue("$sdk $onDevice -> $way", way != ListenRoute.Way.ONLINE)
        }
        assertEquals(ListenRoute.Way.ON_PHONE, ListenRoute.first(31, onDeviceAvailable = true, online = true, phoneOnly = true))
    }

    @Test
    fun anAndroid12PhoneWithInternetListensOnItselfWhenAsked() {
        assertEquals(ListenRoute.Way.ON_PHONE, first(31, onDevice = true, network = true, phoneOnly = true))
        assertEquals(ListenRoute.Way.ON_PHONE, first(32, onDevice = true, network = true, phoneOnly = true))
        // Without an on-device recogniser there is nothing to listen with: said, not sent.
        assertEquals(ListenRoute.Way.NOT_HERE, first(30, onDevice = false, network = true, phoneOnly = true))
    }

    @Test
    fun aMissingPackIsOfferedNotSentOnline() {
        val online = ListenRoute.mayGoOnline(networkUp = true, phoneOnly = true)
        assertEquals(ListenRoute.Next.PACK_MISSING, ListenRoute.afterSupport(installed = false, downloadable = true, online = online))
        assertEquals(ListenRoute.Next.NOT_HERE, ListenRoute.afterSupport(installed = false, downloadable = false, online = online))
        assertEquals(ListenRoute.Next.LISTEN_HERE, ListenRoute.afterSupport(installed = true, downloadable = true, online = online))
    }

    @Test
    fun switchedOffListeningIsAsBefore() {
        // The rules HudhudEars had before the switch, written out: Android 13+ asks the phone first; Android 12 listens on
        // itself only without internet; otherwise online while there is internet.
        assertEquals(ListenRoute.Way.ASK_PHONE, first(33, onDevice = true, network = true, phoneOnly = false))
        assertEquals(ListenRoute.Way.ONLINE, first(32, onDevice = true, network = true, phoneOnly = false))
        assertEquals(ListenRoute.Way.ON_PHONE, first(32, onDevice = true, network = false, phoneOnly = false))
        assertEquals(ListenRoute.Way.ONLINE, first(30, onDevice = false, network = true, phoneOnly = false))
        assertEquals(ListenRoute.Way.NOT_HERE, first(30, onDevice = false, network = false, phoneOnly = false))
        assertEquals(ListenRoute.Way.ONLINE, first(33, onDevice = false, network = true, phoneOnly = false))
        assertEquals(ListenRoute.Next.GO_ONLINE, ListenRoute.afterSupport(installed = false, downloadable = true, online = true))
        assertEquals(ListenRoute.Next.PACK_MISSING, ListenRoute.afterSupport(installed = false, downloadable = true, online = false))
        assertEquals(ListenRoute.Next.GO_ONLINE, ListenRoute.supportUnknown(online = true))
        assertTrue(ListenRoute.mayGoOnline(networkUp = true, phoneOnly = false))
    }
}
