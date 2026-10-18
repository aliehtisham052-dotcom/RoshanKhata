// Roshan Khata - shared module (Kotlin Multiplatform), KMP Phase 2, 18 Oct 2026.
//
// The code that does not need Android: sums, voice parsing, name search,
// zakat, dates in words. The Android app uses it exactly as before (same
// packages, so no import changed); the Windows app will use the same files.
//
// Targets: Android (androidTarget, classic library plugin), and "desktop" (the JVM,
// i.e. Windows/Mac/Linux).
// Source sets:
//   commonMain     - pure Kotlin, for every target (iPhone/web one day)
//   jvmSharedMain  - Kotlin that may use the JDK (java.util.Locale,
//                    Calendar...), shared by Android and desktop, which are
//                    both JVMs. Phase 2's files live here.
//   androidMain / desktopMain - one target only (empty for now)
// Tests in jvmSharedTest run on the desktop JVM: gradle :shared:desktopTest.
plugins {
    kotlin("multiplatform")
    // The classic Android library plugin, not com.android.kotlin.multiplatform.library:
    // KSP 2.3.11 cannot attach to the newer plugin yet ("Kotlin multiplatform
    // Variant API does not support addKspConfigurations() yet", CI 18 Oct), and
    // Room needs KSP. Revisit when moving to AGP 9.
    id("com.android.library")
    // Room's code generator, run for each target (Phase 3: the database).
    id("com.google.devtools.ksp")
    // Room's own plugin keeps the schema files: with a plain KSP argument the
    // Android and desktop KSP runs wrote and read one 31.json at the same
    // time, and one read it half-written (CI, 18 Oct).
    id("androidx.room")
    // Screens in Compose (Phase 5), and the Compose compiler.
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The schema files stay where they have always been (app/schemas): the
// migration tests in the app read them.
room {
    schemaDirectory("$rootDir/app/schemas")
}

kotlin {
    androidTarget()
    jvm("desktop")

    jvmToolchain(17)

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                // api: KhataDatabase extends RoomDatabase, so the app sees Room through it.
                api("androidx.room:room-runtime:2.7.1")
                // api: the app hosts the shared screens in its own activities.
                api(compose.runtime)
                api(compose.foundation)
                api(compose.material3)
                api(compose.components.resources)
            }
        }
        val jvmSharedMain by creating {
            dependsOn(commonMain)
            // The backup format is org.json (BackupCodec). The phone has it
            // built in, so here it is compile-only; the computer gets the
            // same API from the org.json library (desktopMain).
            dependencies { compileOnly("org.json:json:20250517") }
        }
        val androidMain by getting {
            dependsOn(jvmSharedMain)
            // BidiFormatter for Format.ltr / isolate (the app's own version).
            dependencies { implementation("androidx.core:core-ktx:1.13.1") }
        }
        val desktopMain by getting {
            dependsOn(jvmSharedMain)
            dependencies {
                implementation("org.json:json:20250517")
                // JsonStream on the computer: Gson's JsonReader, which Android's copies.
                implementation("com.google.code.gson:gson:2.11.0")
            }
        }

        val commonTest by getting
        val jvmSharedTest by creating {
            dependsOn(commonTest)
            dependencies { implementation("junit:junit:4.13.2") }
        }
        val desktopTest by getting { dependsOn(jvmSharedTest) }
    }
}

android {
    namespace = "com.innovation313.roshankhata.shared"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Room's KSP for each target (Phase 3). KSP for the shared source sets is
// not used: Room writes each target's code, including the actual
// KhataDatabaseConstructor.
dependencies {
    add("kspAndroid", "androidx.room:room-compiler:2.7.1")
    add("kspDesktop", "androidx.room:room-compiler:2.7.1")
}

// The app's own words, pictures' fonts and lists, as Compose resources (Phase 5).
// One source: app/src/main/res stays where translations are made and checked
// (strings.yml), and this copies it for the shared screens at build time, so
// a phone screen and its computer twin can never say different things.
// values-in is Indonesian as Android names it; the computer's JDK calls it
// "id", so it is copied under both names.
val syncAppResources by tasks.registering(Sync::class) {
    val res = rootProject.file("app/src/main/res")
    from(res) {
        include("values/strings.xml", "values/arrays.xml")
        include("values-*/strings.xml", "values-*/arrays.xml")
        exclude("values-night/**")
        include("font/*.ttf")
    }
    from(res.resolve("values-in")) {
        include("strings.xml", "arrays.xml")
        into("values-id")
    }
    into(layout.buildDirectory.dir("appResources"))
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.innovation313.roshankhata.res"
    generateResClass = always
    customDirectory(
        sourceSetName = "commonMain",
        directoryProvider = syncAppResources.map { layout.projectDirectory.dir(it.destinationDir.absolutePath) }
    )
}
