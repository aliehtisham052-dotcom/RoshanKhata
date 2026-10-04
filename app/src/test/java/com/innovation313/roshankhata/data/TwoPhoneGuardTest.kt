package com.innovation313.roshankhata.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule that decides whether a Drive upload would replace ANOTHER phone's
 * backup ([DriveBackup.writtenElsewhere]). Getting it wrong one way loses a
 * day's entries silently; the other way stops an owner over his own backup.
 */
class TwoPhoneGuardTest {

    private val me = "phone-a"
    private val other = "phone-b"

    @Test
    fun `the file this phone last uploaded is its own`() {
        assertFalse(DriveBackup.writtenElsewhere(1000L, 1000L, me, me, true))
    }

    @Test
    fun `changed since this phone last saw it - someone else wrote it`() {
        assertTrue(DriveBackup.writtenElsewhere(1000L, 2000L, other, me, true))
    }

    @Test
    fun `changed by a phone on an older version, which leaves this phone's mark in place`() {
        // The mark still says "me", but the time moved: the content is not ours.
        assertTrue(DriveBackup.writtenElsewhere(1000L, 2000L, me, me, true))
    }

    @Test
    fun `restored from it and unchanged since - a continuation, not a stranger`() {
        // After a restore this phone records the file's time; the mark is the old phone's.
        assertFalse(DriveBackup.writtenElsewhere(1000L, 1000L, other, me, true))
    }

    @Test
    fun `never seen and marked by another phone`() {
        assertTrue(DriveBackup.writtenElsewhere(0L, 2000L, other, me, true))
    }

    @Test
    fun `never seen but carrying this phone's own mark`() {
        assertFalse(DriveBackup.writtenElsewhere(0L, 2000L, me, me, false))
    }

    @Test
    fun `an unmarked backup from before this check - the phone that made backups keeps going`() {
        assertFalse(DriveBackup.writtenElsewhere(0L, 2000L, null, me, true))
    }

    @Test
    fun `an unmarked backup met by a phone that never backed up is somebody else's`() {
        assertTrue(DriveBackup.writtenElsewhere(0L, 2000L, null, me, false))
    }
}
