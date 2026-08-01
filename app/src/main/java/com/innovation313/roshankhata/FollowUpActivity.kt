package com.innovation313.roshankhata

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.innovation313.roshankhata.data.BusinessProfile
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.ui.FollowUpAdapter
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.Reminder
import com.innovation313.roshankhata.ui.ScreenInsets
import kotlinx.coroutines.flow.collectLatest
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
 */
class FollowUpActivity : AppCompatActivity() {

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
            dao.observePartiesWithBalance().collectLatest { all ->
                // balance > 0 means they owe the shop — the only rows this
                // screen is for. lastActivity ascending puts the account
                // that has been quiet longest at the top; the bigger balance
                // wins between two equally quiet ones.
                val debtors = all.filter { it.balance > 0 }
                    .sortedWith(
                        compareBy<PartyWithBalance> { it.lastActivity }
                            .thenByDescending { it.balance }
                    )

                adapter.submitList(debtors)

                val total = debtors.sumOf { it.balance }
                tvSummary.text =
                    getString(R.string.followup_summary, debtors.size, Format.money(total))
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
        val message = Reminder.buildMessage(
            this,
            partyName = party.name,
            balance = party.balance,
            businessName = BusinessProfile.businessName(this)
        )
        Reminder.sendViaWhatsApp(this, party.phone, message)
    }
}
