package com.innovation313.roshankhata.desktop

import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * This computer's own id for the book's list of the owner's devices
 * ([com.innovation313.roshankhata.data.Access.deviceId], 22 Oct 2026, the
 * review). It came from app.properties, a file anyone at the till can open in
 * Notepad: the owner's phone's id, copied out of a daily copy's owner_devices
 * into it, made a counter computer the owner's own — every right, no PIN.
 *
 * Now it is Windows' own machine id (MachineGuid, set when Windows was
 * installed; changing it takes an administrator and the registry editor),
 * kept only as a hash, so the list in the book and in every copy never names
 * the machine and an id read there cannot be typed back in anywhere.
 *
 * Null where Windows' id cannot be read (another system, a locked-down
 * computer): the computer then keeps the id it made, as before.
 */
object MachineId {

    private const val HEX = "0123456789abcdef"

    /** Windows' machine id as this app's device id, or null. Runs reg.exe once; call it off the window's thread. */
    fun read(): String? = runCatching {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return null
        // /reg:64: the 64-bit registry, where MachineGuid lives, whichever Java runs this.
        val p = ProcessBuilder("reg", "query", "HKLM\\SOFTWARE\\Microsoft\\Cryptography", "/v", "MachineGuid", "/reg:64")
            .redirectErrorStream(true)
            .start()
        if (!p.waitFor(10, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            return null
        }
        parse(p.inputStream.bufferedReader().readText())?.let { of(it) }
    }.getOrNull()

    /** The GUID in reg's answer ("MachineGuid    REG_SZ    3f6f…"), lower case, or null. */
    fun parse(regOutput: String): String? =
        Regex("""MachineGuid\s+REG_SZ\s+([0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12})""")
            .find(regOutput)?.groupValues?.get(1)?.lowercase()

    /** The id kept in the book for [guid]: "w" and 31 hex of a SHA-256 — never the GUID itself. */
    fun of(guid: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(("rk-device-v1:" + guid.lowercase()).toByteArray(Charsets.UTF_8))
        // Hex by hand: the app's own Locale.setDefault must never reach an id.
        val hex = StringBuilder()
        for (b in digest) { val v = b.toInt() and 0xff; hex.append(HEX[v shr 4]).append(HEX[v and 0x0f]) }
        return "w" + hex.substring(0, 31)
    }
}
