package com.innovation313.roshankhata.data

/**
 * The shop's own details as text (KMP Phase 4d): name, address, bank,
 * terms, currency, calendar, trade, photo-on-statement. The phone keeps them
 * in SharedPreferences (BusinessProfile, through AndroidShopSettings); the
 * computer in a settings file with the same keys (FileShopSettings). Shared
 * code — the backup above all — reads and writes them only through here.
 *
 * Getters return null for blank, setters trim, as BusinessProfile always has.
 */
interface ShopSettings {
    fun businessName(): String?
    fun setBusinessName(value: String?)
    fun businessNameUrdu(): String?
    fun setBusinessNameUrdu(value: String?)
    fun businessAddress(): String?
    fun setBusinessAddress(value: String?)
    fun businessPhone(): String?
    fun setBusinessPhone(value: String?)
    fun businessEmail(): String?
    fun setBusinessEmail(value: String?)
    fun bankName(): String?
    fun setBankName(value: String?)
    fun bankAccountTitle(): String?
    fun setBankAccountTitle(value: String?)
    fun bankIban(): String?
    fun setBankIban(value: String?)
    fun bankJazzCash(): String?
    fun setBankJazzCash(value: String?)
    fun termsAndConditions(): String?
    fun setTermsAndConditions(value: String?)
    fun strn(): String?
    fun setStrn(value: String?)

    /** The money sign, never blank (Currency.DEFAULT when unset). */
    fun currency(): String
    fun setCurrency(value: String?)
    fun calendar(): ShopCalendar.Kind
    fun setCalendar(kind: ShopCalendar.Kind)
    /** The shop's country (21 Oct 2026); PAKISTAN for a book from before the setting. */
    fun country(): ShopCountry
    fun setCountry(country: ShopCountry)

    /** The trade's stored key (Trade.name on the phone), or null when never chosen. */
    fun tradeKey(): String?
    fun setTradeKey(key: String)
    fun otherTradeName(): String?
    fun otherTradeItems(): String?
    fun setOtherTrade(name: String?, items: String?)

    fun photoOnStatement(): Boolean
    fun setPhotoOnStatement(enabled: Boolean)

    /** The wallet and bank details under every payment reminder (the phone's setting since 2 Oct; on by default). */
    fun paymentOnReminder(): Boolean
    fun setPaymentOnReminder(enabled: Boolean)

    /** After a restore: whatever is printed from these (the calendar) re-reads them. */
    fun refreshDisplay()

    /** The keys both platforms store these under (the phone's SharedPreferences keys). */
    object Keys {
        const val BUSINESS_NAME = "business_name"
        const val BUSINESS_NAME_URDU = "business_name_urdu"
        const val BUSINESS_ADDRESS = "business_address"
        const val BUSINESS_PHONE = "business_phone"
        const val BUSINESS_EMAIL = "business_email"
        const val BANK_NAME = "bank_name"
        const val BANK_TITLE = "bank_account_title"
        const val BANK_IBAN = "bank_iban"
        const val BANK_JAZZCASH = "bank_jazzcash"
        const val TERMS = "terms_and_conditions"
        const val STRN = "strn_ntn"
        const val CURRENCY = "currency"
        const val CALENDAR = "calendar"
        const val COUNTRY = "country"
        const val TRADE = "trade"
        const val TRADE_OTHER_NAME = "trade_other_name"
        const val TRADE_OTHER_ITEMS = "trade_other_items"
        const val PHOTO_ON_STATEMENT = "photo_on_statement"
        const val PAYMENT_ON_REMINDER = "payment_on_reminder"
    }
}
