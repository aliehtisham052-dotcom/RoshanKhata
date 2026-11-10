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
 * Make a logo (10 Nov 2026, the owner: most shops have no logo, and a shopkeeper cannot draw one). The designs of
 * [LogoDesigns] for this shop's name, Urdu name and trade, in a grid; the name on the logo, what the big words
 * say (name, letters, Urdu name) and the colour change them all at once; Use this logo draws the chosen one at
 * print size and keeps it as the shop's logo, exactly as a picked picture is kept. Returns RESULT_OK when saved.
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
    private val previews = mutableMapOf<LogoDesigns.Style, Bitmap>()
    private var drawJob: Job? = null
    private var saving = false
    private lateinit var adapter: Designs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logo_maker)
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)

        trade = Trade.current(this)
        tagline = getString(trade.exampleRes)
        urduName = BusinessProfile.businessNameUrdu(this)?.takeIf { it.isNotBlank() }
        name = savedInstanceState?.getString(KEY_NAME) ?: BusinessProfile.businessName(this).orEmpty()
        palettes = LogoDesigns.palettes(this, trade)
        palette = (savedInstanceState?.getInt(KEY_PALETTE) ?: 0).coerceIn(0, palettes.size - 1)
        styles = LogoDesigns.styles(urduName)
        chosen = (savedInstanceState?.getInt(KEY_CHOSEN) ?: 0).coerceIn(0, styles.size - 1)
        show = savedInstanceState?.getString(KEY_SHOW)?.let { runCatching { LogoDesigns.Show.valueOf(it) }.getOrNull() }
            ?: LogoDesigns.Show.NAME

        findViewById<TextView>(R.id.tvLogoTrade).text = getString(trade.labelRes)
        findViewById<View>(R.id.btnLogoBack).setOnClickListener { finish() }

        val etName: EditText = findViewById(R.id.etLogoName)
        etName.setText(name)
        etName.hint = getString(R.string.business_name_hint)
        etName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                name = s?.toString().orEmpty()
                redraw(delayMs = 250)
            }
        })

        buildShowChips()
        buildColours()

        val rv: RecyclerView = findViewById(R.id.rvLogos)
        rv.layoutManager = GridLayoutManager(this, 2)
        adapter = Designs()
        rv.adapter = adapter

        findViewById<View>(R.id.btnLogoUse).setOnClickListener { save() }
        com.innovation313.roshankhata.ui.TextFit.relax(findViewById(R.id.logoPanel))
        redraw(delayMs = 0)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_NAME, name)
        outState.putInt(KEY_PALETTE, palette)
        outState.putInt(KEY_CHOSEN, chosen)
        outState.putString(KEY_SHOW, show.name)
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
            redraw(delayMs = 0)
        }
    }

    /** A round swatch per colour pair; the chosen one ringed. */
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
                    redraw(delayMs = 0)
                }
            }
            row.addView(dot)
        }
    }

    private fun spec(style: LogoDesigns.Style) = LogoDesigns.Spec(
        style = style,
        palette = palettes[palette],
        show = show,
        name = name.trim().ifEmpty { getString(R.string.app_name) },
        urduName = urduName,
        tagline = tagline,
        trade = trade
    )

    /** Every design drawn again off the main thread, after a short pause while the name is being typed. */
    private fun redraw(delayMs: Long) {
        drawJob?.cancel()
        drawJob = lifecycleScope.launch {
            if (delayMs > 0) delay(delayMs)
            val size = (resources.displayMetrics.widthPixels / 2).coerceIn(240, 520)
            val drawn = withContext(Dispatchers.Default) {
                styles.associateWith { LogoDesigns.draw(this@LogoMakerActivity, spec(it), size) }
            }
            previews.clear()
            previews.putAll(drawn)
            adapter.notifyDataSetChanged()
        }
    }

    /** The chosen design at print size, kept as the shop's logo the way a picked picture is. */
    private fun save() {
        if (saving) return
        saving = true
        val style = styles[chosen]
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val bmp = LogoDesigns.draw(this@LogoMakerActivity, spec(style), 1024)
                    val file = File(File(cacheDir, "logo_maker").apply { mkdirs() }, "logo.png")
                    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bmp.recycle()
                    BusinessProfile.saveLogo(this@LogoMakerActivity, Uri.fromFile(file)).also { file.delete() }
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
            val frame = FrameLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (170 * d).toInt())
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
                val before = chosen
                chosen = holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: return@setOnClickListener
                notifyItemChanged(before)
                notifyItemChanged(chosen)
            }
        }
    }

    private companion object {
        const val KEY_NAME = "logo_name"
        const val KEY_PALETTE = "logo_palette"
        const val KEY_CHOSEN = "logo_chosen"
        const val KEY_SHOW = "logo_show"
    }
}
