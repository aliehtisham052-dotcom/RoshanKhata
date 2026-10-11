package com.innovation313.roshankhata.ui.poster3d

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import com.google.android.filament.Camera
import com.google.android.filament.EntityManager
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.SwapChain
import com.google.android.filament.SwapChainFlags
import com.google.android.filament.Texture
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.PI
import kotlin.math.sin

/**
 * One poster's 3D hero: its model, a camera, the studio light and a sun
 * that throws the hero's shadow onto its plinth.
 *
 * [attach] shows it live on a transparent TextureView laid over the poster,
 * turning at the screen's rate; [still] draws one frame off-screen for the
 * picture that is shared. The shared picture is always the resting pose
 * (t = 0), so the owner sends exactly what he saw when the preview settled.
 *
 * Main thread only — see [Studio].
 */
class HeroStage(context: Context, private val hero: Hero, private val look: Look = Look()) {

    /**
     * What the frame uses. Everything is on in the app; the on-device probe
     * switches parts off one at a time to find which one a GPU cannot take.
     */
    data class Look(
        val model: Boolean = true,
        val studioLight: Boolean = true,
        val sun: Boolean = true,
        val shadows: Boolean = true,
        val msaa: Boolean = true,
        val fxaa: Boolean = true,
        val translucent: Boolean = true
    ) {
        companion object {
            fun named(name: String?): Look = when (name) {
                "empty" -> Look(false, false, false, false, false, false, false)
                "model" -> Look(true, false, false, false, false, false, false)
                "translucent" -> Look(true, false, false, false, false, false, true)
                "ibl" -> Look(true, true, false, false, false, false, false)
                "sun" -> Look(true, false, true, false, false, false, false)
                "shadows" -> Look(true, false, true, true, false, false, false)
                "msaa" -> Look(true, false, false, false, true, false, false)
                "fxaa" -> Look(true, false, false, false, false, true, false)
                // Everything the CI emulator's GPU survives (see Studio.isAndroidEmulator).
                "emulator" -> Look(true, true, true, false, true, false, false)
                else -> Look()
            }
        }
    }


    private val engine = Studio.engine
    private val renderer: Renderer = engine.createRenderer()
    private val scene = engine.createScene()
    private val cameraEntity = EntityManager.get().create()
    private val camera: Camera = engine.createCamera(cameraEntity)
    private val view: View = engine.createView()
    private val light = Studio.studioLight()
    private val sun = EntityManager.get().create()
    private val asset = Studio.load(context, hero.asset)
    private val spinEntity = asset.getFirstEntityByName(hero.spinNode)
    private val motion = try {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    } catch (e: Exception) {
        true
    }

    private var uiHelper: UiHelper? = null
    private var swapChain: SwapChain? = null
    private var liveW = 0
    private var liveH = 0
    private var running = false
    private var startNanos = 0L
    private var destroyed = false
    /** A frame is owed (new surface or size). With animations off, only owed frames are drawn. */
    private var dirty = true

    init {
        if (look.studioLight) scene.setIndirectLight(light)
        LightManager.Builder(LightManager.Type.SUN)
            .color(1.0f, 0.95f, 0.86f)
            .intensity(hero.sunLux)
            .direction(0.35f, -1.0f, -0.55f)
            .sunAngularRadius(1.6f)
            .castShadows(look.shadows)
            .build(engine, sun)
        if (look.sun) scene.addEntity(sun)
        if (look.model) scene.addEntities(asset.entities)
        view.setShadowingEnabled(look.shadows)
        view.setScene(scene)
        view.setCamera(camera)
        view.setBlendMode(if (look.translucent) View.BlendMode.TRANSLUCENT else View.BlendMode.OPAQUE)
        view.setAntiAliasing(if (look.fxaa) View.AntiAliasing.FXAA else View.AntiAliasing.NONE)
        view.setMultiSampleAntiAliasingOptions(View.MultiSampleAntiAliasingOptions().apply {
            enabled = look.msaa
            sampleCount = 4
        })
        renderer.setClearOptions(Renderer.ClearOptions().apply {
            clear = true
            clearColor = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
        })
        camera.setExposure(16f, 1f / 125f, 100f)
        camera.lookAt(
            hero.eye[0].toDouble(), hero.eye[1].toDouble(), hero.eye[2].toDouble(),
            hero.target[0].toDouble(), hero.target[1].toDouble(), hero.target[2].toDouble(),
            0.0, 1.0, 0.0
        )
        pose(0f)
    }

    private fun project(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        view.setViewport(Viewport(0, 0, w, h))
        camera.setProjection(hero.fov, w.toDouble() / h, 0.1, 50.0, Camera.Fov.VERTICAL)
    }

    /** The hero at [t] seconds: a slow turn and a gentle float. */
    private fun pose(t: Float) {
        if (spinEntity == 0) return
        val tm = engine.transformManager
        val inst = tm.getInstance(spinEntity)
        if (inst == 0) return
        val m = FloatArray(16)
        android.opengl.Matrix.setIdentityM(m, 0)
        android.opengl.Matrix.translateM(m, 0, 0f, (sin(t * 2 * PI / 3.6) * hero.bob).toFloat(), 0f)
        android.opengl.Matrix.rotateM(m, 0, hero.restYaw + t * hero.degreesPerSecond, 0f, 1f, 0f)
        tm.setTransform(inst, m)
    }

    private val choreographer = Choreographer.getInstance()
    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            choreographer.postFrameCallback(this)
            if (startNanos == 0L) startNanos = frameTimeNanos
            // With the phone's animations off nothing moves, so nothing is redrawn
            // either: the GPU (a software one on a test emulator) rests.
            if (!motion && !dirty) return
            pose(if (motion) (frameTimeNanos - startNanos) / 1e9f else 0f)
            val sc = swapChain ?: return
            if (uiHelper?.isReadyToRender == true && renderer.beginFrame(sc, frameTimeNanos)) {
                renderer.render(view)
                renderer.endFrame()
                dirty = false
            }
        }
    }

    /** Show the hero live on [target], which must sit over the poster with a clear background. */
    fun attach(target: TextureView) {
        detach()
        target.isOpaque = false
        val helper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
        helper.setOpaque(false)
        helper.setRenderCallback(object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(surface, helper.swapChainFlags)
                dirty = true
            }

            override fun onDetachedFromSurface() {
                swapChain?.let {
                    engine.destroySwapChain(it)
                    engine.flushAndWait()
                }
                swapChain = null
            }

            override fun onResized(width: Int, height: Int) {
                liveW = width
                liveH = height
                project(width, height)
                dirty = true
            }
        })
        helper.attachTo(target)
        uiHelper = helper
        running = true
        startNanos = 0L
        choreographer.postFrameCallback(frame)
    }

    fun detach() {
        running = false
        choreographer.removeFrameCallback(frame)
        uiHelper?.detach()
        uiHelper = null
    }

    /**
     * One frame of the resting hero, [w]×[h], with a clear background, for
     * the shared picture. Rows come back bottom-up from the GPU and are
     * turned the right way up here.
     */
    suspend fun still(w: Int, h: Int): Bitmap = suspendCancellableCoroutine { cont ->
        Log.d(TAG, "still: swapchain ${w}x$h")
        val sc = engine.createSwapChain(w, h, SwapChainFlags.CONFIG_TRANSPARENT or SwapChainFlags.CONFIG_READABLE)
        project(w, h)
        pose(0f)
        val buffer = ByteBuffer.allocateDirect(w * h * 4)
        val handler = Handler(Looper.getMainLooper())
        var finished = false
        // A GPU that never hands the pixels back must not hold the share button
        // (or a test) forever: after WAIT_MS the caller gets an error and draws
        // the flat hero instead. The late callback, if it ever comes, is ignored.
        // The clock starts once the frame is handed over, and the main thread is
        // not blocked meanwhile: on the CI emulator the first frame alone took
        // 5.3 s (shaders compiling), and a cheap phone may be as slow.
        val watchdog = Runnable {
            if (!finished) {
                finished = true
                Log.w(TAG, "readPixels did not return within $WAIT_MS ms")
                engine.destroySwapChain(sc)
                if (cont.isActive) cont.resumeWithException(IllegalStateException("The GPU did not return the picture"))
            }
        }
        val done = Runnable {
            if (finished) return@Runnable
            finished = true
            Log.d(TAG, "still: pixels returned")
            handler.removeCallbacks(watchdog)
            engine.destroySwapChain(sc)
            val bytes = ByteArray(w * h * 4)
            buffer.rewind()
            buffer.get(bytes)
            val flipped = ByteArray(bytes.size)
            val row = w * 4
            for (y in 0 until h) System.arraycopy(bytes, (h - 1 - y) * row, flipped, y * row, row)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(ByteBuffer.wrap(flipped))
            if (cont.isActive) cont.resume(bmp)
        }
        var started = false
        for (attempt in 0 until 4) {
            if (renderer.beginFrame(sc, 0L)) {
                Log.d(TAG, "still: frame begun (attempt $attempt), rendering")
                renderer.render(view)
                Log.d(TAG, "still: rendered, reading pixels")
                renderer.readPixels(0, 0, w, h,
                    Texture.PixelBufferDescriptor(buffer, Texture.Format.RGBA, Texture.Type.UBYTE, 1, 0, 0, w, handler, done))
                renderer.endFrame()
                // The frame is recorded; the live view gets its own size back at
                // once, so its next frames are not drawn at the still's size.
                project(liveW, liveH)
                started = true
                break
            }
            engine.flushAndWait()
        }
        if (started) {
            Log.d(TAG, "still: frame handed to the GPU")
            engine.flush()
            handler.postDelayed(watchdog, WAIT_MS)
        } else {
            finished = true
            handler.removeCallbacks(watchdog)
            engine.destroySwapChain(sc)
            project(liveW, liveH)
            cont.resumeWithException(IllegalStateException("The GPU did not take a frame"))
        }
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        detach()
        engine.flushAndWait()
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyIndirectLight(light)
        engine.destroyEntity(sun)
        EntityManager.get().destroy(sun)
        engine.destroyCameraComponent(cameraEntity)
        EntityManager.get().destroy(cameraEntity)
        Studio.destroyAsset(asset)
    }
}

/**
 * The heroes and how each is framed. Numbers are in the model's own units
 * (store/poster3d/make_models.py builds them); the camera looks down a little
 * so the top of the crate and the plinth's face both show.
 */
private const val TAG = "Poster3D"
private const val WAIT_MS = 20_000L

enum class Hero(
    val asset: String,
    val spinNode: String,
    val eye: FloatArray,
    val target: FloatArray,
    val fov: Double,
    val restYaw: Float,
    val degreesPerSecond: Float,
    val bob: Double,
    val sunLux: Float
) {
    CRATE(
        asset = "poster3d/naya_maal.glb",
        spinNode = "box",
        eye = floatArrayOf(0f, 0.95f, 4.2f),
        target = floatArrayOf(0f, -0.28f, 0f),
        fov = 30.0,
        restYaw = 35f,
        degreesPerSecond = 36f,
        bob = 0.06,
        sunLux = 70_000f
    )
}
