package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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

    private fun shoot(name: String, ur: Boolean, dark: Boolean, dir: File): File {
        val words = desktopWords(Locale.forLanguageTag(if (ur) "ur" else "en"))
        val scene = ImageComposeScene(width = 1440, height = 900, density = Density(1f)) {
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
                                Box(Modifier.weight(1f)) {
                                    HomePane(parties, late = 2, alerts = alerts(), onOpen = {}, onAction = {}, onCustomer = {})
                                }
                            }
                        }
                    }
                }
            }
        }
        try {
            // Two frames: the first lays out, the second has the pictures loaded.
            scene.render(0)
            val image = scene.render(16_000_000)
            val png = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
            return File(dir, "$name.png").also { it.writeBytes(png) }
        } finally {
            scene.close()
        }
    }

    @Test
    fun `home draws in English and Urdu, light and dark`() {
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
}
