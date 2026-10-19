// Roshan Khata for Windows (KMP Phase 6, 18 Oct 2026).
//
// A desktop window around the shared module: the same database (Room,
// schema 31, in one SQLite file), the same backup format, the same words in
// the same nine languages, the same Compose screens as the phone. Packaged as
// an MSI by GitHub's Windows runner (desktop.yml); the MSI becomes an MSIX for
// the Microsoft Store with Microsoft's free MSIX Packaging Tool.
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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
    testImplementation("junit:junit:4.13.2")
}

compose.desktop {
    application {
        mainClass = "com.innovation313.roshankhata.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Roshan Khata"
            // MSI versions are MAJOR.MINOR.BUILD with MAJOR above 0.
            packageVersion = "1.1.0"
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
