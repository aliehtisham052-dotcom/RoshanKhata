package com.innovation313.roshankhata.ui

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.innovation313.roshankhata.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Edit entry dialog has the calculator (21 Oct 2026). A user found that
 * correcting an entry brought up only the phone's number keyboard — no
 * + − × ÷ %, no = — while writing one had the app's pad. Both forms now carry
 * view_calculator and bind it through CalcPad; these tests inflate the real
 * layouts and press the real keys.
 */
@RunWith(AndroidJUnit4::class)
class CalcPadTest {

    private fun inflate(layout: Int): View {
        val themed = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_RoshanKhata)
        return LayoutInflater.from(themed).inflate(layout, null)
    }

    private fun View.tap(vararg ids: Int) = ids.forEach { findViewById<View>(it).performClick() }

    private val keys = listOf(
        R.id.calc0, R.id.calc00, R.id.calc1, R.id.calc2, R.id.calc3, R.id.calc4, R.id.calc5,
        R.id.calc6, R.id.calc7, R.id.calc8, R.id.calc9, R.id.calcDot, R.id.calcPlus,
        R.id.calcMinus, R.id.calcTimes, R.id.calcDivide, R.id.calcPercent, R.id.calcClear,
        R.id.calcBackKey, R.id.calcEquals
    )

    private fun editDialog(): Pair<View, EditText> {
        val view = inflate(R.layout.dialog_edit_entry)
        val amount = view.findViewById<EditText>(R.id.etEditAmount)
        CalcPad.bind(view, amount)
        return view to amount
    }

    @Test
    fun theEditDialogCarriesThePad() {
        val view = inflate(R.layout.dialog_edit_entry)
        val pad = view.findViewById<View>(R.id.editKeypad)
        assertNotNull("Edit entry has no calculator pad", pad)
        assertEquals(View.VISIBLE, pad.visibility)
        keys.forEach {
            assertNotNull("key ${view.resources.getResourceEntryName(it)} is missing", view.findViewById<View>(it))
        }
        // The running total waits for a sum.
        assertEquals(View.GONE, view.findViewById<View>(R.id.tvEditAmountResult).visibility)
    }

    @Test
    fun thePadWritesASumAndEqualsWorksItOut() {
        val (view, amount) = editDialog()
        view.tap(R.id.calc1, R.id.calc2, R.id.calc00, R.id.calcMinus, R.id.calc1, R.id.calc5, R.id.calcPercent)
        assertEquals("1200−15%", amount.text.toString())
        view.tap(R.id.calcEquals)
        assertEquals("1020", amount.text.toString())

        view.tap(R.id.calcClear, R.id.calc4, R.id.calc5, R.id.calc0, R.id.calcTimes, R.id.calc3,
            R.id.calcPlus, R.id.calc5, R.id.calcDot, R.id.calc5, R.id.calcEquals)
        assertEquals("1355.5", amount.text.toString())
    }

    @Test
    fun anAnswerBelowZeroComesBackAsAnAmount() {
        val (view, amount) = editDialog()
        amount.setText("7926")
        view.tap(R.id.calcMinus, R.id.calc3, R.id.calc5, R.id.calc5, R.id.calc5, R.id.calc4, R.id.calcEquals)
        assertEquals("27628", amount.text.toString())
    }

    @Test
    fun backspaceAndClearAndAnEmptyBox() {
        val (view, amount) = editDialog()
        amount.setText("55")
        view.tap(R.id.calcBackKey)
        assertEquals("5", amount.text.toString())
        view.tap(R.id.calcClear)
        assertEquals("", amount.text.toString())
        // Nothing to delete or work out: nothing happens, nothing breaks.
        view.tap(R.id.calcBackKey, R.id.calcEquals)
        assertEquals("", amount.text.toString())
    }

    @Test
    fun theRunningTotalShowsOnlyForASum() {
        val view = inflate(R.layout.dialog_edit_entry)
        val amount = view.findViewById<EditText>(R.id.etEditAmount)
        val result = view.findViewById<TextView>(R.id.tvEditAmountResult)
        amount.setText("3500")
        CalcPad.showTotal(amount, result)
        assertEquals(View.GONE, result.visibility)
        amount.setText("3500+500")
        CalcPad.showTotal(amount, result)
        assertEquals(View.VISIBLE, result.visibility)
        assertEquals(Format.money(4000.0), result.text.toString())
    }

    @Test
    fun theAddEntryFormKeepsItsPadAndTheBackspaceInTheBox() {
        val view = inflate(R.layout.dialog_add_entry)
        val amount = view.findViewById<EditText>(R.id.etAmount)
        CalcPad.bind(view, amount)
        view.tap(R.id.calc9, R.id.calc8, R.id.calcBack)
        assertEquals("9", amount.text.toString())
        view.tap(R.id.calcPlus, R.id.calc1, R.id.calcEquals)
        assertEquals("10", amount.text.toString())
    }
}
