package com.innovation313.roshankhata.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The shared copies of the owner's check list (16 Nov 2026, audit T16): the list holds customers' names, so a copy written
 * to cache/hudhud/ for the share sheet must go again — after [CheckLog.SHARE_KEEP_MS], and only files the list wrote.
 */
class CheckLogCopiesTest {

    private val now = 1_800_000_000_000L
    private val keep = CheckLog.SHARE_KEEP_MS

    @Test
    fun aCopyOlderThanTheKeepIsSwept() {
        val files = listOf(
            "hudhud_check_20261116_0900.tsv" to now - keep - 1,
            "hudhud_check_20261116_0905.tsv" to now - keep
        )
        assertEquals(files.map { it.first }, CheckLog.staleCopies(files, now, keep))
    }

    @Test
    fun aCopyJustHandedOverStays() {
        val files = listOf("hudhud_check_20261116_0959.tsv" to now - 60_000L)
        assertEquals(emptyList<String>(), CheckLog.staleCopies(files, now, keep))
    }

    @Test
    fun clearTakesEveryCopyAtOnce() {
        val files = listOf("hudhud_check_20261116_0959.tsv" to now)
        assertEquals(listOf("hudhud_check_20261116_0959.tsv"), CheckLog.staleCopies(files, now, 0L))
    }

    @Test
    fun aCopyDatedInTheFutureIsNeverKeptForEver() {
        // A clock set back: waiting would never make it old.
        val files = listOf("hudhud_check_20261116_0959.tsv" to now + 3_600_000L)
        assertEquals(listOf("hudhud_check_20261116_0959.tsv"), CheckLog.staleCopies(files, now, keep))
    }

    @Test
    fun anotherFileInTheFolderIsNeverTouched() {
        val old = now - 10 * keep
        val files = listOf(
            "voice_note.tsv" to old,
            "hudhud_check_old.txt" to old,
            "my_hudhud_check_1.tsv" to old,
            "hudhud_check_1.tsv" to old
        )
        assertEquals(listOf("hudhud_check_1.tsv"), CheckLog.staleCopies(files, now, keep))
    }

    @Test
    fun theShareNamesItsCopyByTheSameRule() {
        // HudhudCheck.share writes COPY_PREFIX + time + COPY_SUFFIX; the sweep must recognise exactly that name.
        val name = CheckLog.COPY_PREFIX + "20261116_1000" + CheckLog.COPY_SUFFIX
        assertEquals(listOf(name), CheckLog.staleCopies(listOf(name to now - keep), now, keep))
    }
}
