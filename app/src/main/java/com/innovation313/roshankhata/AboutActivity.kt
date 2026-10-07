package com.innovation313.roshankhata

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import kotlinx.coroutines.launch
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.innovation313.roshankhata.data.ProblemReport

/**
 * About: what this is, where the data lives, and who is answerable for it.
 *
 * The privacy line sits above the fold rather than behind a link, because it
 * is the question a shopkeeper asks before trusting a ledger to a phone. The
 * full policy is a tap away for anyone who wants the long version — and Play
 * requires it to be reachable, which a link inside the app satisfies.
 */
class AboutActivity : BaseActivity() {

    private fun bigBook(seed: Boolean) {
        val toast = android.widget.Toast.makeText(this, if (seed) "Seeding…" else "Removing…", android.widget.Toast.LENGTH_SHORT)
        toast.show()
        androidx.lifecycle.lifecycleScope.launch {
            val started = android.os.SystemClock.elapsedRealtime()
            val n = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                if (seed) com.innovation313.roshankhata.data.BigBook.seed(this@AboutActivity)
                else { com.innovation313.roshankhata.data.BigBook.clear(this@AboutActivity); 0 }
            }
            val secs = (android.os.SystemClock.elapsedRealtime() - started) / 1000
            android.widget.Toast.makeText(
                this@AboutActivity,
                if (seed) "Seeded $n customers in ${secs}s" else "Seeded book removed in ${secs}s",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        // Edge-to-edge, the mechanism proven on the Home screen.
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        val tvVersion = findViewById<TextView>(R.id.tvVersion)
        tvVersion.text = getString(R.string.about_version, BuildConfig.VERSION_NAME)
        // A debug build's door to the big book (P0, 7 Oct): seven taps on the
        // version line write ten thousand seeded customers, so the app can be
        // felt at that size on a real phone; seven more take them out. A Play
        // build has no such door — the taps do nothing there.
        if (BuildConfig.DEBUG) {
            var taps = 0
            tvVersion.setOnClickListener {
                taps++
                if (taps < 7) return@setOnClickListener
                taps = 0
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Big book (debug)")
                    .setMessage("Seed ${com.innovation313.roshankhata.data.BigBook.CUSTOMERS} customers x ${com.innovation313.roshankhata.data.BigBook.ENTRIES_EACH} entries, or remove the seeded ones?")
                    .setPositiveButton("Seed") { _, _ -> bigBook(seed = true) }
                    .setNegativeButton("Remove") { _, _ -> bigBook(seed = false) }
                    .setNeutralButton(R.string.cancel, null)
                    .show()
            }
        }
        findViewById<TextView>(R.id.tvContact).text = ProblemReport.SUPPORT_EMAIL

        findViewById<MaterialButton>(R.id.btnPrivacyPolicy).setOnClickListener {
            openPrivacyPolicy()
        }
    }

    private fun openPrivacyPolicy() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
        } catch (e: ActivityNotFoundException) {
            // No browser. Rare, but a button that silently does nothing is
            // worse than one that says why.
            Toast.makeText(this, R.string.report_no_email, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        /**
         * Published from the repo's docs/ folder via GitHub Pages. Play requires
         * a policy at a stable, publicly reachable address; this is that address.
         */
        private const val PRIVACY_POLICY_URL =
            "https://aliehtisham052-dotcom.github.io/RoshanKhata/privacy-policy.html"
    }
}
