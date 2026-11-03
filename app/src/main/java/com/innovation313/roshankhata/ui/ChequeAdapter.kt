package com.innovation313.roshankhata.ui

import com.innovation313.roshankhata.data.quantity

import com.innovation313.roshankhata.data.Digits

import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.ChequeStatus
import com.innovation313.roshankhata.data.ChequeWithParty
import com.innovation313.roshankhata.data.DayMath
import java.util.concurrent.TimeUnit

class ChequeAdapter(
    private val onClick: (ChequeWithParty) -> Unit
) : RecyclerView.Adapter<ChequeAdapter.VH>() {

    init { setHasStableIds(true) }

    private var items: List<ChequeWithParty> = emptyList()
    private var recycler: RecyclerView? = null
    private val main = Handler(Looper.getMainLooper())

    /** While a stamp plays, the list that came meanwhile waits here (the row stays where it was tapped). */
    private var holding = false
    private var waiting: List<ChequeWithParty>? = null
    private val release = Runnable { endHold() }
    /** The day the rows were last bound for: "due in 2 days" is said again once the day turns. */
    private var boundDay = -1L

    fun submit(newItems: List<ChequeWithParty>) {
        if (holding) { waiting = newItems; return }
        show(newItems)
    }

    /**
     * Settled cheques go below the pending ones (KhataDao.observeCheques): told apart by DiffUtil, a cheque that
     * was just cleared glides down to its place instead of the whole list being redrawn under the owner's thumb.
     */
    private fun show(newItems: List<ChequeWithParty>) {
        val old = items
        items = newItems
        val today = DayMath.dayNumber(System.currentTimeMillis())
        if (old.isEmpty() || today != boundDay || recycler == null) {
            boundDay = today
            notifyDataSetChanged()
            return
        }
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(o: Int, n: Int) = old[o].id == newItems[n].id
            override fun areContentsTheSame(o: Int, n: Int) = old[o] == newItems[n]
        }).dispatchUpdatesTo(this)
    }

    /**
     * A change to a cheque is about to be written (3 Nov 2026, the stamp): the lists that arrive meanwhile wait,
     * so its row is still where the owner tapped it when the stamp lands. Never longer than [MAX_HOLD_MS].
     */
    fun hold() {
        holding = true
        main.removeCallbacks(release)
        main.postDelayed(release, MAX_HOLD_MS)
    }

    /**
     * It was written: the stamp lands on the cheque's row — CLEARED in green, BOUNCED in red, CANCELLED in grey —
     * its status word and stripe turn with it, and once it has been seen the list moves on. With the phone's
     * animations off there is no stamp: the list moves on at once.
     */
    fun stamp(id: Long, status: Int) {
        val holder = recycler?.findViewHolderForItemId(id) as? VH
        val wait = if (holder != null && Motion.enabled(holder.itemView.context)) playStamp(holder, status) else 0L
        main.removeCallbacks(release)
        if (wait <= 0L) endHold() else main.postDelayed(release, wait)
    }

    /** Nothing was written (a cheque another device had already settled): the list goes on at once. */
    fun releaseNow() {
        main.removeCallbacks(release)
        endHold()
    }

    private fun endHold() {
        holding = false
        waiting?.let { waiting = null; show(it) }
    }

    override fun getItemId(position: Int): Long = items[position].id

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) { recycler = recyclerView }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        recycler = null
        main.removeCallbacks(release)
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val stripe: View = view.findViewById(R.id.vStatusStripe)
        val tvParty: TextView = view.findViewById(R.id.tvChequeParty)
        val tvDetail: TextView = view.findViewById(R.id.tvChequeDetail)
        val tvDue: TextView = view.findViewById(R.id.tvChequeDue)
        val tvAmount: TextView = view.findViewById(R.id.tvChequeAmount)
        val tvStatus: TextView = view.findViewById(R.id.tvChequeStatus)
        val stamp: View = view.findViewById(R.id.chequeStamp)
        val stampEcho: View = view.findViewById(R.id.chequeStampEcho)
        val tvStamp: TextView = view.findViewById(R.id.tvChequeStamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_cheque, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        val ctx = holder.itemView.context

        // A row given to another cheque never carries the last one's stamp.
        holder.stamp.animate().cancel()
        holder.stampEcho.animate().cancel()
        holder.tvStatus.animate().cancel()
        holder.tvStatus.alpha = 1f
        holder.stamp.visibility = View.GONE

        holder.tvParty.text = c.partyName

        val detail = buildList {
            add(ctx.getString(if (c.isReceived) R.string.cheque_received else R.string.cheque_issued))
            c.chequeNumber?.takeIf { it.isNotBlank() }?.let { add("#$it") }
            c.bankName?.takeIf { it.isNotBlank() }?.let { add(it) }
        }.joinToString(" · ")
        holder.tvDetail.text = detail
        // A small camera when the cheque's picture is kept (schema 43).
        KeptPhoto.mark(holder.tvDetail, !c.photoPath.isNullOrBlank())

        holder.tvAmount.text = Format.money(c.amount)
        holder.tvAmount.setTextColor(
            ContextCompat.getColor(
                ctx,
                if (c.isReceived) R.color.green_got_text else R.color.red_gave_text
            )
        )

        // A pending cheque's due date is the thing that matters — it is what
        // tells the owner to go to the bank today. Once settled, the countdown
        // is noise, so it goes away.
        if (c.status == ChequeStatus.PENDING) {
            val days = daysUntil(c.dueDate)
            holder.tvDue.visibility = View.VISIBLE
            holder.tvDue.text = when {
                days == 0L -> ctx.getString(R.string.due_today)
                days > 0 -> Digits.quantity(ctx.resources, R.plurals.due_in_days, days.toInt(), days.toInt())
                else -> Digits.quantity(ctx.resources,
                    R.plurals.overdue_by_days, (-days).toInt(), (-days).toInt()
                )
            }
            holder.tvDue.setTextColor(
                ContextCompat.getColor(
                    ctx,
                    if (days <= 0) R.color.red_gave_text else R.color.ink
                )
            )
        } else {
            holder.tvDue.visibility = View.GONE
        }

        val (statusText, statusColour) = statusOf(c.status)
        holder.tvStatus.setText(statusText)
        holder.tvStatus.setTextColor(ContextCompat.getColor(ctx, statusColour))
        holder.stripe.setBackgroundColor(ContextCompat.getColor(ctx, statusColour))

        // Settled cheques stay visible as a record, but faded — they are
        // history, not something needing action. The name and details fade;
        // the status word, its stripe and the amount keep full strength (R99:
        // the whole row at 60% put a bounced cheque's word near 2:1 by night).
        holder.itemView.alpha = 1f
        val faded = if (c.status == ChequeStatus.PENDING) 1f else 0.75f
        holder.tvParty.alpha = faded
        holder.tvDetail.alpha = faded
        holder.tvDue.alpha = faded

        holder.itemView.setOnClickListener { onClick(c) }
    }

    /** A status's word and colour: the text colours, night-aware (23 Oct 2026, review R99). */
    private fun statusOf(status: Int): Pair<Int, Int> = when (status) {
        ChequeStatus.PENDING -> R.string.cheque_pending to R.color.ink
        // The solid fills measured about 2.6:1 on the night page, and the word and its stripe are the
        // cheque's whole news.
        ChequeStatus.CLEARED -> R.string.cheque_cleared to R.color.green_got_text
        ChequeStatus.BOUNCED -> R.string.cheque_bounced to R.color.red_gave_text
        else -> R.string.cheque_cancelled to R.color.text_muted
    }

    /**
     * The stamp (the owner's mockup): it lands in 180 ms from half again its size, an echo of its edge
     * spreads and fades behind it, the row's status word and stripe turn at 140 ms; it is held long enough
     * to read and fades. Returns how long the row must stay where it is.
     */
    private fun playStamp(holder: VH, status: Int): Long {
        val ctx = holder.itemView.context
        val dp = ctx.resources.displayMetrics.density
        val (word, colourRes) = statusOf(status)
        val ink = ContextCompat.getColor(ctx, colourRes)
        fun edge() = GradientDrawable().apply {
            cornerRadius = 8 * dp
            setStroke((3 * dp).toInt(), ink)
            setColor(0)
        }
        holder.tvStamp.setText(word)
        holder.tvStamp.setTextColor(ink)
        holder.tvStamp.background = edge()
        // Spaced capitals read as a stamp in Latin letters; joined scripts (Urdu, Arabic, Hindi's and Bengali's
        // headline) are never spaced apart.
        holder.tvStamp.letterSpacing = if (holder.tvStamp.text.all { !it.isLetter() || it.code < 0x0250 }) 0.1f else 0f
        holder.stampEcho.background = edge()

        holder.stamp.visibility = View.VISIBLE
        holder.stamp.alpha = 0f
        holder.stamp.scaleX = 1.5f
        holder.stamp.scaleY = 1.5f
        // Every delay is set again: a view's animator keeps the last one it was given.
        holder.stamp.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(0).setDuration(180).setInterpolator(MotionKit.EMPHASIZED_DECELERATE)
            .withEndAction {
                // Held to be read, then let go before the row moves (so no other cheque's row ever wears it).
                holder.stamp.animate().alpha(0f).setStartDelay(STAMP_HOLD_MS).setDuration(180)
                    .setInterpolator(MotionKit.STANDARD_ACCELERATE)
                    .withEndAction { holder.stamp.visibility = View.GONE; holder.stamp.alpha = 1f }
                    .start()
            }
            .start()

        holder.stampEcho.alpha = 0.35f
        holder.stampEcho.scaleX = 1f
        holder.stampEcho.scaleY = 1f
        holder.stampEcho.animate().alpha(0f).scaleX(1.14f).scaleY(1.14f)
            .setStartDelay(120).setDuration(300).setInterpolator(MotionKit.STANDARD).start()

        // The row's own word and stripe turn under the stamp.
        holder.tvStatus.animate().alpha(0f).setStartDelay(140).setDuration(80)
            .setInterpolator(MotionKit.STANDARD_ACCELERATE)
            .withEndAction {
                holder.tvStatus.setText(word)
                holder.tvStatus.setTextColor(ink)
                holder.stripe.setBackgroundColor(ink)
                holder.tvStatus.animate().alpha(1f).setStartDelay(0).setDuration(80).setInterpolator(MotionKit.STANDARD).start()
            }
            .start()
        return 180 + STAMP_HOLD_MS + 180 + 40
    }

    /** Whole days from today to the due date. Negative once overdue. */
    private fun daysUntil(dueDate: Long): Long {
        val startOfToday = startOfDay(System.currentTimeMillis())
        val startOfDue = startOfDay(dueDate)
        return TimeUnit.MILLISECONDS.toDays(startOfDue - startOfToday)
    }

    private fun startOfDay(millis: Long): Long = DayMath.startOfDay(millis)

    private companion object {
        /** How long a landed stamp stays whole before it fades. */
        const val STAMP_HOLD_MS = 560L
        /** A write that never answers does not keep the list still. */
        const val MAX_HOLD_MS = 4_000L
    }
}
