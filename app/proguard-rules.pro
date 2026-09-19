# Roshan Khata — R8 / ProGuard rules
#
# WHY THESE EXIST AT ALL
#
# Until now the release build ran with isMinifyEnabled = false, so R8 never
# ran and Play Console measured 1% obfuscation on an 18.7 MB DEX. Google's
# published requirement is 25% each of optimisation, obfuscation and
# shrinking from February 2027, enforced on apps whose DEX exceeds 10 MB —
# which this app's does, so it applies to us rather than merely being advice.
#
# EVERY RULE BELOW NAMES WHAT IT PROTECTS AND WHY. R8's danger is not that it
# breaks the build — it is that the build stays green and the app fails at
# RUNTIME, quietly, on a feature nobody reopened. The exact shape of the
# Guava bug already recorded in this project's history: the build was green
# the whole time the library was missing, because nothing in our own source
# referenced it; it broke only when a Drive client was actually constructed.
#
# Our own code needs almost nothing here: a sweep for reflection found no
# Class.forName, no getDeclaredField, no Serializable, no @Parcelize and no
# WorkManager workers anywhere in app/src/main/java. Backup is written and
# read with org.json by hand, field by field, so no model class is mapped by
# name. The risk is concentrated entirely in the Google client libraries.

# ---------------------------------------------------------------------------
# Crash reports must stay readable
# ---------------------------------------------------------------------------
# Without these, every future stack trace from a user is a list of a(), b(),
# c() with no line numbers. Keeping the table costs a little size and is the
# difference between a report that can be acted on and one that cannot.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Generic signatures and annotations: the Google HTTP client reads both at
# runtime to map JSON onto its model classes. Strip them and Drive responses
# parse into empty objects rather than throwing — a silent wrong answer.
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes *Annotation*,RuntimeVisibleAnnotations,AnnotationDefault

# ---------------------------------------------------------------------------
# Google API client + Drive — the reflective core of backup and restore
# ---------------------------------------------------------------------------
# @Key is how google-http-client marks a field as a JSON property. It matches
# those fields BY NAME against the JSON coming back from Drive, so a renamed
# field silently stops being filled: a file listing returns null ids and the
# backup screen shows nothing rather than failing loudly.
-keepclassmembers class * {
  @com.google.api.client.util.Key <fields>;
}
-keepclasseswithmembers class * {
  @com.google.api.client.util.Key <fields>;
}

# The Drive model classes themselves (File, FileList and friends) are
# constructed and populated by that same mechanism.
-keep class com.google.api.services.drive.model.** { *; }
-keep class com.google.api.client.googleapis.** { *; }
-keep class com.google.api.client.json.** { *; }
-keep class com.google.api.client.util.** { *; }

# Apache HTTP is excluded from the build on purpose (see app/build.gradle.kts)
# and the Java-EE-only corners of the client library are unreachable on
# Android. Both are referenced from inside the compiled library, so R8 warns
# about them unless told they are known absences.
-dontwarn org.apache.http.**
-dontwarn android.net.http.AndroidHttpClient
-dontwarn com.google.api.client.http.apache.**
-dontwarn com.google.api.client.extensions.android.**
-dontwarn com.google.api.client.googleapis.extensions.android.**

# ---------------------------------------------------------------------------
# Guava — present deliberately, and reached only from inside the Drive client
# ---------------------------------------------------------------------------
# The -android variant omits classes that only exist on a desktop JVM, and the
# checker-framework and error-prone annotations it references are compile-time
# only. None of this is a missing dependency; it is a library that ships
# references to things a phone does not have.
-dontwarn com.google.common.**
-dontwarn sun.misc.Unsafe
-dontwarn java.lang.ClassValue
-dontwarn javax.annotation.**
-dontwarn javax.lang.model.**
-dontwarn org.checkerframework.**
-dontwarn afu.org.checkerframework.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**

# ---------------------------------------------------------------------------
# Enums
# ---------------------------------------------------------------------------
# This app decides sort order and filters with values()[which] against a
# dialog's label array, and reads enum names back out of saved preferences.
# Both go through the two synthetic methods below, which R8 would otherwise
# remove as unused because nothing calls them by name in source.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---------------------------------------------------------------------------
# Room
# ---------------------------------------------------------------------------
# Room generates its implementations at compile time via KSP and ships its own
# consumer rules, so entities and DAOs need nothing here. The one thing that
# is NOT generated is the schema identity check: Room compares the migrated
# database against the entities when it opens, and a mismatch means the app
# does not start at all.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# ---------------------------------------------------------------------------
# AndroidX / Play services
# ---------------------------------------------------------------------------
# These ship consumer rules of their own; the lines here only silence warnings
# about optional pieces that are not on the classpath.
-dontwarn com.google.android.gms.**
-dontwarn com.google.android.play.core.**
-dontwarn androidx.credentials.**

# ZXing decodes and encodes QR in-process with no reflection, but its core
# references a few javax.imageio classes that exist only on a JVM.
-dontwarn com.google.zxing.**

# ---------------------------------------------------------------------------
# Kotlin
# ---------------------------------------------------------------------------
-dontwarn kotlin.**
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlin.Metadata { public <methods>; }
