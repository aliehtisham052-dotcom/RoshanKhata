package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.words.Words
import com.innovation313.roshankhata.words.desktopWords
import java.util.Locale

/**
 * An [AnswerEnv] for the roles matrix (plan B7, 24 Nov 2026): the real words of the app (strings.xml read as the computer
 * reads them), the shop's profile filled with the invented markers of [HudhudRolesTable], nothing else of a device. The
 * clock is held at [clock] so "today" is the day the book was seeded. [viewer] is true for a read-only copy of the book.
 *
 * It writes nothing but the two device settings an env always had (the name he was asked to use, the privacy line said);
 * both live in this object and in no book.
 */
internal class HudhudRolesEnv(
    private val clock: Long,
    private val viewer: Boolean,
    override val locale: Locale = Locale.US,
    private val owner: HudhudRolesProfile = HudhudRolesProfile()
) : AnswerEnv {

    override val words: Words = desktopWords(locale)
    override fun now(): Long = clock
    override fun inLanguage(locale: Locale): AnswerEnv = HudhudRolesEnv(clock, viewer, locale, owner)

    private var name: String? = null
    private var privacy = true
    override fun callMe(): String? = name
    override fun setCallMe(name: String) { this.name = name.trim().take(30).takeIf { it.isNotEmpty() } }
    override fun privacyDue(): Boolean = privacy
    override fun privacyTold() { privacy = false }
    override fun radarOn(): Boolean = !viewer
    override fun viewer(): Boolean = viewer
    override fun lookValue(setting: HudhudLook.Setting): Int = when (setting) {
        HudhudLook.Setting.SIZE -> HudhudLook.SIZE_NORMAL
        HudhudLook.Setting.THEME -> HudhudLook.THEME_SYSTEM
    }

    override val profile: AnswerProfile = owner
    override fun tradeLabel(key: String): String = key
    override fun seasons(): Boolean = false
    override fun seasonName(season: SeasonBook.Season): String = "Season"

    override fun appVersion(): String? = "1.0.0-roles"
    override fun testBuild(): Boolean = true
    override fun usageCount(thing: Hudhud.Thing, from: Long, to: Long): Int = 0
    override fun usageSince(): Long? = null
    override fun lastBackupAt(): Long = clock - 60 * 60_000L
    override fun otherPhoneAt(): Long = 0L
    override fun backupFailedAt(): Long = 0L
    override suspend fun alerts(dao: KhataDao): List<AnswerAlert> = emptyList()
    override fun remindedToday(now: Long): Set<Long> = emptySet()
    override suspend fun radarShopDays(dao: KhataDao): Int? = null
    override fun radarReasons(reading: PaymentRadar.Reading, promise: Long?, tone: Int): String = ""
    override fun toneThatWorked(partyId: Long, balance: Double): Int = 0
    override fun automationSettings(): Automations.Settings = object : Automations.Settings {
        override fun isOn(kind: Automations.Kind) = false
        override fun hour(kind: Automations.Kind) = 9
        override fun lastSaid(kind: Automations.Kind): Long? = null
        override fun setSaid(kind: Automations.Kind, stamp: Long) {}
    }
    override fun screenNeeds(screen: ScreenId): Access.Permission? = null
}

/** The shop's profile, every private field one of [HudhudRolesTable.MARKERS] (a field a role may not see must never show). */
internal class HudhudRolesProfile : AnswerProfile {
    private fun marker(name: String) = HudhudRolesTable.MARKERS.first { it.name == name }.text
    override fun businessName(): String? = "Roles Test Traders"
    override fun businessNameUrdu(): String? = null
    override fun businessAddress(): String? = "Main Road, Testville"
    override fun businessPhone(): String? = "0300 0000005"
    override fun businessEmail(): String? = marker("email")
    override fun strn(): String? = marker("tax number")
    override fun taxLabel(): String = "STRN"
    override fun termsAndConditions(): String? = null
    override fun currency(): String = Currency.DEFAULT
    override fun country(): ShopCountry = ShopCountry.FIJI
    override fun calendar(): ShopCalendar.Kind = ShopCalendar.Kind.GREGORIAN
    override fun trade(): String? = "GENERAL"
    override fun bankName(): String? = "Roles Bank"
    override fun bankAccountTitle(): String? = "Roles Test Traders"
    override fun bankIban(): String? = marker("bank account")
    override fun bankJazzCash(): String? = null
    // How the papers behave (H6, 25 Nov 2026): the owner's alone, as their settings screen.
    override fun photoOnStatement(): Boolean = true
    override fun sealChoice(): SealChoice = SealChoice.OWN
    override fun paymentOnReminder(): Boolean = false
}
