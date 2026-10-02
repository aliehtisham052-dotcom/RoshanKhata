package com.innovation313.roshankhata

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.Money
import com.innovation313.roshankhata.data.SeasonBook
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.ScreenInsets
import com.innovation313.roshankhata.ui.SeasonText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Fasal ka Hisaab (2 Oct): every crop season the shop gave credit in, newest
 * first — how many customers, how much went out, how much is back, how much
 * is still out, and how long after the harvest customers usually clear.
 * Tapping a season lists who still owes for it. The arithmetic is all in
 * [SeasonBook]; this screen only lays it out.
 */
class SeasonsActivity : BaseActivity() {

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    private class Shown(
        val summaries: List<SeasonBook.Summary>,
        val rows: List<SeasonBook.PartySeason>,
        val names: Map<Long, String>
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_seasons)
        ScreenInsets.on(this)

        val list = findViewById<LinearLayout>(R.id.seasonList)
        val empty = findViewById<TextView>(R.id.tvSeasonEmpty)

        lifecycleScope.launch {
            dao.observeSeasonLines()
                .combine(dao.observePartiesWithBalance().map { all -> all.associate { it.id to it.name } }) { lines, names ->
                    val rows = SeasonBook.book(lines)
                    Shown(SeasonBook.summaries(rows), rows, names)
                }
                .flowOn(Dispatchers.Default)
                .collectLatest { shown ->
                    // Keep the empty-state view (child 0), redraw the cards.
                    while (list.childCount > 1) list.removeViewAt(1)
                    empty.visibility = if (shown.summaries.isEmpty()) View.VISIBLE else View.GONE
                    for (s in shown.summaries) list.addView(card(list, s, shown))
                }
        }
    }

    private fun card(parent: LinearLayout, s: SeasonBook.Summary, shown: Shown): View {
        val v = layoutInflater.inflate(R.layout.item_season, parent, false)
        v.findViewById<TextView>(R.id.tvSeasonName).text = SeasonText.name(this, s.season)
        v.findViewById<TextView>(R.id.tvSeasonHarvest).text = SeasonText.harvest(this, s.season)
        v.findViewById<TextView>(R.id.tvSeasonGiven).text = resources.getQuantityString(
            R.plurals.season_given, s.customers, s.customers, Format.money(s.given)
        )
        v.findViewById<TextView>(R.id.tvSeasonOut).text =
            getString(R.string.season_back_out, Format.money(s.cleared), Format.money(s.outstanding))
        val d = s.medianDaysAfterHarvest
        v.findViewById<TextView>(R.id.tvSeasonPace).text = when {
            d == null -> getString(R.string.season_pace_none, s.clearedCustomers, s.customers)
            d > 0 -> resources.getQuantityString(R.plurals.season_pace_after, d, s.clearedCustomers, s.customers, d)
            else -> getString(R.string.season_pace_before, s.clearedCustomers, s.customers)
        }
        v.setOnClickListener { showOwing(s.season, shown) }
        return v
    }

    /** Who still owes for this season, biggest first; a tap opens their khata. */
    private fun showOwing(season: SeasonBook.Season, shown: Shown) {
        val owing = shown.rows
            .filter { it.season == season && Money.isPositive(it.outstanding) }
            .sortedByDescending { it.outstanding }
        if (owing.isEmpty()) {
            Toast.makeText(this, R.string.season_all_clear, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = owing.map { "${shown.names[it.partyId] ?: "—"}  ·  ${Format.money(it.outstanding)}" }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.season_owing_title, SeasonText.name(this, season)))
            .setItems(labels) { _, i ->
                startActivity(
                    Intent(this, PartyDetailActivity::class.java)
                        .putExtra(PartyDetailActivity.EXTRA_PARTY_ID, owing[i].partyId)
                )
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }
}
