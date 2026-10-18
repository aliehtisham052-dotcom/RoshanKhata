package com.innovation313.roshankhata.data

import java.io.Closeable
import java.io.Reader

/**
 * A JSON document read token by token, so a backup the size of a
 * wholesaler's book is never held whole (P7). The phone reads with
 * android.util.JsonReader, the computer with Gson's JsonReader — the same
 * reader, Android's being a copy of Gson's. Both are lenient, as the
 * backup reader always was.
 */
interface JsonStream : Closeable {
    fun peek(): JsonKind
    fun beginObject()
    fun endObject()
    fun beginArray()
    fun endArray()
    fun hasNext(): Boolean
    fun nextName(): String
    fun nextString(): String
    fun nextBoolean(): Boolean
    fun nextNull()
}

enum class JsonKind { BEGIN_OBJECT, END_OBJECT, BEGIN_ARRAY, END_ARRAY, NAME, STRING, NUMBER, BOOLEAN, NULL, END_DOCUMENT }

/** A lenient reader over [reader]. */
expect fun openJsonStream(reader: Reader): JsonStream
