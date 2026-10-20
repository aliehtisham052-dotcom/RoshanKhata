// Roshan Khata for Windows (KMP Phase 6, 18 Oct 2026).
//
// A desktop window around the shared module: the same database (Room,
// schema 31, in one SQLite file), the same backup format, the same words in
// the same nine languages, the same Compose screens as the phone. Packaged as
// an MSI by GitHub's Windows runner (desktop.yml); the MSI becomes an MSIX for
// the Microsoft Store with Microsoft's free MSIX Packaging Tool.
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    // Dispatchers.Main on the desktop is Swing's event thread.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    // The business cards' QR (the phone's own library, pure Java).
    implementation("com.google.zxing:core:3.5.3")
    testImplementation("junit:junit:4.13.2")
}

// Only Windows' SQLite in the MSI (20 Oct): androidx.sqlite:sqlite-bundled carries its native
// library for five systems (Linux x64/arm64, macOS x64/arm64, Windows x64), 4.7 MB of them never
// loaded on Windows. On the Windows runner, which builds the MSI and runs the tests there, the
// app's runtime classpath gets a copy of that jar with the other systems' natives left out;
// every other jar passes through untouched. The Linux CI keeps the whole jar for its tests.
abstract class WindowsOnlySqlite : TransformAction<TransformParameters.None> {
    @get:InputArtifact abstract val input: Provider<FileSystemLocation>
    override fun transform(outputs: TransformOutputs) {
        val file = input.get().asFile
        if (!file.name.startsWith("sqlite-bundled")) { outputs.file(input); return }
        val out = outputs.file(file.nameWithoutExtension + "-windows.jar")
        ZipInputStream(file.inputStream().buffered()).use { zin ->
            ZipOutputStream(out.outputStream().buffered()).use { zout ->
                while (true) {
                    val e = zin.nextEntry ?: break
                    val keep = !e.name.startsWith("natives/") || e.name == "natives/" || e.name.startsWith("natives/windows_x64")
                    if (keep) { zout.putNextEntry(ZipEntry(e.name)); zin.copyTo(zout); zout.closeEntry() }
                }
            }
        }
    }
}
if (System.getProperty("os.name").orEmpty().startsWith("Windows")) {
    val windowsNatives = Attribute.of("roshankhata.windowsNatives", Boolean::class.javaObjectType)
    dependencies {
        attributesSchema { attribute(windowsNatives) }
        artifactTypes.getByName("jar") { attributes.attribute(windowsNatives, false) }
        registerTransform(WindowsOnlySqlite::class) {
            from.attribute(windowsNatives, false).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
            to.attribute(windowsNatives, true).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
        }
    }
    configurations.named("runtimeClasspath") { attributes.attribute(windowsNatives, true) }
}

compose.desktop {
    application {
        mainClass = "com.innovation313.roshankhata.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Roshan Khata"
            // MSI versions are MAJOR.MINOR.BUILD with MAJOR above 0.
            packageVersion = "1.8.1"
            vendor = "Innovation 313"
            description = "Roshan Khata - the shopkeeper's udhar khata"
            copyright = "Innovation 313"
            // java.sql for Room's driver plumbing; jdk.unsupported for SQLite's native loader.
            modules("java.sql", "jdk.unsupported")
            windows {
                menu = true
                menuGroup = "Innovation 313"
                shortcut = true
                perUserInstall = true
                dirChooser = true
                // Fixed for ever: Windows recognises a newer MSI as an upgrade of this app by it.
                upgradeUuid = "01886a20-ce3b-43ee-936c-7ee93f78839e"
                // The book icon (the phone's launcher icon, 18 Oct).
                iconFile.set(project.file("icons/roshan_khata.ico"))
            }
        }
    }
}

// The phone's own Home tile icons and their day/night colours, copied at build
// time (never by hand), so the computer's sections wear the same pictures.
val syncTileIcons by tasks.registering(Sync::class) {
    from(rootProject.file("app/src/main/res")) {
        include(
            "drawable/ic_tile_*.xml", "values/colors.xml", "values-night/colors.xml",
            // The header's glyphs (20 Oct redesign): the shop, the bell, the eye, the phone.
            "drawable/ic_storefront.xml", "drawable/ic_bell.xml", "drawable/ic_eye_open.xml",
            "drawable/ic_eye_closed.xml", "drawable/ic_phone.xml", "drawable/ic_search.xml",
            // The shop-type picker's twenty-five trade icons (TradeKit).
            "drawable/ic_trade_*.xml"
        )
    }
    into(layout.buildDirectory.dir("tileRes/tiles"))
}
sourceSets["main"].resources.srcDir(syncTileIcons.map { it.destinationDir.parentFile })

// The phone's invoice fonts and the Roshan Khata mark, copied at build time
// (never by hand): the ten invoice designs draw with them (Phase 10).
val syncPrintAssets by tasks.registering(Sync::class) {
    from(rootProject.file("app/src/main/res/font")) {
        include(
            "sora_variable.ttf", "manrope_regular.ttf", "manrope_bold.ttf", "playfair_display_variable.ttf",
            "ibm_plex_mono_regular.ttf", "ibm_plex_mono_bold.ttf",
            // Urdu business cards are set in Nastaliq, as on the phone.
            "noto_nastaliq_urdu.ttf",
            // The posters' Arabic-script greetings (عید مبارک), in calligraphy.
            "aref_ruqaa_bold.ttf"
        )
        into("fonts")
    }
    from(rootProject.file("app/src/main/assets")) { include("roshan_logo.png") }
    into(layout.buildDirectory.dir("printAssets"))
}
sourceSets["main"].resources.srcDir(syncPrintAssets)

// A failing test's own message in the log, so the workflow's annotation carries it
// (the log itself is not readable from outside).
tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStackTraces = false
    }
}
