package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
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

    fun partyFile(home: File, partyId: Long) = File(home, "party_photos/party_$partyId.jpg")
    fun billFile(home: File) = File(home, "bills/bill_${System.currentTimeMillis()}.jpg")

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
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            Box(Modifier.fillMaxWidth()) {
                if (bmp != null) Image(bmp, contentDescription = title, modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp))
                else Text(word(W.bill_photo_missing))
            }
        },
        confirmButton = { FluentButton(word(W.close), onClose, kind = ButtonKind.STANDARD) }
    )
}
