package com.innovation313.roshankhata.ui

import com.innovation313.roshankhata.ui.ListenChoice.Box
import com.innovation313.roshankhata.ui.ListenChoice.Lacking
import com.innovation313.roshankhata.ui.ListenChoice.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where speech is understood and who said so (25 Nov 2026, D10): audio goes online only after the owner's yes; the pack
 * on the phone is used silently; the question is asked once, only where a yes could be used; a refusal never blocks
 * typing. Pure, so it runs without Android (ListenChoice has no imports).
 */
class ListenChoiceTest {

    private val bools = listOf(true, false)

    @Test
    fun nothingIsOnlineWithoutAYes() {
        for (mode in Mode.values()) for (network in bools) {
            assertEquals("$mode $network", mode == Mode.ONLINE && network, ListenChoice.mayGoOnline(mode, network))
        }
        // An undecided phone and a "type instead" phone are the same to every online gate.
        assertTrue(ListenChoice.phoneOnly(Mode.UNDECIDED))
        assertTrue(ListenChoice.phoneOnly(Mode.PHONE))
        assertFalse(ListenChoice.phoneOnly(Mode.ONLINE))
    }

    @Test
    fun theBoxNeverGoesOnlineWithoutAYes() {
        for (pack in bools) for (mode in Mode.values()) for (network in bools) for (hear in bools) {
            val box = ListenChoice.box(pack, mode, network, hear)
            if (box == Box.ONLINE) assertEquals("$pack $mode $network $hear", Mode.ONLINE, mode)
            // The online box is also never chosen when the phone has the pack: that is LOCAL.
            if (pack) assertEquals(Box.LOCAL, box)
        }
    }

    @Test
    fun aPhoneWithThePackListensOnItselfSilentlyWhateverWasChosen() {
        for (mode in Mode.values()) for (network in bools) for (hear in bools) {
            assertEquals(Box.LOCAL, ListenChoice.box(true, mode, network, hear))
        }
    }

    @Test
    fun noPackAndNothingChosenAsksOnceWhereAYesCouldBeUsed() {
        assertEquals(Lacking.ASK, ListenChoice.lacking(Mode.UNDECIDED, networkUp = true, canHear = true))
        assertEquals(Box.ASK, ListenChoice.box(false, Mode.UNDECIDED, networkUp = true, canHear = true))
        // No internet: a yes would be no use; no speech service at all: nothing to ask about. Said, not asked.
        assertEquals(Lacking.TYPE, ListenChoice.lacking(Mode.UNDECIDED, networkUp = false, canHear = true))
        assertEquals(Lacking.TYPE, ListenChoice.lacking(Mode.UNDECIDED, networkUp = true, canHear = false))
    }

    @Test
    fun anAnsweredPhoneIsNeverAskedAgain() {
        for (network in bools) for (hear in bools) {
            assertTrue(ListenChoice.lacking(Mode.PHONE, network, hear) == Lacking.TYPE)
            assertTrue(ListenChoice.lacking(Mode.ONLINE, network, hear) != Lacking.ASK)
        }
        assertEquals(Lacking.ONLINE, ListenChoice.lacking(Mode.ONLINE, networkUp = true, canHear = true))
        assertEquals(Lacking.TYPE, ListenChoice.lacking(Mode.ONLINE, networkUp = false, canHear = true))
    }

    @Test
    fun theAnswersAreKept() {
        assertEquals(Mode.ONLINE, ListenChoice.answered(yesOnline = true))
        assertEquals(Mode.PHONE, ListenChoice.answered(yesOnline = false))
        // "Type instead" then asks nothing more: the same phone, no pack, with internet.
        assertEquals(Box.TYPE, ListenChoice.box(false, ListenChoice.answered(false), networkUp = true, canHear = true))
        assertEquals(Box.ONLINE, ListenChoice.box(false, ListenChoice.answered(true), networkUp = true, canHear = true))
    }

    @Test
    fun theSwitchOnKeepsThePhoneAndOffIsTheYes() {
        assertEquals(Mode.PHONE, ListenChoice.afterSwitch(on = true))
        assertEquals(Mode.ONLINE, ListenChoice.afterSwitch(on = false))
        // After the switch is turned back on the owner is asked again by the next microphone only if he never said no:
        // "on" is a choice, so no question comes.
        assertEquals(Box.TYPE, ListenChoice.box(false, ListenChoice.afterSwitch(true), networkUp = true, canHear = true))
    }

    @Test
    fun whatAPhoneRemembersReadsOlderSettingsSafely() {
        assertEquals(Mode.UNDECIDED, ListenChoice.fromStored(null, null))
        // The 16 Nov switch: on was a choice for the phone; off was never a yes to "your voice goes to Google".
        assertEquals(Mode.PHONE, ListenChoice.fromStored(null, true))
        assertEquals(Mode.UNDECIDED, ListenChoice.fromStored(null, false))
        // This feature's own value wins over the old switch, and an unknown value is nothing chosen.
        assertEquals(Mode.ONLINE, ListenChoice.fromStored("ONLINE", true))
        assertEquals(Mode.PHONE, ListenChoice.fromStored("PHONE", false))
        assertEquals(Mode.UNDECIDED, ListenChoice.fromStored("garbage", null))
        for (m in Mode.values()) assertEquals(m, ListenChoice.fromStored(m.name, null))
    }

    @Test
    fun everyCombinationHasExactlyOneAnswerAndOnlyAYesGoesOnline() {
        // The whole table (lesson 129: the guard alone decides it).
        for (pack in bools) for (mode in Mode.values()) for (network in bools) for (hear in bools) {
            val box = ListenChoice.box(pack, mode, network, hear)
            val onlineAllowed = mode == Mode.ONLINE && network && !pack
            assertEquals("$pack $mode $network $hear", onlineAllowed, box == Box.ONLINE)
            assertEquals("$pack $mode $network $hear", !pack && mode == Mode.UNDECIDED && network && hear, box == Box.ASK)
        }
    }
}
