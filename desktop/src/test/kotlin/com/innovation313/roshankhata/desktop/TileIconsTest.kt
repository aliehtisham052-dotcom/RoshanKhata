package com.innovation313.roshankhata.desktop

import androidx.compose.ui.res.loadXmlImageVector
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
}
