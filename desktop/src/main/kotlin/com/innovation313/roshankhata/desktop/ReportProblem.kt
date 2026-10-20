package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words
import com.innovation313.roshankhata.words.word
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A crash on the computer written down (20 Oct), the phone's CrashLog: any
 * thread's uncaught exception goes to `logs/errors.log` in the app's folder
 * with the time and the app version, kept to its last 200 KB. Nothing is
 * sent anywhere; the Report a problem window shows it to the owner, who
 * decides what leaves the computer. Compose's own window error dialog is
 * untouched: this only makes sure the record survives it.
 */
object CrashLog {
    private const val KEEP = 200 * 1024

    fun file(home: File) = File(home, "logs/errors.log")

    fun install(home: File) {
        val before = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { write(home, "Thread ${t.name}", e) }
            before?.uncaughtException(t, e)
        }
    }

    @Synchronized
    fun write(home: File, where: String, e: Throwable) {
        val f = file(home)
        f.parentFile?.mkdirs()
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date())
        val entry = "=== $stamp  v${UpdateCheck.installed() ?: "dev"}  $where\n$sw\n"
        val old = if (f.isFile) f.readText(Charsets.UTF_8) else ""
        val text = (old + entry).let { if (it.length > KEEP) it.takeLast(KEEP) else it }
        f.writeText(text, Charsets.UTF_8)
    }

    /** The newest record, or null when nothing has ever crashed. */
    fun latest(home: File): String? {
        val f = file(home)
        if (!f.isFile) return null
        val text = f.readText(Charsets.UTF_8)
        val i = text.lastIndexOf("=== ")
        return if (i < 0) null else text.substring(i).take(6000)
    }
}

/** The computer's side of the phone's ProblemReport.deviceContext: version, Windows, Java. Nothing of the book. */
fun pcContext(): String = buildString {
    appendLine("---")
    appendLine("App version: ${UpdateCheck.installed() ?: "dev"} (Windows)")
    appendLine("Windows: ${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}")
    appendLine("Java: ${System.getProperty("java.version")}")
    appendLine("---")
}

/**
 * The phone's Report a problem on the computer: what kind, what happened,
 * then the whole report shown as it will be sent — the owner reads every
 * line — and two ways out: the computer's mail program (a mailto link with
 * everything filled in) or the clipboard, to paste into WhatsApp or any
 * mail. The support address is written out for a computer with no mail set up.
 */
@Composable
fun ReportProblemDialog(words: Words, home: File, onClose: () -> Unit) {
    val c = LocalRoshanColors.current
    val kinds = listOf(W.report_kind_wrong_number, W.report_kind_crash, W.report_kind_lost, W.report_kind_confusing, W.report_kind_suggestion, W.report_kind_other)
    var kind by remember { mutableStateOf(kinds.last()) }
    var text by remember { mutableStateOf("") }
    var told by remember { mutableStateOf<String?>(null) }
    val crash = remember { runCatching { CrashLog.latest(home) }.getOrNull() }

    fun body(): String = buildString {
        appendLine(text.trim())
        appendLine()
        append(pcContext())
        if (crash != null) { appendLine(); appendLine(words.text(W.report_crash_included)); appendLine(crash) }
    }
    fun subject() = words.text(W.report_subject, words.text(kind))

    DialogWindow(onCloseRequest = onClose, onPreviewKeyEvent = escCloses(onClose), title = word(W.report_problem), state = rememberDialogState(width = 620.dp, height = 760.dp)) {
        Box(Modifier.fillMaxSize().background(c.surface)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(word(W.report_problem), color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(word(W.report_problem_sub), color = c.textMuted, fontSize = 13.sp)
                Text(word(W.report_what_kind), color = c.ink, fontWeight = FontWeight.Bold)
                kinds.forEach { k ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(kind == k, { kind = k })
                        Text(word(k), color = c.ink, fontSize = 14.sp)
                    }
                }
                Text(word(W.report_describe), color = c.ink, fontWeight = FontWeight.Bold)
                Outlined(text, { text = it }, placeholder = { Text(word(W.report_hint)) }, minLines = 4, modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp))
                Text(word(W.report_pc_privacy), color = c.textMuted, fontSize = 12.sp)
                Text(pcContext() + (crash?.let { "\n" + words.text(W.report_crash_included) + "\n" + it.take(1200) } ?: ""),
                    color = c.textMuted, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val empty = word(W.report_empty)
                    val noMail = word(W.report_no_mail_pc)
                    FillButton(word(W.report_open_mail), c.brandGreen) {
                        if (text.isBlank()) { told = empty; return@FillButton }
                        val uri = "mailto:innovation313.support@gmail.com?subject=" + enc(subject()) + "&body=" + enc(body())
                        told = if (runCatching { java.awt.Desktop.getDesktop().mail(URI(uri)) }.isSuccess) null else noMail
                    }
                    TextButton({
                        if (text.isBlank()) { told = empty; return@TextButton }
                        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(subject() + "\n\n" + body()), null) }
                        told = words.text(W.report_copied)
                    }) { Text(word(W.report_copy), color = c.brandGreenText) }
                    FluentButton(word(W.close), onClose, kind = ButtonKind.STANDARD)
                }
                told?.let { Text(it, color = c.textMuted, fontSize = 13.sp) }
            }
        }
    }
}

/** RFC 3986 escaping for a mailto part: spaces as %20, newlines as %0A (URLEncoder's + would arrive as plus signs). */
private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20").replace("%0D%0A", "%0A")
