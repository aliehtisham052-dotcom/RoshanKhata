package com.innovation313.roshankhata.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

/**
 * A customer's signature on one entry (6 Oct 2026): proof for the day
 * someone says "I never took that". Drawn with a finger on the entry's screen.
 *
 * Filed by ENTRY number, one folder per shop (entry ids repeat across shops,
 * as party ids do — see PartyPhoto), so no database column is needed and the
 * image backup carries it like any other picture. Kept on this phone and in
 * the owner's own backup only.
 */
object EntrySignature {

    internal const val DIR = "entry_signatures"

    fun folder(context: Context): File {
        val id = Businesses.active(context).id
        val name = if (id == 1L) DIR else "${DIR}_b$id"
        return File(context.filesDir, name).apply { mkdirs() }
    }

    fun file(context: Context, entryId: Long): File = File(folder(context), "sig_$entryId.png")

    fun load(context: Context, entryId: Long): Bitmap? =
        file(context, entryId).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }

    fun save(context: Context, entryId: Long, bitmap: Bitmap) {
        val f = file(context, entryId)
        val tmp = File(f.parentFile, f.name + ".tmp")
        FileOutputStream(tmp).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (!tmp.renameTo(f)) { tmp.copyTo(f, overwrite = true); tmp.delete() }
    }
}
