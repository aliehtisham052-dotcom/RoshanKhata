package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.innovation313.roshankhata.data.DocActions
import com.innovation313.roshankhata.data.DocKind
import com.innovation313.roshankhata.data.DocRefused
import com.innovation313.roshankhata.data.DocStatus
import com.innovation313.roshankhata.data.EntryItem
import com.innovation313.roshankhata.data.Invoice
import com.innovation313.roshankhata.data.InvoiceItem
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.NoteDocument
import com.innovation313.roshankhata.desktop.invoice.Context
import com.innovation313.roshankhata.desktop.invoice.DocumentPrint
import com.innovation313.roshankhata.desktop.invoice.InvoiceDesigns
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words
import com.innovation313.roshankhata.words.word
import java.awt.print.Printable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Documents page's words and sheets on the computer (29 Oct 2026, the
 * documents' second part): the kind pills, the status tag, how a refusal is
 * said, and the two print-only notes drawn through the invoice designs.
 */

/** The word for a document's kind ([DocKind.wordKey]) in the app's language. */
@Composable
@ReadOnlyComposable
fun docKindWord(kind: String): String = word(DocKind.wordKey(kind))

/** The word for a document's status ([DocStatus.wordKey]) in the app's language. */
@Composable
@ReadOnlyComposable
fun docStatusWord(status: String): String = word(DocStatus.wordKey(status))

/**
 * The status as a small tag beside a document's number — none for OPEN (the
 * ordinary state says nothing), green for ACCEPTED, grey for CONVERTED (it
 * lives on as its next step), red for CANCELLED.
 */
@Composable
fun DocStatusTag(status: String) {
    val c = LocalRoshanColors.current
    when (status) {
        DocStatus.ACCEPTED -> Tag(docStatusWord(status), Look.accent, Look.accentTint)
        DocStatus.CONVERTED -> Tag(docStatusWord(status), c.inkSoft, Look.subtle)
        DocStatus.CANCELLED -> Tag(docStatusWord(status), c.owedToMe, Look.getTint)
        else -> Unit
    }
}

/**
 * The kinds the pill row offers, in the chain's order: All, then quotation,
 * sales order, challan and invoice. The proforma is left out of the row to
 * keep it short on a laptop (it is in the Add picker and shows under All).
 */
val DOC_PILLS: List<String?> = listOf(null, DocKind.QUOTATION, DocKind.SALES_ORDER, DocKind.CHALLAN, DocKind.INVOICE)

/**
 * A document write's refusal in the owner's words: a document no longer live
 * is [W.doc_locked] (change the new one); anything else is the app's generic
 * "not saved" with what the data layer said, as every other failed write.
 */
fun docFailText(words: Words, e: Throwable): String {
    val refused = e as? DocRefused
    val key = refused?.let { DocActions.refusalWordKey(it.why) }
    return if (key != null) words.text(key) else failText(words, e)
}

/**
 * The two print-only notes as the sheet the preview shows, saves and prints
 * (StatementPrint.show): the shop's chosen design through the phone's
 * drawing code, else the plain layout. Nothing is written — a credit note is
 * its return and a debit note its khata entry ([NoteDocument]).
 */
object NoteSheets {
    /** The design a note is drawn in: the shop's own default is 1; a note has no saved row to carry one. */
    const val TEMPLATE = 1

    /** A customer's return ([returnId]) as a credit note, or null when the return is gone. */
    suspend fun creditNote(dao: KhataDao, returnId: Long, designs: Context, plain: (Invoice, List<InvoiceItem>) -> Printable): Pair<Printable, String>? {
        val ret = dao.getReturn(returnId)?.takeIf { !it.isDeleted } ?: return null
        val items = dao.returnItems(returnId)
        val party = ret.partyId?.let { dao.getParty(it) }
        val (inv, lines) = NoteDocument.creditNote(ret, items, party, designs.words.text(W.pos_walk_in))
        return sheet(inv.copy(templateId = dao.newestDocumentTemplate() ?: TEMPLATE), lines, designs, plain) to inv.invoiceNumber
    }

    /** Goods sent back to a supplier (the "I gave" [entry] with its [lines]) as a debit note. */
    suspend fun debitNote(dao: KhataDao, entry: LedgerEntry, lines: List<EntryItem>, designs: Context, plain: (Invoice, List<InvoiceItem>) -> Printable): Pair<Printable, String> {
        val party = dao.getParty(entry.partyId)
        val (inv, rows) = NoteDocument.debitNote(entry, lines, party)
        return sheet(inv.copy(templateId = dao.newestDocumentTemplate() ?: TEMPLATE), rows, designs, plain) to inv.invoiceNumber
    }

    private fun sheet(inv: Invoice, lines: List<InvoiceItem>, designs: Context, plain: (Invoice, List<InvoiceItem>) -> Printable): Printable =
        InvoiceDesigns.pages(designs, inv, lines)?.let { DocumentPrint(it) } ?: plain(inv, lines)
}

/**
 * A document's chain (3 Nov 2026, the owner's mockup "Documents — the chain"; the phone shows it as the convert's
 * sheet): one disc per step across the width with the step's kind and number under it — the steps it came
 * through with a check, itself with a dot, the steps still to come as rings — and the lines between. [play] plays
 * the step just taken once: the line into it fills, the step it came from takes its check, it arrives.
 */
@Composable
fun DocChainStrip(chain: List<com.innovation313.roshankhata.data.DocChain.Node>, play: Boolean, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    if (chain.size < 2) return
    val c = LocalRoshanColors.current
    val current = chain.indexOfLast { it.number != null }.coerceAtLeast(0)
    val playing = play && current > 0
    val t = androidx.compose.runtime.remember(chain) { androidx.compose.animation.core.Animatable(if (playing) 0f else 440f) }
    androidx.compose.runtime.LaunchedEffect(chain, playing) {
        if (playing) {
            t.snapTo(0f)
            t.animateTo(440f, androidx.compose.animation.core.tween(440, easing = androidx.compose.animation.core.LinearEasing))
        }
    }
    val green = Look.accent
    val onGreen = Look.onAccent
    val waiting = c.textMuted.copy(alpha = 0.55f)
    val lineBase = Look.line
    androidx.compose.foundation.layout.Column(modifier.fillMaxWidth()) {
        androidx.compose.foundation.Canvas(androidx.compose.ui.Modifier.fillMaxWidth().height(28.dp)) {
            fun seg(a: Float, b: Float) = ((t.value - a) / (b - a)).coerceIn(0f, 1f)
            val n = chain.size
            val slot = size.width / n
            fun x(i: Int): Float {
                val fromStart = slot * (i + 0.5f)
                return if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) size.width - fromStart else fromStart
            }
            val cy = size.height / 2f
            val r = 11.dp.toPx()
            val half = 1.5.dp.toPx()
            for (i in 0 until n - 1) {
                val a = x(i); val b = x(i + 1)
                val dir = if (b > a) 1f else -1f
                val start = a + dir * r
                val end = b - dir * r
                drawRect(lineBase, androidx.compose.ui.geometry.Offset(minOf(start, end), cy - half), androidx.compose.ui.geometry.Size(kotlin.math.abs(end - start), 2 * half))
                val k = when {
                    i < current - 1 -> 1f
                    i == current - 1 -> DeskMotion.Standard.transform(seg(0f, 280f))
                    else -> 0f
                }
                if (k > 0f) {
                    val to = start + (end - start) * k
                    drawRect(green, androidx.compose.ui.geometry.Offset(minOf(start, to), cy - half), androidx.compose.ui.geometry.Size(kotlin.math.abs(to - start), 2 * half))
                }
            }
            val sw = 2.5.dp.toPx()
            fun check(cx: Float, k: Float) {
                if (k <= 0f) return
                val p1 = androidx.compose.ui.geometry.Offset(cx - 4.6f.dp.toPx(), cy + 0.4f.dp.toPx())
                val p2 = androidx.compose.ui.geometry.Offset(cx - 1.6f.dp.toPx(), cy + 3.4f.dp.toPx())
                val p3 = androidx.compose.ui.geometry.Offset(cx + 4.6f.dp.toPx(), cy - 3f.dp.toPx())
                val ab = (p2 - p1).getDistance(); val bc = (p3 - p2).getDistance()
                val d = (ab + bc) * k
                val cap = androidx.compose.ui.graphics.StrokeCap.Round
                if (d <= ab) drawLine(onGreen, p1, p1 + (p2 - p1) * (d / ab), sw, cap)
                else { drawLine(onGreen, p1, p2, sw, cap); drawLine(onGreen, p2, p2 + (p3 - p2) * ((d - ab) / bc), sw, cap) }
            }
            val ringStroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
            for (i in 0 until n) {
                val cx = x(i)
                val centre = androidx.compose.ui.geometry.Offset(cx, cy)
                when {
                    i < current -> {
                        drawCircle(green, r, centre)
                        if (i == current - 1) {
                            val dot = 1f - DeskMotion.Standard.transform(seg(100f, 220f))
                            if (dot > 0f) drawCircle(onGreen, 4.dp.toPx(), centre, alpha = dot)
                            check(cx, DeskMotion.Standard.transform(seg(140f, 320f)))
                        } else check(cx, 1f)
                    }
                    i == current -> {
                        val pop = if (current == 0) 1f else DeskMotion.EmphasizedDecelerate.transform(seg(220f, 440f))
                        if (pop < 1f) drawCircle(waiting, r - 1.dp.toPx(), centre, style = ringStroke)
                        drawCircle(green, r * (0.85f + 0.15f * pop), centre, alpha = pop)
                        drawCircle(onGreen, 4.dp.toPx(), centre, alpha = pop)
                    }
                    else -> drawCircle(waiting, r - 1.dp.toPx(), centre, style = ringStroke)
                }
            }
        }
        androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.fillMaxWidth()) {
            chain.forEachIndexed { i, node ->
                androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.weight(1f), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    androidx.compose.material3.Text(
                        docKindWord(node.kind), color = if (i <= current) c.ink else c.textMuted, fontSize = 12.sp,
                        fontWeight = if (i == current) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    node.number?.let {
                        androidx.compose.material3.Text(com.innovation313.roshankhata.ui.Format.ltr(it), color = c.textMuted, fontSize = 11.sp, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

