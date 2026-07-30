package com.innovation313.roshankhata.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The image half of a Drive backup — the photos and marks that the text backup
 * deliberately leaves out because they are heavy and personal.
 *
 * This packs every image the app keeps into ONE zip, and unpacks it back into
 * place on restore. It is only ever reached from a Drive backup with the
 * owner's "include images" switch on; the local text file never carries any of
 * this, by the owner's own instruction.
 *
 * WHAT IS PACKED, and where it lives on disk:
 *   party_photos/party_<id>.jpg   — a customer's recognition thumbnail
 *   bills/bill_<timestamp>.jpg    — a photographed bill, pointed at by an
 *                                   entry's billPhotoPath (an ABSOLUTE path —
 *                                   see the restore note about re-mapping)
 *   payment_qr.png                — the shop's payment QR
 *   signature.png                 — the owner's signature
 *   stamp.png                     — the shop's stamp
 *
 * The zip mirrors those relative paths exactly, so unpacking is just "write
 * each entry back under filesDir at the path its name already gives". Nothing
 * about which customer or which bill is encoded anywhere but the file name,
 * which is the same name the rest of the app already looks them up by.
 */
object BackupImages {

    private const val PARTY_DIR = "party_photos"
    private const val BILLS_DIR = "bills"
    private val ROOT_FILES = listOf("payment_qr.png", "signature.png", "stamp.png")

    /**
     * Pack every image into a zip in the cache directory, or return null if
     * there is nothing to pack (no images at all — then there is no archive to
     * upload, and the owner is told images were skipped rather than an empty
     * file being sent).
     *
     * Cache, not files: this is a transient artefact that exists only to be
     * uploaded, and the system is free to reclaim it afterwards.
     */
    fun pack(context: Context): File? {
        val sources = collectFiles(context)
        if (sources.isEmpty()) return null

        val dir = File(context.cacheDir, "image_backup").apply { mkdirs() }
        val zip = File(dir, "images.zip")

        return try {
            ZipOutputStream(FileOutputStream(zip).buffered()).use { out ->
                for ((entryName, file) in sources) {
                    out.putNextEntry(ZipEntry(entryName))
                    file.inputStream().buffered().use { it.copyTo(out) }
                    out.closeEntry()
                }
            }
            zip
        } catch (e: Exception) {
            // A half-written zip must never be uploaded as a backup. Drop it.
            zip.delete()
            null
        }
    }

    /** Every image on disk, paired with the relative path it takes in the zip. */
    private fun collectFiles(context: Context): List<Pair<String, File>> {
        val out = mutableListOf<Pair<String, File>>()

        File(context.filesDir, PARTY_DIR).listFiles()
            ?.filter { it.isFile }
            ?.forEach { out += "$PARTY_DIR/${it.name}" to it }

        File(context.filesDir, BILLS_DIR).listFiles()
            ?.filter { it.isFile }
            ?.forEach { out += "$BILLS_DIR/${it.name}" to it }

        for (name in ROOT_FILES) {
            val f = File(context.filesDir, name)
            if (f.exists()) out += name to f
        }

        return out
    }

    /**
     * Unpack a downloaded image zip back into place, then repair the one thing
     * that does not survive a move between phones: an entry's billPhotoPath.
     *
     * The path stored on each entry is absolute and was written for the phone
     * that took the backup. On any other phone the directory prefix differs, so
     * after the files are back the paths must be re-pointed at where they now
     * live — matched by FILE NAME, which is stable. Every non-null path is
     * rewritten to filesDir/bills/<same-name>; an entry whose photo file did
     * not come back in the zip has its name preserved but simply will not load,
     * which is honest (the record says a photo existed; the file is gone) and
     * never worse than before.
     *
     * The three Business Profile image flags are set from what actually landed
     * on disk, via [BusinessProfile.setImageFlagsFromDisk] — a flag can never
     * end up true with no file behind it.
     *
     * Must run AFTER the text restore, because it reads and updates the very
     * entries the text restore has just rewritten. The caller sequences this.
     *
     * @return the number of image files written back.
     */
    suspend fun restore(context: Context, dao: KhataDao, zipBytes: ByteArray): Int {
        var written = 0

        ZipInputStream(zipBytes.inputStream().buffered()).use { zin ->
            var entry: ZipEntry? = zin.nextEntry
            while (entry != null) {
                val name = entry.name
                // Guard against a zip entry trying to escape filesDir with ".."
                // or an absolute path — a backup we made cannot contain these,
                // but unpacking archive entries blindly is a classic trap.
                if (!entry.isDirectory && isSafeEntryName(name)) {
                    val target = File(context.filesDir, name)
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).buffered().use { zin.copyTo(it) }
                    written++
                }
                zin.closeEntry()
                entry = zin.nextEntry
            }
        }

        remapBillPhotoPaths(context, dao)
        BusinessProfile.setImageFlagsFromDisk(context)

        return written
    }

    /**
     * Re-point every entry's billPhotoPath at the current phone's bills folder,
     * keeping the same file name. Done by name so it is deterministic — there
     * is no guessing which entry a file belongs to, the name already says.
     */
    private suspend fun remapBillPhotoPaths(context: Context, dao: KhataDao) {
        val billsDir = File(context.filesDir, BILLS_DIR)
        for (row in dao.entriesWithBillPhoto()) {
            val oldPath = row.billPhotoPath ?: continue
            val fileName = oldPath.substringAfterLast('/')
            val newPath = File(billsDir, fileName).absolutePath
            if (newPath != oldPath) dao.setBillPhotoPath(row.id, newPath)
        }
    }

    private fun isSafeEntryName(name: String): Boolean =
        !name.contains("..") && !name.startsWith("/") && !name.contains(":")
}
