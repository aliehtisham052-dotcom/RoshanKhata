package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words
import com.innovation313.roshankhata.words.word
import java.net.URI
import java.net.URLEncoder

/**
 * Help on the computer (KMP Phase 7v): the phone's questions whose answers
 * hold true here word for word (percent sums, the Recycle Bin), and Email
 * us to the support address. Answers written for the phone (Drive backup,
 * lock, More menu) are left off rather than shown wrong.
 */
@Composable
fun HelpPane(words: Words) {
    val c = LocalRoshanColors.current
    var open by remember { mutableStateOf<Int?>(null) }
    var kind by remember { mutableStateOf(0) }
    var description by remember { mutableStateOf("") }
    var empty by remember { mutableStateOf(false) }
    // Only answers that are true word for word on the computer: the others
    // name phone screens, Google Drive or the phone itself.
    val faqs = listOf(
        W.faq_q_percent to W.faq_a_percent,
        W.faq_q_delete to W.faq_a_delete
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PaneTitle(word(W.help_support), null)
        // The keyboard shortcuts, named with the app's own words for what they do.
        Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().background(c.surface, RoundedCornerShape(12.dp)).padding(14.dp)) {
            listOf(
                "F1" to word(W.nav_khata), "F2" to word(W.you_gave), "F3" to word(W.you_got),
                "Ctrl + N" to word(W.add_party), "Ctrl + P" to word(W.statement_pdf)
            ).forEach { (k, what) ->
                androidx.compose.foundation.layout.Row(Modifier.padding(vertical = 3.dp)) {
                    Text(k, Modifier.widthIn(min = 90.dp), color = c.brandGreenText, fontWeight = FontWeight.Bold)
                    Text(what, color = c.ink)
                }
            }
        }
        Text(word(W.help_questions), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        faqs.forEachIndexed { i, (q, a) ->
            Column(
                Modifier.widthIn(max = 760.dp).fillMaxWidth().background(c.surface, RoundedCornerShape(12.dp))
                    .clickable { open = if (open == i) null else i }.padding(14.dp)
            ) {
                Text(word(q), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                if (open == i) Text(word(a), Modifier.padding(top = 6.dp), color = c.inkSoft, fontSize = 14.sp)
            }
        }
        // Report a problem (the phone's ReportProblemActivity): the kind, the owner's own
        // words, and the app version and Windows version only — never a customer's
        // name, number or balance. It opens in the owner's own mail app to read first.
        Text(word(W.report_problem), Modifier.padding(top = 10.dp), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(word(W.report_what_kind), color = c.textMuted, fontSize = 13.sp)
        val kinds = listOf(W.report_kind_wrong_number, W.report_kind_lost, W.report_kind_crash, W.report_kind_confusing, W.report_kind_suggestion, W.report_kind_other)
        kinds.forEachIndexed { i, k ->
            val on = i == kind
            Text(
                word(k),
                Modifier.widthIn(max = 760.dp).fillMaxWidth()
                    .background(if (on) c.brandGreen else c.surface, RoundedCornerShape(10.dp))
                    .clickable { kind = i }.padding(horizontal = 12.dp, vertical = 8.dp),
                color = if (on) androidx.compose.ui.graphics.Color.White else c.ink, fontSize = 14.sp
            )
        }
        androidx.compose.material3.OutlinedTextField(
            description, { description = it; empty = false },
            label = { Text(word(W.report_describe)) }, placeholder = { Text(word(W.report_describe_help)) },
            modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(), minLines = 4
        )
        if (empty) Text(word(W.report_empty), color = c.owedToMe, fontSize = 13.sp)
        FillButton(word(W.report_send), c.gaveFill) {
            if (description.isBlank()) { empty = true; return@FillButton }
            val body = description.trim() + "\n\n" +
                "Roshan Khata for Windows " + (System.getProperty("jpackage.app-version") ?: "dev") +
                " \u00b7 " + System.getProperty("os.name") + " " + System.getProperty("os.version") +
                " \u00b7 Java " + System.getProperty("java.version")
            val subject = words.text(W.report_subject, words.text(kinds[kind]))
            fun enc(v: String) = URLEncoder.encode(v, "UTF-8").replace("+", "%20")
            runCatching { java.awt.Desktop.getDesktop().mail(URI("mailto:innovation313.support@gmail.com?subject=${enc(subject)}&body=${enc(body)}")) }
        }
        Text(word(W.help_still_stuck), Modifier.padding(top = 10.dp), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(word(W.help_contact_blurb), color = c.textMuted, fontSize = 13.sp)
        FillButton(word(W.help_email_us), c.brandGreen) {
            val subject = URLEncoder.encode(words.text(W.help_email_subject), "UTF-8").replace("+", "%20")
            runCatching { java.awt.Desktop.getDesktop().mail(URI("mailto:innovation313.support@gmail.com?subject=$subject")) }
        }
    }
}
