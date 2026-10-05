package com.innovation313.roshankhata

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.innovation313.roshankhata.data.BusinessProfile
import com.innovation313.roshankhata.data.Businesses
import com.innovation313.roshankhata.data.DateWords
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.Product
import com.innovation313.roshankhata.data.RateList
import com.innovation313.roshankhata.data.UnitWords
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.RateListImage
import com.innovation313.roshankhata.ui.ScreenInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Rate list — the shop's selling prices as pictures to send on WhatsApp.
 *
 * Read-only: it reads Products and draws; nothing is written to the ledger,
 * so it opens on the helper's phone too. What the owner chose (which products
 * are left off, the heading, the columns) is remembered per shop in this
 * screen's own preferences, so next week's list is one tap.
 *
 * Every picture is drawn off the main thread and written to the cache folder
 * the FileProvider shares from (res/xml/file_paths.xml, "ratelists/"), one
 * page at a time and recycled straight after, so a long list never holds six
 * full-size pictures in memory at once.
 */
class RateListActivity : BaseActivity() {

    private val dao by lazy { KhataDatabase.get(this).khataDao() }
    private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val shopKey by lazy { Businesses.active(this).id.toString() }

    private var products: List<Product> = emptyList()
    private var company: String? = null
    private val excluded = HashSet<Long>()
    private var logo: Bitmap? = null

    private lateinit var controls: View
    private lateinit var heading: TextInputEditText
    private lateinit var cbCash: MaterialCheckBox
    private lateinit var cbCredit: MaterialCheckBox
    private lateinit var markSwitch: SwitchMaterial
    private lateinit var summary: TextView
    private lateinit var share: MaterialButton
    private val productAdapter = ProductRows { id, on -> toggle(id, on) }

    private var summaryJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rate_list)
        ScreenInsets.on(this)

        val rv = findViewById<RecyclerView>(R.id.rvRateList)
        // The layout manager first: inflating against a RecyclerView asks it
        // for layout params, and one with no manager throws (caught by
        // EveryScreenOpensTest on 5 Oct before it reached a phone).
        rv.layoutManager = LinearLayoutManager(this)
        controls = LayoutInflater.from(this).inflate(R.layout.item_rate_list_controls, rv, false)
        heading = controls.findViewById(R.id.etRateHeading)
        cbCash = controls.findViewById(R.id.cbRateCash)
        cbCredit = controls.findViewById(R.id.cbRateCredit)
        markSwitch = controls.findViewById(R.id.switchRateMark)
        summary = findViewById(R.id.tvRateListSummary)
        share = findViewById(R.id.btnShareRateList)

        rv.adapter = ConcatAdapter(OneView(controls), productAdapter)

        restore()

        cbCash.setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(KEY_CASH, on).apply(); updateSummary() }
        cbCredit.setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(KEY_CREDIT, on).apply(); updateSummary() }
        markSwitch.setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(KEY_MARK, on).apply() }
        heading.doAfterTextChanged { saveHeading() }
        controls.findViewById<View>(R.id.btnRateSelectAll).setOnClickListener {
            RateList.candidates(products, company).forEach { excluded.remove(it.id) }
            saveExcluded(); showProducts()
        }
        controls.findViewById<View>(R.id.btnRateClear).setOnClickListener {
            RateList.candidates(products, company).forEach { excluded.add(it.id) }
            saveExcluded(); showProducts()
        }
        share.setOnClickListener { shareList() }

        lifecycleScope.launch {
            val (all, shopLogo) = withContext(Dispatchers.IO) {
                dao.productsOnce() to BusinessProfile.loadLogo(this@RateListActivity)
            }
            products = all
            logo = shopLogo
            // Ticks for products that no longer exist are dropped, not kept forever.
            excluded.retainAll(all.map { it.id }.toSet())
            setUpCompanies()
            showProducts()
        }
    }

    private fun restore() {
        excluded.clear()
        prefs.getString(KEY_EXCLUDED + shopKey, null)
            ?.split(',')?.mapNotNull { it.toLongOrNull() }?.let { excluded.addAll(it) }
        company = prefs.getString(KEY_COMPANY + shopKey, null)
        cbCash.isChecked = prefs.getBoolean(KEY_CASH, true)
        cbCredit.isChecked = prefs.getBoolean(KEY_CREDIT, true)
        markSwitch.isChecked = prefs.getBoolean(KEY_MARK, true)
        heading.setText(prefs.getString(KEY_HEADING + shopKey, null) ?: getString(R.string.rate_list_heading_default))
    }

    /**
     * Kept only when the owner wrote their own: the default is not stored, so
     * a shop that switches the app to another language gets the default in
     * that language instead of last month's words in the old one.
     */
    private fun saveHeading() {
        val text = heading.text?.toString()?.trim().orEmpty()
        val e = prefs.edit()
        if (text.isEmpty() || text == getString(R.string.rate_list_heading_default)) e.remove(KEY_HEADING + shopKey)
        else e.putString(KEY_HEADING + shopKey, text)
        e.apply()
    }

    private fun saveExcluded() {
        prefs.edit().putString(KEY_EXCLUDED + shopKey, excluded.joinToString(",")).apply()
    }

    private fun setUpCompanies() {
        val companies = RateList.companies(products)
        val scroll = controls.findViewById<View>(R.id.companyScroll)
        val group = controls.findViewById<ChipGroup>(R.id.chipCompanies)
        group.removeAllViews()
        // One company or none: nothing to choose between.
        if (companies.size < 2) {
            scroll.visibility = View.GONE
            company = null
            return
        }
        scroll.visibility = View.VISIBLE
        if (company != null && companies.none { it.equals(company, ignoreCase = true) }) company = null

        fun chip(label: String, value: String?): Chip =
            (layoutInflater.inflate(R.layout.item_rate_list_chip, group, false) as Chip).apply {
                id = View.generateViewId()
                text = label
                tag = value
                isChecked = (value == null && company == null) || (value != null && value.equals(company, ignoreCase = true))
            }
        group.addView(chip(getString(R.string.filter_type_all), null))
        companies.forEach { group.addView(chip(it, it)) }
        group.setOnCheckedStateChangeListener { g, ids ->
            val picked = ids.firstOrNull()?.let { g.findViewById<Chip>(it) } ?: return@setOnCheckedStateChangeListener
            company = picked.tag as String?
            prefs.edit().apply {
                if (company == null) remove(KEY_COMPANY + shopKey) else putString(KEY_COMPANY + shopKey, company)
            }.apply()
            showProducts()
        }
    }

    private fun showProducts() {
        val unpriced = RateList.unpricedCount(products)
        controls.findViewById<TextView>(R.id.tvRateUnpriced).apply {
            visibility = if (unpriced > 0) View.VISIBLE else View.GONE
            text = getString(R.string.rate_list_unpriced, unpriced.toString())
        }
        val anyPriced = products.any { RateList.hasPrice(it) && !it.isDeleted }
        findViewById<View>(R.id.tvRateListEmpty).visibility = if (anyPriced) View.GONE else View.VISIBLE
        findViewById<View>(R.id.rvRateList).visibility = if (anyPriced) View.VISIBLE else View.GONE
        findViewById<View>(R.id.rateListBar).visibility = if (anyPriced) View.VISIBLE else View.GONE

        productAdapter.submitList(RateList.candidates(products, company).map { p ->
            ProductRow(
                id = p.id,
                name = p.name,
                sub = listOfNotNull(
                    p.company?.trim()?.takeIf { company == null && it.isNotEmpty() },
                    UnitWords.label(p.defaultUnit).takeIf { it.isNotEmpty() }
                ).joinToString(" \u00b7 "),
                // Each figure kept left-to-right: in Urdu a "₹ 4,200" left loose
                // in a right-to-left line can come out as "4,200 ₹".
                prices = listOfNotNull(
                    p.salePrice?.takeIf { it > 0 }?.let { Format.ltr(Format.money(it)) },
                    p.creditPrice?.takeIf { it > 0 }?.let { Format.ltr(Format.money(it)) }
                ).joinToString(" / "),
                checked = p.id !in excluded
            )
        })
        updateSummary()
    }

    private fun toggle(id: Long, on: Boolean) {
        if (on) excluded.remove(id) else excluded.add(id)
        saveExcluded()
        showProducts()
    }

    /** The rows, columns and text the picture will carry right now. */
    private fun input(): RateListImage.Input {
        val rows = RateList.rows(products, company, excluded)
        val columns = RateList.columns(rows, cbCash.isChecked, cbCredit.isChecked)
        val date = DateWords.format("d MMM yyyy", System.currentTimeMillis())
        val contact = listOfNotNull(
            // A phone number in an Urdu line otherwise reorders its own digit groups (see Format.ltr).
            BusinessProfile.businessPhone(this)?.trim()?.takeIf { it.isNotEmpty() }?.let { Format.ltr(it) },
            BusinessProfile.businessAddress(this)?.trim()?.takeIf { it.isNotEmpty() }
        ).joinToString("  \u00b7  ")
        return RateListImage.Input(
            shopName = BusinessProfile.businessName(this)?.trim().orEmpty(),
            contact = contact,
            logo = logo,
            heading = heading.text?.toString()?.trim().orEmpty().ifEmpty { getString(R.string.rate_list_heading_default) },
            subLine = listOfNotNull(date, company).joinToString("  \u00b7  "),
            rows = RateList.visible(rows, columns),
            columns = columns,
            companyPerRow = company == null,
            mark = if (markSwitch.isChecked) getString(R.string.made_with_app) else null
        )
    }

    /** "Products: 12 · Pictures: 1", worked out off the main thread after the owner stops tapping. */
    private fun updateSummary() {
        summaryJob?.cancel()
        val inp = input()
        share.isEnabled = inp.rows.isNotEmpty()
        summaryJob = lifecycleScope.launch {
            delay(120)
            val pages = withContext(Dispatchers.Default) {
                if (inp.rows.isEmpty()) 0 else RateListImage.plan(this@RateListActivity, inp).pages.size
            }
            summary.text = getString(R.string.rate_list_summary, inp.rows.size.toString(), pages.toString())
        }
    }

    private fun shareList() {
        val inp = input()
        if (inp.rows.isEmpty()) return
        share.isEnabled = false
        lifecycleScope.launch {
            val files = withContext(Dispatchers.Default) {
                try {
                    val dir = File(cacheDir, "ratelists").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() }
                    val plan = RateListImage.plan(this@RateListActivity, inp)
                    plan.pages.indices.map { i ->
                        val bmp = RateListImage.draw(plan, i)
                        val f = File(dir, "rate_list_${i + 1}.png")
                        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        bmp.recycle()
                        f
                    }
                } catch (e: Exception) {
                    null
                }
            }
            share.isEnabled = true
            if (files.isNullOrEmpty()) {
                Toast.makeText(this@RateListActivity, R.string.share_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            send(files)
        }
    }

    private fun send(files: List<File>) {
        try {
            val uris: List<Uri> = files.map { FileProvider.getUriForFile(this, "$packageName.fileprovider", it) }
            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }.apply {
                type = "image/png"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // The read grant travels with ClipData; without it a multi-picture
                // share reaches some apps with only the first picture readable.
                clipData = ClipData.newRawUri(null, uris[0]).also { clip ->
                    uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                }
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share)))
        } catch (e: Exception) {
            Toast.makeText(this, R.string.share_failed, Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- List plumbing ----------

    private data class ProductRow(
        val id: Long,
        val name: String,
        val sub: String,
        val prices: String,
        val checked: Boolean
    )

    /** The controls block as the list's first item. */
    private class OneView(private val view: View) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = 1
        override fun getItemViewType(position: Int) = R.layout.item_rate_list_controls
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            object : RecyclerView.ViewHolder(view) {}
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
    }

    private class ProductRows(
        private val onToggle: (Long, Boolean) -> Unit
    ) : ListAdapter<ProductRow, ProductRows.Holder>(DIFF) {

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val tick: MaterialCheckBox = view.findViewById(R.id.cbRateProduct)
            val name: TextView = view.findViewById(R.id.tvRateName)
            val sub: TextView = view.findViewById(R.id.tvRateSub)
            val prices: TextView = view.findViewById(R.id.tvRatePrices)
        }

        override fun getItemViewType(position: Int) = R.layout.item_rate_list_product

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_rate_list_product, parent, false)
            com.innovation313.roshankhata.ui.TextFit.relax(v)
            return Holder(v)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val row = getItem(position)
            holder.name.text = row.name
            holder.sub.text = row.sub
            holder.sub.visibility = if (row.sub.isEmpty()) View.GONE else View.VISIBLE
            holder.prices.text = row.prices
            holder.tick.isChecked = row.checked
            holder.itemView.setOnClickListener { onToggle(row.id, !row.checked) }
        }

        companion object {
            val DIFF = object : DiffUtil.ItemCallback<ProductRow>() {
                override fun areItemsTheSame(a: ProductRow, b: ProductRow) = a.id == b.id
                override fun areContentsTheSame(a: ProductRow, b: ProductRow) = a == b
            }
        }
    }

    companion object {
        private const val PREFS = "rate_list"
        private const val KEY_EXCLUDED = "excluded_"
        private const val KEY_COMPANY = "company_"
        private const val KEY_HEADING = "heading_"
        private const val KEY_CASH = "cash"
        private const val KEY_CREDIT = "credit"
        private const val KEY_MARK = "mark"
    }
}
