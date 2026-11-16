package com.innovation313.roshankhata

import com.innovation313.roshankhata.data.get

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.chip.ChipGroup
import com.innovation313.roshankhata.data.EntryKind
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.PaymentMethod
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * A supplier's "I got" on the phone's entry form (17 Nov 2026, T43, KhataMoney): "What did you get?" with Goods (on
 * credit) chosen to begin with and no way of paying shown; Money shows "How was it paid?" with Cash chosen. Saved, the
 * Goods entry carries no method and is NORMAL (nothing joins the drawer, C2), the Money entry carries Cash and is
 * MONEY — the one mark the drawer counts a supplier's cash by. Made-up figures only.
 */
class SupplierMoneyOnDeviceTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Before
    fun readyToUse() {
        context.getSharedPreferences("language", Context.MODE_PRIVATE)
            .edit().putBoolean("chosen", true).commit()
    }

    @Test
    fun aSuppliersIGotAsksGoodsOrMoneyAndOnlyMoneyCarriesAWayOfPaying() {
        val seed = EveryScreenOpensTest.seed(context)
        val dao = KhataDatabase.get(context).khataDao()
        val party = Intent(context, PartyDetailActivity::class.java)
            .putExtra(PartyDetailActivity.EXTRA_PARTY_ID, seed.supplierId)
        ActivityScenario.launch<Activity>(party).use { scenario ->
            idle(900)
            // ---- Goods, as the form opens ----
            scenario.onActivity { it.findViewById<View>(R.id.btnGot).performClick() }
            idle(1200)
            val goodsForm = topRoot()
            onMain {
                assertEquals(View.VISIBLE, goodsForm.findViewById<View>(R.id.supplierGotSection).visibility)
                assertEquals(R.id.chipGotGoods, goodsForm.findViewById<ChipGroup>(R.id.cgSupplierGot).checkedChipId)
                assertEquals("goods on credit ask no way of paying", View.GONE,
                    goodsForm.findViewById<View>(R.id.paymentMethodSection).visibility)
                goodsForm.findViewById<EditText>(R.id.etAmount).setText("7371")
                goodsForm.findViewById<View>(R.id.btnEntrySave).performClick()
            }
            idle(1500)

            // ---- Money ----
            scenario.onActivity { it.findViewById<View>(R.id.btnGot).performClick() }
            idle(1200)
            val moneyForm = topRoot()
            onMain {
                moneyForm.findViewById<EditText>(R.id.etAmount).setText("8382")
                moneyForm.findViewById<ChipGroup>(R.id.cgSupplierGot).check(R.id.chipGotMoney)
                assertEquals(View.VISIBLE, moneyForm.findViewById<View>(R.id.paymentMethodSection).visibility)
                assertEquals("Cash to begin with", R.id.chipCash,
                    moneyForm.findViewById<ChipGroup>(R.id.cgPaymentMethod).checkedChipId)
                moneyForm.findViewById<View>(R.id.btnEntrySave).performClick()
            }
            idle(1500)
        }
        val goods = waitFor(dao, seed.supplierId, 7371.0)
        assertNull("goods on credit carry no way of paying", goods.paymentMethod)
        assertEquals(EntryKind.NORMAL, goods.entryKind)
        val money = waitFor(dao, seed.supplierId, 8382.0)
        assertEquals(PaymentMethod.CASH, money.paymentMethod)
        assertEquals(EntryKind.MONEY, money.entryKind)
    }

    /** The saved row (the form writes on AppScope): looked for up to 6 s. */
    private fun waitFor(dao: com.innovation313.roshankhata.data.KhataDao, partyId: Long, amount: Double): LedgerEntry {
        repeat(30) {
            val row = runBlocking { dao.allEntriesForBackup() }
                .lastOrNull { it.partyId == partyId && !it.isDeleted && it.amount == amount }
            if (row != null) return row
            SystemClock.sleep(200)
        }
        throw AssertionError("no entry of $amount was saved for the supplier")
    }

    /** The newest window's root view (a dialog over the activity), as SignatureProofTest finds it. */
    @Suppress("UNCHECKED_CAST")
    private fun topRoot(): View = onMain {
        val cls = Class.forName("android.view.WindowManagerGlobal")
        val global = cls.getMethod("getInstance").invoke(null)
        val field = cls.getDeclaredField("mViews").apply { isAccessible = true }
        (field.get(global) as List<View>).last { it.isShown }
    }

    private fun <T> onMain(block: () -> T): T {
        var out: Result<T>? = null
        instrumentation.runOnMainSync { out = runCatching(block) }
        return out!!.getOrThrow()
    }

    private fun idle(ms: Long) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(ms)
        instrumentation.waitForIdleSync()
    }
}
