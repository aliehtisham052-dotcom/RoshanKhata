package com.innovation313.roshankhata.ui

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.Digits
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * 3 Nov 2026 audit, M41: a quantity, rate or price box takes the language's decimal mark and every keyboard's
 * digits; Android's own decimal filter made an Indonesian "2,5" into 25.
 */
@RunWith(AndroidJUnit4::class)
class DecimalBoxesTest {

    private val before: Locale = Locale.getDefault()

    @After
    fun putBack() = Locale.setDefault(before)

    private fun form(): View {
        val themed = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_RoshanKhata)
        return LayoutInflater.from(themed).inflate(R.layout.dialog_add_entry, null).also { TextFit.relax(it) }
    }

    @Test
    fun anIndonesianQuantityKeepsItsComma() {
        Locale.setDefault(Locale("in", "ID"))
        val qty = form().findViewById<EditText>(R.id.etQuantity)
        qty.setText("2,5")
        assertEquals("2,5", qty.text.toString())
        assertEquals(2.5, Digits.parse(qty.text)!!, 1e-9)
        qty.setText("4.000")
        assertEquals("four thousand, as written there", 4000.0, Digits.parse(qty.text)!!, 1e-9)
    }

    @Test
    fun elsewhereTheCommaStaysOutAndOtherDigitsComeIn() {
        Locale.setDefault(Locale.ENGLISH)
        val form = form()
        val qty = form.findViewById<EditText>(R.id.etQuantity)
        qty.setText("2,5")
        assertEquals("as before: a comma never reaches an English box", "25", qty.text.toString())
        qty.setText("٢٫٥")
        assertEquals(2.5, Digits.parse(qty.text)!!, 1e-9)
        qty.setText("۱۲")
        assertEquals(12.0, Digits.parse(qty.text)!!, 1e-9)
        // The amount box keeps its own characters: the pad's sums are not a decimal box's.
        val amount = form.findViewById<EditText>(R.id.etAmount)
        amount.setText("12+3")
        assertEquals("12+3", amount.text.toString())
    }

    @Test
    fun aSecondMarkOrAStraySignIsRefusedAsAndroidRefusedIt() {
        assertTrue(DecimalBoxes.shapeOk("12.5", signed = false, comma = false))
        assertFalse(DecimalBoxes.shapeOk("12.5.1", signed = false, comma = false))
        assertFalse(DecimalBoxes.shapeOk("-3", signed = false, comma = false))
        assertTrue(DecimalBoxes.shapeOk("-3", signed = true, comma = false))
        assertFalse(DecimalBoxes.shapeOk("3-", signed = true, comma = false))
        assertTrue("the dots group in Indonesian", DecimalBoxes.shapeOk("1.250.000,5", signed = false, comma = true))
        assertFalse(DecimalBoxes.shapeOk("1,2,5", signed = false, comma = true))
        assertFalse(DecimalBoxes.shapeOk("1,5", signed = false, comma = false))
    }
}
