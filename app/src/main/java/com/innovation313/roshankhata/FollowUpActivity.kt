package com.innovation313.roshankhata

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.innovation313.roshankhata.data.BusinessProfile
import com.innovation313.roshankhata.data.FollowUpRank
import com.innovation313.roshankhata.data.ReminderLog
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
    private lateinit var btnRemindAll: com.google.android.material.button.MaterialButton
    private lateinit var tvEmpty: TextView

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    /** Reminders opened today (ReminderLog); re-read on return from WhatsApp. */
    private val reminded = kotlinx.coroutines.flow.MutableStateFlow<Set<Long>>(emptySet())

    override fun onResume() {
        super.onResume()
        reminded.value = ReminderLog.openedToday(this)
        // Back from WhatsApp in the middle of "remind all": offer the next one.
        if (queueWaiting) {
            queueWaiting = false
            showQueueStep()
        }
    }

    // ---------- Remind all, one by one (2 Oct) ----------
    // WhatsApp cannot be sent to in bulk without a paid API, and must not be:
    // the owner reads and sends each. This only removes the hunting — each
    // return from WhatsApp brings up the next person due.

    /** The rows due today with a phone, in list order; refreshed with the list. */
    private var dueToday: List<PartyWithBalance> = emptyList()
    private var queue: List<PartyWithBalance> = emptyList()
    private var queueIndex = 0
    private var queueOpened = 0
    private var queueWaiting = false

    private fun startQueue() {
        queue = dueToday
        queueIndex = 0
        queueOpened = 0
        showQueueStep()
    }

    private fun showQueueStep() {
        if (isFinishing || isDestroyed) return
        if (queueIndex >= queue.size) {
            if (queue.isNotEmpty()) {
                Toast.makeText(this, getString(R.string.followup_queue_done, queueOpened), Toast.LENGTH_LONG).show()
            }
            queue = emptyList()
            return
        }
        val p = queue[queueIndex]
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.followup_queue_title, queueIndex + 1, queue.size))
            .setMessage(getString(R.string.followup_queue_next, p.name, Format.money(p.balance)))
            .setPositiveButton(R.string.followup_queue_open) { _, _ ->
                queueIndex++
                queueOpened++
                queueWaiting = true
                sendReminder(p)
            }
            .setNeutralButton(R.string.followup_queue_skip) { _, _ ->
                queueIndex++
                showQueueStep()
            }
            .setNegativeButton(R.string.followup_queue_stop) { _, _ -> queue = emptyList() }
            .setCancelable(false)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_followup)
        ScreenInsets.on(this)

        tvSummary = findViewById(R.id.tvFollowUpSummary)
        btnRemindAll = findViewById(R.id.btnRemindAll)
        btnRemindAll.setOnClickListener { startQueue() }
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
                .combine(dao.observePromises()) { (all, points), promiseRows -> Triple(all, points, promiseRows) }
                .combine(reminded) { (all, points, promiseRows), remindedToday ->
                    // balance > 0 means they owe the shop — the only rows
                    // this screen is for.
                    val debtors = all.filter { Money.isPositive(it.balance) }
                    val habits = PaymentHabit.forAll(points)
                    val promises = promiseRows.associate { it.partyId to it.due }
                    val now = System.currentTimeMillis()
                    val tz = java.util.TimeZone.getDefault()
                    // Promise due first, then late by habit, then quiet longest:
                    // see FollowUpRank.
                    val ordered = FollowUpRank.order(debtors, habits, promises, now, tz, remindedToday)
                    val today = ordered.count { FollowUpRank.remindToday(it.id, habits, promises, now, tz, remindedToday) }
                    val due = ordered.filter {
                        !it.phone.isNullOrBlank() &&
                            FollowUpRank.remindToday(it.id, habits, promises, now, tz, remindedToday)
                    }
                    FollowUpState(ordered, habits, promises, today, remindedToday, due)
                }
                // A big book is thousands of lines; the arithmetic stays off
                // the main thread.
                .flowOn(Dispatchers.Default)
                .collectLatest { state ->
                    val debtors = state.debtors
                    adapter.habits = state.habits
                    adapter.promises = state.promises
                    // Rows are compared by party only, so a new mark needs a rebind.
                    val marksChanged = adapter.remindedToday != state.remindedToday
                    adapter.remindedToday = state.remindedToday
                    dueToday = state.dueWithPhone
                    btnRemindAll.visibility = if (dueToday.size >= 2) View.VISIBLE else View.GONE
                    adapter.submitList(debtors) { if (marksChanged) adapter.notifyDataSetChanged() }

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
            ReminderLog.record(this@FollowUpActivity, party.id)
            reminded.value = ReminderLog.openedToday(this@FollowUpActivity)
        }
    }
}

private class FollowUpState(
    val debtors: List<PartyWithBalance>,
    val habits: Map<Long, PaymentHabit.Habit>,
    val promises: Map<Long, Long>,
    val today: Int,
    val remindedToday: Set<Long>,
    val dueWithPhone: List<PartyWithBalance>
)
