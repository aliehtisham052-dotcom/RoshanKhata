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
        // null from tryLock means another copy holds it; an IOException (a network share that cannot lock) means nobody can say,
        // and the owner must not be told "already open" then (8 Nov 2026 review, L139): the app runs without the guard.
        val lock = try { channel.tryLock() }
            catch (_: java.nio.channels.OverlappingFileLockException) { null }
            catch (_: java.io.IOException) { runCatching { channel.close() }; return Held(null, null) }
        if (lock == null) {
            runCatching { channel.close() }
            return null
        }
        return Held(channel, lock)
    }

    /** The box before any book or window exists: English, then the computer's language when it is one of the nine. */
    private val alreadyOpenLocal = mapOf(
        "ur" to "روشن کھاتہ پہلے سے کھلا ہے (یا بند ہو رہا ہے)۔ ٹاسک بار پر اس کی ونڈو دیکھیں۔",
        "ar" to "روشن كهاتا مفتوح بالفعل (أو يُغلق الآن). ابحث عن نافذته في شريط المهام.",
        "fa" to "روشن خاتا از قبل باز است (یا در حال بسته شدن است). پنجره‌اش را در نوار وظیفه ببینید.",
        "sd" to "روشن کاتو اڳ ۾ ئي کليل آهي (يا بند ٿي رهيو آهي). ٽاسڪ بار تي ان جي ونڊو ڏسو.",
        "hi" to "रोशन खाता पहले से खुला है (या बंद हो रहा है)। टास्कबार पर इसकी विंडो देखें।",
        "bn" to "রোশন খাতা আগে থেকেই খোলা আছে (বা বন্ধ হচ্ছে)। টাস্কবারে এর উইন্ডো দেখুন।",
        "in" to "Roshan Khata sudah terbuka (atau sedang menutup). Cari jendelanya di bilah tugas.",
        "id" to "Roshan Khata sudah terbuka (atau sedang menutup). Cari jendelanya di bilah tugas."
    )

    fun tellAlreadyOpen() {
        runCatching {
            val english = "Roshan Khata is already open (or still closing). Look for its window on the taskbar."
            val lang = java.util.Locale.getDefault().language
            // Urdu keeps its line even when the computer's language is something else: the shop's own language.
            val local = alreadyOpenLocal[lang] ?: alreadyOpenLocal.getValue("ur")
            JOptionPane.showMessageDialog(
                null,
                "$english\n\n$local",
                "Roshan Khata",
                JOptionPane.INFORMATION_MESSAGE
            )
        }
    }
}
