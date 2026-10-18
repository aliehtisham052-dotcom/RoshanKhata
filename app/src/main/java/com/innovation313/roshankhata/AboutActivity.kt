package com.innovation313.roshankhata

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.ComposeView
import androidx.core.graphics.drawable.toBitmap
import com.innovation313.roshankhata.screens.AboutScreen
import com.innovation313.roshankhata.screens.RoshanTheme
import android.widget.Toast
import com.google.android.material.appbar.MaterialToolbar
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
        lifecycleScope.launch {
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

        // The page is the shared Compose screen; the toolbar and the insets stay
        // the phone's, as on every other screen.
        val icon = androidx.core.content.ContextCompat.getDrawable(this, R.mipmap.ic_launcher)!!
            .toBitmap(216, 216).asImageBitmap()
        val dark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        // A debug build's door to the big book (P0, 7 Oct): seven taps on the
        // version line write ten thousand seeded customers, so the app can be
        // felt at that size on a real phone; seven more take them out. A Play
        // build has no such door — the taps do nothing there.
        var taps = 0
        val versionTap: (() -> Unit)? = if (!BuildConfig.DEV_TOOLS) null else {
            {
                taps++
                if (taps >= 7) {
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
        }
        findViewById<ComposeView>(R.id.aboutCompose).setContent {
            RoshanTheme(dark = dark) {
                AboutScreen(
                    icon = BitmapPainter(icon),
                    versionName = BuildConfig.VERSION_NAME,
                    supportEmail = ProblemReport.SUPPORT_EMAIL,
                    onPrivacyPolicy = { openPrivacyPolicy() },
                    onVersionTap = versionTap
                )
            }
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
