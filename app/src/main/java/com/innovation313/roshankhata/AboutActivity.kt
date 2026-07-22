package com.innovation313.roshankhata

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        findViewById<TextView>(R.id.tvVersion).text =
            getString(R.string.about_version, BuildConfig.VERSION_NAME)
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
