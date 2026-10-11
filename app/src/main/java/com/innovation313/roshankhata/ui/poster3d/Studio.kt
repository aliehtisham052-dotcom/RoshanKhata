package com.innovation313.roshankhata.ui.poster3d

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.util.Log
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.Texture
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The one Filament engine the posters' 3D heroes share (11 Oct).
 *
 * Filament is Google's physically based renderer: gold reflects a studio,
 * a glossy plinth shows its highlight, an edge catches the light — the look
 * a flat Canvas drawing cannot give. It runs on the GPU, so the hero turns
 * at the screen's own rate.
 *
 * The studio light is a dark room with a warm softbox overhead and two strip
 * lights, built in code (store/poster3d/make_env.py) and prefiltered by
 * Filament's own cmgen into assets/poster3d/studio_ibl.ktx. Its irradiance
 * spherical harmonics are below, copied from the same cmgen run.
 *
 * Every call happens on the main thread: Filament's Java objects are not
 * thread-safe, and the engine does its heavy work on its own render thread
 * anyway. If the phone cannot run it (no OpenGL ES 3.0, a missing native
 * library), [available] says no and the posters draw their heroes flat.
 */
object Studio {

    private const val TAG = "Poster3D"

    /** cmgen's irradiance for studio_ibl.ktx: 9 bands × RGB, pre-scaled. */
    private val SH = floatArrayOf(
        0.628030f, 0.596639f, 0.514248f,
        0.720666f, 0.680821f, 0.595977f,
        0.230869f, 0.191480f, 0.168671f,
        0.030540f, 0.046721f, 0.073494f,
        0.015781f, 0.019485f, 0.025639f,
        0.270893f, 0.250696f, 0.219152f,
        -0.103289f, -0.090106f, -0.078189f,
        0.025889f, 0.032267f, 0.043185f,
        -0.145832f, -0.138433f, -0.122793f
    )
    const val IBL_INTENSITY = 30_000f

    private var tried = false
    private var engineOrNull: Engine? = null
    private var materials: UbershaderProvider? = null
    private var loader: AssetLoader? = null
    private var resources: ResourceLoader? = null
    private var reflections: Texture? = null

    val engine: Engine get() = engineOrNull ?: error("Studio not started")

    /**
     * The GL driver this phone would give Filament, e.g. "Adreno (TM) 610 |
     * OpenGL ES 3.2 V@0502.0". Read once through a throwaway 1×1 context.
     */
    val glDriver: String by lazy { readGlDriver() }

    /**
     * The Android emulator's legacy GL translator ("Google SwiftShader",
     * OpenGL ES 3.0, -gpu swiftshader_indirect). Filament does not survive it
     * (checked on the CI emulator, 11 Oct): lit materials come out flat white,
     * the FXAA and translucent-blit shaders fail to compile and abort the app,
     * and shadow maps take the whole emulator down. The same emulator on
     * ANGLE (-gpu swangle_indirect, OpenGL ES 3.1) renders the full poster
     * correctly, as do real phone GPUs, so only this one driver gets flat heroes.
     */
    val isLegacySwiftShader: Boolean by lazy {
        glDriver.contains("SwiftShader", ignoreCase = true) && !glDriver.contains("ANGLE", ignoreCase = true)
    }

    private fun readGlDriver(): String {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        var context = EGL14.EGL_NO_CONTEXT
        var surface = EGL14.EGL_NO_SURFACE
        return try {
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) return ""
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            val wanted = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
            )
            if (!EGL14.eglChooseConfig(display, wanted, 0, configs, 0, 1, count, 0) || count[0] == 0) return ""
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
            surface = EGL14.eglCreatePbufferSurface(display, configs[0],
                intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return ""
            "${GLES20.glGetString(GLES20.GL_RENDERER)} | ${GLES20.glGetString(GLES20.GL_VERSION)}"
        } catch (t: Throwable) {
            ""
        } finally {
            // The display stays initialised: it is shared with Filament and the UI.
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
        }
    }

    private const val EGL_OPENGL_ES3_BIT = 0x40

    /** True when the posters should show their 3D heroes on this phone. */
    fun available(context: Context): Boolean {
        if (isLegacySwiftShader) {
            Log.i(TAG, "legacy SwiftShader translator ($glDriver): posters draw flat")
            return false
        }
        return engineReady(context)
    }

    /**
     * True once the engine is up; false on a phone that cannot start it.
     * Unlike [available] this ignores [isLegacySwiftShader], so the on-device
     * test can still check the parts of the frame that driver does take.
     */
    fun engineReady(context: Context): Boolean {
        if (!tried) {
            tried = true
            try {
                Gltfio.init()
                val e = Engine.create()
                engineOrNull = e
                materials = UbershaderProvider(e)
                loader = AssetLoader(e, materials!!, EntityManager.get())
                resources = ResourceLoader(e)
                reflections = Ktx.cubemap(e, context.assets.open("poster3d/studio_ibl.ktx").use { it.readBytes() })
            } catch (t: Throwable) {
                Log.w(TAG, "3D unavailable, posters draw flat", t)
                shutdown()
            }
        }
        return engineOrNull != null
    }

    /** A fresh light for one scene (each scene owns its IndirectLight). */
    fun studioLight(): IndirectLight =
        IndirectLight.Builder()
            .reflections(reflections!!)
            .irradiance(3, SH)
            .intensity(IBL_INTENSITY)
            .build(engine)

    /** Load a .glb from assets with its textures; the caller adds its entities to a scene. */
    fun load(context: Context, path: String): FilamentAsset {
        val bytes = context.assets.open(path).use { it.readBytes() }
        val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes)
        buffer.rewind()
        val asset = loader!!.createAsset(buffer) ?: error("Could not read $path")
        resources!!.loadResources(asset)
        asset.releaseSourceData()
        return asset
    }

    fun destroyAsset(asset: FilamentAsset) {
        loader?.destroyAsset(asset)
    }

    private fun shutdown() {
        try {
            resources?.destroy(); loader?.destroy(); materials?.destroyMaterials(); materials?.destroy()
            reflections?.let { engineOrNull?.destroyTexture(it) }
            engineOrNull?.destroy()
        } catch (_: Throwable) {
        }
        resources = null; loader = null; materials = null; reflections = null; engineOrNull = null
    }
}
