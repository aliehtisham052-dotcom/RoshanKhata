package com.innovation313.roshankhata

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.innovation313.roshankhata.data.ProblemReport

/**
 * Help and Support: the questions that get asked, and a way through when the
 * answer is not among them.
 *
 * The questions come first on purpose. Most of what a shopkeeper wants to know
 * has an answer already, and reading it takes a moment where waiting on a
 * reply takes a day.
 *
 * Both routes out — a general email and a problem report — open the owner's
 * own mail app, so they read every word before it leaves the phone. Nothing is
 * sent from here in the background, and no part of the ledger goes with it.
 */
class HelpActivity : AppCompatActivity() {

    /** Question and answer, paired so the list cannot fall out of step. */
    private data class Faq(val questionRes: Int, val answerRes: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        buildFaqList()

        findViewById<TextView>(R.id.tvSupportEmail).text = ProblemReport.SUPPORT_EMAIL
        findViewById<TextView>(R.id.tvVersion).text =
            getString(R.string.help_version, BuildConfig.VERSION_NAME)

        findViewById<MaterialButton>(R.id.btnEmail).setOnClickListener { emailUs() }
        findViewById<MaterialButton>(R.id.btnReportProblem).setOnClickListener {
            startActivity(Intent(this, ReportProblemActivity::class.java))
        }
    }

    private fun buildFaqList() {
        val faqs = listOf(
            Faq(R.string.faq_q_backup, R.string.faq_a_backup),
            Faq(R.string.faq_q_restore, R.string.faq_a_restore),
            Faq(R.string.faq_q_lock, R.string.faq_a_lock),
            Faq(R.string.faq_q_hide, R.string.faq_a_hide),
            Faq(R.string.faq_q_percent, R.string.faq_a_percent),
            Faq(R.string.faq_q_delete, R.string.faq_a_delete),
            Faq(R.string.faq_q_language, R.string.faq_a_language),
            Faq(R.string.faq_q_data, R.string.faq_a_data)
        )

        val container = findViewById<LinearLayout>(R.id.faqList)
        container.removeAllViews()

        faqs.forEach { faq ->
            val row = layoutInflater.inflate(R.layout.item_faq, container, false)
            row.findViewById<TextView>(R.id.tvQuestion).setText(faq.questionRes)

            val answer = row.findViewById<TextView>(R.id.tvAnswer)
            answer.setText(faq.answerRes)

            // Answers start folded away: eight of them open at once is a wall
            // of text to scroll past, and the point of a list is to let
            // someone find their own question quickly.
            row.setOnClickListener {
                answer.visibility =
                    if (answer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
            container.addView(row)
        }
    }

    private fun emailUs() {
        val intent = ProblemReport.compose(
            context = this,
            subject = getString(R.string.help_email_subject),
            // The version and phone model, so a reply does not have to start
            // by asking. Nothing about the business goes with it.
            body = "\n\n" + ProblemReport.deviceContext()
        )
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // No mail app installed. Say so, and leave the address on screen
            // for them to copy — better than a button that does nothing.
            Toast.makeText(this, R.string.report_no_email, Toast.LENGTH_LONG).show()
        }
    }
}
