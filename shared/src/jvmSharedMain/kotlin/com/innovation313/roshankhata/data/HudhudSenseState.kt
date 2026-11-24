package com.innovation313.roshankhata.data

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Whether Hudhud's learnt sense is there (24 Nov 2026, stage 7 item F3).
 *
 * The sense is a model the app ships as an asset (`hudhud/sense.bin`) and reads on the first question. When that read fails — the
 * asset missing from a build, a corrupt file, a decoder that throws — [HudhudSense] swallows it (its loader is `runCatching`, so a
 * bad model reads as "no sense") and Hudhud goes on with his rules alone. That is a real mode (the shared tests run in it, and the
 * rules read about half of the held-out sentences on their own against 91% with the sense), but it is a far weaker one, and nobody
 * was ever told: not the log, not a bug report, not the owner's check log. A phone that lost its model looked exactly like one
 * that had it, only dumber.
 *
 * [check] is the one place that notices. [Hudhud.understand] calls it on every sentence (it costs a volatile read once the model is
 * loaded), and the first time the sense is found missing it says why once — to [sink], which the platforms point at their log — and
 * [mode] reads [Mode.RULES_ONLY] from then on, for the platform to show or record. When the sense is there again the mode goes
 * back; the log line is said once for the process, not once per sentence.
 *
 * [reading] wraps the shipped loader so that the reason is the loader's own ("the asset is not in this build", "reading it
 * threw …") and not just "it did not load".
 */
object HudhudSenseState {

    /** UNCHECKED: no sentence has been read yet. WITH_SENSE: the model is loaded. RULES_ONLY: it is not, and he reads by his rules alone. */
    enum class Mode { UNCHECKED, WITH_SENSE, RULES_ONLY }

    @Volatile
    private var current: Mode = Mode.UNCHECKED

    @Volatile
    private var lost: String? = null

    private val told = AtomicBoolean(false)

    /** Where the one line goes (the phone: Log.w, the computer: its errors.log). Null: nowhere (the shared tests). */
    @Volatile
    var sink: ((String) -> Unit)? = null

    /** The mode as of the last [check]. */
    val mode: Mode get() = current

    /** Why the sense is missing, when it is: the loader's own words, else that the model did not load. Null otherwise. */
    val reason: String? get() = if (current == Mode.RULES_ONLY) lost ?: NOT_LOADED else null

    /** The sense is missing and he reads by his rules alone. */
    val rulesOnly: Boolean get() = current == Mode.RULES_ONLY

    /**
     * [read], watched: gives its bytes unchanged and notes why when it gives none or throws (a throw becomes "no bytes", as
     * [HudhudSense.useModel] has always treated it). Pass this to `HudhudSense.useModel` in place of a bare `runCatching`.
     */
    fun reading(source: String, read: () -> ByteArray?): () -> ByteArray? = {
        try {
            read().also { if (it == null || it.isEmpty()) lost = "$source is not in this build" }
        } catch (e: Exception) {
            lost = "reading $source threw ${e.javaClass.simpleName}"
            null
        }
    }

    /**
     * Looks whether the sense is there now, records the mode and, the first time it is missing, says so once. Safe on any thread,
     * cheap once the model is loaded; the first call after [HudhudSense.useModel] reads the model itself (as the first question does).
     */
    fun check(): Mode {
        val ready = HudhudSense.ready
        current = if (ready) Mode.WITH_SENSE else Mode.RULES_ONLY
        if (ready) {
            lost = null
        } else if (told.compareAndSet(false, true)) {
            sink?.invoke("Hudhud's sense could not be loaded (${lost ?: NOT_LOADED}); he reads by his rules alone")
        }
        return current
    }

    /** Tests: forget what was said, so the next missing sense is told again. */
    internal fun forgetForTest() {
        told.set(false)
        current = Mode.UNCHECKED
        lost = null
        sink = null
    }

    private const val NOT_LOADED = "the model did not load"
}
