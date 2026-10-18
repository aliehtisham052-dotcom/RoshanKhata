package com.innovation313.roshankhata.data

import android.util.JsonReader
import android.util.JsonToken
import java.io.Reader

actual fun openJsonStream(reader: Reader): JsonStream {
    val r = JsonReader(reader).apply { isLenient = true }
    return object : JsonStream {
        override fun peek(): JsonKind = when (r.peek()) {
            JsonToken.BEGIN_OBJECT -> JsonKind.BEGIN_OBJECT
            JsonToken.END_OBJECT -> JsonKind.END_OBJECT
            JsonToken.BEGIN_ARRAY -> JsonKind.BEGIN_ARRAY
            JsonToken.END_ARRAY -> JsonKind.END_ARRAY
            JsonToken.NAME -> JsonKind.NAME
            JsonToken.STRING -> JsonKind.STRING
            JsonToken.NUMBER -> JsonKind.NUMBER
            JsonToken.BOOLEAN -> JsonKind.BOOLEAN
            JsonToken.NULL -> JsonKind.NULL
            else -> JsonKind.END_DOCUMENT
        }
        override fun beginObject() = r.beginObject()
        override fun endObject() = r.endObject()
        override fun beginArray() = r.beginArray()
        override fun endArray() = r.endArray()
        override fun hasNext(): Boolean = r.hasNext()
        override fun nextName(): String = r.nextName()
        override fun nextString(): String = r.nextString()
        override fun nextBoolean(): Boolean = r.nextBoolean()
        override fun nextNull() = r.nextNull()
        override fun close() = r.close()
    }
}
