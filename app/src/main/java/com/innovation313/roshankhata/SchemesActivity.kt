package com.innovation313.roshankhata

import com.innovation313.roshankhata.data.UnitWords
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.data.AppScope
import com.innovation313.roshankhata.data.Digits
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.Party
import com.innovation313.roshankhata.data.Scheme
import com.innovation313.roshankhata.data.SchemeMath
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.ScreenInsets
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * C1 (2 Oct): company schemes. Each card reads its purchases from the
 * supplier bills already in the app and shows how far along it is, what the
 * next slab needs, what has been earned, and — once the scheme has ended —
 * whether the claim is still due. A claim stays "due" until the owner records
 * what actually arrived. All arithmetic lives in [SchemeMath].
 */
class SchemesActivity : BaseActivity() {

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    /** Bumped on return: a bill added meanwhile must count at once. */
    private val tick = kotlinx.coroutines.flow.MutableStateFlow(0)

    override fun onResume() {
        super.onResume()
        tick.value = tick.value + 1
    }

    private class Row(val scheme: Scheme, val progress: SchemeMath.Progress, val unpriced: Int, val supplier: String?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schemes)
        ScreenInsets.on(this)

        val list = findViewById<LinearLayout>(R.id.schemeList)
        val empty = findViewById<TextView>(R.id.tvSchemesEmpty)
        val summary = findViewById<TextView>(R.id.tvSchemesSummary)
        findViewById<MaterialButton>(R.id.btnAddScheme).setOnClickListener { showForm(null) }

        lifecycleScope.launch {
            dao.observeSchemes().combine(tick) { schemes, _ -> schemes }.collectLatest { schemes ->
                val suppliers = dao.suppliersOnce().associate { it.id to it.name }
                val rows = schemes.map { s ->
                    val byValue = s.measure == SchemeMath.BY_VALUE
                    val bought = dao.schemePurchased(byValue, s.startDate, s.endDate, s.partyId, s.company, s.unit)
                    val unpriced = if (byValue) dao.schemeUnpricedLines(s.startDate, s.endDate, s.partyId, s.company) else 0
                    Row(s, SchemeMath.progress(s, bought), unpriced, s.partyId?.let { suppliers[it] })
                }
                while (list.childCount > 1) list.removeViewAt(1)
                empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
                val now = System.currentTimeMillis()
                val due = rows.count { SchemeMath.status(it.scheme, it.progress, now) == SchemeMath.Status.CLAIM_DUE }
                summary.text = if (due > 0) Digits.quantity(resources, R.plurals.schemes_claims_due, due, due)
                else getString(R.string.schemes_subtitle)
                rows.forEach { list.addView(card(list, it, now)) }
            }
        }
    }

    private fun amountText(s: Scheme, value: Double): String =
        if (s.measure == SchemeMath.BY_VALUE) Format.money(value) else Format.qty(value, s.unit)

    private fun rewardText(s: Scheme, slab: SchemeMath.Slab): String = when (s.rewardKind) {
        SchemeMath.PERCENT -> getString(R.string.scheme_reward_pct_value, Format.plain(slab.reward))
        SchemeMath.AMOUNT -> Format.money(slab.reward)
        else -> getString(R.string.scheme_reward_units_value, Format.plain(slab.reward))
    }

    private fun card(parent: LinearLayout, r: Row, now: Long): View {
        val s = r.scheme
        val p = r.progress
        val v = layoutInflater.inflate(R.layout.item_scheme, parent, false)
        v.findViewById<TextView>(R.id.tvSchemeName).text = s.name
        v.findViewById<TextView>(R.id.tvSchemeScope).text = listOfNotNull(
            r.supplier, s.company,
            getString(R.string.scheme_period, Format.dateOnly(s.startDate), Format.dateOnly(s.endDate))
        ).joinToString("  ·  ")
        v.findViewById<TextView>(R.id.tvSchemeBought).text = buildString {
            append(getString(R.string.scheme_bought, amountText(s, p.purchased)))
            // A bill line with no rate adds nothing to a value scheme: say so.
            if (r.unpriced > 0) append("\n").append(Digits.quantity(resources, R.plurals.scheme_unpriced, r.unpriced, r.unpriced))
        }
        v.findViewById<TextView>(R.id.tvSchemeNext).text = p.next?.let {
            getString(R.string.scheme_next, amountText(s, it.target), rewardText(s, it), amountText(s, p.toNext ?: 0.0))
        } ?: getString(R.string.scheme_top_reached)
        v.findViewById<TextView>(R.id.tvSchemeEarned).text = when {
            p.achieved == null -> getString(R.string.scheme_nothing_yet)
            s.rewardKind == SchemeMath.UNITS -> getString(R.string.scheme_earned, rewardText(s, p.achieved))
            else -> getString(R.string.scheme_earned, p.earned?.let { Format.money(it) } ?: rewardText(s, p.achieved))
        }
        val status = SchemeMath.status(s, p, now)
        v.findViewById<TextView>(R.id.tvSchemeStatus).apply {
            text = when (status) {
                SchemeMath.Status.RUNNING -> getString(R.string.scheme_status_running)
                SchemeMath.Status.CLAIM_DUE -> getString(R.string.scheme_status_claim_due)
                SchemeMath.Status.CLAIMED -> getString(R.string.scheme_status_claimed, Format.money(s.claimedAmount ?: 0.0))
                SchemeMath.Status.MISSED -> getString(R.string.scheme_status_missed)
            }
            setTextColor(ContextCompat.getColor(this@SchemesActivity,
                if (status == SchemeMath.Status.CLAIM_DUE) R.color.red_gave_text else R.color.text_muted))
        }
        v.setOnClickListener { showActions(s) }
        return v
    }

    private fun showActions(s: Scheme) {
        val options = listOf(
            getString(R.string.scheme_edit),
            getString(if (s.claimedAmount == null) R.string.scheme_mark_claimed else R.string.scheme_unmark_claimed),
            getString(R.string.scheme_delete)
        ).toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(s.name)
            .setItems(options) { _, i ->
                when (i) {
                    0 -> showForm(s)
                    1 -> if (s.claimedAmount == null) askClaim(s) else AppScope.launch { dao.setSchemeClaim(s.id, null, null) }
                    2 -> MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.scheme_delete)
                        .setMessage(getString(R.string.scheme_delete_confirm, s.name))
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.scheme_delete) { _, _ -> AppScope.launch { dao.deleteScheme(s.id) } }
                        .show()
                }
            }
            .show()
    }

    /** "Claim received" — what actually came (credit note, rebate, free bags' value). */
    private fun askClaim(s: Scheme) {
        val et = EditText(this).apply {
            hint = getString(R.string.scheme_claim_hint)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val box = LinearLayout(this).apply {
            setPadding(48, 16, 48, 0)
            addView(et)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.scheme_mark_claimed)
            .setView(box)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val amount = Digits.parse(et.text.toString())
                if (amount == null || amount < 0) {
                    Toast.makeText(this, R.string.scheme_claim_hint, Toast.LENGTH_SHORT).show()
                } else AppScope.launch { dao.setSchemeClaim(s.id, amount, System.currentTimeMillis()) }
            }
            .show()
    }

    private fun showForm(existing: Scheme?) {
        lifecycleScope.launch {
            val suppliers: List<Party> = dao.suppliersOnce()
            val view = layoutInflater.inflate(R.layout.dialog_scheme, null)
            val etName = view.findViewById<EditText>(R.id.etSchemeName)
            // A company's crop-season scheme is an agri shop's example; any
            // other trade is shown a festival offer instead (9 Oct).
            if (com.innovation313.roshankhata.data.Trade.current(this@SchemesActivity) != com.innovation313.roshankhata.data.Trade.AGRI) {
                etName.setHint(R.string.scheme_name_hint_general)
            }
            val btnSupplier = view.findViewById<MaterialButton>(R.id.btnSchemeSupplier)
            val etCompany = view.findViewById<EditText>(R.id.etSchemeCompany)
            val btnStart = view.findViewById<MaterialButton>(R.id.btnSchemeStart)
            val btnEnd = view.findViewById<MaterialButton>(R.id.btnSchemeEnd)
            val rgMeasure = view.findViewById<RadioGroup>(R.id.rgMeasure)
            val etUnit = view.findViewById<EditText>(R.id.etSchemeUnit)
            val rbPercent = view.findViewById<RadioButton>(R.id.rbPercent)
            val rgReward = view.findViewById<RadioGroup>(R.id.rgReward)
            val targets = listOf(R.id.etT1, R.id.etT2, R.id.etT3).map { view.findViewById<EditText>(it) }
            val rewards = listOf(R.id.etR1, R.id.etR2, R.id.etR3).map { view.findViewById<EditText>(it) }
            val etNote = view.findViewById<EditText>(R.id.etSchemeNote)

            var partyId: Long? = existing?.partyId
            var start = existing?.startDate ?: startOfDay(System.currentTimeMillis())
            var end = existing?.endDate ?: endOfDay(System.currentTimeMillis() + 90L * 24 * 60 * 60 * 1000)

            fun showSupplier() {
                btnSupplier.text = getString(R.string.scheme_supplier,
                    suppliers.firstOrNull { it.id == partyId }?.name ?: getString(R.string.scheme_any_supplier))
            }
            fun showDates() {
                btnStart.text = getString(R.string.scheme_from, Format.dateOnly(start))
                btnEnd.text = getString(R.string.scheme_to, Format.dateOnly(end))
            }
            // A percentage needs a rupee base: only offered on a value scheme.
            fun syncMeasure() {
                val byQty = rgMeasure.checkedRadioButtonId == R.id.rbQty
                etUnit.visibility = if (byQty) View.VISIBLE else View.GONE
                rbPercent.isEnabled = !byQty
                if (byQty && rbPercent.isChecked) rgReward.check(R.id.rbAmount)
            }

            existing?.let { s ->
                etName.setText(s.name)
                etCompany.setText(s.company)
                rgMeasure.check(if (s.measure == SchemeMath.BY_QTY) R.id.rbQty else R.id.rbValue)
                etUnit.setText(UnitWords.label(s.unit))
                rgReward.check(when (s.rewardKind) {
                    SchemeMath.AMOUNT -> R.id.rbAmount
                    SchemeMath.UNITS -> R.id.rbUnits
                    else -> R.id.rbPercent
                })
                val slabs = listOf(s.target1 to s.reward1, s.target2 to s.reward2, s.target3 to s.reward3)
                slabs.forEachIndexed { i, (t, r) ->
                    t?.let { targets[i].setText(Format.plain(it)) }
                    r?.let { rewards[i].setText(Format.plain(it)) }
                }
                etNote.setText(s.note)
            }
            showSupplier(); showDates(); syncMeasure()
            rgMeasure.setOnCheckedChangeListener { _, _ -> syncMeasure() }
            btnSupplier.setOnClickListener {
                val names = listOf(getString(R.string.scheme_any_supplier)) + suppliers.map { it.name }
                MaterialAlertDialogBuilder(this@SchemesActivity)
                    .setItems(names.toTypedArray()) { _, i ->
                        partyId = if (i == 0) null else suppliers[i - 1].id
                        showSupplier()
                    }
                    .show()
            }
            btnStart.setOnClickListener { pickDate(start) { start = startOfDay(it); showDates() } }
            btnEnd.setOnClickListener { pickDate(end) { end = endOfDay(it); showDates() } }

            MaterialAlertDialogBuilder(this@SchemesActivity)
                .setTitle(if (existing == null) R.string.schemes_add else R.string.scheme_edit)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save) { _, _ ->
                    val name = etName.text.toString().trim()
                    val slabs = targets.indices.mapNotNull { i ->
                        val t = Digits.parse(targets[i].text.toString())
                        val r = Digits.parse(rewards[i].text.toString())
                        if (t != null && t > 0 && r != null && r > 0) t to r else null
                    }.sortedBy { it.first }
                    when {
                        name.isEmpty() -> Toast.makeText(this@SchemesActivity, R.string.scheme_need_name, Toast.LENGTH_SHORT).show()
                        slabs.isEmpty() -> Toast.makeText(this@SchemesActivity, R.string.scheme_need_slab, Toast.LENGTH_SHORT).show()
                        end < start -> Toast.makeText(this@SchemesActivity, R.string.scheme_bad_dates, Toast.LENGTH_SHORT).show()
                        else -> {
                            val byQty = rgMeasure.checkedRadioButtonId == R.id.rbQty
                            val scheme = Scheme(
                                id = existing?.id ?: 0,
                                name = name,
                                partyId = partyId,
                                company = etCompany.text.toString().trim().ifEmpty { null },
                                startDate = start,
                                endDate = end,
                                measure = if (byQty) SchemeMath.BY_QTY else SchemeMath.BY_VALUE,
                                unit = if (byQty) UnitWords.canonical(etUnit.text.toString()) else null,
                                rewardKind = when (rgReward.checkedRadioButtonId) {
                                    R.id.rbAmount -> SchemeMath.AMOUNT
                                    R.id.rbUnits -> SchemeMath.UNITS
                                    else -> SchemeMath.PERCENT
                                },
                                target1 = slabs[0].first, reward1 = slabs[0].second,
                                target2 = slabs.getOrNull(1)?.first, reward2 = slabs.getOrNull(1)?.second,
                                target3 = slabs.getOrNull(2)?.first, reward3 = slabs.getOrNull(2)?.second,
                                claimedAmount = existing?.claimedAmount,
                                claimedAt = existing?.claimedAt,
                                note = etNote.text.toString().trim().ifEmpty { null },
                                createdAt = existing?.createdAt ?: System.currentTimeMillis()
                            )
                            AppScope.launch { dao.saveScheme(scheme) }
                        }
                    }
                }
                .show()
        }
    }

    private fun pickDate(startAt: Long, onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = startAt }
        DatePickerDialog(this, { _, y, m, d ->
            onPicked(Calendar.getInstance().apply { clear(); set(y, m, d) }.timeInMillis)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun startOfDay(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun endOfDay(t: Long): Long = startOfDay(t) + 24L * 60 * 60 * 1000 - 1
}
