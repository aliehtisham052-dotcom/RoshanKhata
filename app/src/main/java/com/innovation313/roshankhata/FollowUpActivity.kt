package com.innovation313.roshankhata

import com.innovation313.roshankhata.data.get
import com.innovation313.roshankhata.data.record
import com.innovation313.roshankhata.data.state

import com.innovation313.roshankhata.data.quantity
import com.innovation313.roshankhata.data.string

import com.innovation313.roshankhata.data.Digits

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.innovation313.roshankhata.data.BusinessProfile
import com.innovation313.roshankhata.data.FollowUpRank
import com.innovation313.roshankhata.data.PaymentRequest
import com.innovation313.roshankhata.data.ReminderLog
import com.innovation313.roshankhata.data.lastReminded
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.Money
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.data.PaymentHabit
import com.innovation313.roshankhata.ui.FollowUpAdapter
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.Reminder
import com.innovation313.roshankhata.ui.ReminderConfirm
import com.innovation313.roshankhata.ui.SendPill
import com.innovation313.roshankhata.ui.ScreenInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
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

    /**
     * A reminder handed to WhatsApp (or the SMS app) and not yet confirmed (8 Nov 2026, the owner's phone test):
     * Android cannot say whether it was sent, so it counts only when the owner answers "Sent" on the way back.
     */
    private var pending: ReminderConfirm.Pending? = null
    private var asking: androidx.appcompat.app.AlertDialog? = null

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        ReminderConfirm.save(outState, pending)
    }

    override fun onResume() {
        super.onResume()
        markReminded()
        // Back from WhatsApp or the SMS app: the question comes first, the next person of "remind all" after it.
        if (pending != null) askPending() else resumeQueue()
    }

    override fun onDestroy() {
        // The question goes with the screen; the pending reminder is already in the saved state.
        asking?.dismiss()
        asking = null
        super.onDestroy()
    }

    private fun resumeQueue() {
        // Back from WhatsApp in the middle of "remind all": offer the next one.
        if (queueWaiting) {
            queueWaiting = false
            showQueueStep()
        }
    }

    private fun askPending() {
        val p = pending ?: return
        if (asking?.isShowing == true || isFinishing || isDestroyed) return
        asking = ReminderConfirm.ask(
            this, p,
            onSent = { pending = null; asking = null; confirmSent(p); resumeQueue() },
            onNotSent = {
                pending = null; asking = null
                pillOf(p.partyId)?.let { SendPill.back(it) }
                if (p.viaSms) resumeQueue() else sendBySms(p)
            },
            onClosed = { pending = null; asking = null; pillOf(p.partyId)?.let { SendPill.back(it) }; resumeQueue() }
        )
    }

    /** "Not sent": the same words by SMS (no bold marks); that is asked about on the way back in the same way. */
    private fun sendBySms(p: ReminderConfirm.Pending) {
        val plain = Reminder.plainText(p.message)
        Reminder.sendViaSms(this, p.phone, plain, opened = {
            pending = p.copy(message = plain, viaSms = true)
        })
    }

    /** "Sent": only now the reminder counts - the ladder, the day's mark, the pill's check, the row's move to the foot. */
    private fun confirmSent(p: ReminderConfirm.Pending) {
        ReminderLog.record(this, p.partyId)
        com.innovation313.roshankhata.data.ReminderLadder.record(this, p.partyId, p.balance, sent = p.step.takeIf { it > 0 })
        adapter.openedHere += p.partyId
        pillOf(p.partyId)?.let { SendPill.openedNow(it) }
        // The row glides to the foot a moment after the check, not while the pill is still changing (3 Nov 2026).
        window.decorView.postDelayed({
            if (!isFinishing && !isDestroyed) markReminded()
        }, 260)
    }

    /** The Message pill of a customer's row, if that row is on screen. */
    private fun pillOf(partyId: Long): com.google.android.material.button.MaterialButton? {
        val rv = findViewById<RecyclerView>(R.id.rvFollowUp) ?: return null
        for (i in 0 until rv.childCount) {
            val vh = rv.getChildViewHolder(rv.getChildAt(i)) as? FollowUpAdapter.VH ?: continue
            if (vh.tvSend.getTag(R.id.motion_pill_party) == partyId) return vh.tvSend
        }
        return null
    }

    /**
     * Today's reminded, from the log: the list ranks by it, and the pills marked "Opened today" on this screen keep
     * only today's — a screen kept past midnight showed yesterday's as opened today (3 Nov 2026, the review).
     */
    private fun markReminded() {
        val today = ReminderLog.openedToday(this)
        adapter.openedHere.retainAll(today)
        reminded.value = today
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

    /** Opened from the morning notification on a fresh screen: start the queue when the list is read. */
    private var queueAsked = false

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
                Toast.makeText(this, Digits.quantity(resources, R.plurals.followup_queue_done, queueOpened, queueOpened), Toast.LENGTH_LONG).show()
            }
            queue = emptyList()
            return
        }
        val p = queue[queueIndex]
        MaterialAlertDialogBuilder(this)
            .setTitle(Digits.string(resources, R.string.followup_queue_title, queueIndex + 1, queue.size))
            .setMessage(getString(R.string.followup_queue_next, Format.isolate(p.name), Format.moneyLtr(p.balance)))
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
        // How this works, the first time (1 Nov 2026) - not when a notification brought the owner here to act.
        if (!intent.getBooleanExtra(EXTRA_START_QUEUE, false)) com.innovation313.roshankhata.ui.FeatureIntro.showOnce(this, com.innovation313.roshankhata.ui.FeatureIntro.Topic.FOLLOWUP)
        ScreenInsets.on(this)

        tvSummary = findViewById(R.id.tvFollowUpSummary)
        btnRemindAll = findViewById(R.id.btnRemindAll)
        btnRemindAll.setOnClickListener { startQueue() }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAging).setOnClickListener {
            startActivity(android.content.Intent(this, AgingActivity::class.java))
        }
        tvEmpty = findViewById(R.id.tvFollowUpEmpty)

        adapter = FollowUpAdapter(
            onOpen = { party ->
                startActivity(
                    Intent(this, PartyDetailActivity::class.java)
                        .putExtra(PartyDetailActivity.EXTRA_PARTY_ID, party.id)
                )
            },
            onSend = { party, pill -> sendReminder(party, pill) }
        )
        findViewById<RecyclerView>(R.id.rvFollowUp).apply {
            layoutManager = LinearLayoutManager(this@FollowUpActivity)
            adapter = this@FollowUpActivity.adapter
            // The reminded row's glide to the foot (3 Nov 2026): a little slower than the default 250 ms.
            (itemAnimator as? androidx.recyclerview.widget.DefaultItemAnimator)?.moveDuration = 320
        }

        pending = ReminderConfirm.restore(savedInstanceState)
        queueAsked = savedInstanceState == null && intent.getBooleanExtra(EXTRA_START_QUEUE, false)
        observeDebtors()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeDebtors() {
        // Only while on screen (8 Nov 2026 review, L145): behind another screen it re-ran its reads on every write.
        lifecycleScope.launch { repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            // The Payment Radar (2 Nov 2026): this shop's normal, read once here, and the switch on this phone.
            // L88 (8 Nov 2026 review): the Radar is for REPORTS (owner, manager); a cashier's list shows no "usually pays now".
            val radarOn = com.innovation313.roshankhata.ui.RadarUi.on(this@FollowUpActivity) && com.innovation313.roshankhata.data.Access.can(com.innovation313.roshankhata.data.Access.Permission.REPORTS)
            val shopDays = if (radarOn) com.innovation313.roshankhata.ui.RadarUi.shopDays(dao) else null
            dao.observePartiesWithBalance()
                // The owing customers' lines a slice at a time, from these same parties (LedgerLines, 3 Nov 2026).
                .mapLatest { all -> all to com.innovation313.roshankhata.data.LedgerLines.owingOf(dao, all) }
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
                    // Those in their usual paying days lead the list after promises come due (the Payment Radar).
                    val likely = if (radarOn) com.innovation313.roshankhata.data.PaymentRadar.likelyNow(points, shopDays, promises, now, tz) else emptySet()
                    val ordered = FollowUpRank.order(debtors, habits, promises, now, tz, remindedToday, likely)
                    val today = ordered.count { FollowUpRank.remindToday(it.id, habits, promises, now, tz, remindedToday, likely) }
                    val due = ordered.filter {
                        !it.phone.isNullOrBlank() &&
                            FollowUpRank.remindToday(it.id, habits, promises, now, tz, remindedToday, likely)
                    }
                    // "Reminded on …, nothing received since" (PaymentRequest, 29 Oct 2026): the ladder's last
                    // time on this phone, re-read with the marks (a reminder opened just now moves it), against
                    // the newest payment in the points already read for the habit.
                    val lastReminded = com.innovation313.roshankhata.data.ReminderLadder.lastReminded(this@FollowUpActivity)
                    FollowUpState(ordered, habits, promises, today, remindedToday, due, lastReminded, PaymentRequest.lastGotAt(points), likely)
                }
                // A big book is thousands of lines; the arithmetic stays off
                // the main thread.
                .flowOn(Dispatchers.Default)
                .collectLatest { state ->
                    val debtors = state.debtors
                    adapter.habits = state.habits
                    adapter.promises = state.promises
                    adapter.lastReminded = state.lastReminded
                    adapter.lastGot = state.lastGot
                    adapter.likely = state.likely
                    // Rows are compared by party only, so a new mark needs a rebind: in place, with a payload,
                    // so the reminded row's move to the foot still glides (notifyDataSetChanged made it jump).
                    val marksChanged = adapter.remindedToday != state.remindedToday
                    adapter.remindedToday = state.remindedToday
                    dueToday = state.dueWithPhone
                    // The morning automation's tap: the queue, once, as soon as there is someone to remind.
                    if (queueAsked && dueToday.isNotEmpty() && queue.isEmpty()) { queueAsked = false; startQueue() }
                    btnRemindAll.visibility = if (dueToday.size >= 2) View.VISIBLE else View.GONE
                    adapter.submitList(debtors) {
                        if (marksChanged) adapter.notifyItemRangeChanged(0, adapter.itemCount, FollowUpAdapter.MARKS)
                    }

                    val total = debtors.sumOf { it.balance }
                    val summary = Digits.quantity(resources, 
                        R.plurals.followup_summary, debtors.size,
                        debtors.size, Format.money(total)
                    )
                    // The one number the owner opens this screen for.
                    tvSummary.text = if (state.today > 0) {
                        summary + "\n" + Digits.quantity(resources, R.plurals.followup_today, state.today, state.today)
                    } else summary
                    tvSummary.visibility = if (debtors.isEmpty()) View.GONE else View.VISIBLE
                    tvEmpty.visibility = if (debtors.isEmpty()) View.VISIBLE else View.GONE
                }
        } }
    }

    /**
     * One tap: the same polite message the party screen's reminder builds,
     * handed to WhatsApp with the text ready. Nothing is sent silently —
     * WhatsApp itself is where the owner reads it and presses send.
     */
    private fun sendReminder(party: PartyWithBalance, pill: com.google.android.material.button.MaterialButton? = null) {
        // The pill still belongs to this party's row (a recycled one is never animated for someone else).
        fun own() = pill?.takeIf { it.getTag(R.id.motion_pill_party) == party.id }
        own()?.let { com.innovation313.roshankhata.ui.SendPill.sending(it) }
        // Room's suspend queries run off the main thread themselves, so this
        // reads the agreed date without blocking the tap.
        lifecycleScope.launch {
            // The promised day and the sale being chased, read together (ReminderText.facts, 29 Oct 2026).
            val facts = runCatching { com.innovation313.roshankhata.data.ReminderText.facts(dao, party.id) }
                .getOrDefault(com.innovation313.roshankhata.data.ReminderText.Facts(null, null))
            // The ladder (6 Oct) picks how firm this one is: gentle, plain, serious.
            val state = com.innovation313.roshankhata.data.ReminderLadder.state(this@FollowUpActivity, party.id)
            val message = Reminder.buildMessage(
                this@FollowUpActivity,
                partyName = party.name,
                balance = party.balance,
                businessName = BusinessProfile.businessName(this@FollowUpActivity),
                promisedDate = facts.promisedDate,
                step = com.innovation313.roshankhata.data.ReminderLadder.step(state, party.balance, System.currentTimeMillis()),
                firstSentAt = state?.firstAt,
                reference = facts.reference
            )
            // Opening WhatsApp counts nothing (8 Nov 2026): a number not on WhatsApp opens "Invite" and sends
            // nothing. The reminder waits as pending; the owner is asked on the way back (onResume).
            Reminder.sendViaWhatsApp(
                this@FollowUpActivity, party.phone, message, from = own(),
                opened = {
                    pending = ReminderConfirm.Pending(party.id, party.name, party.balance, party.phone, message, 0, false)
                },
                failed = { own()?.let { SendPill.back(it) } }
            )
        }
    }

    companion object {
        /** Start the one-by-one reminder queue once the list is read (the morning automation, 31 Oct 2026). */
        const val EXTRA_START_QUEUE = "start_queue"
    }
}

private class FollowUpState(
    val debtors: List<PartyWithBalance>,
    val habits: Map<Long, PaymentHabit.Habit>,
    val promises: Map<Long, Long>,
    val today: Int,
    val remindedToday: Set<Long>,
    val dueWithPhone: List<PartyWithBalance>,
    val lastReminded: Map<Long, Long>,
    val lastGot: Map<Long, Long>,
    val likely: Set<Long> = emptySet()
)
