package com.innovation313.roshankhata

import android.content.Intent
import android.os.Bundle
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
 */
class MeraKhataActivity : BaseActivity() {

    private lateinit var list: LinearLayout
    private lateinit var empty: TextView
    private var lastTap = 0L
    private var busy = false

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
            startActivity(Intent(this, GateActivity::class.java).putExtra(GateActivity.EXTRA_SETUP, true))
            finish()
        }
        com.innovation313.roshankhata.ui.TextFit.relax(findViewById(android.R.id.content))
        if (savedInstanceState == null) take(intent)
        render()
        refresh(force = false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        take(intent)
        render()
        refresh(force = false)
    }

    /** The link this screen was opened with, kept first in the list; a link that is not one is said. */
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
        list.removeAllViews()
        empty.visibility = if (saved.isEmpty()) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnMkRefresh).visibility = if (saved.isEmpty()) View.GONE else View.VISIBLE
        val inflater = LayoutInflater.from(this)
        saved.forEach { s -> list.addView(card(inflater, s)) }
        com.innovation313.roshankhata.ui.TextFit.relax(list)
    }

    private fun card(inflater: LayoutInflater, s: MeraKhataStore.Saved): View {
        val v = inflater.inflate(R.layout.item_mera_khata, list, false)
        val shop = v.findViewById<TextView>(R.id.tvMkShop)
        val name = v.findViewById<TextView>(R.id.tvMkName)
        val side = v.findViewById<TextView>(R.id.tvMkSide)
        val amount = v.findViewById<TextView>(R.id.tvMkAmount)
        val updated = v.findViewById<TextView>(R.id.tvMkUpdated)
        val lines = v.findViewById<TextView>(R.id.tvMkLines)
        val status = v.findViewById<TextView>(R.id.tvMkStatus)
        v.findViewById<ImageButton>(R.id.btnMkMore).setOnClickListener { askRemove(s) }

        val page = CustomerPage.reopen(s.link, s.blob)
        fun say(text: Int) { status.setText(text); status.visibility = View.VISIBLE }
        when {
            page == null -> {
                shop.setText(R.string.mera_khata_unknown_shop)
                name.visibility = View.GONE; side.visibility = View.GONE; amount.visibility = View.GONE; updated.visibility = View.GONE
                say(if (s.state == MeraKhataStore.State.OK) R.string.mera_khata_not_yet else stateWord(s.state))
            }
            page.paused -> {
                shop.setText(R.string.mera_khata_unknown_shop)
                name.visibility = View.GONE; side.visibility = View.GONE; amount.visibility = View.GONE; updated.visibility = View.GONE
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
                updated.text = if (page.updated > 0L) getString(R.string.mera_khata_updated, DateWords.format("d MMM yyyy", page.updated)) else ""
                updated.visibility = if (page.updated > 0L) View.VISIBLE else View.GONE
                if (page.lines.isNotEmpty()) {
                    lines.text = page.lines.joinToString("\n") { l ->
                        val what = l.words ?: getString(if (l.gave) R.string.mera_khata_line_gave else R.string.mera_khata_line_got)
                        DateWords.format("d MMM", l.at) + " · " + what + " · " +
                            CustomerPage.money(page.currency, page.decimals, page.language, l.amount)
                    }
                    lines.visibility = View.VISIBLE
                }
                if (s.state != MeraKhataStore.State.OK) say(stateWord(s.state))
            }
        }
        return v
    }

    private fun stateWord(state: MeraKhataStore.State): Int = when (state) {
        MeraKhataStore.State.GONE -> R.string.mera_khata_gone
        MeraKhataStore.State.WRONG -> R.string.mera_khata_wrong
        MeraKhataStore.State.OK -> R.string.mera_khata_not_yet
    }

    private fun askRemove(s: MeraKhataStore.Saved) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.mera_khata_remove)
            .setMessage(R.string.mera_khata_remove_body)
            .setPositiveButton(R.string.mera_khata_remove) { _, _ ->
                MeraKhataStore.remove(this, s.link)
                render()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Asks the server for the pages due: every page on [force] (the owner's tap, at most once a minute), else only
     * those not asked for [CustomerPage.FRESH_MS]. One at a time; the screen is drawn again once they are in.
     */
    private fun refresh(force: Boolean) {
        if (busy) return
        val now = System.currentTimeMillis()
        val due = MeraKhataStore.all(this).filter { force || now - it.askedAt > CustomerPage.FRESH_MS }
        if (due.isEmpty()) return
        busy = true
        val app = applicationContext
        lifecycleScope.launch {
            var offline = false
            withContext(Dispatchers.IO) {
                for (s in due) {
                    when (val r = CustomerPage.fetch(s.link, s.etag.takeIf { s.blob != null })) {
                        is CustomerPage.Fetched.Ok -> MeraKhataStore.update(app, s.copy(blob = r.blob, etag = r.etag, askedAt = System.currentTimeMillis(), state = MeraKhataStore.State.OK))
                        CustomerPage.Fetched.Same -> MeraKhataStore.update(app, s.copy(askedAt = System.currentTimeMillis(), state = MeraKhataStore.State.OK))
                        CustomerPage.Fetched.Gone -> MeraKhataStore.update(app, s.copy(askedAt = System.currentTimeMillis(), state = MeraKhataStore.State.GONE))
                        CustomerPage.Fetched.Wrong -> MeraKhataStore.update(app, s.copy(askedAt = System.currentTimeMillis(), state = MeraKhataStore.State.WRONG))
                        CustomerPage.Fetched.Offline -> offline = true
                    }
                }
            }
            busy = false
            if (isFinishing || isDestroyed) return@launch
            render()
            if (offline) Toast.makeText(this@MeraKhataActivity, R.string.mera_khata_offline, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        /** True when this phone opened a shop's link (the More sheet and the front door show Mera Khata only then). */
        fun has(context: android.content.Context): Boolean = MeraKhataStore.any(context)
    }
}
