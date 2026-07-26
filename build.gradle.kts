// Roshan Khata — root build file
//
// Versions are kept in step with Roshan Camera, which already builds green on
// exactly these and on targetSdk 36. Two apps from one shop should not need
// two different toolchains reasoned about separately, and copying a
// combination that is known to work beats picking one that ought to.
//
// KSP's version is tied to Kotlin's: it must begin with the same Kotlin
// version or it will not load. 2.1.21-2.0.2 is the release built for Kotlin
// 2.1.21 — checked against Google's own tag list rather than assumed.
plugins {
    id("com.android.application") version "8.11.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.21" apply false
    id("com.google.devtools.ksp") version "2.1.21-2.0.2" apply false
}
