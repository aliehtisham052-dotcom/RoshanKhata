package com.innovation313.roshankhata

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.innovation313.roshankhata.data.BusinessProfile
import com.innovation313.roshankhata.data.Trade
import com.innovation313.roshankhata.ui.LogoDesigns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Make a logo (10 Nov 2026, the owner: most shops have no logo, and a shopkeeper cannot draw one), in two steps as
 * design apps do (the owner's photo the same day: a preview, the grid and the controls on one screen cut each other
 * off, and a colour changed every design at once).
 *
 * Step one, the designs of [LogoDesigns]: the whole screen is the grid, each with the shop's name in the trade's own
 * colour. A tap opens step two: that design alone, large, with the name on it, what its big words say and its colour
 * under it - every change here is this logo's only - "Other designs" back to the grid, and Use this logo, which draws
 * it at print size and keeps it as the shop's logo exactly as a picked picture is kept, and remembers how it was made
 * so Change in the profile opens it again here. Returns RESULT_OK when saved.
 */
class LogoMakerActivity : BaseActivity() {

    private lateinit var trade: Trade
    private lateinit var tagline: String
    private var urduName: String? = null
    private var name = ""
    private var show = LogoDesigns.Show.NAME
    private var palettes: List<LogoDesigns.Palette> = emptyList()
    private var palette = 0
    private var styles: List<LogoDesigns.Style> = emptyList()
    private var chosen = 0
    private var editing = false
    private val previews = mutableMapOf<LogoDesigns.Style, Bitmap>()
    private var gridJob: Job? = null
    private var bigJob: Job? = null
    private var saving = false
    private lateinit var adapter: Designs
    private lateinit var rv: RecyclerView

    /** Back on step two goes to the designs, not out of the screen. */
    private val backToDesigns = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = showStep(edit = false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logo_maker)
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)
        onBackPressedDispatcher.addCallback(this, backToDesigns)

        // The logo made here before opens on step two to be edited, not as a new one (the owner: Change started over),
        // and exactly as it was made: in its own trade's picture and words even if the shop type changed since (10 Nov
        // 2026, the owner's photos: the profile showed the shirt logo kept, the editor drew a shoe).
        val made = if (savedInstanceState == null) Made.parse(BusinessProfile.logoDesign(this)) else null
        trade = savedInstanceState?.getString(KEY_TRADE)?.let { Trade.of(it) } ?: made?.trade ?: Trade.current(this)
        tagline = getString(trade.exampleRes)
        urduName = BusinessProfile.businessNameUrdu(this)?.takeIf { it.isNotBlank() }
        palettes = LogoDesigns.palettes(this, trade)
        styles = LogoDesigns.styles(urduName)
        name = savedInstanceState?.getString(KEY_NAME) ?: made?.name ?: BusinessProfile.businessName(this).orEmpty()
        palette = (savedInstanceState?.getInt(KEY_PALETTE) ?: made?.palette ?: 0).coerceIn(0, palettes.size - 1)
        chosen = (savedInstanceState?.getInt(KEY_CHOSEN) ?: made?.style?.let { styles.indexOf(it) }?.takeIf { it >= 0 } ?: 0)
            .coerceIn(0, styles.size - 1)
        show = savedInstanceState?.getString(KEY_SHOW)?.let { runCatching { LogoDesigns.Show.valueOf(it) }.getOrNull() }
            ?: made?.show?.takeIf { it != LogoDesigns.Show.URDU || urduName != null }
            ?: LogoDesigns.Show.NAME
        val startEditing = savedInstanceState?.getBoolean(KEY_EDITING) ?: (made != null)

        findViewById<TextView>(R.id.tvLogoTrade).text = getString(trade.labelRes)
        findViewById<View>(R.id.btnLogoBack).setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        findViewById<View>(R.id.btnLogoOthers).setOnClickListener { showStep(edit = false) }

        val etName: EditText = findViewById(R.id.etLogoName)
        etName.setText(name)
        etName.hint = getString(R.string.business_name_hint)
        etName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val typed = s?.toString().orEmpty()
                if (typed == name) return
                name = typed
                redrawBig(delayMs = 200)
                redrawGrid(delayMs = 400)
            }
        })

        buildShowChips()
        buildColours()

        rv = findViewById(R.id.rvLogos)
        rv.layoutManager = GridLayoutManager(this, 2)
        adapter = Designs()
        rv.adapter = adapter
        if (chosen > 0) rv.scrollToPosition(chosen)

        findViewById<View>(R.id.btnLogoUse).setOnClickListener { save() }
        com.innovation313.roshankhata.ui.TextFit.relax(findViewById(R.id.logoPanel))
        com.innovation313.roshankhata.ui.TextFit.relax(findViewById(R.id.logoUseBar))
        redrawGrid(delayMs = 0)
        showStep(startEditing)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_NAME, name)
        outState.putInt(KEY_PALETTE, palette)
        outState.putInt(KEY_CHOSEN, chosen)
        outState.putString(KEY_SHOW, show.name)
        outState.putBoolean(KEY_EDITING, editing)
        outState.putString(KEY_TRADE, trade.name)
    }

    /** Step one (the designs) or step two (the chosen one, large, and what can change on it). */
    private fun showStep(edit: Boolean) {
        editing = edit
        backToDesigns.isEnabled = edit
        rv.visibility = if (edit) View.GONE else View.VISIBLE
        findViewById<View>(R.id.logoEdit).visibility = if (edit) View.VISIBLE else View.GONE
        findViewById<View>(R.id.logoUseBar).visibility = if (edit) View.VISIBLE else View.GONE
        if (edit) {
            findViewById<androidx.core.widget.NestedScrollView>(R.id.logoEdit).scrollTo(0, 0)
            redrawBig(delayMs = 0)
        } else {
            currentFocus?.let { f ->
                (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                    .hideSoftInputFromWindow(f.windowToken, 0)
                f.clearFocus()
            }
            adapter.notifyDataSetChanged()
            rv.scrollToPosition(chosen)
        }
    }

    /** Name, Letters and - when an Urdu name is kept - Urdu name. */
    private fun buildShowChips() {
        val group: ChipGroup = findViewById(R.id.cgLogoShow)
        val options = buildList {
            add(LogoDesigns.Show.NAME to R.string.logo_show_name)
            add(LogoDesigns.Show.LETTERS to R.string.logo_show_letters)
            if (urduName != null) add(LogoDesigns.Show.URDU to R.string.logo_show_urdu)
        }
        options.forEach { (which, label) ->
            val chip = layoutInflater.inflate(R.layout.item_rate_list_chip, group, false) as Chip
            chip.id = View.generateViewId()
            chip.text = getString(label)
            chip.tag = which
            chip.isChecked = which == show
            group.addView(chip)
        }
        group.setOnCheckedStateChangeListener { g, ids ->
            val picked = ids.firstOrNull()?.let { g.findViewById<Chip>(it)?.tag as? LogoDesigns.Show } ?: return@setOnCheckedStateChangeListener
            show = picked
            redrawBig(delayMs = 0)
        }
    }

    /** A round swatch per colour pair; the chosen one ringed. Changes the chosen logo only. */
    private fun buildColours() {
        val row: LinearLayout = findViewById(R.id.llLogoColours)
        row.removeAllViews()
        val d = resources.displayMetrics.density
        palettes.forEachIndexed { i, p ->
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams((44 * d).toInt(), (44 * d).toInt()).apply { marginEnd = (10 * d).toInt() }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(p.fill)
                    if (i == palette) setStroke((4 * d).toInt(), p.accent) else setStroke((2 * d).toInt(), Color.WHITE)
                }
                elevation = if (i == palette) 4 * d else d
                contentDescription = getString(R.string.logo_colour) + " " + (i + 1)
                setOnClickListener {
                    palette = i
                    buildColours()
                    redrawBig(delayMs = 0)
                }
            }
            row.addView(dot)
        }
    }

    private fun shownName() = name.trim().ifEmpty { getString(R.string.app_name) }

    /** The chosen logo as it will be kept: its own colour and words. */
    private fun spec(style: LogoDesigns.Style) = LogoDesigns.Spec(
        style = style,
        palette = palettes[palette],
        show = show,
        name = shownName(),
        urduName = urduName,
        tagline = tagline,
        trade = trade
    )

    /** A design on the grid: the shop's name in the trade's own colour, so the designs are compared as designs. */
    private fun gridSpec(style: LogoDesigns.Style) = LogoDesigns.Spec(
        style = style,
        palette = palettes[0],
        show = if (style == LogoDesigns.Style.URDU) LogoDesigns.Show.URDU else LogoDesigns.Show.NAME,
        name = shownName(),
        urduName = urduName,
        tagline = tagline,
        trade = trade
    )

    /** Every design on the grid drawn again off the main thread, after a short pause while the name is typed. */
    private fun redrawGrid(delayMs: Long) {
        gridJob?.cancel()
        gridJob = lifecycleScope.launch {
            if (delayMs > 0) delay(delayMs)
            val size = (resources.displayMetrics.widthPixels / 2).coerceIn(240, 520)
            val drawn = withContext(Dispatchers.Default) {
                styles.associateWith { LogoDesigns.draw(this@LogoMakerActivity, gridSpec(it), size) }
            }
            previews.clear()
            previews.putAll(drawn)
            adapter.notifyDataSetChanged()
        }
    }

    /** The chosen logo, large, on step two. */
    private fun redrawBig(delayMs: Long) {
        bigJob?.cancel()
        bigJob = lifecycleScope.launch {
            if (delayMs > 0) delay(delayMs)
            val style = styles[chosen]
            val size = resources.displayMetrics.widthPixels.coerceIn(480, 900)
            val bmp = withContext(Dispatchers.Default) { LogoDesigns.draw(this@LogoMakerActivity, spec(style), size) }
            findViewById<ImageView>(R.id.ivLogoBig).setImageBitmap(bmp)
        }
    }

    /** The chosen design at print size, kept as the shop's logo the way a picked picture is. */
    private fun save() {
        if (saving) return
        saving = true
        val style = styles[chosen]
        val made = Made(style, palette, show, name.trim(), trade)
        val drawSpec = spec(style)
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val bmp = LogoDesigns.draw(this@LogoMakerActivity, drawSpec, 1024)
                    val file = File(File(cacheDir, "logo_maker").apply { mkdirs() }, "logo.png")
                    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bmp.recycle()
                    BusinessProfile.saveLogo(this@LogoMakerActivity, Uri.fromFile(file)).also { saved ->
                        file.delete()
                        if (saved) BusinessProfile.setLogoDesign(this@LogoMakerActivity, made.text())
                    }
                }.getOrDefault(false)
            }
            saving = false
            Toast.makeText(this@LogoMakerActivity, if (ok) R.string.bp_logo_saved else R.string.bp_logo_save_failed, Toast.LENGTH_SHORT).show()
            if (ok) {
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    private inner class Designs : RecyclerView.Adapter<Designs.VH>() {
        inner class VH(val frame: FrameLayout, val image: ImageView, val tick: ImageView) : RecyclerView.ViewHolder(frame)

        override fun getItemCount() = styles.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val d = resources.displayMetrics.density
            // Square cards: a design is never cut by its card.
            val side = ((parent.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels) - (20 * d).toInt()) / 2 - (12 * d).toInt()
            val frame = FrameLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, side.coerceAtLeast((140 * d).toInt()))
                    .apply { setMargins((6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt()) }
                foreground = androidx.core.content.ContextCompat.getDrawable(context, android.R.drawable.list_selector_background)
            }
            val image = ImageView(parent.context).apply {
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    .apply { setMargins((14 * d).toInt(), (14 * d).toInt(), (14 * d).toInt(), (14 * d).toInt()) }
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            val tick = ImageView(parent.context).apply {
                layoutParams = FrameLayout.LayoutParams((26 * d).toInt(), (26 * d).toInt(), android.view.Gravity.TOP or android.view.Gravity.END)
                    .apply { setMargins(0, (8 * d).toInt(), (8 * d).toInt(), 0) }
                setImageResource(R.drawable.ic_check_circle)
                imageTintList = android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(context, R.color.brand_green_text)
                )
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            frame.addView(image)
            frame.addView(tick)
            return VH(frame, image, tick)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val d = resources.displayMetrics.density
            val picked = position == chosen
            holder.frame.background = GradientDrawable().apply {
                cornerRadius = 16 * d
                setColor(androidx.core.content.ContextCompat.getColor(holder.frame.context, R.color.surface))
                setStroke(
                    ((if (picked) 3 else 1) * d).toInt(),
                    androidx.core.content.ContextCompat.getColor(holder.frame.context, if (picked) R.color.brand_green_text else R.color.page_line)
                )
            }
            holder.tick.visibility = if (picked) View.VISIBLE else View.GONE
            holder.image.setImageBitmap(previews[styles[position]])
            holder.frame.contentDescription = getString(R.string.logo_make) + " " + (position + 1)
            holder.frame.setOnClickListener {
                chosen = holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: return@setOnClickListener
                showStep(edit = true)
            }
        }
    }

    /**
     * How a made logo is remembered: "v2|STYLE|colour|SHOW|TRADE|name" (the name last, so a "|" in it stays). The
     * trade is kept since 10 Nov 2026 (v1 had none: it opens in the shop's trade of the day).
     */
    private data class Made(
        val style: LogoDesigns.Style, val palette: Int, val show: LogoDesigns.Show, val name: String, val trade: Trade?
    ) {
        fun text() = listOf("v2", style.name, palette.toString(), show.name, trade?.name.orEmpty(), name).joinToString("|")

        companion object {
            fun parse(text: String?): Made? {
                val head = text?.substringBefore("|") ?: return null
                val parts = when (head) {
                    "v1" -> text.split("|", limit = 5).takeIf { it.size == 5 }?.let { it.subList(0, 4) + "" + it[4] }
                    "v2" -> text.split("|", limit = 6).takeIf { it.size == 6 }
                    else -> null
                } ?: return null
                return runCatching {
                    Made(
                        LogoDesigns.Style.valueOf(parts[1]), parts[2].toInt(), LogoDesigns.Show.valueOf(parts[3]), parts[5],
                        parts[4].takeIf { it.isNotEmpty() }?.let { key -> Trade.entries.firstOrNull { it.name == key } }
                    )
                }.getOrNull()
            }
        }
    }

    private companion object {
        const val KEY_NAME = "logo_name"
        const val KEY_PALETTE = "logo_palette"
        const val KEY_CHOSEN = "logo_chosen"
        const val KEY_SHOW = "logo_show"
        const val KEY_EDITING = "logo_editing"
        const val KEY_TRADE = "logo_trade"
    }
}
