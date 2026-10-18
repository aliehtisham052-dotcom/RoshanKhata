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
                // Plural rules for the computer's words (the phone uses android.icu).
                implementation("com.ibm.icu:icu4j:77.1")
                // The computer's SQLite, packed with the app (Room KMP's bundled driver).
                api("androidx.sqlite:sqlite-bundled:2.5.1")
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

// ---------- The app's words for the shared screens (KMP Phase 5a) ----------
//
// One source: app/src/main/res, where translations are made and checked
// (strings.yml). Compose Multiplatform's own resources were tried first and
// cannot carry Roman Urdu: the script qualifier (values-b+ur+Latn) arrives in
// 1.12, which needs compileSdk 37 and so AGP 9 (CI, 18 Oct). Instead:
//  - W / WP / WA: every string, plural and list name as a constant, so a
//    shared screen that names a missing word does not compile;
//  - the phone reads the words through its own resources (AndroidWords);
//  - the computer reads the same strings.xml files (XmlWords), copied here.
val appRes = rootProject.file("app/src/main/res")

val generateWordKeys by tasks.registering {
    val out = layout.buildDirectory.dir("generated/wordKeys/kotlin")
    inputs.files(fileTree(appRes) { include("values/strings.xml", "values/arrays.xml") })
    outputs.dir(out)
    doLast {
        val text = listOf("strings.xml", "arrays.xml").map { appRes.resolve("values/$it") }.filter { it.isFile }
            .joinToString("\n") { it.readText() }
        fun names(tag: String) = Regex("<$tag\\s+name=\"([^\"]+)\"").findAll(text).map { it.groupValues[1] }.distinct().sorted().toList()
        val keywords = setOf("as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in",
            "interface", "is", "null", "object", "package", "return", "super", "this", "throw", "true", "try",
            "typealias", "typeof", "val", "var", "when", "while")
        fun id(n: String) = if (n in keywords) "`$n`" else n
        fun block(obj: String, doc: String, list: List<String>) = buildString {
            append("/** $doc */\nobject $obj {\n")
            list.forEach { append("    const val ${id(it)} = \"$it\"\n") }
            append("}\n\n")
        }
        val file = out.get().file("com/innovation313/roshankhata/words/WordKeys.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "// Generated from app/src/main/res/values by :shared:generateWordKeys. Do not edit.\n" +
                "package com.innovation313.roshankhata.words\n\n" +
                block("W", "Every string's name.", names("string")) +
                block("WP", "Every plural's name.", names("plurals")) +
                block("WA", "Every list's name (string-array).", names("string-array"))
        )
    }
}

// The strings.xml files themselves, for the computer (and for the phone's
// test that holds XmlWords to Android's own reading of them).
val syncAppWords by tasks.registering(Sync::class) {
    from(appRes) {
        include("values/strings.xml", "values/arrays.xml", "values-*/strings.xml", "values-*/arrays.xml")
        exclude("values-night/**")
    }
    into(layout.buildDirectory.dir("appWords/words"))
}

kotlin.sourceSets.getByName("jvmSharedMain").kotlin.srcDir(generateWordKeys)
kotlin.sourceSets.getByName("desktopMain").resources.srcDir(syncAppWords.map { it.destinationDir.parentFile })
