package com.innovation313.roshankhata

import com.innovation313.roshankhata.data.audit
import com.innovation313.roshankhata.data.auditAmount
import com.innovation313.roshankhata.data.AuditAction as AuditActs

import com.innovation313.roshankhata.data.get

import com.innovation313.roshankhata.ui.ChoiceSheet
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.DateTimeField

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.innovation313.roshankhata.data.AppScope
import com.innovation313.roshankhata.data.CashEntry
import com.innovation313.roshankhata.data.ReminderLog
import com.innovation313.roshankhata.data.LineMath
import com.innovation313.roshankhata.data.Money
import com.innovation313.roshankhata.data.PaymentMethod
import com.innovation313.roshankhata.data.PaymentRef
import com.innovation313.roshankhata.data.labelOf
import com.innovation313.roshankhata.data.DayCloseMath
import com.innovation313.roshankhata.data.DayClose
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.ui.CashAdapter
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.customerBalanceColour
import com.innovation313.roshankhata.ui.signedTotalColourOnDark
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The cashbook: business money with no customer attached.
 *
 * Kept firmly apart from the party ledger. Rent paid, wages paid, a walk-in
 * cash sale — none of it changes what any customer owes, and none of it should
 * ever touch a balance. Mixing the two would either invent a phantom party or
 * quietly corrupt a real one's account.
 */
class CashbookActivity : BaseActivity() {

    private lateinit var adapter: CashAdapter
    private lateinit var tvIn: TextView
    private lateinit var tvOut: TextView
    private lateinit var tvNet: TextView
    private lateinit var tvEmpty: TextView

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    // ---------- A receipt's picture (schema 43, 4 Nov 2026) ----------
    //
    // A bill photo's rules: the scanner, the camera or the gallery (BillScanFlow), the picture kept in the app's
    // own bills folder (BillPhoto) and pointed at by the row. On the money form it waits ([pendingPhoto]) until
    // Save, and goes if the form is left; on a row already written it is set at once ([photoForRow]), the old
    // picture going once no row points at it.

    /** The form's picture, taken but not yet written; also kept across a rebuild while the camera was in front. */
    private var pendingPhoto: String? = null
    private var photoButton: com.google.android.material.button.MaterialButton? = null
    /** The row whose picture is being set or changed, while the camera or the gallery is open; null: the form's. */
    private var photoForRow: Long? = null

    private val photoFlow = com.innovation313.roshankhata.ui.BillScanFlow(
        activity = this,
        onPhoto = { uri, temp -> keepPhoto(uri, temp) },
        onNothing = { photoForRow = null }
    )

    /**
     * The money in/out form while it is open: which way and what is typed in it (8 Nov 2026 review, M90 — a rotation, dark
     * mode or a process killed behind the camera lost "Money out 4,500 Bijli"). Chips, the day and the picture are not kept.
     */
    /** The day-close form's typed boxes (M90, 8 Nov 2026). */
    private val closeDraft = com.innovation313.roshankhata.ui.FormDraft("close_form")
    private var closeRestore: List<String>? = null

    private var cashFormIncome: Boolean? = null
    private var cashFormTexts: (() -> List<String>)? = null

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        closeDraft.save(outState)
        cashFormIncome?.let { inc ->
            outState.putBoolean(STATE_FORM_INCOME, inc)
            cashFormTexts?.let { outState.putStringArrayList(STATE_FORM_TEXTS, ArrayList(it())) }
        }
        photoFlow.saveState(outState, STATE_PHOTO_CAMERA)
        pendingPhoto?.let { outState.putString(STATE_PHOTO_PENDING, it) }
        photoForRow?.let { outState.putLong(STATE_PHOTO_ROW, it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        photoFlow.restoreState(savedInstanceState, STATE_PHOTO_CAMERA)
        pendingPhoto = savedInstanceState?.getString(STATE_PHOTO_PENDING)
        photoForRow = savedInstanceState?.getLong(STATE_PHOTO_ROW, -1L)?.takeIf { it > 0 }
        setContentView(R.layout.activity_cashbook)
        closeDraft.saved(savedInstanceState)?.let { sv ->
            closeRestore = sv.texts
            window.decorView.post { if (!isFinishing && !isDestroyed) showDayClose() }
        }
        if (savedInstanceState?.containsKey(STATE_FORM_INCOME) == true) {
            val inc = savedInstanceState.getBoolean(STATE_FORM_INCOME)
            val texts = savedInstanceState.getStringArrayList(STATE_FORM_TEXTS)
            window.decorView.post { if (!isFinishing && !isDestroyed) showAddDialog(inc, texts) }
        }
        // How this works, the first time (1 Nov 2026) - not when a notification brought the owner here to act.
        if (!intent.getBooleanExtra(EXTRA_CLOSE_DAY, false)) com.innovation313.roshankhata.ui.FeatureIntro.showOnce(this, com.innovation313.roshankhata.ui.FeatureIntro.Topic.CASHBOOK)
        // Owner only, like the screen itself: a helper's phone shows no staff list.
        findViewById<android.view.View>(R.id.btnStaff).apply {
            visibility = if (com.innovation313.roshankhata.data.ViewerMode.isOn(this@CashbookActivity))
                android.view.View.GONE else android.view.View.VISIBLE
            setOnClickListener { startActivity(android.content.Intent(this@CashbookActivity, StaffActivity::class.java)) }
        }

        // Edge-to-edge, the mechanism proven on the Home screen.
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)

        tvIn = findViewById(R.id.tvCashIn)
        tvOut = findViewById(R.id.tvCashOut)
        tvNet = findViewById(R.id.tvCashNet)
        tvEmpty = findViewById(R.id.tvNoCash)

        adapter = CashAdapter(onLongClick = { entry -> confirmDelete(entry) }, onClick = { entry -> showCashActions(entry) })
        val rv: RecyclerView = findViewById(R.id.rvCash)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter
        // The newest page first, more as the owner scrolls (23 Oct 2026, the review: every row was read).
        com.innovation313.roshankhata.ui.GrowingList.attach(rv, listLimit) { listShown }

        findViewById<ExtendedFloatingActionButton>(R.id.fabAddCash).setOnClickListener {
            showDirectionChoice()
        }

        findViewById<View>(R.id.btnCloseDay).setOnClickListener { showDayClose() }
        // The day's figures (POS Phase 4): a long press on Close the day shows the Z report.
        findViewById<View>(R.id.btnCloseDay).setOnLongClickListener { showDayReport(); true }
        findViewById<View>(R.id.btnDayReport).setOnClickListener { showDayReport() }
        // The closing automation's tap (31 Oct 2026): Close the day at once, on a fresh screen only.
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_CLOSE_DAY, false)) {
            findViewById<View>(R.id.btnCloseDay).post { if (!isFinishing && !isDestroyed) showDayClose() }
        }
        val tvLastClose = findViewById<TextView>(R.id.tvLastClose)
        // The last count is shown as what was COUNTED (8 Nov 2026, the owner's test: it showed "+ Rs 4,554,670", the
        // count's difference from the books, beside Who did what's "Day closed 4,555,225.00", the counted figure).
        // A tap opens its sheet: when, the figures, by whom, and the way to correct it.
        var shownClose: DayClose? = null
        tvLastClose.setOnClickListener { shownClose?.let { showCountSheet(it) } }
        lifecycleScope.launch {
            dao.observeLastDayClose().collectLatest { c ->
                shownClose = c
                tvLastClose.isClickable = c != null
                tvLastClose.text = if (c == null) getString(R.string.dayclose_never) else getString(
                    R.string.dayclose_last, Format.dateOnly(c.closedAt), Format.ltr(Format.signedIfNegative(c.counted))
                )
            }
        }

        observe()
    }

    /**
     * The last count in full (8 Nov 2026, the owner's test): a count is a record of what was physically counted, so it
     * is never deleted with the cashbook rows; a wrong one is corrected here, and the correction is written in Who did
     * what with the figure it replaced ([KhataDao.saveDayClose]).
     */
    private fun showCountSheet(c: DayClose) {
        lifecycleScope.launch {
            val by = dao.dayCloseUser(c.id)
            if (isFinishing || isDestroyed) return@launch
            val who = if (by == null || by == com.innovation313.roshankhata.data.Access.OWNER) getString(R.string.access_owner) else Format.name(by)
            val body = getString(
                R.string.dayclose_count_body, Format.dateTime(c.closedAt),
                Format.ltr(Format.signedIfNegative(c.counted)), Format.ltr(Format.signedIfNegative(c.expected)),
                Format.ltr(Format.signedTotal(c.difference)), who
            ) + (c.note?.takeIf { it.isNotBlank() }?.let { "\n" + it } ?: "")
            MaterialAlertDialogBuilder(this@CashbookActivity)
                .setTitle(R.string.dayclose_count_title)
                .setMessage(body)
                .setPositiveButton(R.string.dayclose_count_correct) { _, _ -> correctCount(c) }
                .setNegativeButton(R.string.close, null)
                .show()
        }
    }

    /** Correct a count: the right figure and why. The same working day's row is replaced, its old figure kept in the log. */
    private fun correctCount(c: DayClose) {
        if (!com.innovation313.roshankhata.ui.AccessUi.may(this, dao, com.innovation313.roshankhata.data.Access.Permission.EDIT_BOOK) { correctCount(c) }) return
        val etCount = com.innovation313.roshankhata.ui.FormBox.style(EditText(this)).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            hint = getString(R.string.dayclose_correct_amount_hint)
            setText(Format.plain(c.counted))
        }
        val etWhy = com.innovation313.roshankhata.ui.FormBox.style(EditText(this)).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            hint = getString(R.string.dayclose_correct_reason_hint)
        }
        val form = com.innovation313.roshankhata.ui.FormBox.column(this, etCount, etWhy)
        com.innovation313.roshankhata.ui.TextFit.relax(form)
        val opened = etCount.text.toString()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dayclose_count_correct)
            .setView(form)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .show().also { dialog ->
                com.innovation313.roshankhata.ui.KeyboardFollow.dialog(dialog)
                dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)?.setOnClickListener {
                    val figure = com.innovation313.roshankhata.data.Digits.parse(etCount.text)
                    if (figure == null) {
                        Toast.makeText(this, R.string.dayclose_need_count, Toast.LENGTH_SHORT).show()
                        etCount.requestFocus()
                        return@setOnClickListener
                    }
                    val why = etWhy.text.toString().trim()
                    if (why.isEmpty()) {
                        Toast.makeText(this, R.string.dayclose_correct_need_reason, Toast.LENGTH_LONG).show()
                        etWhy.requestFocus()
                        return@setOnClickListener
                    }
                    val note = listOfNotNull(why, c.note?.takeIf { it.isNotBlank() }).joinToString(" \u00b7 ")
                    AppScope.launch { dao.saveDayClose(c.copy(counted = LineMath.round(figure), note = note)) }
                    Toast.makeText(this, R.string.dayclose_correct_saved, Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                }
                com.innovation313.roshankhata.ui.FormGuard.guard(
                    activity = this,
                    dialog = dialog,
                    typed = { etCount.text.toString() != opened || etWhy.text.isNotBlank() }
                )
            }
    }

    /**
     * Galla milan (2 Oct): what the drawer should hold since the last count —
     * the last counted cash, plus cash in (cashbook income and khata receipts
     * marked Cash), minus cash out (expenses and khata payments marked Cash) —
     * against what the owner counts now. Entries with no payment method are
     * not guessed at; the help line says so.
     */
    private fun showDayClose() {
        lifecycleScope.launch {
            // The figures by the one shared rule (DayCloseMath.drawerByTheBooks, 29 Oct 2026): the computer's Home,
            // Cashbook and Close the day read the same, so no two screens can disagree on the drawer.
            val figures = DayCloseMath.drawerByTheBooks(dao)
            val last = figures.last
            val now = figures.now
            val cbIn = figures.cbIn
            val cbOut = figures.cbOut
            val kIn = figures.kIn
            val kOut = figures.kOut
            val cashIn = figures.cashIn
            val cashOut = figures.cashOut
            if (isFinishing || isDestroyed) return@launch

            val view = layoutInflater.inflate(R.layout.dialog_day_close, null)
            val etOpening = view.findViewById<EditText>(R.id.etOpening)
            val etCounted = view.findViewById<EditText>(R.id.etCounted)
            val etNote = view.findViewById<EditText>(R.id.etCloseNote)
            val rows = view.findViewById<android.widget.LinearLayout>(R.id.breakdownRows)
            val tvDiff = view.findViewById<TextView>(R.id.tvDifference)
            val resultPanel = view.findViewById<View>(R.id.dayCloseResult)
            val resultMark = view.findViewById<com.innovation313.roshankhata.ui.ResultMark>(R.id.dayCloseMark)
            // Which answer is on show (match / short / over): the panel moves only when that changes, not at
            // every figure typed (3 Nov 2026, the owner's mockup "Close the day — the difference").
            var shownKind: com.innovation313.roshankhata.ui.ResultMark.Glyph? = null
            // Which stretch the figures cover, and what the first box is (2 Nov 2026, the owner's first close:
            // a blank "at the last count" box over figures that ran from the book's first entry).
            view.findViewById<TextView>(R.id.tvSince).text = if (last == null) getString(R.string.dayclose_since_first)
            else getString(R.string.dayclose_since_last, Format.dateTime(last.closedAt))
            if (last == null) view.findViewById<TextView>(R.id.tvOpeningLabel).setText(R.string.dayclose_opening_first)
            com.innovation313.roshankhata.ui.TextFit.relax(view)
            last?.let { etOpening.setText(Format.plain(it.counted)) }
            // A form rebuilt after a rotation or a killed process gets back what was typed in it (M90).
            closeRestore?.let { com.innovation313.roshankhata.ui.FormDraft.fill(listOf(etOpening, etCounted, etNote), it) }
            closeRestore = null
            closeDraft.open(0) { listOf(etOpening, etCounted, etNote) }

            // Both figures on the app's calculator pad (21 Oct): it types
            // into whichever of the two has the focus, so a drawer can be
            // counted note by note — "50×12+100×7" — and the note box keeps
            // the phone's keyboard. Read back with CalcPad.figure, which keeps
            // a counted zero (an empty drawer is a real count).
            com.innovation313.roshankhata.ui.CalcPad.bindAll(
                root = view,
                pad = view.findViewById(R.id.closeKeypad),
                fields = listOf(etOpening, etCounted),
                results = mapOf(
                    etOpening to view.findViewById<TextView>(R.id.tvOpeningResult),
                    etCounted to view.findViewById<TextView>(R.id.tvCountedResult)
                )
            )

            fun refresh() {
                val opening = com.innovation313.roshankhata.ui.CalcPad.figure(etOpening.text) ?: 0.0
                val expected = DayCloseMath.expected(opening, cashIn, cashOut)
                // The total keeps its sign (4 Nov 2026, the owner's photo: 9,000 − 5,000 − 20,000 read "Rs 16,000"; Format.money
                // drops the minus by design). Below zero it is a minus in red: the books say more went out than came in.
                breakdownRows(rows, listOf(Format.money(cbIn), Format.money(kIn), Format.money(cbOut), Format.money(kOut), Format.signedIfNegative(expected), Format.money(figures.otherOut)),
                    totalInk = if (Money.isNegative(expected)) R.color.red_gave_text else R.color.ink)
                val counted = com.innovation313.roshankhata.ui.CalcPad.figure(etCounted.text)
                if (counted == null) {
                    resultPanel.visibility = View.GONE
                    shownKind = null
                    etNote.setHint(R.string.dayclose_note_hint)
                } else {
                    val d = DayCloseMath.difference(counted, expected)
                    // A difference needs its reason: the note box says so before Save is pressed (R155).
                    etNote.hint = getString(if (kotlin.math.abs(d) >= 0.005) R.string.z_variance_reason else R.string.dayclose_note_hint)
                    // Matches in green, short in red, more than the books in gold: the line the owner acts on,
                    // each on its own wash with its mark (a check, a minus, a plus).
                    val glyph = when {
                        Money.isPositive(d) -> com.innovation313.roshankhata.ui.ResultMark.Glyph.PLUS
                        Money.isPositive(-d) -> com.innovation313.roshankhata.ui.ResultMark.Glyph.MINUS
                        else -> com.innovation313.roshankhata.ui.ResultMark.Glyph.CHECK
                    }
                    val (words, colour, wash) = when (glyph) {
                        com.innovation313.roshankhata.ui.ResultMark.Glyph.PLUS ->
                            Triple(getString(R.string.dayclose_over, Format.moneyLtr(d)), R.color.gold_ink, R.color.wash_gold)
                        com.innovation313.roshankhata.ui.ResultMark.Glyph.MINUS ->
                            Triple(getString(R.string.dayclose_short, Format.moneyLtr(-d)), R.color.red_gave_text, R.color.wash_gave)
                        else -> Triple(getString(R.string.dayclose_match), R.color.green_got_text, R.color.wash_got)
                    }
                    val ink = androidx.core.content.ContextCompat.getColor(this@CashbookActivity, colour)
                    val washColour = androidx.core.content.ContextCompat.getColor(this@CashbookActivity, wash)
                    tvDiff.text = words
                    tvDiff.setTextColor(ink)
                    resultMark.show(glyph, ink, androidx.core.content.ContextCompat.getColor(this@CashbookActivity, R.color.surface))
                    if (glyph != shownKind) {
                        val first = resultPanel.visibility != View.VISIBLE
                        val from = resultPanel.backgroundTintList?.defaultColor ?: washColour
                        resultPanel.visibility = View.VISIBLE
                        if (first) {
                            // Through tintTo, which first stops a change of wash still running (the count cleared and
                            // typed again within it would have left the old wash under the new words).
                            com.innovation313.roshankhata.ui.MotionKit.tintTo(resultPanel, washColour, washColour)
                            com.innovation313.roshankhata.ui.MotionKit.fadeIn(resultPanel, duration = 160)
                        } else {
                            com.innovation313.roshankhata.ui.MotionKit.tintTo(resultPanel, from, washColour)
                            com.innovation313.roshankhata.ui.MotionKit.fadeIn(tvDiff, duration = 160)
                        }
                        shownKind = glyph
                    }
                }
            }
            val watcher = object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) = refresh()
            }
            etOpening.addTextChangedListener(watcher)
            etCounted.addTextChangedListener(watcher)
            refresh()
            // The opening as the form put it (the last count's figure, or blank), to tell it from one the owner changed.
            val openedOpening = etOpening.text.toString()

            MaterialAlertDialogBuilder(this@CashbookActivity)
                .setTitle(R.string.dayclose_title)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                // Save is wired after show() (23 Oct 2026, review R155): a builder's button closes the
                // dialog whatever its listener returns, so a refused save threw away both counted figures.
                .setPositiveButton(R.string.save, null)
                .show().also { dialog ->
                    com.innovation313.roshankhata.ui.CalcPad.dock(dialog, view, view.findViewById(R.id.closeKeypad))
                    com.innovation313.roshankhata.ui.KeyboardFollow.dialog(dialog)
                    // The pad types into the box with the focus: the count, once the opening is the last count's
                    // figure; the opening first only when there was no count to start from.
                    if (last != null) etCounted.requestFocus() else etOpening.requestFocus()
                    dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)?.setOnClickListener {
                        val counted = com.innovation313.roshankhata.ui.CalcPad.figure(etCounted.text)
                        if (counted == null) {
                            Toast.makeText(this@CashbookActivity, R.string.dayclose_need_count, Toast.LENGTH_SHORT).show()
                            etCounted.requestFocus()
                            return@setOnClickListener
                        }
                        // A drawer that does not match the books needs its reason written down (POS Phase 4):
                        // a figure with no story is one nobody can answer for next week.
                        val openingNow = com.innovation313.roshankhata.ui.CalcPad.figure(etOpening.text) ?: 0.0
                        val expectedNow = DayCloseMath.expected(openingNow, cashIn, cashOut)
                        if (kotlin.math.abs(DayCloseMath.difference(counted, expectedNow)) >= 0.005 && etNote.text.toString().isBlank()) {
                            Toast.makeText(this@CashbookActivity, R.string.z_variance_reason, Toast.LENGTH_LONG).show()
                            // The box it wants sits below the whole pad: brought on screen and given the focus.
                            etNote.requestFocus()
                            (view as? android.widget.ScrollView)?.let { sv -> sv.post { sv.smoothScrollTo(0, etNote.top) } }
                            return@setOnClickListener
                        }
                        val close = DayClose(
                            // The working day: a drawer counted after midnight is the evening before's (23 Oct 2026).
                            day = DayCloseMath.businessDayOf(now),
                            opening = com.innovation313.roshankhata.ui.CalcPad.figure(etOpening.text) ?: 0.0,
                            cashIn = LineMath.round(cashIn),
                            cashOut = LineMath.round(cashOut),
                            counted = counted,
                            note = etNote.text.toString().trim().ifEmpty { null },
                            closedAt = now
                        )
                        AppScope.launch { dao.saveDayClose(close) }
                        dialog.dismiss()
                    }
                    // Back, a tap beside it or Cancel threw a counted drawer away in silence (5 Nov 2026, the supplier
                    // bill's flaw): a count, a note or a changed opening asks first; an untouched form closes at once.
                    // Save above is not touched: a refused save still keeps the form open.
                    com.innovation313.roshankhata.ui.FormGuard.guard(
                        activity = this@CashbookActivity,
                        dialog = dialog,
                        typed = {
                            etOpening.text.toString() != openedOpening || etCounted.text.isNotBlank() || etNote.text.isNotBlank()
                        }
                    )
                    dialog.setOnDismissListener { closeDraft.closed(this@CashbookActivity) }
                }
        }
    }

    /**
     * The books' sum as rows (2 Nov 2026, the owner's screenshot: five lines of words and figures run together).
     * Each line of `dayclose_breakdown` in the owner's language is one row — its words at the start, its figure
     * (the line's own %n$s) at the end, left to right — and the last line, the total, under a rule in bold. The
     * placeholders are found in the words, so a language that puts the figure first still reads right.
     */
    private fun breakdownRows(container: android.widget.LinearLayout, figures: List<String>, totalInk: Int = R.color.ink) {
        container.removeAllViews()
        val lines = getString(R.string.dayclose_breakdown).split("\n").filter { it.isNotBlank() }
        val ink = ContextCompat.getColor(this, R.color.ink)
        val dp = resources.displayMetrics.density
        lines.forEachIndexed { i, line ->
            val total = i == lines.lastIndex
            // "%1$s" in the words: "\$" keeps Kotlin from reading "$s" as a template.
            val mark = Regex("%(\\d)\\\$s").find(line)
            val figure = mark?.groupValues?.get(1)?.toIntOrNull()?.let { figures.getOrNull(it - 1) }.orEmpty()
            val words = (if (mark != null) line.removeRange(mark.range) else line).trim().replace(Regex("\\s{2,}"), " ")
            if (total) container.addView(View(this).apply {
                setBackgroundColor(ContextCompat.getColor(this@CashbookActivity, R.color.page_line))
            }, android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt().coerceAtLeast(1)).apply {
                topMargin = (6 * dp).toInt(); bottomMargin = (6 * dp).toInt()
            })
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, (3 * dp).toInt(), 0, (3 * dp).toInt())
            }
            row.addView(TextView(this).apply {
                text = words
                textSize = if (total) 15f else 14f
                setTextColor(ink)
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                if (total) setTypeface(typeface, android.graphics.Typeface.BOLD)
            }, android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(this).apply {
                text = figure
                textSize = if (total) 15f else 14f
                setTextColor(if (total) ContextCompat.getColor(this@CashbookActivity, totalInk) else ink)
                textDirection = View.TEXT_DIRECTION_LTR
                if (total) setTypeface(typeface, android.graphics.Typeface.BOLD)
            }, android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = (12 * dp).toInt()
            })
            container.addView(row)
        }
        com.innovation313.roshankhata.ui.TextFit.relax(container)
    }

    /**
     * The day report: since 3 Nov 2026 the day book (DayBookActivity), the same figures with the day's every entry
     * under them and any day stepped back to; it was a dialog of today's totals.
     */
    private fun showDayReport() {
        // The day's profit, expected cash and the till's difference are the shop's figures (23 Oct 2026, the
        // review): a cashier needs a manager's PIN, as for every other report.
        if (!com.innovation313.roshankhata.ui.AccessUi.may(this, dao, com.innovation313.roshankhata.data.Access.Permission.REPORTS) { showDayReport() }) return
        startActivity(android.content.Intent(this, DayBookActivity::class.java))
    }

    private val listLimit = kotlinx.coroutines.flow.MutableStateFlow(com.innovation313.roshankhata.data.ListWindow.PAGE)
    private var listShown = 0

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observe() {
        lifecycleScope.launch {
            listLimit.flatMapLatest { dao.observeCashPage(it) }.collectLatest { list ->
                listShown = list.size
                adapter.submit(list)
                tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            combine(
                dao.observeCashIncome(),
                dao.observeCashExpense()
            ) { income, expense -> income to expense }
                .collectLatest { (income, expense) ->
                    tvIn.text = Format.money(income)
                    tvOut.text = Format.money(expense)

                    // Net carried no sign at all: Format.money() takes abs()
                    // on purpose, because everywhere else the direction is
                    // already said by a label or a + / − beside it. Here it
                    // was the whole meaning, so Rs 50,000 short read as
                    // Rs 50,000 in hand. Sign and colour both now say which
                    // way the month went.
                    val net = income - expense
                    tvNet.text = Format.signedTotal(net)
                    tvNet.setTextColor(
                        ContextCompat.getColor(this@CashbookActivity, Format.signedTotalColourOnDark(net))
                    )
                }
        }
    }

    /** In or out — asked first, because it changes what the entry means. */
    private fun showDirectionChoice() {
        // In on the "got" green, out on the "gave" red, with the same arrows
        // as the khata (10 Oct).
        val options = listOf(
            ChoiceSheet.Option(getString(R.string.cash_income), icon = R.drawable.ic_arrow_got,
                iconTint = R.color.bal_i_owe, tile = R.color.summary_give_bg),
            ChoiceSheet.Option(getString(R.string.cash_expense), icon = R.drawable.ic_arrow_gave,
                iconTint = R.color.bal_owed_to_me, tile = R.color.summary_get_bg)
        )
        ChoiceSheet.show(this, getString(R.string.add_cash_entry), options) { which ->
            showAddDialog(isIncome = which == 0)
        }
    }

    private fun showAddDialog(isIncome: Boolean, restore: List<String>? = null) {
        val view = layoutInflater.inflate(R.layout.dialog_add_cash, null)
        com.innovation313.roshankhata.ui.TextFit.relax(view)
        val etAmount: EditText = view.findViewById(R.id.etCashAmount)
        val etCategory: AutoCompleteTextView = view.findViewById(R.id.etCashCategory)
        val etNote: EditText = view.findViewById(R.id.etCashNote)

        // How the money moved and its reference (29 Oct 2026): the add-entry form's chips, one per
        // PaymentMethod.ALL, Cash checked first; the reference box only beside a way that has one.
        val cgMethod: com.google.android.material.chip.ChipGroup = view.findViewById(R.id.cgCashMethod)
        val etRef: EditText = view.findViewById(R.id.etCashRef)
        val chipKeys = HashMap<Int, String>()
        PaymentMethod.ALL.forEach { key ->
            val chip = layoutInflater.inflate(R.layout.item_rate_list_chip, cgMethod, false) as com.google.android.material.chip.Chip
            chip.id = View.generateViewId()
            chip.text = PaymentMethod.labelOf(this, key)
            chipKeys[chip.id] = key
            cgMethod.addView(chip)
            if (key == PaymentMethod.CASH) cgMethod.check(chip.id)
        }
        fun chosenMethod(): String? = chipKeys[cgMethod.checkedChipId]
        restore?.let { t ->
            listOf(etAmount, etCategory, etNote, etRef).forEachIndexed { i, box -> t.getOrNull(i)?.let { box.setText(it) } }
        }
        cashFormIncome = isIncome
        cashFormTexts = { listOf(etAmount, etCategory, etNote, etRef).map { it.text.toString() } }
        fun showRefBox() {
            etRef.visibility = if (PaymentRef.applies(chosenMethod())) View.VISIBLE else View.GONE
            view.findViewById<View>(R.id.tvCashRefLabel).visibility = etRef.visibility
        }
        // After the first check (lesson 4): the listener only follows the chip.
        cgMethod.setOnCheckedStateChangeListener { _, _ -> showRefBox() }
        showRefBox()

        // The app's own calculator pad for the amount (21 Oct), as on the
        // add-entry form; the category and note keep the phone's keyboard.
        com.innovation313.roshankhata.ui.CalcPad.bindAll(
            root = view,
            pad = view.findViewById(R.id.cashKeypad),
            fields = listOf(etAmount),
            results = mapOf(etAmount to view.findViewById<TextView>(R.id.tvCashAmountResult))
        )

        // The owner's own categories first, then our starting suggestions.
        // Their words for their business beat ours.
        lifecycleScope.launch {
            val used = dao.cashCategories()
            val defaults = resources.getStringArray(R.array.cash_categories).toList()
            val merged = (used + defaults).distinct()

            etCategory.setAdapter(
                ArrayAdapter(
                    this@CashbookActivity,
                    R.layout.item_dropdown_row,
                    merged
                )
            )
        }

        // When the cash actually moved. Defaults to now, which is right most of
        // the time, but the day's takings are often written up at closing.
        var chosenTime = System.currentTimeMillis()
        DateTimeField.attach(
            activity = this,
            button = view.findViewById(R.id.btnCashDate),
            initial = chosenTime
        ) { chosenTime = it }

        // The receipt's picture (schema 43): one taken while the screen was rebuilt waits here for this form.
        val btnPhoto: com.google.android.material.button.MaterialButton = view.findViewById(R.id.btnCashPhoto)
        photoButton = btnPhoto
        btnPhoto.setText(if (pendingPhoto != null) R.string.photo_attached else R.string.attach_photo)
        btnPhoto.setOnClickListener {
            if (pendingPhoto == null) { photoForRow = null; photoFlow.chooseSource(R.string.cash_photo); return@setOnClickListener }
            ChoiceSheet.show(this, getString(R.string.cash_photo), listOf(
                ChoiceSheet.photo(getString(R.string.view_photo)),
                ChoiceSheet.camera(getString(R.string.change_photo)),
                ChoiceSheet.delete(getString(R.string.remove_photo))
            )) { which ->
                when (which) {
                    0 -> pendingPhoto?.let { com.innovation313.roshankhata.ui.KeptPhoto.show(this, it, getString(R.string.cash_photo)) }
                    1 -> { photoForRow = null; photoFlow.chooseSource(R.string.cash_photo) }
                    else -> { com.innovation313.roshankhata.data.BillPhoto.delete(pendingPhoto); pendingPhoto = null; btnPhoto.setText(R.string.attach_photo) }
                }
            }
        }
        // Written with the row, or gone with the form: a picture no row points at is nothing but bytes.
        var written = false
        // The time the form opened with, to tell a day the owner picked from the one it started on.
        val openedTime = chosenTime

        MaterialAlertDialogBuilder(this)
            .setTitle(if (isIncome) R.string.cash_income else R.string.cash_expense)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setOnDismissListener {
                if (!isChangingConfigurations) { cashFormIncome = null; cashFormTexts = null }
                photoButton = null
                // A dismiss for the camera's sake never happens (the form stays behind it); a closed form's picture goes.
                if (!written && !isChangingConfigurations) { com.innovation313.roshankhata.data.BillPhoto.delete(pendingPhoto); pendingPhoto = null }
            }
            // Save is wired after show() (5 Nov 2026), as Close the day's: a builder's button closes the dialog
            // whatever its listener decides, so an amount or a category the form refused threw the rest away.
            .setPositiveButton(R.string.save, null)
            .show().also { form ->
                com.innovation313.roshankhata.ui.CalcPad.dock(form, view, view.findViewById(R.id.cashKeypad))
                com.innovation313.roshankhata.ui.KeyboardFollow.dialog(form)
                com.innovation313.roshankhata.ui.FormGuard.onSave(form) {
                    val amount = Calc.evalAmount(etAmount.text.toString())
                    if (amount == null) {
                        Toast.makeText(this@CashbookActivity, R.string.enter_valid_amount, Toast.LENGTH_SHORT).show()
                        etAmount.requestFocus()
                        return@onSave false
                    }

                    val category = etCategory.text.toString().trim()
                    if (category.isEmpty()) {
                        Toast.makeText(this@CashbookActivity, R.string.enter_category, Toast.LENGTH_SHORT).show()
                        etCategory.requestFocus()
                        return@onSave false
                    }

                    // AppScope, not lifecycleScope — see AppScope's own comment.
                    // This dialog closes the instant Save is tapped and shows no
                    // progress, so a quick Back press right after could cancel the
                    // write before it landed, taking the typed amount, category,
                    // note and date with it.
                    // The picture goes with the row; the form lets go of it before the dialog's dismiss runs.
                    val photo = pendingPhoto
                    written = true
                    pendingPhoto = null
                    // What the form says at Save, read here on the main thread rather than inside the write.
                    val entry = CashEntry(
                        amount = amount,
                        isIncome = isIncome,
                        category = category,
                        note = etNote.text.toString().trim().ifEmpty { null },
                        timestamp = chosenTime,
                        paymentMethod = chosenMethod(),
                        paymentRef = PaymentRef.clean(chosenMethod(), etRef.text.toString()),
                        photoPath = photo
                    )
                    AppScope.launch { dao.insertCashEntryAudited(entry) }
                    true
                }
                // Back, a tap beside it or Cancel threw a typed amount (and the receipt's picture) away in silence
                // (5 Nov 2026, the supplier bill's flaw): the form opens blank, on Cash and now, and anything changed
                // from that — a picture waiting for Save included — asks first; an untouched form closes at once.
                com.innovation313.roshankhata.ui.FormGuard.guard(
                    activity = this@CashbookActivity,
                    dialog = form,
                    typed = {
                        etAmount.text.isNotBlank() || etCategory.text.isNotBlank() || etNote.text.isNotBlank() ||
                            etRef.text.isNotBlank() || chosenMethod() != PaymentMethod.CASH ||
                            chosenTime != openedTime || pendingPhoto != null
                    },
                    onDiscard = {
                        // The picture taken for this form goes with it, as the bill form's kept photo does.
                        com.innovation313.roshankhata.data.BillPhoto.delete(pendingPhoto)
                        pendingPhoto = null
                    }
                )
            }
    }

    /**
     * A picture that came back from the scanner, the camera or the gallery (schema 43): copied into the bills folder,
     * then the form's, or the row's it was taken for. [temp] is the camera's own working file, deleted once read.
     */
    private fun keepPhoto(uri: android.net.Uri, temp: java.io.File?) {
        val row = photoForRow
        photoForRow = null
        lifecycleScope.launch {
            val path = withContext(Dispatchers.IO) { com.innovation313.roshankhata.data.BillPhoto.save(this@CashbookActivity, uri) }
            temp?.delete()
            if (path == null) {
                Toast.makeText(this@CashbookActivity, R.string.bill_photo_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            if (row == null) {
                // Replacing the form's earlier pick: that one is nothing but bytes now.
                com.innovation313.roshankhata.data.BillPhoto.delete(pendingPhoto)
                pendingPhoto = path
                photoButton?.setText(R.string.photo_attached)
                return@launch
            }
            // A row already written: its new picture at once, the old one gone once nothing points at it.
            val app = applicationContext
            val done = AppScope.async {
                runCatching { com.innovation313.roshankhata.data.BillPhoto.purgingOrphans(app, dao) { dao.setCashPhotoPath(row, path) } }.isSuccess
            }.await()
            if (!done) com.innovation313.roshankhata.data.BillPhoto.delete(path)
            if (!isFinishing && !isDestroyed) Toast.makeText(this@CashbookActivity, if (done) R.string.photo_saved else R.string.photo_save_failed, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * A tap on a row (schema 43): its receipt's picture — seen, taken, changed or taken away — and the delete a long
     * press has always offered. A helper's read-only phone sees the picture and nothing that would change the row.
     */
    private fun showCashActions(entry: CashEntry) {
        val photo = entry.photoPath?.takeIf { it.isNotBlank() }
        val readOnly = com.innovation313.roshankhata.data.ViewerMode.isOn(this)
        val actions = ArrayList<Pair<ChoiceSheet.Option, () -> Unit>>()
        if (photo != null) actions += ChoiceSheet.photo(getString(R.string.view_photo)) to {
            com.innovation313.roshankhata.ui.KeptPhoto.show(this, photo, getString(R.string.cash_photo))
        }
        if (!readOnly) {
            actions += ChoiceSheet.camera(getString(if (photo == null) R.string.attach_photo else R.string.change_photo)) to {
                com.innovation313.roshankhata.ui.AccessUi.edit(this, dao) { photoForRow = entry.id; photoFlow.chooseSource(R.string.cash_photo) }
            }
            if (photo != null) actions += ChoiceSheet.stop(getString(R.string.remove_photo)) to {
                com.innovation313.roshankhata.ui.AccessUi.edit(this, dao) { removeRowPhoto(entry.id) }
            }
            actions += ChoiceSheet.delete(getString(R.string.delete)) to { confirmDelete(entry) }
        }
        if (actions.isEmpty()) return
        val title = listOf(cashCategoryWord(entry.category), Format.money(entry.amount)).joinToString(" · ")
        ChoiceSheet.show(this, title, actions.map { it.first }) { which -> actions.getOrNull(which)?.second?.invoke() }
    }

    /** The row's picture taken away; the file goes once no row of any shop on this phone points at it. */
    private fun removeRowPhoto(id: Long) {
        val app = applicationContext
        lifecycleScope.launch {
            val done = AppScope.async {
                runCatching { com.innovation313.roshankhata.data.BillPhoto.purgingOrphans(app, dao) { dao.setCashPhotoPath(id, null) } }.isSuccess
            }.await()
            if (done && !isFinishing && !isDestroyed) Toast.makeText(this@CashbookActivity, R.string.photo_removed, Toast.LENGTH_SHORT).show()
        }
    }

    /** The counter's own categories in the owner's language, the owner's as he typed them (the rows' rule). */
    private fun cashCategoryWord(category: String): String = when (category) {
        com.innovation313.roshankhata.data.CashCategory.SALE -> getString(R.string.cash_category_sale)
        com.innovation313.roshankhata.data.CashCategory.RETURN -> getString(R.string.cash_category_return)
        else -> category
    }

    private fun confirmDelete(entry: CashEntry) {
        if (!com.innovation313.roshankhata.ui.AccessUi.may(this, dao) { confirmDelete(entry) }) return
        lifecycleScope.launch {
            // A line a counter ticket or a return wrote is changed from there (22 Oct 2026, the review: deleting
            // it here left the drawer expecting less while the ticket still read as paid).
            val owner = withContext(Dispatchers.IO) { runCatching { dao.cashOwner(entry.id) }.getOrNull() }
            if (isFinishing || isDestroyed) return@launch
            if (owner != null) {
                MaterialAlertDialogBuilder(this@CashbookActivity)
                    .setTitle(R.string.entry_linked_title)
                    .setMessage(if (owner == "return") R.string.entry_linked_return else R.string.entry_linked_sale)
                    .setPositiveButton(R.string.ok, null)
                    .show()
                return@launch
            }
            MaterialAlertDialogBuilder(this@CashbookActivity)
                .setTitle(R.string.delete_cash_entry)
                .setMessage(R.string.delete_cash_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete) { _, _ ->
                    lifecycleScope.launch {
                        // Checked again inside the delete's own transaction, with its audit row.
                        val done = AppScope.async { runCatching { dao.softDeleteOwnCashEntry(entry.id, auditAmount(entry.amount)) }.getOrDefault(false) }.await()
                        Toast.makeText(
                            this@CashbookActivity,
                            if (done) R.string.moved_to_bin else R.string.entry_linked_sale,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .show()
        }
    }

    companion object {
        /** Open Close the day at once (the closing automation's notification, 31 Oct 2026). */
        const val EXTRA_CLOSE_DAY = "close_day"
        private const val STATE_PHOTO_CAMERA = "photo_camera_path"
        private const val STATE_PHOTO_PENDING = "photo_pending"
        private const val STATE_PHOTO_ROW = "photo_row"
        private const val STATE_FORM_INCOME = "form_income"
        private const val STATE_FORM_TEXTS = "form_texts"
    }
}
