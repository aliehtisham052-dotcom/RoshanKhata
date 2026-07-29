package com.innovation313.roshankhata.ui

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.innovation313.roshankhata.R

/**
 * One slide per invoice template, swiped through rather than picked from a
 * vertical list — each slide is that template's own live-rendered preview,
 * so choosing a design and seeing what it looks like are the same motion.
 *
 * Bitmaps are supplied already rendered (see
 * InvoiceEditorActivity.renderAllPreviews) — this adapter only displays
 * them; it does not know how a template is drawn or what invoice they
 * belong to.
 */
class TemplatePagerAdapter : RecyclerView.Adapter<TemplatePagerAdapter.SlideHolder>() {

    private var bitmaps: List<Bitmap?> = emptyList()

    /** Replaces every slide's image at once — the list is small (currently 2, at most 10), so a full refresh is simpler than diffing which slide actually changed. */
    fun submit(newBitmaps: List<Bitmap?>) {
        bitmaps = newBitmaps
        notifyDataSetChanged()
    }

    class SlideHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.ivTemplateSlide)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlideHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_template_slide, parent, false)
        return SlideHolder(view)
    }

    override fun onBindViewHolder(holder: SlideHolder, position: Int) {
        val bmp = bitmaps.getOrNull(position)
        if (bmp != null) holder.image.setImageBitmap(bmp) else holder.image.setImageDrawable(null)
    }

    override fun getItemCount(): Int = bitmaps.size
}
