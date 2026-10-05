package com.innovation313.roshankhata.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.graphics.Bitmap
import com.innovation313.roshankhata.data.EscPos
import java.util.UUID

/**
 * Sends a picture to a Bluetooth receipt printer the owner has ALREADY paired
 * in the phone's own Bluetooth settings. The app never scans for devices, so
 * it needs no location and no scan permission — only BLUETOOTH_CONNECT on
 * Android 12+ (BLUETOOTH, capped at API 30, below that). Nothing leaves the
 * phone except the picture, to the owner's own printer.
 *
 * Callers check the permission first; the functions here are marked
 * @SuppressLint("MissingPermission") for that reason.
 */
object ReceiptPrinter {

    /** The serial-port profile every common ESC/POS Bluetooth printer speaks. */
    private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun adapter(context: Context) =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    @SuppressLint("MissingPermission")
    fun paired(context: Context): List<BluetoothDevice> =
        adapter(context)?.bondedDevices?.toList().orEmpty()
            .sortedBy { (it.name ?: it.address).lowercase() }

    @SuppressLint("MissingPermission")
    fun label(device: BluetoothDevice): String = device.name?.takeIf { it.isNotBlank() } ?: device.address

    /** The picture scaled to the roll's width, as printer bytes. */
    fun bytes(picture: Bitmap): ByteArray {
        val w = EscPos.WIDTH_58MM
        val h = (picture.height.toLong() * w / picture.width).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(picture, w, h, true)
        val px = IntArray(w * h)
        scaled.getPixels(px, 0, w, 0, 0, w, h)
        if (scaled !== picture) scaled.recycle()
        return EscPos.raster(px, w, h)
    }

    /** Connect, or close the socket and rethrow — a failed attempt must not hold the link. */
    @SuppressLint("MissingPermission")
    private fun open(s: BluetoothSocket): BluetoothSocket = try {
        s.connect(); s
    } catch (e: Exception) {
        try { s.close() } catch (_: Exception) {}
        throw e
    }

    /**
     * Blocking: call off the main thread. Tries the secure socket first and
     * the insecure one second — many cheap printers only answer the latter.
     */
    @SuppressLint("MissingPermission")
    fun send(device: BluetoothDevice, data: ByteArray) {
        var socket: BluetoothSocket? = null
        try {
            socket = try {
                open(device.createRfcommSocketToServiceRecord(SPP))
            } catch (first: Exception) {
                open(device.createInsecureRfcommSocketToServiceRecord(SPP))
            }
            val out = socket!!.outputStream
            // Small pieces with a breath between: a printer's buffer is a few kilobytes.
            var at = 0
            while (at < data.size) {
                val n = minOf(1024, data.size - at)
                out.write(data, at, n)
                out.flush()
                at += n
                Thread.sleep(20)
            }
            Thread.sleep(300) // let the last band reach the head before the link drops
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }
}
