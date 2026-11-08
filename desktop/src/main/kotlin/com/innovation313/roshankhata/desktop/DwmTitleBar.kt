package com.innovation313.roshankhata.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference

/**
 * The window's title bar in the dark theme's colour (8 Nov 2026 review, m90): Windows 10 1809+ and 11 draw it dark when
 * asked through DwmSetWindowAttribute (DWMWA_USE_IMMERSIVE_DARK_MODE, 20; 19 on builds before 20H1). Any trouble —
 * not Windows, an older build, JNA blocked — leaves the title bar as it was.
 */
object DwmTitleBar {
    private interface Dwm : Library {
        fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: IntByReference, size: Int): Int
    }

    fun apply(window: java.awt.Window, dark: Boolean) {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) return
        runCatching {
            val hwnd = Native.getComponentPointer(window) ?: return
            val dwm = Native.load("dwmapi", Dwm::class.java)
            val value = IntByReference(if (dark) 1 else 0)
            if (dwm.DwmSetWindowAttribute(hwnd, 20, value, 4) != 0) dwm.DwmSetWindowAttribute(hwnd, 19, value, 4)
        }
    }
}
