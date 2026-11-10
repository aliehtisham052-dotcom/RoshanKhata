package com.innovation313.roshankhata

import com.innovation313.roshankhata.data.string

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.innovation313.roshankhata.data.Digits
import com.innovation313.roshankhata.ui.PdfShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A PDF the app made, shown inside the app (10 Nov 2026, the owner's photo of the Khata Report): Preview handed
 * the file to another app, whose own share button made Preview and Share look like one thing. Here the pages
 * are drawn with Android's own PdfRenderer, the same file Share sends; Share is the one action at the foot, and
 * "open in another app" stays behind the more button for whoever wants to print or zoom there.
 *
 * Only files in this app's cache come here (the screen is not exported), and the renderer is read one page at a
 * time on a background thread: it allows one open page at once.
 */
class PdfPreviewActivity : BaseActivity() {

    companion object {
        const val EXTRA_PATH = "path"
        const val EXTRA_TITLE = "title"

        fun open(context: Context, file: File, title: String? = null) {
            context.startActivity(
                Intent(context, PdfPreviewActivity::class.java)
                    .putExtra(EXTRA_PATH, file.absolutePath)
                    .putExtra(EXTRA_TITLE, title)
                    .apply { if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            )
        }
    }

    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private val renderLock = Mutex()
    private lateinit var file: File
    private lateinit var tvPage: TextView
    private var pageCount = 0

    /** Rendered pages, as much as an eighth of the app's memory allows. */
    private val cache = object : LruCache<Int, Bitmap>((Runtime.getRuntime().maxMemory() / 8 / 1024).toInt()) {
        override fun sizeOf(key: Int, value: Bitmap) = value.byteCount / 1024
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_preview)
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)

        val path = intent.getStringExtra(EXTRA_PATH)
        // Only a file of this app's own cache: the path came from inside the app, but it is checked all the same.
        val f = path?.let { File(it) }
        if (f == null || !f.isFile || !f.canonicalPath.startsWith(cacheDir.canonicalPath)) {
            finish()
            return
        }
        file = f
        findViewById<TextView>(R.id.tvPreviewTitle).text =
            intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: getString(R.string.pdf_preview)
        tvPage = findViewById(R.id.tvPreviewPage)

        findViewById<View>(R.id.btnPreviewBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnPreviewShare).setOnClickListener { PdfShare.shareDirect(this, file) }
        findViewById<View>(R.id.btnPreviewMore).setOnClickListener { anchor ->
            android.widget.PopupMenu(this, anchor).apply {
                menu.add(0, 1, 0, R.string.pdf_open_other)
                setOnMenuItemClickListener { PdfShare.openElsewhere(this@PdfPreviewActivity, file); true }
            }.show()
        }

        val rv: RecyclerView = findViewById(R.id.rvPages)
        val lm = LinearLayoutManager(this)
        rv.layoutManager = lm
        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) = showPage(lm)
        })

        lifecycleScope.launch {
            val opened = withContext(Dispatchers.IO) {
                runCatching {
                    val d = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    pfd = d
                    PdfRenderer(d).also { renderer = it }.pageCount
                }.getOrNull()
            }
            if (opened == null || opened == 0) {
                // A file the renderer cannot read: the phone's own PDF app may still manage it.
                findViewById<TextView>(R.id.tvPreviewStatus).apply {
                    setText(R.string.pdf_no_viewer)
                    visibility = View.VISIBLE
                }
                return@launch
            }
            pageCount = opened
            rv.adapter = PagesAdapter()
            showPage(lm)
        }
    }

    private fun showPage(lm: LinearLayoutManager) {
        if (pageCount <= 1) { tvPage.visibility = View.GONE; return }
        val at = (lm.findFirstVisibleItemPosition().coerceAtLeast(0)) + 1
        tvPage.visibility = View.VISIBLE
        tvPage.text = Digits.string(resources, R.string.pdf_stmt_page, at, pageCount)
    }

    /** One page drawn to [width] pixels, from the cache or the renderer. */
    private suspend fun page(index: Int, width: Int): Bitmap? {
        cache.get(index)?.let { if (it.width == width) return it }
        return withContext(Dispatchers.IO) {
            renderLock.withLock {
                val r = renderer ?: return@withLock null
                runCatching {
                    r.openPage(index).use { p ->
                        val h = (width.toFloat() * p.height / p.width).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }.getOrNull()?.also { cache.put(index, it) }
            }
        }
    }

    private inner class PagesAdapter : RecyclerView.Adapter<PagesAdapter.VH>() {
        inner class VH(val image: ImageView) : RecyclerView.ViewHolder(image)

        override fun getItemCount() = pageCount

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val d = resources.displayMetrics.density
            val iv = ImageView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { bottomMargin = (12 * d).toInt() }
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(Color.WHITE)
                elevation = 2 * d
                contentDescription = null
            }
            return VH(iv)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val width = (holder.image.parent as? View)?.let { it.width - it.paddingStart - it.paddingEnd }
                ?.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
            holder.image.tag = position
            val ready = cache.get(position)?.takeIf { it.width == width }
            if (ready != null) {
                holder.image.setImageBitmap(ready)
                return
            }
            // A sheet of A4's shape while the page is drawn, so the list does not jump.
            holder.image.setImageDrawable(null)
            holder.image.minimumHeight = (width * 1.414f).toInt()
            lifecycleScope.launch {
                val bmp = page(position, width)
                if (holder.image.tag == position && bmp != null) {
                    holder.image.minimumHeight = 0
                    holder.image.setImageBitmap(bmp)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // The renderer and its file closed on the same lock the pages use, so a page being drawn finishes first.
        val r = renderer
        val d = pfd
        renderer = null
        pfd = null
        com.innovation313.roshankhata.data.AppScope.launch {
            renderLock.withLock {
                runCatching { r?.close() }
                runCatching { d?.close() }
            }
        }
        cache.evictAll()
    }
}
