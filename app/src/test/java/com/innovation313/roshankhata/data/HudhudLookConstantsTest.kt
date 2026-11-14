package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Hudhud's look reader lives in :shared and cannot see the app's TextSize and ThemeMode, so it keeps its own numbers for
 * the four sizes and the three themes (HudhudLook.SIZE_*, THEME_*). HudhudLookDoor hands those numbers straight to
 * TextSize.setLevel and ThemeMode.set, so a number that drifted would change the wrong setting (14 Nov 2026).
 */
class HudhudLookConstantsTest {

    @Test fun `the reader's four sizes are the app's four sizes`() {
        assertEquals(TextSize.SMALL, HudhudLook.SIZE_SMALL)
        assertEquals(TextSize.NORMAL, HudhudLook.SIZE_NORMAL)
        assertEquals(TextSize.LARGE, HudhudLook.SIZE_LARGE)
        assertEquals(TextSize.LARGEST, HudhudLook.SIZE_LARGEST)
    }

    @Test fun `the reader's three themes are the app's three themes`() {
        assertEquals(ThemeMode.LIGHT, HudhudLook.THEME_LIGHT)
        assertEquals(ThemeMode.DARK, HudhudLook.THEME_DARK)
        assertEquals(ThemeMode.SYSTEM, HudhudLook.THEME_SYSTEM)
    }
}
