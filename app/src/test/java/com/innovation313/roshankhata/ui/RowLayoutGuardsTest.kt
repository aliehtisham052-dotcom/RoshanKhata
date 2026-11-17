package com.innovation313.roshankhata.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Matrix
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Two row guards that were fixed with no test (the register's NONE rows, 17 Nov 2026).
 *
 * BC-074: a recycled list row kept the transform an animation left on it (a cut-off shared-element
 * return, an item animator's move): cards over each other and gaps between, the owner's photos of
 * 1 and 13 Nov. [RowToScreen.plain] must undo every one of them, not only visibility.
 *
 * BC-065: a name beside a figure on a 360 dp phone at large text got a few letters' width and broke a
 * letter a line. Above [TextFit.STACK_SCALE] [TextFit.stackWhenLarge] puts the figure under the name;
 * at the normal size the row is left as designed.
 */
@RunWith(AndroidJUnit4::class)
class RowLayoutGuardsTest {

    private val app: Context get() = ApplicationProvider.getApplicationContext()

    private fun scaled(scale: Float): Context =
        app.createConfigurationContext(Configuration(app.resources.configuration).apply { fontScale = scale })

    @Test
    fun `a recycled row is made plain whole with every transform an animation leaves`() {
        val row = View(app).apply {
            transitionName = "row"
            visibility = View.INVISIBLE
            alpha = 0.2f
            translationX = 30f; translationY = -40f; translationZ = 6f
            scaleX = 0.7f; scaleY = 1.3f
            rotation = 9f; rotationX = 4f; rotationY = 5f
            clipBounds = Rect(0, 0, 10, 10)
            if (Build.VERSION.SDK_INT >= 29) animationMatrix = Matrix().apply { setTranslate(5f, 5f) }
        }
        RowToScreen.plain(row)
        assertNull(row.transitionName)
        assertEquals(View.VISIBLE, row.visibility)
        assertEquals(1f, row.alpha, 0f)
        assertEquals(0f, row.translationX, 0f)
        assertEquals(0f, row.translationY, 0f)
        assertEquals(0f, row.translationZ, 0f)
        assertEquals(1f, row.scaleX, 0f)
        assertEquals(1f, row.scaleY, 0f)
        assertEquals(0f, row.rotation, 0f)
        assertEquals(0f, row.rotationX, 0f)
        assertEquals(0f, row.rotationY, 0f)
        assertNull(row.clipBounds)
        if (Build.VERSION.SDK_INT >= 29) assertNull(row.animationMatrix)
    }

    /** name column | figure column, side by side as the Khata list's row lays them out. */
    private class Row(ctx: Context) {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val name = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; addView(TextView(ctx).apply { text = "Test Supplier" }) }
        val figure = TextView(ctx).apply { text = "Rs 4,000" }
        init { row.addView(name); row.addView(figure) }
    }

    @Test
    fun `at large text the figure goes under the name`() {
        val r = Row(scaled(TextFit.STACK_SCALE + 0.1f))
        TextFit.stackWhenLarge(r.name, r.figure)
        assertSame(r.name, r.figure.parent)
        assertEquals(1, r.row.childCount)
        assertEquals(View.TEXT_ALIGNMENT_VIEW_START, r.figure.textAlignment)
    }

    @Test
    fun `at the normal size and just under the line the row stays as designed`() {
        for (scale in listOf(1.0f, TextFit.STACK_SCALE - 0.01f)) {
            val r = Row(scaled(scale))
            TextFit.stackWhenLarge(r.name, r.figure)
            assertSame("$scale", r.row, r.figure.parent)
            assertEquals("$scale", 2, r.row.childCount)
        }
    }

    @Test
    fun `asked twice it moves the figure once`() {
        val r = Row(scaled(TextFit.STACK_SCALE + 0.3f))
        TextFit.stackWhenLarge(r.name, r.figure)
        TextFit.stackWhenLarge(r.name, r.figure)
        assertSame(r.name, r.figure.parent)
        assertEquals(2, r.name.childCount)
    }
}
