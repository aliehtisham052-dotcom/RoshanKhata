package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberDialogState
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word
import java.awt.FileDialog
import java.awt.Frame
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Photos on the computer (Phase 10): a customer's face for recognition and a
 * supplier bill's picture, the phone's two kinds. They stay on this computer:
 * nothing is uploaded, and — as on the phone — a customer's photo is never
 * printed on a statement.
 *
 * The text backup carries no photos (the owner's rule), so a book restored
 * from the phone has paths to the phone's files; those are shown as missing,
 * never as an error.
 */
object Photos {
    private val cache = object : LinkedHashMap<String, ImageBitmap?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap?>?) = size > 300
    }

    /**
     * One folder of faces per business (R26, 23 Oct 2026): party ids start at 1
     * in every book, so shop B's customer 5 wrote over shop A's customer 5.
     * Business 1 keeps the folder it always had (paths already stored stay
     * right); the others are party_photos_b<id>, the phone's own naming.
     */
    fun partyDir(home: File, businessId: Long) = File(home, if (businessId == 1L) "party_photos" else "party_photos_b$businessId")
    fun partyFile(home: File, businessId: Long, partyId: Long) = File(partyDir(home, businessId), "party_$partyId.jpg")
    fun billFile(home: File) = File(home, "bills/bill_${System.currentTimeMillis()}.jpg")

    /**
     * A merged-away customer's face goes to the one who stays (the phone's PartyPhoto.transferOnMerge; Marhala 4, the
     * audit: on the computer it stayed with the binned record), only when that one has none on this computer. File work
     * after the merge's own transaction; harmless to repeat. The binned record keeps its old path, read as missing.
     */
    suspend fun moveOnMerge(dao: com.innovation313.roshankhata.data.KhataDao, home: File, businessId: Long, loserIds: List<Long>, survivorId: Long) {
        val keep = dao.getParty(survivorId) ?: return
        if (keep.photoPath?.let { File(it).isFile } == true) return
        for (loserId in loserIds) {
            val from = dao.getParty(loserId)?.photoPath?.let { File(it) }?.takeIf { it.isFile } ?: continue
            val to = partyFile(home, businessId, survivorId)
            to.parentFile?.mkdirs()
            if (to.exists() && !to.delete()) return
            if (from.renameTo(to)) {
                synchronized(cache) { cache.keys.removeAll { it.startsWith(to.path) || it.startsWith(from.path) } }
                dao.updateParty(keep.copy(photoPath = to.absolutePath))
                return
            }
        }
    }

    /** The pictures a book points at: its entries' and supplier bills' photos and its customers' faces. */
    suspend fun referenced(dao: com.innovation313.roshankhata.data.KhataDao): Set<String> =
        (dao.entriesWithBillPhoto().map { it.billPhotoPath } + dao.supplierBillsWithPhoto().map { it.photoPath } + dao.partyPhotoPaths())
            .filterNotNull().filter { it.isNotBlank() }.toSet()

    /**
     * A permanent delete, then the pictures it left with no row pointing at them (the phone's BillPhoto.purgingOrphans;
     * Marhala 4, the audit: the computer's bin left them on disk). Compared by the paths this book pointed at before and
     * after, so a cascade (a customer taking his entries with him) is covered. A picture [others] still points at stays —
     * the bills folder is every business's — and nothing goes when another book cannot be read ([others] gives null).
     * Only a picture this app wrote is ever deleted ([ours]): a path that came in a backup may point anywhere.
     */
    suspend fun <T> purgingOrphans(
        dao: com.innovation313.roshankhata.data.KhataDao, home: File, others: suspend () -> Set<String>?, block: suspend () -> T
    ): T {
        val before = referenced(dao)
        val result = block()
        val gone = before - referenced(dao)
        if (gone.isNotEmpty()) {
            val elsewhere = runCatching { others() }.getOrNull() ?: return result
            for (path in gone - elsewhere) {
                val f = File(path)
                if (f.isFile && ours(home, f)) runCatching {
                    if (f.delete()) synchronized(cache) { cache.keys.removeAll { it.startsWith(f.path) } }
                }
            }
        }
        return result
    }

    /** True for a picture this app keeps for a book on this computer: in its bills folder or a business's faces folder. */
    internal fun ours(home: File, f: File): Boolean {
        val root = runCatching { home.canonicalFile }.getOrNull() ?: return false
        val dir = runCatching { f.canonicalFile.parentFile }.getOrNull() ?: return false
        if (dir.parentFile != root) return false
        return dir.name == "bills" || dir.name == "party_photos" || dir.name.startsWith("party_photos_b")
    }

    /** The owner picks a picture; null when cancelled. */
    fun pick(title: String): File? {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        startIn(dialog, "Pictures")
        dialog.setFilenameFilter { _, name -> name.lowercase().let { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") || it.endsWith(".bmp") } }
        dialog.file = "*.jpg;*.jpeg;*.png"
        dialog.isVisible = true
        val name = dialog.file ?: return null
        return File(dialog.directory, name)
    }

    /** [source] copied to [target] as a JPEG no larger than [max] on its long side. False when it is not a picture. */
    fun save(source: File, target: File, max: Int = 1600): Boolean {
        val img = runCatching { ImageIO.read(source) }.getOrNull() ?: return false
        val scale = minOf(1.0, max.toDouble() / maxOf(img.width, img.height))
        val w = (img.width * scale).toInt().coerceAtLeast(1)
        val h = (img.height * scale).toInt().coerceAtLeast(1)
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.color = java.awt.Color.WHITE
        g.fillRect(0, 0, w, h)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.drawImage(img, 0, 0, w, h, null)
        g.dispose()
        target.parentFile?.mkdirs()
        val tmp = File(target.path + ".tmp")
        if (!runCatching { ImageIO.write(out, "jpg", tmp) }.getOrDefault(false)) return false
        target.delete()
        synchronized(cache) { cache.keys.removeAll { it.startsWith(target.path) } }
        return tmp.renameTo(target)
    }

    /** The picture at [path] for the screen, or null when it is not on this computer. */
    fun bitmap(path: String?, max: Int = 0): ImageBitmap? {
        val p = path?.takeIf { it.isNotBlank() } ?: return null
        val f = File(p)
        val key = "$p|$max|${f.lastModified()}"
        synchronized(cache) { if (cache.containsKey(key)) return cache[key] }
        val bmp = runCatching {
            if (!f.isFile) return@runCatching null
            val img = ImageIO.read(f) ?: return@runCatching null
            if (max <= 0 || maxOf(img.width, img.height) <= max) img.toComposeImageBitmap()
            else {
                val s = max.toDouble() / maxOf(img.width, img.height)
                val small = BufferedImage((img.width * s).toInt().coerceAtLeast(1), (img.height * s).toInt().coerceAtLeast(1), BufferedImage.TYPE_INT_RGB)
                small.createGraphics().apply {
                    setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                    drawImage(img, 0, 0, small.width, small.height, null); dispose()
                }
                small.toComposeImageBitmap()
            }
        }.getOrNull()
        synchronized(cache) { cache[key] = bmp }
        return bmp
    }
}

/** A photo shown large, or the phone's "not on this device" line when its file is elsewhere. */
@Composable
fun PhotoDialog(title: String, path: String?, onClose: () -> Unit) {
    val bmp = Photos.bitmap(path, 1400)
    // In the app's frame (Marhala 4), the picture as large as the window allows, whole (never cropped).
    AppDialog(onCloseRequest = onClose, title = title, state = rememberDialogState(width = fitW(900.dp), height = fitH(760.dp))) {
        DialogFrame(title, onClose = onClose, scroll = false, footer = { FluentButton(word(W.close), onClose, kind = ButtonKind.STANDARD) }) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = androidx.compose.ui.Alignment.Center) {
                if (bmp != null) Image(bmp, contentDescription = title, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                else Text(word(W.bill_photo_missing), color = com.innovation313.roshankhata.screens.LocalRoshanColors.current.inkSoft)
            }
        }
    }
}
