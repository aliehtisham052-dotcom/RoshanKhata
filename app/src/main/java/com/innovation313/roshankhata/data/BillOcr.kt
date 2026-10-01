package com.innovation313.roshankhata.data

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * The photo-to-text half of Scan bill: a picture of a supplier's bill in,
 * lines of text with their positions out. [BillScan] does the reading.
 *
 * On the phone, through Google Play Services' text model. The picture is not
 * uploaded anywhere and is not kept: it is decoded, read, and released.
 */
object BillOcr {

    sealed class Result {
        data class Read(val lines: List<OcrLine>) : Result()
        /** The picture could not be opened at all. */
        object NoImage : Result()
        /**
         * The reader itself failed: most often its model is still being
         * fetched by Play Services on a first use, or the phone has no Play
         * Services. Worth trying again; not the owner's photo at fault.
         */
        object Unavailable : Result()
    }

    /**
     * Long side the photo is decoded at. Big enough for a printed A4 bill's
     * small type, small enough that a 50-megapixel camera frame is never
     * held whole in memory (PhotoDecode samples it down while reading).
     */
    private const val EDGE = 1600

    suspend fun read(context: Context, photo: Uri): Result {
        val bitmap = withContext(Dispatchers.IO) {
            PhotoDecode.read(context, photo, EDGE, keepShortEdge = false)
        } ?: return Result.NoImage

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            suspendCancellableCoroutine { cont ->
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { text ->
                        val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                            val box = line.boundingBox ?: return@mapNotNull null
                            OcrLine(line.text, box.left, box.top, box.right, box.bottom)
                        }
                        if (cont.isActive) cont.resume(Result.Read(lines))
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(Result.Unavailable)
                    }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e                      // the screen went away; not a failure to report
        } catch (e: Exception) {
            Result.Unavailable
        } finally {
            // The bitmap is NOT recycled here: if the owner leaves mid-read the
            // reader may still be using it on its own thread, and a recycled
            // bitmap under it is a native crash. It is unreferenced once this
            // returns and the collector frees it.
            recognizer.close()
        }
    }
}
