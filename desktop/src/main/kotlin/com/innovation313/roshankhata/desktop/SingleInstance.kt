package com.innovation313.roshankhata.desktop

import java.io.File
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.StandardOpenOption
import javax.swing.JOptionPane

/**
 * One Roshan Khata at a time on this computer (21 Oct 2026, the critical
 * review). Nothing stopped a second copy: an owner who double-clicked the
 * shortcut again while the first was still checking the book got two
 * windows on one book. Each kept its own picture of the balances and stock,
 * each rewrote app.properties from its own memory — so an App Lock PIN or a
 * backup folder set in one window was dropped by the next save in the other
 * — and both wrote the same day's copy. Now the app holds a lock on a small
 * file in its own folder for as long as it runs; a second start finds the
 * lock taken, says so, and goes.
 */
object SingleInstance {

    /** The lock held while the app runs; empty (nothing to release) when the lock file could not be made. */
    class Held internal constructor(private val channel: FileChannel?, private val lock: FileLock?) {
        fun release() {
            runCatching { lock?.release() }
            runCatching { channel?.close() }
        }
    }

    /**
     * The lock, or null when another Roshan Khata already holds it. A lock
     * file that cannot even be opened (a read-only folder) must not keep the
     * owner out of the book: the app then runs without the guard, as before.
     */
    fun acquire(home: File): Held? {
        home.mkdirs()
        val channel = runCatching {
            FileChannel.open(File(home, "app.lock").toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        }.getOrNull() ?: return Held(null, null)
        val lock = runCatching { channel.tryLock() }.getOrNull()
        if (lock == null) {
            runCatching { channel.close() }
            return null
        }
        return Held(channel, lock)
    }

    /** Said before the window would open; in English and Urdu, as the words are not read yet. */
    fun tellAlreadyOpen() {
        runCatching {
            JOptionPane.showMessageDialog(
                null,
                "Roshan Khata is already open (or still closing). Look for its window on the taskbar.\n\n" +
                    "روشن کھاتہ پہلے سے کھلا ہے (یا بند ہو رہا ہے)۔ ٹاسک بار پر اس کی ونڈو دیکھیں۔",
                "Roshan Khata",
                JOptionPane.INFORMATION_MESSAGE
            )
        }
    }
}
