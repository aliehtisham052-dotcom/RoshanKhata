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
import com.google.android.filament.TextureSampler
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
 * One poster's 3D hero ([Hero]): its model, a camera, the studio light and a
 * sun that throws the hero's shadow.
 *
 * [attach] shows it live on a transparent TextureView laid over the poster,
 * moving at the screen's rate; [still] draws one frame off-screen for the
 * picture that is shared, always in the resting pose (see [Motion]).
 *
 * Main thread only — see [Studio].
 */
class HeroStage(
    context: Context,
    private val hero: Hero,
    private val look: Look = Look(),
    /** For a hero with a [Hero.face]: the shop's mark, colour and metal (see SealFace). */
    face: SealFace.Maps? = null
) {

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
    /** Each moving node with its rest pose and its motions, in the order they apply. */
    private val movers: List<Mover> = hero.motions.groupBy { it.node }.mapNotNull { (node, list) ->
        val entity = asset.getFirstEntityByName(node)
        val inst = if (entity == 0) 0 else engine.transformManager.getInstance(entity)
        if (inst == 0) {
            Log.w(TAG, "${hero.name}: no node \"$node\"")
            null
        } else {
            Mover(inst, engine.transformManager.getTransform(inst, FloatArray(16)), list)
        }
    }
    private val faceTextures = mutableListOf<Texture>()
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
        face?.let { applyFace(it) }
        pose(0f, live = false)
    }

    private class Mover(val instance: Int, val rest: FloatArray, val motions: List<Motion>)

    private fun project(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        view.setViewport(Viewport(0, 0, w, h))
        camera.setProjection(hero.fov, w.toDouble() / h, 0.1, 50.0, Camera.Fov.VERTICAL)
    }

    /**
     * The hero at [t] seconds. Each part's own motions are applied on top of
     * its rest pose, in the order the hero lists them. A clock reads the
     * phone's time when [live]; the shared picture shows the watchmakers'
     * 10:10, the hands framing the face.
     */
    private fun pose(t: Float, live: Boolean) {
        val tm = engine.transformManager
        val m = FloatArray(16)
        val tmp = FloatArray(16)
        val out = FloatArray(16)
        val now = if (live) java.util.Calendar.getInstance() else null
        val sec = now?.let { it.get(java.util.Calendar.SECOND) + it.get(java.util.Calendar.MILLISECOND) / 1000f } ?: 36f
        val min = now?.get(java.util.Calendar.MINUTE)?.toFloat()?.plus(sec / 60f) ?: (10f + sec / 60f)
        val hour = now?.get(java.util.Calendar.HOUR)?.toFloat()?.plus(min / 60f) ?: (10f + min / 60f)
        for (mover in movers) {
            android.opengl.Matrix.setIdentityM(m, 0)
            for (mo in mover.motions) {
                val tt = t + mo.phase
                android.opengl.Matrix.setIdentityM(tmp, 0)
                when (mo.kind) {
                    Motion.Kind.BOB ->
                        android.opengl.Matrix.translateM(tmp, 0, 0f, (sin(tt * 2 * PI / mo.period) * mo.amount).toFloat(), 0f)
                    Motion.Kind.SPIN ->
                        android.opengl.Matrix.rotateM(tmp, 0, mo.rest + (if (live) tt else 0f) * mo.amount, mo.axis.x, mo.axis.y, mo.axis.z)
                    Motion.Kind.SWAY ->
                        android.opengl.Matrix.rotateM(tmp, 0,
                            mo.rest + (if (live) (sin(tt * 2 * PI / mo.period) * mo.amount).toFloat() else 0f),
                            mo.axis.x, mo.axis.y, mo.axis.z)
                    // Clockwise seen from the front is a negative turn about +Z.
                    Motion.Kind.HOUR -> android.opengl.Matrix.rotateM(tmp, 0, -hour * 30f, 0f, 0f, 1f)
                    Motion.Kind.MINUTE -> android.opengl.Matrix.rotateM(tmp, 0, -min * 6f, 0f, 0f, 1f)
                    // The second hand ticks, as a quartz clock's does.
                    Motion.Kind.SECOND -> android.opengl.Matrix.rotateM(tmp, 0, -kotlin.math.floor(sec) * 6f, 0f, 0f, 1f)
                }
                if (mo.kind == Motion.Kind.BOB && !live) android.opengl.Matrix.setIdentityM(tmp, 0)
                android.opengl.Matrix.multiplyMM(out, 0, m, 0, tmp, 0)
                System.arraycopy(out, 0, m, 0, 16)
            }
            android.opengl.Matrix.multiplyMM(out, 0, mover.rest, 0, m, 0)
            tm.setTransform(mover.instance, out)
        }
    }

    /** Put the shop's mark on the hero's face material (its base colour and metal). */
    private fun applyFace(maps: SealFace.Maps) {
        val name = hero.face ?: return
        val sampler = TextureSampler(TextureSampler.MinFilter.LINEAR_MIPMAP_LINEAR,
            TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.CLAMP_TO_EDGE)
        val base = texture(maps.colour, srgb = true)
        val metal = texture(maps.metalRough, srgb = false)
        var found = false
        for (mi in asset.instance.materialInstances) {
            if (mi.name != name) continue
            mi.setParameter("baseColorMap", base, sampler)
            mi.setParameter("metallicRoughnessMap", metal, sampler)
            found = true
        }
        if (!found) Log.w(TAG, "${hero.name}: no material \"$name\"")
    }

    private fun texture(bmp: Bitmap, srgb: Boolean): Texture {
        var levels = 1
        while ((bmp.width shr levels) > 0 && (bmp.height shr levels) > 0) levels++
        val tex = Texture.Builder()
            .width(bmp.width).height(bmp.height).levels(levels)
            .sampler(Texture.Sampler.SAMPLER_2D)
            .format(if (srgb) Texture.InternalFormat.SRGB8_A8 else Texture.InternalFormat.RGBA8)
            // generateMipmaps refuses a texture not declared for it (found on the CI emulator, 11 Oct).
            .usage(Texture.Usage.DEFAULT or Texture.Usage.GEN_MIPMAPPABLE)
            .build(engine)
        val buf = ByteBuffer.allocateDirect(bmp.byteCount)
        bmp.copyPixelsToBuffer(buf)
        buf.rewind()
        tex.setImage(engine, 0, Texture.PixelBufferDescriptor(buf, Texture.Format.RGBA, Texture.Type.UBYTE))
        tex.generateMipmaps(engine)
        faceTextures += tex
        return tex
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
            pose(if (motion) (frameTimeNanos - startNanos) / 1e9f else 0f, live = motion)
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
     * the shared picture.
     */
    suspend fun still(w: Int, h: Int): Bitmap = suspendCancellableCoroutine { cont ->
        Log.d(TAG, "still: swapchain ${w}x$h")
        val sc = engine.createSwapChain(w, h, SwapChainFlags.CONFIG_TRANSPARENT or SwapChainFlags.CONFIG_READABLE)
        project(w, h)
        pose(0f, live = false)
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
            // Filament hands the rows back top row first, as a Bitmap wants them.
            // (They were flipped here until 11 Oct, which turned every shared
            // hero upside down; the CI pictures showed the crate under its plinth.)
            buffer.rewind()
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(buffer)
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
            // How the picture comes back, from Filament 1.72.1's source: readPixels
            // copies into a buffer behind a GPU fence; only driver.tick() checks the
            // fence, and only the Renderer's beginFrame and endFrame call tick; then
            // Engine.flush() hands the finished callback to our handler. So until
            // the picture is back: an empty frame on the still's own swapchain (two
            // ticks) and a flush, every 16 ms. Without it the callback came only
            // when something else happened to render — never, on the CI emulator
            // after the poster screen had used the engine, or on a phone with its
            // animations off (no live frames). Found 12 Oct.
            val pump = object : Runnable {
                override fun run() {
                    if (finished || destroyed) return
                    if (renderer.beginFrame(sc, 0L)) renderer.endFrame()
                    engine.flush()
                    handler.postDelayed(this, 16L)
                }
            }
            handler.post(pump)
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
        faceTextures.forEach { engine.destroyTexture(it) }
        faceTextures.clear()
    }
}

private const val TAG = "Poster3D"

/** How long [HeroStage.still] waits for the GPU once the frame is handed over. */
private const val WAIT_MS = 20_000L
