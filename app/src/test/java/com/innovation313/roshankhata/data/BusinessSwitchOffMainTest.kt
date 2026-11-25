package com.innovation313.roshankhata.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * BC-246 (the ANR audit): a shop switch commits prefs, closes Room under its lock and cancels the shade's notices,
 * so it never runs on the main thread. A SOURCE check, not a runtime one: Robolectric's main looper is the test
 * thread, so "which thread ran it" cannot tell a hop to IO from none. Holds that [Businesses.switchOffMain] hops to
 * Dispatchers.IO and that no screen calls the blocking [Businesses.switchTo] or hops to Main for it.
 */
class BusinessSwitchOffMainTest {
    private val root: File = listOf("src/main/java", "app/src/main/java").map(::File).first { it.isDirectory }

    private fun source(vararg parts: String) =
        File(root, "com/innovation313/roshankhata/" + parts.joinToString("/")).readText()

    @Test fun `switchOffMain runs the switch on the IO dispatcher`() {
        val s = source("data", "Businesses.kt")
        val body = s.substringAfter("suspend fun switchOffMain").substringBefore("\n    }")
        assertTrue(body.contains("withContext(Dispatchers.IO)"))
        assertTrue(body.contains("switchTo("))
    }

    @Test fun `no screen calls the blocking switch or hops to Main for it`() {
        val screens = root.walkTopDown().filter { it.extension == "kt" && !it.path.endsWith("data/Businesses.kt") }
        for (f in screens) {
            val t = f.readText()
            assertFalse("${f.name} calls Businesses.switchTo", Regex("""Businesses\.switchTo\(""").containsMatchIn(t))
        }
    }

    @Test fun `the two switching screens use switchOffMain`() {
        assertTrue(source("BusinessSwitchActivity.kt").contains("Businesses.switchOffMain("))
        assertTrue(source("BackupActivity.kt").contains("Businesses.switchOffMain("))
    }
}
