// Roshan Khata — root build file
//
// Versions are kept in step with Roshan Camera, which already builds green on
// exactly these and on targetSdk 36. Two apps from one shop should not need
// two different toolchains reasoned about separately, and copying a
// combination that is known to work beats picking one that ought to.
//
// 18 Oct 2026, KMP Phase 2 step 1: Kotlin 2.1.21 -> 2.4.21 and KSP
// 2.1.21-2.0.2 -> 2.3.11. Kotlin 2.1.21's Multiplatform plugin is supported
// only up to AGP 8.7.2 and Gradle 8.12.1 (kotlinlang.org KMP compatibility
// guide); this app is on AGP 8.11.1 and CI pins Gradle 8.14.3. Kotlin 2.4.21
// (latest stable, 8 Oct 2026) supports AGP 8.5.2-9.3.1 and Gradle up to 9.7.
// From KSP 2.3 its version no longer starts with Kotlin's; 2.3.10 is the
// release that made it work with Kotlin 2.4. This parts company with Roshan
// Camera's toolchain, on purpose.
plugins {
    id("com.android.application") version "8.11.1" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("com.google.devtools.ksp") version "2.3.11" apply false
    // KMP Phase 2: the shared module (Android + desktop).
    id("org.jetbrains.kotlin.multiplatform") version "2.4.21" apply false
    id("com.android.library") version "8.11.1" apply false
}
