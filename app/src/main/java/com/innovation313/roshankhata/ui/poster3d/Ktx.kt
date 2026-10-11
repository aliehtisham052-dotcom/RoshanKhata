package com.innovation313.roshankhata.ui.poster3d

import com.google.android.filament.Engine
import com.google.android.filament.Texture
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Just enough of KTX1 to load the cubemap cmgen writes: R11F_G11F_B10F,
 * six faces, a few mip levels. Filament's own KTX loader lives in its
 * utils library, which would bring a second native library and a Kotlin
 * runtime newer than this app's for one file; forty lines replace it.
 *
 * Layout (KTX 1.1): 12-byte identifier, thirteen uint32 header fields, the
 * key/value block, then per mip level a uint32 image size followed by the
 * six faces, each that many bytes (a 4-byte pixel never needs padding).
 */
object Ktx {

    private const val GL_R11F_G11F_B10F = 0x8C3A

    fun cubemap(engine: Engine, bytes: ByteArray): Texture {
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        b.position(12)
        val endian = b.int
        require(endian == 0x04030201) { "KTX: big-endian files are not supported" }
        b.int; b.int; b.int                       // glType, glTypeSize, glFormat
        val internal = b.int
        b.int                                      // glBaseInternalFormat
        val width = b.int
        val height = b.int
        b.int; b.int                               // depth, array elements
        val faces = b.int
        val levels = maxOf(1, b.int)
        val kv = b.int
        require(internal == GL_R11F_G11F_B10F && faces == 6 && width == height) { "KTX: not a cmgen cubemap" }
        b.position(b.position() + kv)

        val tex = Texture.Builder()
            .width(width).height(height).levels(levels)
            .sampler(Texture.Sampler.SAMPLER_CUBEMAP)
            .format(Texture.InternalFormat.R11F_G11F_B10F)
            .build(engine)
        for (level in 0 until levels) {
            val size = b.int
            val data = ByteBuffer.allocateDirect(size * 6).order(ByteOrder.nativeOrder())
            val chunk = ByteArray(size * 6)
            b.get(chunk)
            data.put(chunk).rewind()
            val offsets = IntArray(6) { it * size }
            tex.setImage(engine, level,
                Texture.PixelBufferDescriptor(data, Texture.Format.RGB, Texture.Type.UINT_10F_11F_11F_REV), offsets)
        }
        return tex
    }
}
