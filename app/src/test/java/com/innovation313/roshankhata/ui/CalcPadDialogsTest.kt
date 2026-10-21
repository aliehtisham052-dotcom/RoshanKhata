package com.innovation313.roshankhata.ui

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.innovation313.roshankhata.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The calculator pad in every phone dialog where money is typed (21 Oct
 * 2026): cashbook, close the day, cheques, supplier bills, plans, invoice
 * options, Zakat, staff and scheme claims. Each layout is inflated for real,
 * its pad and keys looked for, and the pad's own characters typed into each
 * money box through CalcPad — a box whose digits leave out − × ÷ % would
 * drop them.
 */
@RunWith(AndroidJUnit4::class)
class CalcPadDialogsTest {

    private val themed: Context
        get() = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_RoshanKhata)

    private fun inflate(layout: Int): View = LayoutInflater.from(themed).inflate(layout, null)

    private fun View.tap(vararg ids: Int) = ids.forEach { findViewById<View>(it).performClick() }

    private val keys = listOf(
        R.id.calc0, R.id.calc00, R.id.calc1, R.id.calc2, R.id.calc3, R.id.calc4, R.id.calc5,
        R.id.calc6, R.id.calc7, R.id.calc8, R.id.calc9, R.id.calcDot, R.id.calcPlus,
        R.id.calcMinus, R.id.calcTimes, R.id.calcDivide, R.id.calcPercent, R.id.calcClear,
        R.id.calcBackKey, R.id.calcEquals
    )

    /** A layout, its pad, and each money box with the running-total line under it. */
    private class Form(val layout: Int, val pad: Int, val boxes: List<Pair<Int, Int>>)

    private val forms = listOf(
        Form(R.layout.dialog_add_cash, R.id.cashKeypad, listOf(R.id.etCashAmount to R.id.tvCashAmountResult)),
        Form(R.layout.dialog_day_close, R.id.closeKeypad, listOf(
            R.id.etOpening to R.id.tvOpeningResult, R.id.etCounted to R.id.tvCountedResult)),
        Form(R.layout.dialog_add_cheque, R.id.chequeKeypad, listOf(R.id.etChequeAmount to R.id.tvChequeAmountResult)),
        Form(R.layout.dialog_add_bill, R.id.billKeypad, listOf(R.id.etBillTotal to R.id.tvBillTotalResult)),
        Form(R.layout.dialog_add_plan, R.id.planKeypad, listOf(
            R.id.etPlanTotal to R.id.tvPlanTotalResult, R.id.etPlanInstallment to R.id.tvPlanInstallmentResult)),
        Form(R.layout.dialog_invoice_more_options, R.id.moreKeypad, listOf(
            R.id.etInvoiceDiscount to R.id.tvDiscountResult, R.id.etChargeAmount to R.id.tvChargeResult,
            R.id.etReceived to R.id.tvReceivedResult)),
        Form(R.layout.activity_zakat, R.id.zakatKeypad, listOf(
            R.id.etSilverPrice to R.id.tvPriceResult, R.id.etCashStock to R.id.tvCashStockResult)),
    )

    private fun name(view: View, id: Int) = view.resources.getResourceEntryName(id)

    @Test
    fun everyMoneyFormCarriesThePadAndARunningTotal() {
        forms.forEach { form ->
            val view = inflate(form.layout)
            val where = name(view, form.layout)
            assertNotNull("$where has no calculator pad", view.findViewById<View>(form.pad))
            keys.forEach {
                assertNotNull("$where: key ${name(view, it)} is missing", view.findViewById<View>(it))
            }
            form.boxes.forEach { (box, result) ->
                assertNotNull("$where: ${name(view, box)} is missing", view.findViewById<EditText>(box))
                val line = view.findViewById<TextView>(result)
                assertNotNull("$where: ${name(view, result)} is missing", line)
                assertEquals("$where: the running total waits for a sum", View.GONE, line.visibility)
            }
        }
    }

    @Test
    fun everyMoneyBoxTakesThePadsOwnCharacters() {
        forms.forEach { form ->
            val view = inflate(form.layout)
            val pad = view.findViewById<View>(form.pad)
            form.boxes.forEach { (id, _) ->
                val box = view.findViewById<EditText>(id)
                CalcPad.bindAll(view, pad, listOf(box))
                view.tap(R.id.calc1, R.id.calcPlus, R.id.calc2, R.id.calcTimes, R.id.calc3,
                    R.id.calcMinus, R.id.calc4, R.id.calcDivide, R.id.calc2, R.id.calcPercent)
                assertEquals("${name(view, form.layout)}: ${name(view, id)}",
                    "1+2×3−4÷2%", box.text.toString())
                assertFalse("${name(view, id)} still calls the phone keyboard", box.showSoftInputOnFocus)
            }
        }
    }

    @Test
    fun onePadTypesIntoTheFocusedBox() {
        val view = inflate(R.layout.dialog_day_close)
        val opening = view.findViewById<EditText>(R.id.etOpening)
        val counted = view.findViewById<EditText>(R.id.etCounted)
        CalcPad.bindAll(view, view.findViewById(R.id.closeKeypad), listOf(opening, counted))

        assertTrue("the counted box would not take the focus", counted.requestFocus())
        view.tap(R.id.calc5, R.id.calc0, R.id.calcTimes, R.id.calc1, R.id.calc2)
        assertEquals("50×12", counted.text.toString())
        assertEquals("", opening.text.toString())
        view.tap(R.id.calcEquals)
        assertEquals("600", counted.text.toString())

        // Back to the first box: the keys follow it.
        assertTrue(opening.requestFocus())
        view.tap(R.id.calc7, R.id.calcBackKey, R.id.calc9)
        assertEquals("9", opening.text.toString())
        assertEquals("600", counted.text.toString())
    }

    @Test
    fun aTappedBoxIsTheOneThePadWritesTo() {
        val view = inflate(R.layout.dialog_add_plan)
        val total = view.findViewById<EditText>(R.id.etPlanTotal)
        val each = view.findViewById<EditText>(R.id.etPlanInstallment)
        val pad = view.findViewById<View>(R.id.planKeypad)
        CalcPad.bindAll(view, pad, listOf(total, each))

        // Nothing focused yet: the first box.
        view.tap(R.id.calc3)
        assertEquals("3", total.text.toString())

        each.performClick()
        assertEquals("a tap on a money box shows the pad", View.VISIBLE, pad.visibility)
        view.tap(R.id.calc1, R.id.calc00)
        assertEquals("100", each.text.toString())
        assertEquals("3", total.text.toString())
    }

    @Test
    fun theRunningTotalFollowsEachBox() {
        val view = inflate(R.layout.dialog_day_close)
        val opening = view.findViewById<EditText>(R.id.etOpening)
        val counted = view.findViewById<EditText>(R.id.etCounted)
        val openingTotal = view.findViewById<TextView>(R.id.tvOpeningResult)
        val countedTotal = view.findViewById<TextView>(R.id.tvCountedResult)
        CalcPad.bindAll(view, view.findViewById(R.id.closeKeypad), listOf(opening, counted),
            mapOf(opening to openingTotal, counted to countedTotal))

        counted.setText("500+250")
        assertEquals(View.VISIBLE, countedTotal.visibility)
        assertEquals(Format.money(750.0), countedTotal.text.toString())
        assertEquals(View.GONE, openingTotal.visibility)
        counted.setText("750")
        assertEquals(View.GONE, countedTotal.visibility)
    }

    @Test
    fun theDiscountInPercentKeepsThePhoneKeyboard() {
        val view = inflate(R.layout.dialog_invoice_more_options)
        val discount = view.findViewById<EditText>(R.id.etInvoiceDiscount)
        val received = view.findViewById<EditText>(R.id.etReceived)
        var inMoney = false
        val binding = CalcPad.bindAll(view, view.findViewById(R.id.moreKeypad), listOf(discount, received),
            usesPad = { it !== discount || inMoney })

        assertTrue(discount.showSoftInputOnFocus)
        assertFalse(received.showSoftInputOnFocus)
        assertTrue(discount.requestFocus())
        view.tap(R.id.calc1, R.id.calc0, R.id.calcPercent)
        assertEquals("a % discount is not written on the pad", "", discount.text.toString())

        inMoney = true
        binding.refresh()
        assertFalse(discount.showSoftInputOnFocus)
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.moreKeypad).visibility)
        view.tap(R.id.calc2, R.id.calc0, R.id.calc0)
        assertEquals("200", discount.text.toString())
    }

    @Test
    fun aFigureKeepsZeroAndWorksOutASum() {
        assertEquals(0.0, CalcPad.figure("0")!!, 0.0)
        assertNull(CalcPad.figure(""))
        assertNull(CalcPad.figure(null))
        assertNull(CalcPad.figure("abc"))
        assertEquals(700.0, CalcPad.figure("50×12+100")!!, 1e-9)
        assertEquals(3.0, CalcPad.figure("7−10")!!, 1e-9)
        assertEquals(1500.0, CalcPad.figure("1500")!!, 0.0)
    }

    @Test
    fun aDialogBuiltInCodeGetsThePadAtFullWidth() {
        val body = LinearLayout(themed).apply { orientation = LinearLayout.VERTICAL }
        val box = EditText(themed)
        CalcPad.moneyBox(box)
        val result = CalcPad.resultLine(themed)
        body.addView(box)
        body.addView(result)
        val pad = CalcPad.inflatePad(body)
        body.addView(pad)

        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, pad.layoutParams.width)
        assertEquals(View.GONE, result.visibility)
        CalcPad.bindAll(body, pad, listOf(box), mapOf(box to result))
        body.tap(R.id.calc9, R.id.calcMinus, R.id.calc4, R.id.calcTimes, R.id.calc2, R.id.calcDivide,
            R.id.calc1, R.id.calcPercent)
        assertEquals("9−4×2÷1%", box.text.toString())
        body.tap(R.id.calcClear, R.id.calc6, R.id.calcPlus, R.id.calc4)
        assertEquals(View.VISIBLE, result.visibility)
        body.tap(R.id.calcEquals)
        assertEquals("10", box.text.toString())
    }

    @Test
    fun aPlanPaymentCanCarryANote() {
        // PlansActivity shows step two with the note alone; the note must be
        // one of step two's own children for that to work.
        val view = inflate(R.layout.dialog_add_entry)
        val stepTwo = view.findViewById<ViewGroup>(R.id.stepTwo)
        val note = view.findViewById<View>(R.id.etNote)
        assertSame(stepTwo, note.parent)
        assertNotNull(view.findViewById<View>(R.id.entryKeypad))
        assertNotNull(view.findViewById<View>(R.id.tvAmountResult))
    }
}
