package com.innovation313.roshankhata.data

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import com.innovation313.roshankhata.AboutActivity
import com.innovation313.roshankhata.AgingActivity
import com.innovation313.roshankhata.AlertsActivity
import com.innovation313.roshankhata.AndroidAutomationSettings
import com.innovation313.roshankhata.AuditActivity
import com.innovation313.roshankhata.BackupActivity
import com.innovation313.roshankhata.BillsActivity
import com.innovation313.roshankhata.BusinessCardActivity
import com.innovation313.roshankhata.BusinessSettingsActivity
import com.innovation313.roshankhata.BusinessSwitchActivity
import com.innovation313.roshankhata.CalculatorActivity
import com.innovation313.roshankhata.CashFlowActivity
import com.innovation313.roshankhata.CashbookActivity
import com.innovation313.roshankhata.ChequesActivity
import com.innovation313.roshankhata.CounterSaleActivity
import com.innovation313.roshankhata.DayBookActivity
import com.innovation313.roshankhata.DuplicateCustomersActivity
import com.innovation313.roshankhata.ExpiringActivity
import com.innovation313.roshankhata.FollowUpActivity
import com.innovation313.roshankhata.HelpActivity
import com.innovation313.roshankhata.InsightsActivity
import com.innovation313.roshankhata.InvoicesActivity
import com.innovation313.roshankhata.KhataActivity
import com.innovation313.roshankhata.LanguageActivity
import com.innovation313.roshankhata.LapsedActivity
import com.innovation313.roshankhata.MeraKhataActivity
import com.innovation313.roshankhata.OrdersActivity
import com.innovation313.roshankhata.PartyDetailActivity
import com.innovation313.roshankhata.PaymentVoiceActivity
import com.innovation313.roshankhata.PlansActivity
import com.innovation313.roshankhata.PosterActivity
import com.innovation313.roshankhata.ProductCustomersActivity
import com.innovation313.roshankhata.ProductsActivity
import com.innovation313.roshankhata.RateListActivity
import com.innovation313.roshankhata.RecycleBinActivity
import com.innovation313.roshankhata.SalesReportActivity
import com.innovation313.roshankhata.SchemesActivity
import com.innovation313.roshankhata.SeasonsActivity
import com.innovation313.roshankhata.ShiftActivity
import com.innovation313.roshankhata.SnapshotsActivity
import com.innovation313.roshankhata.StaffActivity
import com.innovation313.roshankhata.SupplierRatesActivity
import com.innovation313.roshankhata.ViewerActivity
import com.innovation313.roshankhata.WarisActivity
import com.innovation313.roshankhata.ZakatActivity
import com.innovation313.roshankhata.ui.AccessUi
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.HudhudPrefs
import com.innovation313.roshankhata.ui.RadarUi
import com.innovation313.roshankhata.ui.SeasonText
import com.innovation313.roshankhata.words.Words
import java.util.Locale

/**
 * The phone's side of Hudhud's shared answers (16 Nov 2026, audit T22): an [AnswerEnv] over a [Context], every member the very
 * call the answers made before they moved to :shared (HudhudPrefs, RadarUi, BusinessProfile, HomeAlerts, the screens' own
 * gates…), so a card reads exactly as it did. A [Context] in another language is another env ([inLanguage]): the voice's.
 *
 * [clock] is the moment the answers count from ([AnswerEnv.now]): the real clock on the phone; a test holds it still
 * (HudhudGoldenAnswersTest). Another language keeps it.
 */
class HudhudPhoneEnv(val context: Context, private val clock: () -> Long = { System.currentTimeMillis() }) : AnswerEnv {

    override fun now(): Long = clock()

    override val words: Words = AndroidWords(context)

    override val locale: Locale get() = context.resources.configuration.locales[0]

    override fun inLanguage(locale: Locale): AnswerEnv {
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return HudhudPhoneEnv(context.createConfigurationContext(config), clock)
    }

    override fun callMe(): String? = HudhudPrefs.callMe(context)
    override fun setCallMe(name: String) = HudhudPrefs.setCallMe(context, name)
    override fun privacyDue(): Boolean = HudhudPrefs.privacyDue(context)
    override fun privacyTold() = HudhudPrefs.privacyTold(context)
    override fun radarOn(): Boolean = RadarUi.on(context)
    override fun viewer(): Boolean = ViewerMode.isOn(context)
    override fun lookValue(setting: HudhudLook.Setting): Int = when (setting) {
        HudhudLook.Setting.SIZE -> TextSize.level(context)
        HudhudLook.Setting.THEME -> ThemeMode.get(context)
    }

    override val profile: AnswerProfile = object : AnswerProfile {
        override fun businessName() = BusinessProfile.businessName(context)
        override fun businessNameUrdu() = BusinessProfile.businessNameUrdu(context)
        override fun businessAddress() = BusinessProfile.businessAddress(context)
        override fun businessPhone() = BusinessProfile.businessPhone(context)
        override fun businessEmail() = BusinessProfile.businessEmail(context)
        override fun strn() = BusinessProfile.strn(context)
        override fun taxLabel() = BusinessProfile.taxLabel(context)
        override fun termsAndConditions() = BusinessProfile.termsAndConditions(context)
        override fun currency() = BusinessProfile.currency(context)
        override fun country() = BusinessProfile.country(context)
        override fun calendar() = BusinessProfile.calendar(context)
        override fun trade() = BusinessProfile.trade(context)
        override fun bankName() = BusinessProfile.bankName(context)
        override fun bankAccountTitle() = BusinessProfile.bankAccountTitle(context)
        override fun bankIban() = BusinessProfile.bankIban(context)
        override fun bankJazzCash() = BusinessProfile.bankJazzCash(context)
    }

    override fun tradeLabel(key: String): String = Trade.of(key).label(context)
    override fun seasons(): Boolean = TradeFeatures.seasons(context)
    override fun seasonName(season: SeasonBook.Season): String = SeasonText.name(context, season)

    override fun appVersion(): String? = context.packageManager.getPackageInfo(context.packageName, 0).versionName
    override fun testBuild(): Boolean = context.packageName.endsWith(".dev")
    override fun usageCount(thing: Hudhud.Thing, from: Long, to: Long): Int = UsageLog.count(
        context, if (thing == Hudhud.Thing.CALCULATIONS) UsageLog.What.CALCULATIONS else UsageLog.What.APP_OPENS, from, to
    )
    override fun usageSince(): Long? = UsageLog.since(context)
    override fun lastBackupAt(): Long = BackupReminder.lastBackupAt(context)
    override fun otherPhoneAt(): Long = AlertNotes.otherPhoneAt(context)
    override fun backupFailedAt(): Long = AlertNotes.backupFailedAt(context)

    override suspend fun alerts(dao: KhataDao): List<AnswerAlert> =
        HomeAlerts.compute(context, dao) { Format.money(it) }.map { AnswerAlert(kindOf(it.kind), it.title) }

    override fun remindedToday(now: Long): Set<Long> = ReminderLog.openedToday(context, now)
    override suspend fun radarShopDays(dao: KhataDao): Int? = RadarUi.shopDays(context, dao)
    override fun radarReasons(reading: PaymentRadar.Reading, promise: Long?, tone: Int): String =
        RadarUi.reasons(context, reading, promise, tone)
    override fun toneThatWorked(partyId: Long, balance: Double): Int = RadarUi.toneThatWorked(context, partyId, balance)
    override fun automationSettings(): Automations.Settings = AndroidAutomationSettings(context)
    override fun screenNeeds(screen: ScreenId): Access.Permission? = AccessUi.needs(activity(screen))

    companion object {
        /** The bell's kind as the shared answers name it. No else: a kind added to the bell does not build until it is named. */
        fun kindOf(k: HomeAlerts.Kind): AnswerAlert.Kind = when (k) {
            HomeAlerts.Kind.CHEQUE -> AnswerAlert.Kind.CHEQUE
            HomeAlerts.Kind.PLAN -> AnswerAlert.Kind.PLAN
            HomeAlerts.Kind.BILL -> AnswerAlert.Kind.BILL
            HomeAlerts.Kind.EXPIRING -> AnswerAlert.Kind.EXPIRING
            HomeAlerts.Kind.STOCK -> AnswerAlert.Kind.STOCK
            HomeAlerts.Kind.DRAWER -> AnswerAlert.Kind.DRAWER
            HomeAlerts.Kind.BACKUP -> AnswerAlert.Kind.BACKUP
            HomeAlerts.Kind.BACKUP_FAILED -> AnswerAlert.Kind.BACKUP_FAILED
            HomeAlerts.Kind.BACKUP_OTHER_PHONE -> AnswerAlert.Kind.BACKUP_OTHER_PHONE
            HomeAlerts.Kind.CASHFLOW -> AnswerAlert.Kind.CASHFLOW
            HomeAlerts.Kind.UPDATED -> AnswerAlert.Kind.UPDATED
            HomeAlerts.Kind.SYNC -> AnswerAlert.Kind.SYNC
            HomeAlerts.Kind.LATE -> AnswerAlert.Kind.LATE
        }

        /** The phone's screen for a page. No else: a page added to [ScreenId] does not build until the phone says where it is. */
        fun activity(id: ScreenId): Class<*> = when (id) {
            ScreenId.ABOUT -> AboutActivity::class.java
            ScreenId.AGING -> AgingActivity::class.java
            ScreenId.ALERTS -> AlertsActivity::class.java
            ScreenId.AUDIT -> AuditActivity::class.java
            ScreenId.BACKUP -> BackupActivity::class.java
            ScreenId.BILLS -> BillsActivity::class.java
            ScreenId.BUSINESS_CARD -> BusinessCardActivity::class.java
            ScreenId.BUSINESS_SETTINGS -> BusinessSettingsActivity::class.java
            ScreenId.BUSINESS_SWITCH -> BusinessSwitchActivity::class.java
            ScreenId.CALCULATOR -> CalculatorActivity::class.java
            ScreenId.CASH_FLOW -> CashFlowActivity::class.java
            ScreenId.CASHBOOK -> CashbookActivity::class.java
            ScreenId.CHEQUES -> ChequesActivity::class.java
            ScreenId.COUNTER_SALE -> CounterSaleActivity::class.java
            ScreenId.DAY_BOOK -> DayBookActivity::class.java
            ScreenId.DUPLICATE_CUSTOMERS -> DuplicateCustomersActivity::class.java
            ScreenId.EXPIRING -> ExpiringActivity::class.java
            ScreenId.FOLLOW_UP -> FollowUpActivity::class.java
            ScreenId.HELP -> HelpActivity::class.java
            ScreenId.INSIGHTS -> InsightsActivity::class.java
            ScreenId.INVOICES -> InvoicesActivity::class.java
            ScreenId.KHATA -> KhataActivity::class.java
            ScreenId.LANGUAGE -> LanguageActivity::class.java
            ScreenId.LAPSED -> LapsedActivity::class.java
            ScreenId.MERA_KHATA -> MeraKhataActivity::class.java
            ScreenId.ORDERS -> OrdersActivity::class.java
            ScreenId.PAYMENT_VOICE -> PaymentVoiceActivity::class.java
            ScreenId.PLANS -> PlansActivity::class.java
            ScreenId.POSTER -> PosterActivity::class.java
            ScreenId.PRODUCT_CUSTOMERS -> ProductCustomersActivity::class.java
            ScreenId.PRODUCTS -> ProductsActivity::class.java
            ScreenId.RATE_LIST -> RateListActivity::class.java
            ScreenId.RECYCLE_BIN -> RecycleBinActivity::class.java
            ScreenId.SALES_REPORT -> SalesReportActivity::class.java
            ScreenId.SCHEMES -> SchemesActivity::class.java
            ScreenId.SEASONS -> SeasonsActivity::class.java
            ScreenId.SHIFT -> ShiftActivity::class.java
            ScreenId.SNAPSHOTS -> SnapshotsActivity::class.java
            ScreenId.STAFF -> StaffActivity::class.java
            ScreenId.SUPPLIER_RATES -> SupplierRatesActivity::class.java
            ScreenId.VIEWER -> ViewerActivity::class.java
            ScreenId.WARIS -> WarisActivity::class.java
            ScreenId.ZAKAT -> ZakatActivity::class.java
        }

        /** What a page opens on, as the phone's extras: the very keys the answers put in a Bundle before they moved. */
        fun extras(w: OpenWith): Bundle = Bundle().apply {
            when (w) {
                is OpenWith.SalesPeriod -> putString(SalesReportActivity.EXTRA_PERIOD, w.period)
                is OpenWith.SalesDays -> {
                    putLong(SalesReportActivity.EXTRA_FIRST_DAY, w.firstDay)
                    putLong(SalesReportActivity.EXTRA_LAST_DAY, w.lastDay)
                }
                is OpenWith.EntryForm -> {
                    putDouble(PartyDetailActivity.EXTRA_VOICE_AMOUNT, w.amount)
                    putBoolean(PartyDetailActivity.EXTRA_VOICE_IS_GIVEN, w.isGiven)
                    // The day the sentence said (16 Nov 2026); the form clamps it to now.
                    w.time?.let { putLong(PartyDetailActivity.EXTRA_VOICE_TIME, it) }
                }
                is OpenWith.ProductBuyers -> {
                    putLong(ProductCustomersActivity.EXTRA_PRODUCT_ID, w.productId)
                    putString(ProductCustomersActivity.EXTRA_PRODUCT_NAME, w.productName)
                    // The days Hudhud was asked about (17 Nov 2026): the screen opens on them.
                    if (w.from > 0L && w.to > w.from) {
                        putLong(ProductCustomersActivity.EXTRA_FROM, w.from)
                        putLong(ProductCustomersActivity.EXTRA_TO, w.to)
                    }
                }
                is OpenWith.Day -> putLong(DayBookActivity.EXTRA_DAY, w.day)
            }
        }
    }
}
