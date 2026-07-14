package com.innovation313.roshankhata

import android.content.ActivityNotFoundException
import android.os.Bundle
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.innovation313.roshankhata.data.ProblemReport

/**
 * A way for the owner to say something is wrong.
 *
 * A shopkeeper who hits a bug otherwise has two choices: uninstall, or leave
 * one star. Neither tells us what broke, and the second is permanent. This
 * costs them half a minute and is the only route by which we ever hear about
 * the fault that made them stop trusting the app.
 *
 * The privacy line is not decoration. This app holds a record of who owes whom
 * — in a trade where that is nobody else's business — and none of it travels
 * with a bug report. The email opens in the owner's own mail app so they can
 * read every word before it leaves, and delete any of it. Nothing is sent
 * behind their back.
 */
class ReportProblemActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report_problem)

        findViewById<MaterialButton>(R.id.btnSendReport).setOnClickListener { send() }
    }

    private fun send() {
        val description = findViewById<EditText>(R.id.etDescription)
            .text.toString().trim()

        if (description.isEmpty()) {
            Toast.makeText(this, R.string.report_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val kind = when (findViewById<RadioGroup>(R.id.rgKind).checkedRadioButtonId) {
            R.id.rbWrongNumber -> getString(R.string.report_kind_wrong_number)
            R.id.rbCrash -> getString(R.string.report_kind_crash)
            R.id.rbLost -> getString(R.string.report_kind_lost)
            R.id.rbConfusing -> getString(R.string.report_kind_confusing)
            R.id.rbSuggestion -> getString(R.string.report_kind_suggestion)
            else -> getString(R.string.report_kind_other)
        }

        // The body is assembled here, in full, and handed to the mail app. The
        // owner sees all of it. There is no second, silent channel.
        val body = buildString {
            appendLine(description)
            appendLine()
            appendLine(ProblemReport.deviceContext())
        }

        val intent = ProblemReport.compose(
            context = this,
            subject = getString(R.string.report_subject, kind),
            body = body
        )

        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // No mail app. Say so plainly and give them the address — rather
            // than a dead button and no explanation.
            Toast.makeText(this, R.string.report_no_email, Toast.LENGTH_LONG).show()
        }
    }
}
