package com.innovation313.roshankhata.desktop

import androidx.compose.ui.res.loadXmlImageVector
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xml.sax.InputSource
import java.io.StringReader

/**
 * Every section's picture and tint arrives from the phone's res folder and
 * reads, in day and night: a missing or unreadable icon would otherwise
 * show as a blank tile, silently.
 */
class TileIconsTest {
    @Test
    fun `every section has its phone icon and tile colour, day and night`() {
        for (dark in listOf(false, true)) for (s in Section.entries) {
            val xml = TileIcons.xml(s.icon, dark)
            assertNotNull("${s.icon} missing", xml)
            assertFalse("${s.icon} has an unresolved colour", xml!!.contains("@color/"))
            assertNotNull("${s.icon} does not read", loadXmlImageVector(InputSource(StringReader(xml)), Density(1f)))
            assertNotNull("${s.tile} has no colour (dark=$dark)", TileIcons.color(s.tile, dark))
        }
    }

    @Test
    fun `a one-colour glyph has its ink, day and night, and a folded group its picture`() {
        for (dark in listOf(false, true)) {
            for (s in Section.entries) s.ink?.let { assertNotNull("${s.name}'s ink $it (dark=$dark)", TileIcons.color(it, dark)) }
            for (g in Group.entries.filter { it.folds }) {
                val icon = g.icon
                assertNotNull("${g.name} has no picture", icon)
                val xml = TileIcons.xml(icon!!, dark)
                assertNotNull("$icon missing", xml)
                assertNotNull("$icon does not read", loadXmlImageVector(InputSource(StringReader(xml!!)), Density(1f)))
                assertNotNull("${g.name}'s tint", TileIcons.color(g.tile ?: "", dark))
                g.ink?.let { assertNotNull("${g.name}'s ink $it", TileIcons.color(it, dark)) }
            }
        }
    }

    @Test
    fun `every glyph copied from the phone reads, day and night`() {
        // Marhala 4 (25 Oct 2026): the buttons' glyphs too, not only the rail's pictures — the Khata page's tools
        // came from the phone's res folder, and one that did not read would be an empty button, silently.
        val dir = TileIconsTest::class.java.classLoader.getResource("tiles/drawable")
        assertNotNull("tiles/drawable is not on the classpath", dir)
        assertTrue("tiles/drawable is not a folder: $dir", dir!!.protocol == "file")
        val names = java.io.File(dir.toURI()).listFiles { f -> f.name.endsWith(".xml") }!!.map { it.name.removeSuffix(".xml") }.sorted()
        for (needed in listOf("ic_action_whatsapp", "ic_filter_sliders", "ic_sort_arrows", "ic_download", "ic_check_circle", "ic_truck", "ic_people", "ic_delete")) {
            assertTrue("$needed was not copied", needed in names)
        }
        for (dark in listOf(false, true)) for (n in names) {
            val xml = TileIcons.xml(n, dark)
            assertNotNull("$n missing", xml)
            assertFalse("$n has an unresolved colour", xml!!.contains("@color/") || xml.contains("@android:color/"))
            assertNotNull("$n does not read", loadXmlImageVector(InputSource(StringReader(xml)), Density(1f)))
        }
    }

    @Test
    fun `no two places in the rail wear the same picture`() {
        // 25 Oct 2026, the audit: eleven places shared a picture (Counter and Invoices, Staff and Who did what...).
        val pictures = Section.entries.map { it.icon } + Group.entries.mapNotNull { it.icon }
        val twice = pictures.groupBy { it }.filterValues { it.size > 1 }.keys
        assertTrue("worn twice: $twice", twice.isEmpty())
    }

    @Test
    fun `every trade has its phone icon and both colours, day and night`() {
        for (dark in listOf(false, true)) for (key in Trades.KEYS) {
            val low = key.lowercase()
            val xml = TileIcons.xml("ic_trade_$low", dark)
            assertNotNull("ic_trade_$low missing", xml)
            assertFalse("ic_trade_$low has an unresolved colour", xml!!.contains("@color/") || xml.contains("@android:color/"))
            assertNotNull("ic_trade_$low does not read", loadXmlImageVector(InputSource(StringReader(xml)), Density(1f)))
            assertNotNull("trade_${low}_bg (dark=$dark)", TileIcons.color("trade_${low}_bg", dark))
            assertNotNull("trade_${low}_fg (dark=$dark)", TileIcons.color("trade_${low}_fg", dark))
        }
    }
}
