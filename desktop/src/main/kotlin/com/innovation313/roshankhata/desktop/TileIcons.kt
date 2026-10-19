package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.loadXmlImageVector
import org.xml.sax.InputSource
import java.io.StringReader

/**
 * The phone's Home tile icons on the computer. The vector files and
 * colors.xml (day and night) are copied from app/src/main/res at build time
 * (desktop/build.gradle.kts, syncTileIcons); the icons' @color names are
 * resolved here, night over day as Android does, before the vector is read.
 */
object TileIcons {

    private fun read(path: String): String? =
        TileIcons::class.java.classLoader.getResourceAsStream(path)?.use { it.readBytes().toString(Charsets.UTF_8) }

    private val colourLine = Regex("""<color\s+name="([^"]+)"\s*>\s*([^<\s]+)\s*</color>""")

    private fun table(path: String): Map<String, String> =
        read(path)?.let { text -> colourLine.findAll(text).associate { it.groupValues[1] to it.groupValues[2] } } ?: emptyMap()

    private val day by lazy { table("tiles/values/colors.xml") }
    private val night by lazy { day + table("tiles/values-night/colors.xml") }

    /** A colour by name, following @color/ references; null when it is not a plain #hex in the end. */
    fun hex(name: String, dark: Boolean): String? {
        val t = if (dark) night else day
        var v: String? = t[name]
        var hops = 0
        while (v != null && v.startsWith("@color/") && hops++ < 5) v = t[v.removePrefix("@color/")]
        return v?.takeIf { it.startsWith("#") }
    }

    fun color(name: String, dark: Boolean): Color? = hex(name, dark)?.let { h ->
        val s = h.removePrefix("#")
        val argb = when (s.length) {
            6 -> 0xFF000000 or s.toLong(16)
            8 -> s.toLong(16)
            else -> return null
        }
        Color(argb.toInt())
    }

    /** The vector's text with every @color/x made a #hex in the theme's colours. */
    fun xml(icon: String, dark: Boolean): String? = read("tiles/drawable/$icon.xml")?.replace(Regex("@color/(\\w+)")) { m ->
        hex(m.groupValues[1], dark) ?: "#FF888888"
    }
}

/** The icon [icon] (ic_tile_khata ...) as a painter in the system's light or dark, or null when missing. */
@Composable
fun tileIcon(icon: String): Painter? {
    val dark = isSystemInDarkTheme()
    val density = LocalDensity.current
    val vector = remember(icon, dark, density) {
        TileIcons.xml(icon, dark)?.let { runCatching { loadXmlImageVector(InputSource(StringReader(it)), density) }.getOrNull() }
    } ?: return null
    return rememberVectorPainter(vector)
}
