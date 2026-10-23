package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.innovation313.roshankhata.desktop.gfx.PdfDocument
import com.innovation313.roshankhata.desktop.invoice.DocumentPrint
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.print.Printable
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * The pages of anything the app prints, whatever drew them (1.8.4): a Printable
 * laid out on A4 (statements, reports, tables, the card sheet) or one of the
 * phone's invoice designs, whose pages keep their own size (the thermal roll is
 * narrow). Sizes are in points; a page is drawn at any scale for the preview,
 * the PDF and the tests alike, so all three show the same sheet.
 */
class Sheets private constructor(
    val count: Int,
    private val sizeOf: (Int) -> Pair<Float, Float>,
    private val drawOn: (Int, Graphics2D) -> Unit
) {
    fun width(i: Int): Float = sizeOf(i).first
    fun height(i: Int): Float = sizeOf(i).second

    /** Page [i] on white, [scale] pixels per point. No alpha: a JPEG in the PDF has none. */
    fun picture(i: Int, scale: Float): BufferedImage {
        val img = BufferedImage(maxOf(1, (width(i) * scale).toInt()), maxOf(1, (height(i) * scale).toInt()), BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        try {
            g.color = Color.WHITE
            g.fillRect(0, 0, img.width, img.height)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.scale(scale.toDouble(), scale.toDouble())
            drawOn(i, g)
        } finally {
            g.dispose()
        }
        return img
    }

    companion object {
        /** Never more pages than this from one Printable: a sheet that never says "no such page" stops here. */
        private const val MOST = 2000

        fun of(p: Printable): Sheets = if (p is DocumentPrint) of(p.doc) else {
            val format = StatementPrint.a4()
            // Statements know their own page count (21 Oct 2026); only other sheets are counted by drawing.
            val known = when (p) { is StatementPrint -> p.pageCount(); is AllStatements -> p.pageCount(); else -> null }
            // Others are counted by drawing each page once on a scrap: a Printable only says it has no
            // more pages by being asked for one (Java's own printing finds the end the same way).
            val n = known ?: run {
                val scrap = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
                var c = 0
                while (c < MOST) {
                    val g = scrap.createGraphics()
                    val r = try { p.print(g, format, c) } finally { g.dispose() }
                    if (r != Printable.PAGE_EXISTS) break
                    c++
                }
                c
            }
            Sheets(n, { format.width.toFloat() to format.height.toFloat() }) { i, g -> p.print(g, format, i) }
        }

        fun of(doc: PdfDocument): Sheets = Sheets(
            doc.finished.size,
            { i -> doc.finished[i].info.pageWidth.toFloat() to doc.finished[i].info.pageHeight.toFloat() }
        ) { i, g -> doc.finished[i].canvas.replay(g) }
    }
}

/**
 * Sheets kept as PDF files in Documents\Roshan Khata. Each page goes in as a
 * picture at about 180 dpi (2.5 pixels a point), so the file looks exactly as
 * the screen and the printer show it, Urdu and the designs' artwork included;
 * the price is that its words cannot be selected (the owner's choice, 20 Oct).
 */
object PdfFiles {
    /** 3.5 pixels a point, about 250 dots an inch on paper: crisp text and cards (it was 2.5, 180 dpi). */
    const val SCALE = 3.5f

    /** Documents\Roshan Khata, made if missing; the home folder when Documents is not there. */
    fun folder(): File {
        val home = File(System.getProperty("user.home"))
        val docs = File(home, "Documents").takeIf { it.isDirectory } ?: home
        return File(docs, "Roshan Khata").apply { mkdirs() }
    }

    /** "INV-000001_2026-10-20.pdf"; a second one that day "…(2).pdf", never over the first. */
    fun next(title: String, dir: File = folder(), now: Date = Date()): File {
        val clean = title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ").replace(Regex("\\s+"), " ").trim()
            .ifEmpty { "Roshan Khata" }.take(80)
        val base = clean + "_" + SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(now)
        var file = File(dir, "$base.pdf")
        var k = 2
        while (file.exists()) file = File(dir, "$base ($k).pdf").also { k++ }
        return file
    }

    /** Every page as a picture on a page of its own size; written whole or not at all. */
    fun write(sheets: Sheets, file: File, scale: Float = SCALE) {
        val part = File(file.parentFile, file.name + ".part")
        // Pages kept in a temporary file, not in memory (21 Oct 2026): every customer's statement as one
        // PDF ran out of memory on a 4 GB laptop.
        PDDocument(org.apache.pdfbox.io.IOUtils.createTempFileOnlyStreamCache()).use { pdf ->
            for (i in 0 until sheets.count) {
                val w = sheets.width(i)
                val h = sheets.height(i)
                val page = PDPage(PDRectangle(w, h))
                pdf.addPage(page)
                val image = JPEGFactory.createFromImage(pdf, sheets.picture(i, scale), 0.92f)
                PDPageContentStream(pdf, page).use { it.drawImage(image, 0f, 0f, w, h) }
            }
            pdf.save(part)
        }
        if (file.exists()) file.delete()
        if (!part.renameTo(file)) { part.copyTo(file, overwrite = true); part.delete() }
    }
}

/** What the preview window shows: the sheet and its name. Set by [StatementPrint.show]. */
object Preview {
    class Request(val printable: Printable, val title: String)

    var request by mutableStateOf<Request?>(null)
        private set

    fun open(printable: Printable, title: String) { request = Request(printable, title) }
    fun close() { request = null }

    /** One drawing thread: a Printable is drawn by one caller at a time, as a printer would. */
    val drawing = Executors.newSingleThreadExecutor { r -> Thread(r, "sheet-drawing").apply { isDaemon = true } }.asCoroutineDispatcher()
}

/** The preview window, whenever a sheet was asked for. Placed once, in the book's window. */
@Composable
fun PreviewHost() {
    val req = Preview.request ?: return
    androidx.compose.runtime.key(req) {
        AppDialog(
            onCloseRequest = { Preview.close() },
            onPreviewKeyEvent = escCloses { Preview.close() },
            title = req.title,
            state = rememberDialogState(width = fitW(860.dp), height = fitH(900.dp))
        ) {
            PreviewContent(req)
        }
    }
}

@Composable
private fun PreviewContent(req: Preview.Request) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    var sheets by remember { mutableStateOf<Sheets?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var busy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<File?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    val failedWord = word(W.pc_pdf_failed, "%s")
    val savedWord = word(W.report_saved_to, "%s")
    LaunchedEffect(req) { sheets = withContext(Preview.drawing) { Sheets.of(req.printable) } }

    fun openFile(f: File) {
        // The PDF in Windows' own viewer (Edge, Acrobat...), to send on or read in full.
        Thread({ runCatching { java.awt.Desktop.getDesktop().open(f) } }, "pdf-open").start()
    }

    Column(Modifier.fillMaxSize().background(c.pageBg)) {
        Row(
            Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(req.title, Modifier.weight(1f), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            OutlineAction(onClick = { zoom = (zoom - 0.25f).coerceAtLeast(0.5f) }) { Text("−", color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            Text("${(zoom * 100).toInt()}%", color = c.inkSoft, fontSize = 13.sp)
            OutlineAction(onClick = { zoom = (zoom + 0.25f).coerceAtMost(2.5f) }) { Text("+", color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            OutlineAction(onClick = { StatementPrint.printDialog(req.printable, req.title) }) {
                Text(word(W.print_short), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
            OutlineAction(onClick = { Thread({ runCatching { java.awt.Desktop.getDesktop().open(PdfFiles.folder()) } }, "pdf-folder").start() }) {
                Text(word(W.pc_pdf_open_folder), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
            FillButton(word(W.pc_pdf_save), c.brandGreen, enabled = sheets != null && !busy) {
                val s = sheets ?: return@FillButton
                busy = true
                note = null
                scope.launch {
                    val file = PdfFiles.next(req.title)
                    val result = runCatching { withContext(Preview.drawing) { PdfFiles.write(s, file) } }
                    busy = false
                    result.onSuccess {
                        saved = file
                        note = savedWord.replace("%s", file.absolutePath)
                        openFile(file)
                    }.onFailure { note = failedWord.replace("%s", it.message ?: it.toString()) }
                }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        note?.let {
            Text(
                it,
                Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 16.dp, vertical = 6.dp)
                    .let { m -> saved?.let { f -> m.press { openFile(f) } } ?: m },
                color = if (saved != null) c.brandGreenText else c.owedToMe, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
        HorizontalDivider(color = Look.line)
        val s = sheets
        if (s == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(10.dp))
                    Text(word(W.pc_pdf_making), color = c.textMuted, fontSize = 13.sp)
                }
            }
        } else {
            val density = LocalDensity.current.density
            LazyColumn(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
            ) {
                items(s.count) { i ->
                    val w = s.width(i) * zoom
                    val h = s.height(i) * zoom
                    var page by remember(i, zoom) { mutableStateOf<ImageBitmap?>(null) }
                    LaunchedEffect(i, zoom) {
                        // Drawn as sharp as the screen shows it, one page at a time, only when scrolled to.
                        page = withContext(Preview.drawing) { s.picture(i, zoom * density).toComposeImageBitmap() }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(w.dp, h.dp).background(androidx.compose.ui.graphics.Color.White)
                                .border(1.dp, Look.line),
                            contentAlignment = Alignment.Center
                        ) {
                            val p = page
                            if (p == null) CircularProgressIndicator(Modifier.size(28.dp))
                            else Image(p, contentDescription = null, modifier = Modifier.size(w.dp, h.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(word(W.pdf_stmt_page, i + 1, s.count), color = c.textMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
