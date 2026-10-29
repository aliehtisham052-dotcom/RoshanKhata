package com.innovation313.roshankhata.data

import java.io.File
import java.util.Properties

/**
 * The computer's [ShopSettings]: one properties file per shop, the same
 * keys the phone uses, the same rules (blank reads as unset, values are
 * trimmed). Written straight away on every change, like the phone's apply().
 */
class FileShopSettings(private val file: File) : ShopSettings {

    private val props = Properties().apply {
        if (file.isFile) file.reader(Charsets.UTF_8).use { load(it) }
    }

    private fun text(key: String): String? = props.getProperty(key)?.takeIf { it.isNotBlank() }

    @Synchronized
    private fun put(vararg pairs: Pair<String, String>) {
        for ((k, v) in pairs) props.setProperty(k, v)
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writer(Charsets.UTF_8).use { props.store(it, "Roshan Khata shop settings") }
        // Swapped in one step (21 Oct 2026): Windows will not rename over a file, and the old way deleted
        // the settings first — a power cut in between lost them all (App Lock, counter mode, the backup folder).
        replaceAtomically(tmp, file)
    }

    private fun putText(key: String, value: String?) = put(key to value?.trim().orEmpty())

    override fun businessName() = text(ShopSettings.Keys.BUSINESS_NAME)
    override fun setBusinessName(value: String?) = putText(ShopSettings.Keys.BUSINESS_NAME, value)
    override fun businessNameUrdu() = text(ShopSettings.Keys.BUSINESS_NAME_URDU)
    override fun setBusinessNameUrdu(value: String?) = putText(ShopSettings.Keys.BUSINESS_NAME_URDU, value)
    override fun businessAddress() = text(ShopSettings.Keys.BUSINESS_ADDRESS)
    override fun setBusinessAddress(value: String?) = putText(ShopSettings.Keys.BUSINESS_ADDRESS, value)
    override fun businessPhone() = text(ShopSettings.Keys.BUSINESS_PHONE)
    override fun setBusinessPhone(value: String?) = putText(ShopSettings.Keys.BUSINESS_PHONE, value)
    override fun businessEmail() = text(ShopSettings.Keys.BUSINESS_EMAIL)
    override fun setBusinessEmail(value: String?) = putText(ShopSettings.Keys.BUSINESS_EMAIL, value)
    override fun bankName() = text(ShopSettings.Keys.BANK_NAME)
    override fun setBankName(value: String?) = putText(ShopSettings.Keys.BANK_NAME, value)
    override fun bankAccountTitle() = text(ShopSettings.Keys.BANK_TITLE)
    override fun setBankAccountTitle(value: String?) = putText(ShopSettings.Keys.BANK_TITLE, value)
    override fun bankIban() = text(ShopSettings.Keys.BANK_IBAN)
    override fun setBankIban(value: String?) = putText(ShopSettings.Keys.BANK_IBAN, value)
    override fun bankJazzCash() = text(ShopSettings.Keys.BANK_JAZZCASH)
    override fun setBankJazzCash(value: String?) = putText(ShopSettings.Keys.BANK_JAZZCASH, value)
    override fun termsAndConditions() = text(ShopSettings.Keys.TERMS)
    override fun setTermsAndConditions(value: String?) = putText(ShopSettings.Keys.TERMS, value)
    override fun strn() = text(ShopSettings.Keys.STRN)
    override fun setStrn(value: String?) = putText(ShopSettings.Keys.STRN, value)

    override fun currency(): String = Currency.clean(props.getProperty(ShopSettings.Keys.CURRENCY))
    override fun setCurrency(value: String?) = put(ShopSettings.Keys.CURRENCY to Currency.clean(value))
    override fun calendar(): ShopCalendar.Kind = ShopCalendar.Kind.of(props.getProperty(ShopSettings.Keys.CALENDAR))
    override fun setCalendar(kind: ShopCalendar.Kind) = put(ShopSettings.Keys.CALENDAR to kind.key)
    override fun country(): ShopCountry = ShopCountry.of(props.getProperty(ShopSettings.Keys.COUNTRY))
    override fun setCountry(country: ShopCountry) = put(ShopSettings.Keys.COUNTRY to country.code)

    override fun tradeKey() = text(ShopSettings.Keys.TRADE)
    override fun setTradeKey(key: String) = put(ShopSettings.Keys.TRADE to key)
    override fun otherTradeName() = text(ShopSettings.Keys.TRADE_OTHER_NAME)
    override fun otherTradeItems() = text(ShopSettings.Keys.TRADE_OTHER_ITEMS)
    override fun setOtherTrade(name: String?, items: String?) = put(
        ShopSettings.Keys.TRADE_OTHER_NAME to name?.trim().orEmpty(),
        ShopSettings.Keys.TRADE_OTHER_ITEMS to items?.trim().orEmpty()
    )

    override fun photoOnStatement() = props.getProperty(ShopSettings.Keys.PHOTO_ON_STATEMENT) == "true"
    override fun setPhotoOnStatement(enabled: Boolean) = put(ShopSettings.Keys.PHOTO_ON_STATEMENT to enabled.toString())
    // On unless switched off, as the phone's default reads.
    override fun paymentOnReminder() = props.getProperty(ShopSettings.Keys.PAYMENT_ON_REMINDER) != "false"
    override fun setPaymentOnReminder(enabled: Boolean) = put(ShopSettings.Keys.PAYMENT_ON_REMINDER to enabled.toString())

    /**
     * A new shop starts where the open one is (the phone's BusinessProfile.copyRegion, 23 Oct 2026; Marhala 4,
     * part 8 — the audit: a new business on the computer started as Pakistan): its country, currency sign and
     * calendar, so a second shop in Dhaka is not silently +92, Rs, STRN and Rabi/Kharif. Only what [from] has,
     * never over a choice this shop already made; the owner changes any of it in the new shop's Profile.
     */
    fun copyRegionFrom(from: FileShopSettings) {
        if (from === this) return
        // Present is chosen, even blank: "Other" is stored as an empty country code (the phone's rule, contains()).
        val pairs = listOf(ShopSettings.Keys.COUNTRY, ShopSettings.Keys.CURRENCY, ShopSettings.Keys.CALENDAR).mapNotNull { k ->
            val v = from.props.getProperty(k) ?: return@mapNotNull null
            if (!props.containsKey(k)) k to v else null
        }
        if (pairs.isNotEmpty()) put(*pairs.toTypedArray())
    }

    /** The computer prints from ShopCalendar.current and Currency.symbol: set them from the file. */
    override fun refreshDisplay() {
        ShopCalendar.current = calendar()
        Currency.symbol = currency()
        ShopCountry.current = country()
    }
}

/** [tmp] moved over [target] atomically where the disk allows, else replacing it in one move. */
internal fun replaceAtomically(tmp: java.io.File, target: java.io.File) {
    try {
        java.nio.file.Files.move(tmp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
    } catch (e: Exception) {
        java.nio.file.Files.move(tmp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
}
