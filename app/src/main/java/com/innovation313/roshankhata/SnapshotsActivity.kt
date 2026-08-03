package com.innovation313.roshankhata

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.data.Snapshots
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.ScreenInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The way back to yesterday.
 *
 * The app has copied the ledger every day for months, kept seven, checked
 * each one before writing it — and had no screen to reach them from. That is
 * insurance with no claims desk, and it mattered most in the one case nothing
 * else covers: a database that will not open at all leaves the owner locked
 * out of a book that is sitting intact, seven times over, on their own phone.
 *
 * Nothing here goes through Room. The list is file names, and the restore is
 * a file copy, so this screen still works on the day the database is the
 * broken thing.
 */
class SnapshotsActivity : AppCompatActivity() {

    private lateinit var adapter: Adapter
    private lateinit var empty: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_snapshots)
        ScreenInsets.on(this)

        empty = findViewById(R.id.tvSnapshotsEmpty)
        adapter = Adapter()
        findViewById<RecyclerView>(R.id.rvSnapshots).apply {
            layoutManager = LinearLayoutManager(this@SnapshotsActivity)
            adapter = this@SnapshotsActivity.adapter
        }

        refresh()
    }

    private fun refresh() {
        val items = Snapshots.list(this)
        adapter.submit(items)
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun confirm(snapshot: Snapshots.Snapshot) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.snapshot_restore_title)
            .setMessage(
                getString(R.string.snapshot_restore_warning, Format.dateOnly(snapshot.takenOn))
            )
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.snapshot_restore_action) { _, _ -> restore(snapshot) }
            .show()
    }

    private fun restore(snapshot: Snapshots.Snapshot) {
        Toast.makeText(this, R.string.snapshot_restoring, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                Snapshots.restore(this@SnapshotsActivity, snapshot)
            }
            when (result) {
                is Snapshots.RestoreResult.Ok -> {
                    // Everything downstream is holding a handle to a database
                    // that no longer exists on disk. Nothing is repaired in
                    // place; the whole task restarts against the file that is
                    // there now.
                    MaterialAlertDialogBuilder(this@SnapshotsActivity)
                        .setTitle(R.string.snapshot_restored_title)
                        .setMessage(R.string.snapshot_restored_body)
                        .setCancelable(false)
                        .setPositiveButton(R.string.ok) { _, _ ->
                            startActivity(
                                Intent(this@SnapshotsActivity, KhataActivity::class.java)
                                    .addFlags(
                                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                            Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    )
                            )
                        }
                        .show()
                }
                is Snapshots.RestoreResult.SnapshotUnhealthy ->
                    MaterialAlertDialogBuilder(this@SnapshotsActivity)
                        .setTitle(R.string.snapshot_unhealthy_title)
                        .setMessage(R.string.snapshot_unhealthy_body)
                        .setPositiveButton(R.string.ok, null)
                        .show()
                is Snapshots.RestoreResult.Failed ->
                    Toast.makeText(
                        this@SnapshotsActivity, R.string.snapshot_restore_failed,
                        Toast.LENGTH_LONG
                    ).show()
            }
        }
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {

        private var items: List<Snapshots.Snapshot> = emptyList()

        fun submit(list: List<Snapshots.Snapshot>) {
            items = list
            notifyDataSetChanged()
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val date: TextView = v.findViewById(R.id.tvSnapshotDate)
            val size: TextView = v.findViewById(R.id.tvSnapshotSize)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_snapshot, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val s = items[position]
            holder.date.text = Format.dateOnly(s.takenOn)
            holder.size.text = getString(R.string.snapshot_size_kb, s.bytes / 1024)
            holder.itemView.setOnClickListener { confirm(s) }
        }
    }
}
