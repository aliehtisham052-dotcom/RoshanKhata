package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.words.Words
import java.util.Locale

/**
 * Everything Hudhud's answers may know besides the book (16 Nov 2026, audit T22): the words in one language, the owner's own
 * choices kept on this device, the shop's profile, the bell, and who may open which page. Passed in, so the same answer code
 * runs on the phone (each member over the phone's own preferences and screens, exactly as the answers read them before) and on
 * the computer (over its own settings), and a test can build one.
 *
 * The words: [getString] and [quantity] are the phone's `context.getString` and `Digits.quantity` by a word's key (`W.x`,
 * `WP.x`), formatted in 0-9 as [Words] always is. [inLanguage] is the same env in another language: the voice's (an answer
 * said in Roman Urdu on an Urdu phone), and English and both Urdu scripts for matching help pages.
 *
 * Nothing here writes the book. The two writes it has are the owner's own device settings the answers always kept: the name he
 * asked to be called (only from the card's Save) and that the privacy line was said once.
 */
interface AnswerEnv {
    val words: Words
    /** The language the words are in (dates, a country's name, the full stop of a spoken sentence). */
    val locale: Locale

    fun getString(name: String, vararg args: Any): String = words.text(name, *args)

    fun quantity(name: String, count: Int, vararg args: Any): String = words.plural(name, count, *args)

    /**
     * The moment the answers count from (17 Nov 2026, audit L035): "today", "this month", "a few minutes ago" and every span
     * of days are worked out from it, never from the system clock directly, so a test can hold the clock still. The real
     * clock unless an env says otherwise; [inLanguage] keeps it.
     */
    fun now(): Long = System.currentTimeMillis()

    /** The same env with its words in [locale]. */
    fun inLanguage(locale: Locale): AnswerEnv

    // ------------------------------------------------------------------------------------------ the owner's choices, this device

    /** What the owner asked to be called, or null. */
    fun callMe(): String?
    fun setCallMe(name: String)
    /** The privacy line is still to be said once with his introduction. */
    fun privacyDue(): Boolean
    fun privacyTold()
    /** The Payment Radar is on on this device (never on a helper's read-only phone). */
    fun radarOn(): Boolean
    /** A helper's read-only copy of the book (the phone's ViewerMode): nothing can be written from it. */
    fun viewer(): Boolean
    /** Where a look setting stands now: the text size's level or the theme's mode (the numbers [HudhudLook] uses). */
    fun lookValue(setting: HudhudLook.Setting): Int

    // ------------------------------------------------------------------------------------------ the shop

    val profile: AnswerProfile
    /** The words of a trade's name, by its stored key. */
    fun tradeLabel(key: String): String
    /** The shop keeps seasons (agri in a country that has them). */
    fun seasons(): Boolean
    /** A season's own words (Rabi 2026, Kharif 2026). */
    fun seasonName(season: SeasonBook.Season): String

    // ------------------------------------------------------------------------------------------ the app on this device

    /** The app's version name, or null when it cannot be read. */
    fun appVersion(): String?
    /** A test build (not the one from the store). */
    fun testBuild(): Boolean
    /** The app's own counts on this device: calculator sums ([Hudhud.Thing.CALCULATIONS]) or app opens, in [from, to). */
    fun usageCount(thing: Hudhud.Thing, from: Long, to: Long): Int
    /** The day this device began counting them, or null. */
    fun usageSince(): Long?
    /** The newest backup the owner made, 0 for none. */
    fun lastBackupAt(): Long
    /** When another phone backed up this shop, 0 for never. */
    fun otherPhoneAt(): Long
    /** When the last backup failed, 0 for never. */
    fun backupFailedAt(): Long
    /** The bell's rows, as the bell shows them now. */
    suspend fun alerts(dao: KhataDao): List<AnswerAlert>
    /** The customers already reminded today (Follow-up's rule). */
    fun remindedToday(now: Long): Set<Long>
    /** The Payment Radar's shop norm in days, or null when the book is too young. */
    suspend fun radarShopDays(dao: KhataDao): Int?
    /** The Radar's reasons for a reading, as the customer's page shows them. */
    fun radarReasons(reading: PaymentRadar.Reading, promise: Long?, tone: Int): String
    /** The reminder step that worked last with this customer (0: none known). */
    fun toneThatWorked(partyId: Long, balance: Double): Int
    /** The automations' switches and hours on this device. */
    fun automationSettings(): Automations.Settings
    /** The permission [screen]'s own gate asks, or null when anyone signed in may open it. */
    fun screenNeeds(screen: ScreenId): Access.Permission?
}

/**
 * The shop's profile as the answers read it: each getter as the phone's BusinessProfile returns it (blank kept as the profile
 * keeps it; the readers decide what is "not set").
 */
interface AnswerProfile {
    fun businessName(): String?
    fun businessNameUrdu(): String?
    fun businessAddress(): String?
    fun businessPhone(): String?
    fun businessEmail(): String?
    fun strn(): String?
    fun taxLabel(): String
    fun termsAndConditions(): String?
    fun currency(): String
    fun country(): ShopCountry
    fun calendar(): ShopCalendar.Kind
    fun trade(): String?
    fun bankName(): String?
    fun bankAccountTitle(): String?
    fun bankIban(): String?
    fun bankJazzCash(): String?
}
