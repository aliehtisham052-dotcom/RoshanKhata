package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words
import com.innovation313.roshankhata.words.word
import java.net.URI
import java.net.URLEncoder

/**
 * Help on the computer (KMP Phase 7v; Marhala 4, part 10 — the page frame): the keyboard's shortcuts, Ctrl+F
 * among them; the phone's questions whose answers hold true here word for word (percent sums, the Recycle Bin);
 * Report a problem as the one full window ([onReport]: the kind, the owner's words, everything sent shown first,
 * mail or copy — the page's own second, weaker form is gone); the support address written out for a computer
 * with no mail program; and the app's version. Answers written for the phone (Drive backup, lock, More menu) are
 * left off rather than shown wrong.
 */
@Composable
fun HelpPane(words: Words, onReport: () -> Unit = {}) {
    val c = LocalRoshanColors.current
    var open by remember { mutableStateOf<Int?>(null) }
    // Only answers that are true word for word on the computer: the others
    // name phone screens, Google Drive or the phone itself.
    val faqs = listOf(
        W.faq_q_percent to W.faq_a_percent,
        W.faq_q_delete to W.faq_a_delete
    )
    FluentPage(word(W.help_support), null) {
        // The keyboard shortcuts, named with the app's own words for what they do.
        Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().card().padding(14.dp)) {
            listOf(
                "F1" to word(W.nav_khata), "F2" to word(W.you_gave), "F3" to word(W.you_got),
                "Ctrl + F" to word(W.search_parties), "Ctrl + N" to word(W.add_party), "Ctrl + P" to word(W.statement_pdf)
            ).forEach { (k, what) ->
                androidx.compose.foundation.layout.Row(Modifier.padding(vertical = 3.dp)) {
                    Text(Format.ltr(k), Modifier.widthIn(min = 90.dp), color = Look.accent, fontWeight = FontWeight.Bold)
                    Text(what, color = c.ink)
                }
            }
        }
        Text(word(W.help_questions), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        faqs.forEachIndexed { i, (q, a) ->
            Column(
                Modifier.widthIn(max = 760.dp).fillMaxWidth().card()
                    .press(Look.card) { open = if (open == i) null else i }.padding(14.dp)
            ) {
                Text(word(q), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                if (open == i) Text(word(a), Modifier.padding(top = 6.dp), color = c.inkSoft, fontSize = 14.sp)
            }
        }
        // Report a problem (the phone's ReportProblemActivity): the kind, the owner's own words, and the app
        // version and Windows version only — never a customer's name, number or balance. One window for it.
        Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(word(W.report_problem), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(word(W.report_problem_sub), color = c.textMuted, fontSize = 13.sp)
            FluentButton(word(W.report_problem), onReport, kind = ButtonKind.ACCENT)
        }
        Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(word(W.help_still_stuck), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(word(W.help_contact_blurb), color = c.textMuted, fontSize = 13.sp)
            // The address itself, for a computer with no mail program to open.
            Text(Format.ltr(SUPPORT_EMAIL), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            FluentButton(word(W.help_email_us), {
                val subject = URLEncoder.encode(words.text(W.help_email_subject), "UTF-8").replace("+", "%20")
                runCatching { java.awt.Desktop.getDesktop().mail(URI("mailto:$SUPPORT_EMAIL?subject=$subject")) }
            }, kind = ButtonKind.STANDARD)
        }
        Text(word(W.about_version, System.getProperty("jpackage.app-version") ?: "dev"), color = c.textMuted, fontSize = 12.sp)
    }
}

/** The app's support address (the owner's rule: support mail goes here). */
private const val SUPPORT_EMAIL = "innovation313.support@gmail.com"
