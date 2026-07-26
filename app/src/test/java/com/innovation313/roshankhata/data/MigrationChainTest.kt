package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guard against the update that crashes every phone.
 *
 * This database deliberately has no destructive fallback: if an update ever
 * reached a phone with a schema version but without the migration to it, Room
 * would refuse to open and the app would crash on launch — on every phone,
 * every time, until fixed. The data would survive; the trust would not.
 *
 * So the rule is enforced where it cannot be forgotten: here, in a test that
 * runs on every single build. KHATA_DB_VERSION is the one place the version
 * lives, and this test fails the build the moment the migration chain stops
 * short of it. Bumping the version without writing its migration does not
 * produce a broken APK — it produces a red build and no APK at all.
 */
class MigrationChainTest {

    @Test
    fun `the chain runs unbroken from the first version to the current one`() {
        val steps = ALL_MIGRATIONS.map { it.startVersion to it.endVersion }

        // Every step moves one version forward. A jump would strand phones on
        // the versions it skipped.
        steps.forEach { (from, to) ->
            assertEquals("migration $from->$to must be a single step", from + 1, to)
        }

        // Sorted, the steps must join end to start with no gap and no repeat:
        // 1->2, 2->3, ... right up to the current version. A phone on ANY past
        // version must have a road to today.
        val sorted = steps.sortedBy { it.first }
        assertEquals("chain must begin at version 1", 1, sorted.first().first)
        sorted.zipWithNext().forEach { (a, b) ->
            assertEquals("gap after ${a.first}->${a.second}", a.second, b.first)
        }
        assertEquals(
            "chain must reach KHATA_DB_VERSION ($KHATA_DB_VERSION) — " +
                "a version bump shipped without its migration",
            KHATA_DB_VERSION,
            sorted.last().second
        )
    }

    @Test
    fun `no version is migrated twice`() {
        val starts = ALL_MIGRATIONS.map { it.startVersion }
        assertTrue(
            "two migrations claim the same starting version",
            starts.size == starts.distinct().size
        )
    }
}
