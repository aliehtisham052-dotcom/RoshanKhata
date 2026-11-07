package com.innovation313.roshankhata

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.data.AppLock
import com.innovation313.roshankhata.data.CustomerLink
import com.innovation313.roshankhata.data.CustomerPage
import com.innovation313.roshankhata.data.DateWords
import com.innovation313.roshankhata.data.MeraKhataStore
import com.innovation313.roshankhata.data.SetupProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Mera Khata (7 Nov 2026, the owner: the customer sees his total balance in the app, and the app is how he comes
 * to Roshan Khata): every shop that sent this phone a khata link, with what is owed either way.
 *
 * Opened by the shop's link (https://khata.ehtisham.shop/#…, an App Link), by the web page's "Open in the app"
 * button (roshankhata://khata?l=…), from Home's More sheet, or by the front door on a phone that has links but no
 * shop of its own. Read only: the shop writes, the customer looks.
 *
 * Light on the server ([CustomerPage]): a shop's page is asked when this screen opens only if the last look is
 * older than ten minutes, a tap on Refresh no sooner than a minute after the last, each time with the last ETag
 * (an unchanged page answers in a few bytes). Nothing in the background.
 *
 * After the security review of the same day: a link is kept only once its page opened ([MeraKhataStore.pending]
 * until then); a page under a lower counter than the one kept is an older page served again — the kept one stays and
 * the card says so; a link the shop stopped shows as stopped, without its old figure; a day outside the years a page
 * may carry is no day. On a phone whose owner has App Lock on, this screen asks it too when it is due, and it keeps a
 * task of its own (the manifest), so Back from a shop's link never lands on the book.
 */
class MeraKhataActivity : BaseActivity() {

    private lateinit var list: LinearLayout
    private lateinit var empty: TextView
    private var lastTap = 0L
    private var busy = false
    /** App Lock was asked from here, and how many unlocks there had been then. */
    private var lockAsked = false
    private var unlocksWhenAsked = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mera_khata)
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        list = findViewById(R.id.mkList)
        empty = findViewById(R.id.tvMkEmpty)
        findViewById<MaterialButton>(R.id.btnMkRefresh).setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastTap < CustomerPage.AGAIN_MS) {
                Toast.makeText(this, R.string.mera_khata_just_now, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lastTap = now
            refresh(force = true)
        }
        // A phone that is only a customer's: the way to its own shop's khata (the setup), the app's own invitation.
        findViewById<View>(R.id.mkStartOwn).visibility =
            if (SetupProgress.next(this) != SetupProgress.Step.DONE) View.VISIBLE else View.GONE
        findViewById<MaterialButton>(R.id.btnMkStartOwn).setOnClickListener {
            // The setup goes into the app's own task, not this screen's (it keeps one of its own).
            startActivity(
                Intent(this, GateActivity::class.java).putExtra(GateActivity.EXTRA_SETUP, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            finish()
        }
        com.innovation313.roshankhata.ui.TextFit.relax(findViewById(android.R.id.content))
        lockAsked = savedInstanceState?.getBoolean(STATE_LOCK_ASKED) ?: false
        unlocksWhenAsked = savedInstanceState?.getInt(STATE_UNLOCKS) ?: 0
        if (savedInstanceState == null) take(intent)
        // Drawn in onStart, once App Lock (when the owner has it on) has had its say.
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_LOCK_ASKED, lockAsked)
        outState.putInt(STATE_UNLOCKS, unlocksWhenAsked)
    }

    override fun onStart() {
        super.onStart()
        if (lockHolds()) return
        render()
        refresh(force = false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        take(intent)
        // A link opened while this screen is on top; a screen coming back from the background is drawn by onStart.
        if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) || lockHolds()) return
        render()
        refresh(force = false)
    }

    /**
     * App Lock, when this phone's owner has it on and it is due (7 Nov 2026, the review's L2): the list is his own
     * balances with other shops. Nothing is drawn until it is answered; asked once from here, and a return with no
     * unlock since closes the screen. True while the lock holds.
     */
    private fun lockHolds(): Boolean {
        val due = AppLock.Away.due(SystemClock.elapsedRealtime()) { AppLock.required(this) }
        findViewById<View>(R.id.mkScroll).visibility = if (due) View.INVISIBLE else View.VISIBLE
        if (!due) { lockAsked = false; return false }
        if (lockAsked && AppLock.Away.unlocks == unlocksWhenAsked) { finish(); return true }
        lockAsked = true
        unlocksWhenAsked = AppLock.Away.unlocks
        startActivity(Intent(this, LockActivity::class.java).putExtra(LockActivity.EXTRA_RELOCK, true))
        return true
    }

    /** The link this screen was opened with: kept apart until its page opens, then first in the list. */
    private fun take(intent: Intent?) {
        val data = runCatching { intent?.dataString }.getOrNull() ?: return
        val link = CustomerPage.linkFrom(data)
        if (link == null) {
            Toast.makeText(this, R.string.mera_khata_bad_link, Toast.LENGTH_LONG).show()
            return
        }
        MeraKhataStore.add(this, link)
    }

    private fun render() {
        val saved = MeraKhataStore.all(this)
        val waiting = MeraKhataStore.pending(this).filter { p -> saved.none { it.link == p } }
        list.removeAllViews()
        val none = saved.isEmpty() && waiting.isEmpty()
        empty.visibility = if (none) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnMkRefresh).visibility = if (none) View.GONE else View.VISIBLE
        val inflater = LayoutInflater.from(this)
        waiting.forEach { link -> list.addView(card(inflater, MeraKhataStore.Saved(link, null, null, 0L, MeraKhataStore.State.OK), kept = false)) }
        saved.forEach { s -> list.addView(card(inflater, s, kept = true)) }
        com.innovation313.roshankhata.ui.TextFit.relax(list)
    }

    /** A day as the phone's calendar writes it; never a crash for a day the calendar cannot draw. */
    private fun dayOf(pattern: String, millis: Long): String? =
        runCatching { DateWords.format(pattern, millis) }.getOrNull()

    private fun card(inflater: LayoutInflater, s: MeraKhataStore.Saved, kept: Boolean): View {
        val v = inflater.inflate(R.layout.item_mera_khata, list, false)
        val shop = v.findViewById<TextView>(R.id.tvMkShop)
        val name = v.findViewById<TextView>(R.id.tvMkName)
        val side = v.findViewById<TextView>(R.id.tvMkSide)
        val amount = v.findViewById<TextView>(R.id.tvMkAmount)
        val updated = v.findViewById<TextView>(R.id.tvMkUpdated)
        val lines = v.findViewById<TextView>(R.id.tvMkLines)
        val status = v.findViewById<TextView>(R.id.tvMkStatus)
        v.findViewById<ImageButton>(R.id.btnMkMore).setOnClickListener { askRemove(s.link) }

        val page = CustomerPage.reopen(s.link, s.blob)
        fun say(text: Int) { status.setText(text); status.visibility = View.VISIBLE }
        fun noFigures() { side.visibility = View.GONE; amount.visibility = View.GONE; updated.visibility = View.GONE; lines.visibility = View.GONE }
        when {
            page == null -> {
                shop.setText(R.string.mera_khata_unknown_shop)
                name.visibility = View.GONE
                noFigures()
                say(if (!kept || s.state == MeraKhataStore.State.OK) R.string.mera_khata_not_yet else stateWord(s.state))
            }
            // Stopped by the shop: who it was, never the old figure as if it still stood.
            s.state == MeraKhataStore.State.GONE -> {
                shop.text = page.shop.ifBlank { getString(R.string.mera_khata_unknown_shop) }
                name.text = page.name
                name.visibility = if (page.name.isBlank()) View.GONE else View.VISIBLE
                noFigures()
                say(R.string.mera_khata_gone)
            }
            page.paused -> {
                shop.setText(R.string.mera_khata_unknown_shop)
                name.visibility = View.GONE
                noFigures()
                say(R.string.mera_khata_paused)
            }
            else -> {
                shop.text = page.shop.ifBlank { getString(R.string.mera_khata_unknown_shop) }
                name.text = page.name
                name.visibility = if (page.name.isBlank()) View.GONE else View.VISIBLE
                val which = CustomerPage.sideOf(page.balance)
                side.setText(when (which) {
                    CustomerPage.Side.OWES -> R.string.mera_khata_you_owe
                    CustomerPage.Side.OWED -> R.string.mera_khata_shop_owes
                    CustomerPage.Side.SETTLED -> R.string.mera_khata_settled
                })
                amount.text = CustomerPage.money(page.currency, page.decimals, page.language, page.balance)
                amount.setTextColor(ContextCompat.getColor(this,
                    if (which == CustomerPage.Side.OWES) R.color.red_gave_text else R.color.green_got_text))
                val day = if (page.updated > 0L) dayOf("d MMM yyyy", page.updated) else null
                updated.text = if (day != null) getString(R.string.mera_khata_updated, day) else ""
                updated.visibility = if (day != null) View.VISIBLE else View.GONE
                if (page.lines.isNotEmpty()) {
                    lines.text = page.lines.joinToString("\n") { l ->
                        val what = l.words ?: getString(if (l.gave) R.string.mera_khata_line_gave else R.string.mera_khata_line_got)
                        listOfNotNull(dayOf("d MMM", l.at).takeIf { l.at > 0L }, what,
                            CustomerPage.money(page.currency, page.decimals, page.language, l.amount)).joinToString(" · ")
                    }
                    lines.visibility = View.VISIBLE
                }
                when {
                    s.state != MeraKhataStore.State.OK -> say(stateWord(s.state))
                    s.older -> say(R.string.mera_khata_older)
                }
            }
        }
        return v
    }

    private fun stateWord(state: MeraKhataStore.State): Int = when (state) {
        MeraKhataStore.State.GONE -> R.string.mera_khata_gone
        MeraKhataStore.State.WRONG -> R.string.mera_khata_wrong
        MeraKhataStore.State.OK -> R.string.mera_khata_not_yet
    }

    private fun askRemove(link: CustomerLink.Link) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.mera_khata_remove)
            .setMessage(R.string.mera_khata_remove_body)
            .setPositiveButton(R.string.mera_khata_remove) { _, _ ->
                MeraKhataStore.remove(this, link)
                render()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Asks the server for the pages due: every page on [force] (the owner's tap, at most once a minute), else only
     * those not asked for [CustomerPage.FRESH_MS]; a link still waiting for its page every time. One at a time; the
     * screen is drawn again once they are in.
     */
    private fun refresh(force: Boolean) {
        if (busy) return
        val now = System.currentTimeMillis()
        val saved = MeraKhataStore.all(this)
        val due = saved.filter { force || now - it.askedAt > CustomerPage.FRESH_MS }
        val waiting = MeraKhataStore.pending(this).filter { p -> saved.none { it.link == p } }
        if (due.isEmpty() && waiting.isEmpty()) return
        busy = true
        val app = applicationContext
        lifecycleScope.launch {
            var offline = false
            var refused: Int? = null
            withContext(Dispatchers.IO) {
                for (link in waiting) {
                    when (val r = CustomerPage.fetch(link, null)) {
                        is CustomerPage.Fetched.Ok -> MeraKhataStore.confirm(app, MeraKhataStore.Saved(
                            link, r.blob, r.etag, System.currentTimeMillis(), MeraKhataStore.State.OK, r.page.seq))
                        // A link that does not open is not kept: said once, and gone.
                        CustomerPage.Fetched.Gone -> { MeraKhataStore.dropPending(app, link); refused = R.string.mera_khata_gone }
                        CustomerPage.Fetched.Wrong -> { MeraKhataStore.dropPending(app, link); refused = R.string.mera_khata_wrong }
                        CustomerPage.Fetched.Same -> Unit
                        CustomerPage.Fetched.Offline -> offline = true
                    }
                }
                for (s in due) {
                    val at = System.currentTimeMillis()
                    when (val r = CustomerPage.fetch(s.link, s.etag.takeIf { s.blob != null })) {
                        is CustomerPage.Fetched.Ok -> MeraKhataStore.update(app, when (CustomerPage.fresh(CustomerPage.reopen(s.link, s.blob), r.page)) {
                            CustomerPage.Fresh.NEWER -> s.copy(blob = r.blob, etag = r.etag, askedAt = at, state = MeraKhataStore.State.OK, seq = r.page.seq, older = false)
                            // The same page from another phone of the shop's book: the higher counter stays, nothing to say.
                            CustomerPage.Fresh.SAME -> s.copy(askedAt = at, state = MeraKhataStore.State.OK, older = false)
                            // An older page served again: the newest one seen stays, and the card says so.
                            CustomerPage.Fresh.OLDER -> s.copy(askedAt = at, state = MeraKhataStore.State.OK, older = true)
                        })
                        CustomerPage.Fetched.Same -> MeraKhataStore.update(app, s.copy(askedAt = at, state = MeraKhataStore.State.OK, older = false))
                        CustomerPage.Fetched.Gone -> MeraKhataStore.update(app, s.copy(askedAt = at, state = MeraKhataStore.State.GONE, older = false))
                        CustomerPage.Fetched.Wrong -> MeraKhataStore.update(app, s.copy(askedAt = at, state = MeraKhataStore.State.WRONG))
                        CustomerPage.Fetched.Offline -> offline = true
                    }
                }
            }
            busy = false
            if (isFinishing || isDestroyed) return@launch
            render()
            refused?.let { Toast.makeText(this@MeraKhataActivity, it, Toast.LENGTH_LONG).show() }
            if (offline) Toast.makeText(this@MeraKhataActivity, R.string.mera_khata_offline, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val STATE_LOCK_ASKED = "mk_lock_asked"
        private const val STATE_UNLOCKS = "mk_unlocks"

        /** True when this phone opened a shop's link (the More sheet and the front door show Mera Khata only then). */
        fun has(context: android.content.Context): Boolean = MeraKhataStore.any(context)
    }
}
