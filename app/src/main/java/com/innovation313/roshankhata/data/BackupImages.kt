package com.innovation313.roshankhata.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The image half of a Drive backup — the photos and marks that the text backup
 * deliberately leaves out because they are heavy and personal.
 *
 * This packs every image the app keeps into ONE zip, and unpacks it back into
 * place on restore. It is reached from a Drive backup with the owner's
 * "include images" switch on, and from a copy the owner chooses to send to a
 * helper's phone WITH photos ([packCopy]). The local text backup file never
 * carries any of this, by the owner's own instruction.
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
    private const val ROOT_QR = "payment_qr.png"
    private const val ROOT_SIGNATURE = "signature.png"
    private const val ROOT_STAMP = "stamp.png"
    private const val ROOT_LOGO = "logo.png"

    /** The book itself inside a helper's copy; see [packCopy]. */
    internal const val COPY_TEXT = "backup.txt"

    /**
     * Pack every image into a zip in the cache directory, or return null if
     * there is nothing to pack (no images at all — then there is no archive to
     * upload, and the owner is told images were skipped rather than an empty
     * file being sent).
     *
     * Cache, not files: this is a transient artefact that exists only to be
     * uploaded, and the system is free to reclaim it afterwards.
     */
    suspend fun pack(context: Context, dao: KhataDao): File? {
        val sources = collectFiles(context, dao)
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

    // ---------- A helper's copy: the book and its photos in one file ----------
    //
    // A helper's phone used to receive the text alone, so every customer was
    // a pair of initials and no bill could be shown at the counter. The copy
    // the owner sends can now carry the pictures too: ONE zip, the book as
    // [COPY_TEXT] and the images under the same names as the Drive archive.
    //
    // Two images never travel in it: the owner's SIGNATURE and the shop's
    // STAMP. A helper is given the book to read and payments to chase; the
    // marks that make a paper the owner's own stay on the owner's phone. A
    // read-only phone also refuses them from a Drive archive (see [targetFor]).

    /** How heavy the photos would make a helper's copy. 0 = there are none. */
    suspend fun copyImageBytes(context: Context, dao: KhataDao): Long =
        collectFiles(context, dao).filter { helperMayHold(it.first) }.sumOf { it.second.length() }

    /**
     * The owner's book and its photos as one file to send, or null if it
     * could not be written (a half-made copy is deleted, never sent).
     *
     * Written under cache/backups, the folder the FileProvider already shares
     * from. Older copies are cleared first: with photos these are large, and
     * yesterday's has no reader.
     */
    suspend fun packCopy(context: Context, dao: KhataDao, json: String): File? {
        val dir = File(context.cacheDir, "backups").apply { mkdirs() }
        dir.listFiles()?.filter { it.name.startsWith(COPY_PREFIX) }?.forEach { it.delete() }
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        val zip = File(dir, "$COPY_PREFIX$stamp.zip")
        return try {
            val sources = collectFiles(context, dao).filter { helperMayHold(it.first) }
            ZipOutputStream(FileOutputStream(zip).buffered()).use { out ->
                out.putNextEntry(ZipEntry(COPY_TEXT))
                out.write(json.toByteArray(Charsets.UTF_8))
                out.closeEntry()
                for ((entryName, file) in sources) {
                    out.putNextEntry(ZipEntry(entryName))
                    file.inputStream().buffered().use { it.copyTo(out) }
                    out.closeEntry()
                }
            }
            zip
        } catch (e: Exception) {
            zip.delete()
            null
        }
    }

    private const val COPY_PREFIX = "RoshanKhata_Copy_"

    /** What a helper's phone received: the book, and whether photos came with it. */
    class CopyFile(val text: String, val hasImages: Boolean)

    /**
     * Read a file the owner sent, whichever kind it is: the plain text backup
     * (as before, and as every older version of the app sends), or the zip
     * made by [packCopy]. The file is opened more than once — first to see
     * which kind it is, then to read it — so [open] must give a fresh stream
     * each time. Null when it cannot be read or holds no book.
     *
     * Only the text is held in memory. The photos stay in the file and are
     * streamed out later by [restore], so a large copy cannot exhaust a
     * small phone.
     */
    fun readCopy(open: () -> InputStream?): CopyFile? {
        val head = ByteArray(4)
        val got = open()?.use { input ->
            var n = 0
            while (n < head.size) {
                val r = input.read(head, n, head.size - n)
                if (r < 0) break
                n += r
            }
            n
        } ?: return null
        val isZip = got == 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() &&
            head[2] == 3.toByte() && head[3] == 4.toByte()
        if (!isZip) {
            val text = open()?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: return null
            return CopyFile(text, false)
        }
        var text: String? = null
        var images = false
        val stream = open() ?: return null
        ZipInputStream(stream.buffered()).use { zin ->
            var entry: ZipEntry? = zin.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    // readBytes() stops at the end of THIS entry, not the file.
                    if (entry.name == COPY_TEXT) text = zin.readBytes().toString(Charsets.UTF_8)
                    else images = true
                }
                zin.closeEntry()
                entry = zin.nextEntry
            }
        }
        return text?.let { CopyFile(it, images) }
    }

    /** The owner's signature and stamp stay on the owner's phone. */
    private fun helperMayHold(entryName: String): Boolean =
        entryName != ROOT_SIGNATURE && entryName != ROOT_STAMP

    /**
     * Remove every picture a read-only phone holds of the owner's book,
     * before a fresh copy is unpacked. No-op on a normal phone.
     *
     * Photos are filed by customer NUMBER, so a picture left behind from an
     * earlier copy — or from another shop's book — would sit on whichever
     * customer now carries that number: a stranger's face on a man's account.
     * A copy that arrives without photos therefore shows none, which is true.
     */
    fun clearViewerImages(context: Context) {
        if (!ViewerMode.isOn(context)) return
        PartyPhoto.folder(context).listFiles()?.forEach { it.delete() }
        billsDir(context).deleteRecursively()
        listOf(
            BusinessProfile.qrFile(context), BusinessProfile.signatureFile(context),
            BusinessProfile.stampFile(context), BusinessProfile.logoFile(context)
        ).forEach { it.delete() }
        BusinessProfile.setImageFlagsFromDisk(context)
        PartyPhoto.dropCaches()
    }

    /**
     * Where bill photos live for the book that is open.
     *
     * Every real business shares one folder (their paths are rows in each
     * shop's own database). A read-only phone's copy gets a folder of its
     * own, inside the viewer's "biz" folder: the owner's bill photos must
     * never land among this phone's own — a same-named file would be written
     * over — and [ViewerMode.leave] removes that folder whole, so nothing of
     * the owner's stays behind when the phone stops being a viewer.
     */
    private fun billsDir(context: Context): File =
        if (ViewerMode.isOn(context)) {
            File(File(context.filesDir, "biz" + Businesses.suffixFor(ViewerMode.VIEWER_ID)), BILLS_DIR)
        } else {
            File(context.filesDir, BILLS_DIR)
        }

    /**
     * Every image that belongs to the ACTIVE business, paired with the
     * relative path it takes in the zip.
     *
     * The zip's entry names stay canonical — party_photos/, bills/, and the
     * three root names — whichever business the archive was made from. The
     * archive describes WHAT each image is; WHOSE it is, is decided at
     * restore time by whichever business is open. That is what lets a
     * backup made from any shop restore into any shop, including a fresh
     * phone's Business 1.
     *
     * Sources, by contrast, are all business-scoped:
     * - the party folder and the three profile images resolve through the
     *   same per-business paths the app itself reads;
     * - bill photos live in one shared folder (their absolute paths are
     *   rows in each business's own database), so the active book's own
     *   entries are the allow-list — without it, one shop's backup would
     *   carry every OTHER shop's photographed bills too.
     */
    private suspend fun collectFiles(context: Context, dao: KhataDao): List<Pair<String, File>> {
        val out = mutableListOf<Pair<String, File>>()

        PartyPhoto.folder(context).listFiles()
            ?.filter { it.isFile }
            ?.forEach { out += "$PARTY_DIR/${it.name}" to it }

        val ownBills = (dao.entriesWithBillPhoto().map { it.billPhotoPath } +
            dao.supplierBillsWithPhoto().map { it.photoPath })
            .mapNotNull { it?.substringAfterLast('/') }
            .toSet()
        File(context.filesDir, BILLS_DIR).listFiles()
            ?.filter { it.isFile && it.name in ownBills }
            ?.forEach { out += "$BILLS_DIR/${it.name}" to it }

        val roots = listOf(
            ROOT_QR to BusinessProfile.qrFile(context),
            ROOT_SIGNATURE to BusinessProfile.signatureFile(context),
            ROOT_STAMP to BusinessProfile.stampFile(context),
            ROOT_LOGO to BusinessProfile.logoFile(context)
        )
        for ((name, f) in roots) {
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
    suspend fun restore(context: Context, dao: KhataDao, zipBytes: ByteArray): Int =
        restore(context, dao, zipBytes.inputStream())

    /**
     * The same, straight from a stream — for a helper's copy, whose photos
     * are read out of the file the owner sent without ever being held in
     * memory whole. Closes [input].
     */
    suspend fun restore(context: Context, dao: KhataDao, input: InputStream): Int {
        var written = 0

        ZipInputStream(input.buffered()).use { zin ->
            var entry: ZipEntry? = zin.nextEntry
            while (entry != null) {
                val name = entry.name
                // Guard against a zip entry trying to escape filesDir with ".."
                // or an absolute path — a backup we made cannot contain these,
                // but unpacking archive entries blindly is a classic trap.
                if (!entry.isDirectory && isSafeEntryName(name)) {
                    // Canonical entry name -> the ACTIVE business's own
                    // places. An archive from any shop, restored into any
                    // shop, lands in the folders that shop actually reads.
                    val target = targetFor(context, name)
                    if (target != null) {
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).buffered().use { zin.copyTo(it) }
                        written++
                    }
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
        val billsDir = billsDir(context)
        for (row in dao.entriesWithBillPhoto()) {
            val oldPath = row.billPhotoPath ?: continue
            val fileName = oldPath.substringAfterLast('/')
            val newPath = File(billsDir, fileName).absolutePath
            if (newPath != oldPath) dao.setBillPhotoPath(row.id, newPath)
        }
        // A cash bill's photo (v21): same folder, same by-name rule.
        for (row in dao.supplierBillsWithPhoto()) {
            val oldPath = row.photoPath ?: continue
            val newPath = File(billsDir, oldPath.substringAfterLast('/')).absolutePath
            if (newPath != oldPath) dao.setSupplierBillPhotoPath(row.id, newPath)
        }
    }

    /**
     * Where a canonical zip entry lands for the business that is open now.
     * An entry this method does not recognise is skipped, not written — an
     * archive is data, and unknown data does not get to choose a path.
     */
    private fun targetFor(context: Context, name: String): File? = when {
        name.startsWith("$PARTY_DIR/") ->
            File(PartyPhoto.folder(context), name.removePrefix("$PARTY_DIR/"))
        name.startsWith("$BILLS_DIR/") ->
            File(billsDir(context), name.removePrefix("$BILLS_DIR/"))
        // A read-only phone never holds the owner's signature or stamp, even
        // when the archive (the owner's own Drive backup) carries them.
        !helperMayHold(name) && ViewerMode.isOn(context) -> null
        name == ROOT_QR -> BusinessProfile.qrFile(context)
        name == ROOT_SIGNATURE -> BusinessProfile.signatureFile(context)
        name == ROOT_STAMP -> BusinessProfile.stampFile(context)
        name == ROOT_LOGO -> BusinessProfile.logoFile(context)
        else -> null
    }

    private fun isSafeEntryName(name: String): Boolean =
        !name.contains("..") && !name.startsWith("/") && !name.contains(":")
}
