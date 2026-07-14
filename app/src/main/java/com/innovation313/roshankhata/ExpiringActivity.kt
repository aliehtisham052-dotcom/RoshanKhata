package com.innovation313.roshankhata

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.innovation313.roshankhata.data.ExpiryWindow
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.ui.ExpiringAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Stock at or near its expiry date.
 *
 * For an agri-input dealer this is money, not housekeeping. An expired drum of
 * pesticide is a total write-off; the same drum flagged two months out can
 * still be sold, discounted, or returned while the supplier will accept it.
 *
 * The screen is honest about its own blind spot: it can only warn about stock
 * whose expiry date was actually recorded. Silence here does not mean nothing
 * is expiring — it may only mean nobody entered a date.
 */
class ExpiringActivity : AppCompatActivity() {

    private lateinit var adapter: ExpiringAdapter
    private lateinit var tvEmpty: TextView

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_expiring)

        tvEmpty = findViewById(R.id.tvNoExpiring)

        adapter = ExpiringAdapter()
        val rv: RecyclerView = findViewById(R.id.rvExpiring)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        val cutoff = System.currentTimeMillis() + ExpiryWindow.WARN_MS

        lifecycleScope.launch {
            dao.observeExpiringBatches(cutoff).collectLatest { list ->
                adapter.submit(list)
                tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
}
