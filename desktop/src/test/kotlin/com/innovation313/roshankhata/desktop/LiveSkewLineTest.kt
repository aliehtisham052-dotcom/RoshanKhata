package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.KHATA_DB_VERSION
import com.innovation313.roshankhata.data.LiveSync
import com.innovation313.roshankhata.data.LiveWire
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BC-248 (24 Nov 2026): the Live sync window names which device to update for each device whose parcel this version
 * refused ([LiveSync.skewed]) — "update this computer" when the other is newer, "update that phone" when it is older.
 */
class LiveSkewLineTest {

    private val names = object : Words {
        override fun text(name: String, vararg args: Any): String = if (args.isEmpty()) "<$name>" else "<$name|" + args.joinToString("|") + ">"
        override fun plural(name: String, count: Int, vararg args: Any): String = "<$name>"
        override fun list(name: String): List<String> = emptyList()
    }

    private fun key(line: String?) = line!!.removePrefix("<").substringBefore('|').removeSuffix(">")

    @Test
    fun `a newer device asks this computer to be updated, an older one asks for that device`() {
        assertEquals(W.live_skew_pc_this, key(skewLine(names, "Ali phone", "schema ${KHATA_DB_VERSION + 1}")))
        assertEquals(W.live_skew_pc_other, key(skewLine(names, "Ali phone", "schema ${KHATA_DB_VERSION - 1}")))
        assertEquals(W.live_skew_pc_this, key(skewLine(names, "Ali phone", "protocol ${LiveSync.PROTOCOL + 1}")))
        assertEquals(W.live_skew_pc_unknown, key(skewLine(names, "Ali phone", LiveWire.WIRE_OTHER)))
    }

    @Test
    fun `the device's name is in the line, and a reason that is no version refusal gives no line`() {
        assertTrue(skewLine(names, "Ali phone", "schema ${KHATA_DB_VERSION - 1}")!!.contains("Ali phone"))
        assertNull(skewLine(names, "Ali phone", "unreachable"))
        assertNull(skewLine(names, "Ali phone", "day-limit"))
    }

    @Test
    fun `the window reads the skewed devices and words the relay line the same way`() {
        // Gradle runs a module's tests in the module's folder; a run from the repository's root finds it under desktop/.
        val rel = "src/main/kotlin/com/innovation313/roshankhata/desktop/LiveDesk.kt"
        val file = listOf(File(rel), File("desktop/$rel")).first { it.isFile }
        val src = file.readText().replace("\r\n", "\n")
        assertTrue("the window lists LiveSync.skewed per device", src.contains("LiveSync.skewed(book.syncSql())") && src.contains("skews[p.device]"))
        assertTrue("a version refusal is no 'relay down'", src.contains("LiveSync.needsUpdate(r.why)) skewLine("))
    }
}
