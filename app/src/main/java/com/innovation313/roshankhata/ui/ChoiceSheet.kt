package com.innovation313.roshankhata.ui

import android.app.Activity
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.innovation313.roshankhata.R

/**
 * The one chooser (10 Oct). Every "pick one" in the app opens this sheet
 * instead of a system radio dialog: the owner picked the empty Khata card's
 * look - a tinted icon tile, plain words, a green action - and asked that
 * every picker look like it.
 *
 * Each choice is a card (the shop-type picker's card, so the app has one
 * "chosen" look): its tile, its name, and an optional line on what it does.
 * The current choice wears the green ring and tick.
 *
 * Two ways to decide:
 *  - Default: a tap decides. The tapped card takes the ring, the sheet
 *    closes a moment later so the owner sees what he chose, then [onPick].
 *  - With [confirm]: a tap only marks a card and the button decides - for
 *    choices that change the book (which customer survives a merge), where
 *    a stray tap must not act.
 *
 * Swiping the sheet away or Back is Cancel, as everywhere else in Android;
 * no Cancel button is needed.
 */
object ChoiceSheet {

    /**
     * One choice.
     * [icon] a drawable for the tile, tinted [iconTint] when that is non-zero
     * (white glyph icons) or drawn in its own colours when zero (Home-tile
     * duotones). Or [glyph], a few characters drawn in the tile instead
     * ("Aa", "Rs", "A–Z"), at [glyphSp]. [tile] is the tile's fill.
     */
    class Option(
        val title: CharSequence,
        val subtitle: CharSequence? = null,
        val icon: Int = 0,
        val iconTint: Int = R.color.brand_green_text,
        val tile: Int = R.color.tile_khata_bg,
        val glyph: CharSequence? = null,
        val glyphSp: Float = 14f
    )

    /** A button under the cards: its label and what it does. */
    class Action(val label: CharSequence, val run: () -> Unit)

    /**
     * Opens the sheet. [selected] is the current choice (-1 for none).
     * [confirm] turns on button-decides mode (see the class note); its run()
     * is not used - [onPick] gets the marked index. [secondary] is a quieter
     * second button (it closes the sheet first), e.g. "Not a duplicate".
     */
    fun show(
        activity: Activity,
        title: CharSequence,
        options: List<Option>,
        selected: Int = -1,
        message: CharSequence? = null,
        confirm: CharSequence? = null,
        secondary: Action? = null,
        onPick: (Int) -> Unit
    ): BottomSheetDialog {
        val sheet = BottomSheetDialog(activity)
        val inflater = LayoutInflater.from(activity)
        val view = inflater.inflate(R.layout.sheet_choice, null)

        view.findViewById<TextView>(R.id.tvChoiceTitle).text = title
        view.findViewById<TextView>(R.id.tvChoiceMessage).apply {
            if (message.isNullOrBlank()) visibility = View.GONE
            else { text = message; visibility = View.VISIBLE }
        }

        val list = view.findViewById<LinearLayout>(R.id.choiceList)
        val rows = ArrayList<View>(options.size)
        var marked = selected.takeIf { it in options.indices } ?: -1
        var decided = false

        fun mark(index: Int) {
            marked = index
            rows.forEachIndexed { i, row ->
                row.isSelected = i == index
                row.findViewById<View>(R.id.choiceTick).visibility =
                    if (i == index) View.VISIBLE else View.INVISIBLE
            }
        }

        options.forEachIndexed { i, opt ->
            val row = inflater.inflate(R.layout.item_choice_row, list, false)
            bindRow(activity, row, opt)
            row.setOnClickListener {
                if (decided) return@setOnClickListener
                mark(i)
                if (confirm == null) {
                    // Let the ring and tick show for a beat, then close and act.
                    decided = true
                    row.postDelayed({
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            sheet.dismiss()
                            onPick(i)
                        }
                    }, 160L)
                }
            }
            // Read by TalkBack as a radio button: "Name (A-Z), selected".
            row.setAccessibilityDelegate(object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = RadioButton::class.java.name
                    info.isCheckable = true
                    info.isChecked = host.isSelected
                }
            })
            rows.add(row)
            list.addView(row)
        }
        mark(marked)

        if (confirm != null || secondary != null) {
            view.findViewById<View>(R.id.choiceActions).visibility = View.VISIBLE
            val primary = view.findViewById<MaterialButton>(R.id.btnChoicePrimary)
            if (confirm != null) {
                primary.text = confirm
                primary.setOnClickListener {
                    if (marked < 0) return@setOnClickListener
                    sheet.dismiss()
                    onPick(marked)
                }
            } else {
                primary.visibility = View.GONE
            }
            if (secondary != null) {
                view.findViewById<MaterialButton>(R.id.btnChoiceSecondary).apply {
                    visibility = View.VISIBLE
                    text = secondary.label
                    setOnClickListener { sheet.dismiss(); secondary.run() }
                }
            }
        }

        TextFit.relax(view)
        sheet.setContentView(view)
        // Open in full: a half-open sheet showing one and a half cards reads
        // as broken (the Settings sheet's rule). A long list still scrolls.
        sheet.behavior.skipCollapsed = true
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.show()
        return sheet
    }

    private fun bindRow(activity: Activity, row: View, opt: Option) {
        row.findViewById<TextView>(R.id.tvChoiceRowTitle).text = opt.title
        row.findViewById<TextView>(R.id.tvChoiceRowSub).apply {
            if (opt.subtitle.isNullOrBlank()) visibility = View.GONE
            else { text = opt.subtitle; visibility = View.VISIBLE }
        }
        val tile = row.findViewById<FrameLayout>(R.id.choiceTile)
        tile.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(activity, opt.tile))
        val icon = row.findViewById<ImageView>(R.id.ivChoiceIcon)
        val glyph = row.findViewById<TextView>(R.id.tvChoiceGlyph)
        val ink = ContextCompat.getColor(activity, if (opt.iconTint != 0) opt.iconTint else R.color.brand_green_text)
        if (opt.glyph != null) {
            icon.visibility = View.GONE
            glyph.visibility = View.VISIBLE
            glyph.text = opt.glyph
            glyph.setTextColor(ink)
            glyph.setTextSize(TypedValue.COMPLEX_UNIT_SP, opt.glyphSp)
        } else if (opt.icon != 0) {
            icon.setImageResource(opt.icon)
            icon.imageTintList = if (opt.iconTint != 0) ColorStateList.valueOf(ink) else null
        } else {
            tile.visibility = View.GONE
        }
    }
}
