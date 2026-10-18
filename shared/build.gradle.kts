// Roshan Khata - shared module (Kotlin Multiplatform), KMP Phase 2, 18 Oct 2026.
//
// The code that does not need Android: sums, voice parsing, name search,
// zakat, dates in words. The Android app uses it exactly as before (same
// packages, so no import changed); the Windows app will use the same files.
//
// Targets: Android, and "desktop" (the JVM, i.e. Windows/Mac/Linux).
// Source sets:
//   commonMain     - pure Kotlin, for every target (iPhone/web one day)
//   jvmSharedMain  - Kotlin that may use the JDK (java.util.Locale,
//                    Calendar...), shared by Android and desktop, which are
//                    both JVMs. Phase 2's files live here.
//   androidMain / desktopMain - one target only (empty for now)
// Tests in jvmSharedTest run on the desktop JVM: gradle :shared:desktopTest.
plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    // Room's code generator, run for each target (Phase 3: the database).
    id("com.google.devtools.ksp")
}

// The schema files stay where they have always been (app/schemas): the
// migration tests in the app read them.
ksp {
    arg("room.schemaLocation", "$rootDir/app/schemas")
}

kotlin {
    // AGP 8.11's name for the Android target block (android {} from 8.12).
    androidLibrary {
        namespace = "com.innovation313.roshankhata.shared"
        compileSdk = 36
        minSdk = 24
    }
    jvm("desktop")

    jvmToolchain(17)

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                // api: KhataDatabase extends RoomDatabase, so the app sees Room through it.
                api("androidx.room:room-runtime:2.7.1")
            }
        }
        val jvmSharedMain by creating { dependsOn(commonMain) }
        val androidMain by getting { dependsOn(jvmSharedMain) }
        val desktopMain by getting { dependsOn(jvmSharedMain) }

        val commonTest by getting
        val jvmSharedTest by creating {
            dependsOn(commonTest)
            dependencies { implementation("junit:junit:4.13.2") }
        }
        val desktopTest by getting { dependsOn(jvmSharedTest) }
    }
}

// Room's KSP for each target (Phase 3). KSP for the shared source sets is
// not used: Room writes each target's code, including the actual
// KhataDatabaseConstructor.
dependencies {
    add("kspAndroid", "androidx.room:room-compiler:2.7.1")
    add("kspDesktop", "androidx.room:room-compiler:2.7.1")
}
