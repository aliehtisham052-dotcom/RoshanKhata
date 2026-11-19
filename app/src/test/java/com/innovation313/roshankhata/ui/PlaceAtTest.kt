package com.innovation313.roshankhata.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * BC-237: a view laid over a picture by the picture's own left stood beside its place in a window that reads right
 * to left. The posters' live 3D hero did on the owner's Urdu phone (19 Nov 2026): the clock, the mosque and the
 * crate each stood their own left margin too far to the right, because a FrameLayout hangs a child with no gravity
 * from its START, which is the right edge there, and never reads the left margin. The seal's stamp on a statement
 * was placed the same way.
 *
 * [PlaceAt.params] names the side. The last test is the class itself, kept so the reason stays proved: the bare
 * margin the screens used to set lands at the right edge.
 */
@RunWith(AndroidJUnit4::class)
class PlaceAtTest {

    private val app: Context get() = ApplicationProvider.getApplicationContext()

    /** The app declares supportsRtl in its manifest; said here too, so the test does not rest on how the test runner reads it. */
    @Before
    fun readsBothWays() {
        app.applicationInfo.flags = app.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
    }

    /** A 500 x 900 frame reading [direction], with one child laid out by [params]. */
    private fun laidOut(direction: Int, params: FrameLayout.LayoutParams): View {
        val frame = FrameLayout(app).apply { layoutDirection = direction }
        val child = View(app)
        frame.addView(child, params)
        frame.measure(
            View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY))
        frame.layout(0, 0, 500, 900)
        assertEquals("the frame must read the way the test asks", direction, frame.layoutDirection)
        return child
    }

    @Test
    fun `a placed view lies at the picture's own left whichever way the window reads`() {
        for (direction in listOf(View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL)) {
            val child = laidOut(direction, PlaceAt.params(left = 120, top = 60, width = 200, height = 100))
            assertEquals("left, direction $direction", 120, child.left)
            assertEquals("top, direction $direction", 60, child.top)
            assertEquals("width, direction $direction", 200, child.width)
            assertEquals("height, direction $direction", 100, child.height)
        }
    }

    @Test
    fun `a hero in the middle of the picture stays in the middle in a right-to-left window`() {
        // The clock's own numbers at the screen's scale: 240..840 of 1080 on a 500 px frame.
        val s = 500 / 1080f
        val child = laidOut(View.LAYOUT_DIRECTION_RTL,
            PlaceAt.params((240 * s).toInt(), (126 * s).toInt(), (600 * s).toInt(), (600 * s).toInt()))
        val centre = child.left + child.width / 2
        assertEquals("the clock's centre is the frame's", 250f, centre.toFloat(), 1.5f)
    }

    @Test
    fun `the class - a bare left margin is hung from the right edge where the window reads right to left`() {
        fun bare() = FrameLayout.LayoutParams(200, 100).apply { leftMargin = 120; topMargin = 60 }
        assertEquals("left to right the bare margin is read", 120, laidOut(View.LAYOUT_DIRECTION_LTR, bare()).left)
        // 500 wide, 200 of child, no right margin: the child ends at the right edge, 180 px from where it was put.
        assertEquals("right to left it is not: the child hangs from the right", 300, laidOut(View.LAYOUT_DIRECTION_RTL, bare()).left)
    }
}
