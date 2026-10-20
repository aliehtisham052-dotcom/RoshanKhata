package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.innovation313.roshankhata.data.WifiSync
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phone to computer over the shop's WiFi (20 Oct), the computer's half: a
 * window with a QR code. While it is open the computer listens on one port
 * for a phone that scanned this code (Backup → Send to computer); the book
 * that arrives is kept in this computer's Roshan Khata folder and handed to
 * the usual restore confirmation, so nothing is replaced until the owner
 * says yes. Closing the window stops listening. See data/WifiSync.
 */
object PhoneSync {

    /** This computer's addresses on the local network, WiFi and cable first, virtual adapters last; at most four. */
    fun lanAddresses(): List<String> = runCatching {
        val virtualWords = listOf("virtual", "vmware", "vbox", "hyper-v", "vethernet", "wsl", "docker", "bluetooth", "loopback")
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual && !it.isPointToPoint }
            .sortedBy { n -> if (virtualWords.any { w -> w in (n.displayName ?: n.name).lowercase() }) 1 else 0 }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress }
            .mapNotNull { it.hostAddress }
            .distinct()
            .take(4)
    }.getOrDefault(emptyList())

    fun computerName(): String = runCatching { InetAddress.getLocalHost().hostName }.getOrNull().orEmpty().take(40)

    /** The pairing as a black-on-white QR picture of [size] pixels. */
    fun qr(text: String, size: Int = 520): BufferedImage {
        val hints = mapOf(EncodeHintType.MARGIN to 2, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val img = BufferedImage(m.width, m.height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until m.height) for (x in 0 until m.width) img.setRGB(x, y, if (m.get(x, y)) 0x000000 else 0xFFFFFF)
        return img
    }

    /** Where a book from the phone is kept before the owner confirms it: this computer's own folder, never the shop's. */
    fun saveIncoming(home: File, book: ByteArray): File {
        val dir = File(home, "from-phone").apply { mkdirs() }
        val name = "RoshanKhata_FromPhone_" + SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.ENGLISH).format(Date()) + ".txt"
        val file = File(dir, name)
        val tmp = File(dir, "$name.tmp")
        tmp.writeBytes(book)
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        // The last five are kept; older ones go, so the folder does not fill with whole books.
        dir.listFiles { f -> f.name.startsWith("RoshanKhata_FromPhone_") && f.name.endsWith(".txt") }
            ?.sortedByDescending { it.name }?.drop(5)?.forEach { it.delete() }
        return file
    }
}

/** The window. [onBook] gets the saved file of a book that arrived whole; the caller shows the restore confirmation. */
@Composable
fun PhoneSyncDialog(words: Words, home: File, onClose: () -> Unit, onBook: (File) -> Unit) {
    val c = LocalRoshanColors.current
    val secret = remember { WifiSync.newSecret() }
    val hosts = remember { PhoneSync.lanAddresses() }
    val server = remember { runCatching { ServerSocket(0) }.getOrNull() }
    var stopped by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(words.text(if (hosts.isEmpty()) W.wifi_pc_no_network else W.wifi_pc_waiting)) }
    val qr: ImageBitmap? = remember {
        if (server == null || hosts.isEmpty()) null
        else runCatching {
            PhoneSync.qr(WifiSync.pairingText(WifiSync.Pairing(hosts, server.localPort, secret, PhoneSync.computerName()))).toComposeImageBitmap()
        }.getOrNull()
    }

    DisposableEffect(Unit) {
        onDispose { stopped = true; runCatching { server?.close() } }
    }
    LaunchedEffect(server) {
        val s = server ?: run { status = words.text(W.wifi_pc_failed); return@LaunchedEffect }
        if (hosts.isEmpty()) return@LaunchedEffect
        val book = withContext(Dispatchers.IO) { runCatching { WifiSync.receive(s, secret) { stopped } }.getOrNull() }
        if (book != null && !stopped) {
            val file = withContext(Dispatchers.IO) { runCatching { PhoneSync.saveIncoming(home, book) }.getOrNull() }
            if (file != null) onBook(file) else status = words.text(W.wifi_pc_failed)
        }
    }

    DialogWindow(onCloseRequest = onClose, title = word(W.wifi_pc_title), state = rememberDialogState(width = 560.dp, height = 820.dp)) {
        Box(Modifier.fillMaxSize().background(c.surface)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(word(W.wifi_pc_title), color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(word(W.wifi_pc_steps), color = c.ink, fontSize = 14.sp)
                qr?.let { Image(it, contentDescription = word(W.wifi_pc_title), modifier = Modifier.size(320.dp)) }
                Text(status, color = c.brandGreenText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                if (hosts.isNotEmpty() && server != null) {
                    Text(hosts.joinToString("  ·  ") + "  :" + server.localPort, color = c.textMuted, fontSize = 12.sp)
                }
                Text(word(W.wifi_pc_firewall), color = c.textMuted, fontSize = 12.sp)
                TextButton(onClose) { Text(word(W.close), color = c.brandGreenText) }
            }
        }
    }
}
