package com.innovation313.roshankhata

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.innovation313.roshankhata.data.BusinessProfile
import com.innovation313.roshankhata.data.FollowUpRank
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.Money
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.data.PaymentHabit
import com.innovation313.roshankhata.ui.FollowUpAdapter
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.Reminder
import com.innovation313.roshankhata.ui.ScreenInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * "Aaj kis se lena hai" — the day's collection round as a ranked list.
 *
 * Only parties who owe the shop (balance > 0), the longest-quiet accounts
 * first, biggest amount breaking ties. The home summary already counts how
 * many of these have gone 30+ days without an entry; this screen is where
 * that count becomes names, amounts, and a reminder button.
 *
 * The list is live (the same reactive stream the home screen uses), so a
 * payment recorded while this screen is open drops the row on its own.
 *
 * Each row also carries the customer's own payment habit (PaymentHabit,
 * 1 Oct): "usually pays every 30 days", and when they have fallen behind it,
 * "paying late". Those late by their own rhythm come first, most overdue at
 * the top; everyone else keeps the quiet-longest order below them.
 */
class FollowUpActivity : BaseActivity() {

    private lateinit var adapter: FollowUpAdapter
    private lateinit var tvSummary: TextView
    private lateinit var tvEmpty: TextView

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_followup)
        ScreenInsets.on(this)

        tvSummary = findViewById(R.id.tvFollowUpSummary)
        tvEmpty = findViewById(R.id.tvFollowUpEmpty)

        adapter = FollowUpAdapter(
            onOpen = { party ->
                startActivity(
                    Intent(this, PartyDetailActivity::class.java)
                        .putExtra(PartyDetailActivity.EXTRA_PARTY_ID, party.id)
                )
            },
            onSend = { party -> sendReminder(party) }
        )
        findViewById<RecyclerView>(R.id.rvFollowUp).apply {
            layoutManager = LinearLayoutManager(this@FollowUpActivity)
            adapter = this@FollowUpActivity.adapter
        }

        observeDebtors()
    }

    private fun observeDebtors() {
        lifecycleScope.launch {
            dao.observePartiesWithBalance()
                .combine(dao.observeLedgerPoints()) { all, points -> all to points }
                .combine(dao.observePromises()) { (all, points), promiseRows ->
                    // balance > 0 means they owe the shop — the only rows
                    // this screen is for.
                    val debtors = all.filter { Money.isPositive(it.balance) }
                    val habits = PaymentHabit.forAll(points)
                    val promises = promiseRows.associate { it.partyId to it.due }
                    val now = System.currentTimeMillis()
                    val tz = java.util.TimeZone.getDefault()
                    // Promise due first, then late by habit, then quiet longest:
                    // see FollowUpRank.
                    val ordered = FollowUpRank.order(debtors, habits, promises, now, tz)
                    val today = ordered.count { FollowUpRank.remindToday(it.id, habits, promises, now, tz) }
                    FollowUpState(ordered, habits, promises, today)
                }
                // A big book is thousands of lines; the arithmetic stays off
                // the main thread.
                .flowOn(Dispatchers.Default)
                .collectLatest { state ->
                    val debtors = state.debtors
                    adapter.habits = state.habits
                    adapter.promises = state.promises
                    adapter.submitList(debtors)

                    val total = debtors.sumOf { it.balance }
                    val summary = resources.getQuantityString(
                        R.plurals.followup_summary, debtors.size,
                        debtors.size, Format.money(total)
                    )
                    // The one number the owner opens this screen for.
                    tvSummary.text = if (state.today > 0) {
                        summary + "\n" + resources.getQuantityString(R.plurals.followup_today, state.today, state.today)
                    } else summary
                    tvSummary.visibility = if (debtors.isEmpty()) View.GONE else View.VISIBLE
                    tvEmpty.visibility = if (debtors.isEmpty()) View.VISIBLE else View.GONE
                }
        }
    }

    /**
     * One tap: the same polite message the party screen's reminder builds,
     * handed to WhatsApp with the text ready. Nothing is sent silently —
     * WhatsApp itself is where the owner reads it and presses send.
     */
    private fun sendReminder(party: PartyWithBalance) {
        // Room's suspend queries run off the main thread themselves, so this
        // reads the agreed date without blocking the tap.
        lifecycleScope.launch {
            val promisedDate = runCatching { dao.promisedDateForParty(party.id) }.getOrNull()
            val message = Reminder.buildMessage(
                this@FollowUpActivity,
                partyName = party.name,
                balance = party.balance,
                businessName = BusinessProfile.businessName(this@FollowUpActivity),
                promisedDate = promisedDate
            )
            Reminder.sendViaWhatsApp(this@FollowUpActivity, party.phone, message)
        }
    }
}

private class FollowUpState(
    val debtors: List<PartyWithBalance>,
    val habits: Map<Long, PaymentHabit.Habit>,
    val promises: Map<Long, Long>,
    val today: Int
)
