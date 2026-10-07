package com.innovation313.roshankhata

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.innovation313.roshankhata.data.BigBook
import com.innovation313.roshankhata.data.KhataDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app against a wholesaler's book: 10,000 customers, 300,000 lines
 * (P0 of the scaling work, 7 Oct 2026).
 *
 * Three stopwatches, each a thing the owner feels:
 *  - the Khata list opening until its first row is drawn;
 *  - one keystroke in its search box, measured ON the main thread, because
 *    that is the thread the keyboard waits for;
 *  - a customer's own screen opening until its first line is drawn.
 *
 * The budgets are an emulator's (CI's emulator is several times slower than
 * a phone), so a number inside them here is comfortably inside on a Rs 20,000
 * phone. Every measurement is logged under [TAG] whether or not it passes,
 * so a run that fails says by how much, not merely that it did.
 */
@RunWith(AndroidJUnit4::class)
class BigBookPerfTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun seed() {
        context.getSharedPreferences("language", Context.MODE_PRIVATE)
            .edit().putBoolean("chosen", true).commit()
        val t = SystemClock.elapsedRealtime()
        runBlocking { BigBook.seed(context) }
        Log.i(TAG, "seeded ${BigBook.CUSTOMERS} customers x ${BigBook.ENTRIES_EACH} in ${SystemClock.elapsedRealtime() - t} ms")
    }

    @After
    fun clear() {
        runBlocking { BigBook.clear(context) }
    }

    private fun waitForRows(scenario: ActivityScenario<Activity>, id: Int, timeoutMs: Long): Long {
        val start = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - start < timeoutMs) {
            var rows = 0
            scenario.onActivity { rows = it.findViewById<RecyclerView>(id).childCount }
            if (rows > 0) return SystemClock.elapsedRealtime() - start
            SystemClock.sleep(20)
        }
        return -1
    }

    @Test
    fun khataListOpensAndSearchesInsideBudget() {
        val t0 = SystemClock.elapsedRealtime()
        ActivityScenario.launch<Activity>(Intent(context, KhataActivity::class.java)).use { scenario ->
            val open = waitForRows(scenario, R.id.rvParties, 30_000)
            val total = SystemClock.elapsedRealtime() - t0
            Log.i(TAG, "Khata list: first row after $open ms (launch to row $total ms)")
            assertTrue("Khata list took $open ms to show a row (budget $OPEN_BUDGET_MS)", open in 0..OPEN_BUDGET_MS)

            // One keystroke, timed on the main thread: setText runs the
            // TextWatcher, and the TextWatcher runs render().
            var box: EditText? = null
            scenario.onActivity { box = it.findViewById(R.id.etSearchParties) }
            val worst = listOf("b", "bi", "bil", "bila", "asg", "lapewali").maxOf { q ->
                var ms = 0L
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    val t = SystemClock.elapsedRealtime()
                    box!!.setText(q)
                    ms = SystemClock.elapsedRealtime() - t
                }
                Log.i(TAG, "search '$q': $ms ms on the main thread")
                ms
            }
            // Every figure in the message: a failing run then reports the whole
            // picture through the CI annotation, not only the one line that broke.
            val report = "Khata list first row $open ms (budget $OPEN_BUDGET_MS); worst search keystroke $worst ms on the main thread (budget $KEY_BUDGET_MS)"
            Log.i(TAG, report)
            assertTrue(report, worst <= KEY_BUDGET_MS)
        }
    }

    @Test
    fun customerScreenOpensInsideBudget() {
        val id = runBlocking {
            KhataDatabase.get(context).khataDao().seededPartyId("%${BigBook.MARK}")
        }
        assertTrue("no seeded customer found", id > 0)
        val intent = Intent(context, PartyDetailActivity::class.java)
            .putExtra(PartyDetailActivity.EXTRA_PARTY_ID, id)
        ActivityScenario.launch<Activity>(intent).use { scenario ->
            val open = waitForRows(scenario, R.id.rvEntries, 30_000)
            Log.i(TAG, "customer screen: first line after $open ms")
            assertTrue("customer screen took $open ms to show a line (budget $DETAIL_BUDGET_MS)", open in 0..DETAIL_BUDGET_MS)
        }
    }

    companion object {
        const val TAG = "BigBookPerf"
        const val OPEN_BUDGET_MS = 2_000L
        const val KEY_BUDGET_MS = 50L
        const val DETAIL_BUDGET_MS = 1_500L
    }
}
