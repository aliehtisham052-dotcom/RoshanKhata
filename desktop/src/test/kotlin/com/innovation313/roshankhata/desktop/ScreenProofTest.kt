package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.innovation313.roshankhata.data.LedgerAlerts
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.screens.RoshanTheme
import com.innovation313.roshankhata.words.ProvideWords
import com.innovation313.roshankhata.words.desktopWords
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * The Windows window itself as a picture (20 Oct redesign): the header, the
 * rail and Home, in English and Urdu, light and dark, with a made-up shop and
 * made-up customers. Kept in build/screen-proof and put on the desktop-test
 * pre-release by desktop.yml, so the design is looked at without a laptop
 * (lesson 16). Never real names or numbers.
 */
@OptIn(ExperimentalComposeUiApi::class)
class ScreenProofTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = System.currentTimeMillis()

    private val parties = listOf(
        PartyWithBalance(1, "Muhammad Aslam", "03000000001", true, null, 36200.0, now - 2 * 60 * 60 * 1000),
        PartyWithBalance(2, "Ghulam Rasool", "03000000002", true, null, 18900.0, now - 5 * 60 * 60 * 1000),
        PartyWithBalance(3, "Haji Bashir Ahmad", "03000000003", true, null, 0.0, now - day),
        PartyWithBalance(4, "Sample Seeds Co.", "03000000004", false, null, -48000.0, now - 2 * day),
        PartyWithBalance(5, "Allah Ditta", null, true, null, 9450.0, now - 3 * day),
        PartyWithBalance(6, "Khalid Javed", "03000000006", true, null, 12400.0, now - 6 * day)
    )

    private fun alerts() = listOf(
        LedgerAlerts.Alert(LedgerAlerts.Kind.CHEQUE, "Cheque", "Ghulam Rasool"),
        LedgerAlerts.Alert(LedgerAlerts.Kind.PLAN, "Plan", "Allah Ditta"),
        LedgerAlerts.Alert(LedgerAlerts.Kind.EXPIRING, "Expiring", "")
    )

    private fun shoot(
        name: String, ur: Boolean, dark: Boolean, dir: File,
        width: Int = 1440, height: Int = 900, scale: Float = 1f,
        page: @androidx.compose.runtime.Composable () -> Unit = {
            HomePane(parties, late = 2, alerts = alerts(), onOpen = {}, onAction = {}, onCustomer = {})
        }
    ): File {
        // As Main does on choosing a language: the default locale is what Format.ltr and the
        // dates read their direction from. Without it the Urdu proof showed "Oct 2026 20" even
        // where the app itself is right (20 Oct): the picture must be drawn as the app draws it.
        val before = Locale.getDefault()
        val locale = Locale.forLanguageTag(if (ur) "ur" else "en").also { Locale.setDefault(it) }
        val words = desktopWords(locale)
        val scene = ImageComposeScene(width = width, height = height, density = Density(scale)) {
            RoshanTheme(dark = dark) {
                ProvideWords(words) {
                    CompositionLocalProvider(LocalLayoutDirection provides if (ur) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                        Column(Modifier.fillMaxSize().background(LocalRoshanColors.current.pageBg)) {
                            AppHeader(
                                shopName = if (ur) "النور ایگری اسٹور" else "Al-Noor Agri Store",
                                logo = null, onShop = {},
                                backupLabel = words.text(com.innovation313.roshankhata.words.W.backup_now),
                                onBackup = {}, onPhone = {}, alerts = 3, onAlerts = {}, menu = emptyList(), onAddParty = {}
                            )
                            Row(Modifier.fillMaxSize()) {
                                SectionRail(Section.HOME, {}, badges = mapOf(Section.FOLLOWUP to 2))
                                Box(Modifier.weight(1f)) { page() }
                            }
                        }
                    }
                }
            }
        }
        try {
            // The first frame lays out, the next loads the pictures; then a second on, so Home's
            // rise and every eased colour (Motion.kt, 1.8.3) have finished before the picture.
            scene.render(0)
            scene.render(16_000_000)
            scene.render(500_000_000)
            val image = scene.render(1_000_000_000)
            val png = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
            return File(dir, "$name.png").also { it.writeBytes(png) }
        } finally {
            scene.close()
            Locale.setDefault(before)
        }
    }

    @Test
    fun `home draws in English and Urdu, light and dark`() {
        // Windows only: the pictures are for the Windows app and desktop.yml (Windows) publishes
        // them. On the Ubuntu runner Skia's native renderer crashed the test JVM with a SIGSEGV
        // in libstdc++ (Android CI, 20 Oct), taking every other desktop test down with it.
        Assume.assumeTrue("screen proofs are drawn on Windows", System.getProperty("os.name").orEmpty().startsWith("Windows"))
        val dir = File("build/screen-proof").apply { mkdirs() }
        val small = ArrayList<String>()
        for (ur in listOf(false, true)) for (dark in listOf(false, true)) {
            val name = "screen-home-${if (ur) "ur" else "en"}-${if (dark) "dark" else "light"}"
            val f = shoot(name, ur, dark, dir)
            // A blank window compresses to a few kilobytes; a drawn Home to far more.
            if (f.length() < 15_000) small += "$name=${f.length()}"
        }
        assertTrue("screens that look empty: $small", small.isEmpty())
    }

    /**
     * The owner's own laptop (20 Oct photos): 1366 x 768 at Windows' 125%, about
     * 1093 dp wide. Home, the Khata controls and an invoice's design stepper must
     * fit there without a word broken across lines ("Filte r by date").
     */
    @Test
    fun `laptop size draws without wrapping`() {
        Assume.assumeTrue("screen proofs are drawn on Windows", System.getProperty("os.name").orEmpty().startsWith("Windows"))
        val dir = File("build/screen-proof").apply { mkdirs() }
        val home = shoot("screen-laptop-home-en", ur = false, dark = false, dir = dir, width = 1366, height = 768, scale = 1.25f)
        val khata = shoot("screen-laptop-khata-ur", ur = true, dark = false, dir = dir, width = 1366, height = 768, scale = 1.25f) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth(0.42f)) {
                    KhataControls(
                        type = PartyType.ALL, onType = {}, side = PartySide.ALL, onSide = {},
                        sort = PartySort.RECENT, onSort = {}, whenFilter = PartyWhen.ALL, onWhen = {},
                        selecting = false, onSelecting = {}
                    )
                }
                Box(Modifier.fillMaxWidth(0.5f)) { DesignStepper(shown = 3, saved = 1, onShow = {}, onApply = {}) }
            }
        }
        val khataDark = shoot("screen-laptop-khata-en-dark", ur = false, dark = true, dir = dir, width = 1366, height = 768, scale = 1.25f) {
            Box(Modifier.fillMaxWidth(0.42f)) {
                KhataControls(
                    type = PartyType.CUSTOMERS, onType = {}, side = PartySide.TO_GET, onSide = {},
                    sort = PartySort.RECENT, onSort = {}, whenFilter = PartyWhen.ALL, onWhen = {},
                    selecting = false, onSelecting = {}
                )
            }
        }
        for (f in listOf(home, khata, khataDark)) assertTrue("${f.name} looks empty: ${f.length()}", f.length() > 15_000)
    }
}
