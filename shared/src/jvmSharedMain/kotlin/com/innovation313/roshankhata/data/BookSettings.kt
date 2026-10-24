package com.innovation313.roshankhata.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A setting that belongs to the book, not to one device (schema 36): it
 * travels with every backup, so the phone and the computer read the same
 * value. Keys are [BookSettings]' constants; values are text.
 */
@Entity(tableName = "book_settings")
data class BookSetting(
    @PrimaryKey val key: String,
    val value: String?
)

/** The book's own settings, by key. Read and written through the DAO's settingValue / putSetting. */
object BookSettings {
    /** The owner's counter PIN, as a salted PBKDF2 hash ([PinHash]) and its salt. Never the PIN. */
    const val OWNER_PIN_HASH = "owner_pin_hash"
    const val OWNER_PIN_SALT = "owner_pin_salt"

    /** The owner's recovery code ([RecoveryCode]), as a hash and its salt: it sets a new owner PIN when the old one is forgotten. */
    const val OWNER_RECOVERY_HASH = "owner_recovery_hash"
    const val OWNER_RECOVERY_SALT = "owner_recovery_salt"

    /** "1" when the products' prices already hold their tax (the usual retail way); else tax is added on top. */
    const val PRICES_INCLUDE_TAX = "prices_include_tax"

    /** Loyalty (schema 36): points earned per 100 of money spent, and what one point is worth when spent. Unset = loyalty off. */
    const val LOYALTY_POINTS_PER_100 = "loyalty_points_per_100"
    const val LOYALTY_POINT_VALUE = "loyalty_point_value"

    /**
     * A weighing scale's labels ([ScaleCode], 21 Oct 2026): their two-digit
     * prefix ("21" when unset), and "1" when the scale prints the price
     * instead of the weight.
     */
    const val SCALE_PREFIX = "scale_prefix"
    const val SCALE_PRICE = "scale_price"
    /** Digits after the decimal point in a price label: "0".."3" (2 when unset), see [ScaleCode.Rule]. */
    const val SCALE_PRICE_DECIMALS = "scale_price_decimals"

    /**
     * The counter's big-shop tools (24 Oct 2026, the owner: "ye kya bala bana
     * di"): shifts, the day report (Z), loyalty, the weighing scale, the
     * customer's screen and the ticket's tax box. "1" shows them, "0" hides
     * them, unset decides by the book ([counterAdvanced]). Hidden is only
     * hidden: no figure, rule or permission changes with it.
     */
    const val COUNTER_ADVANCED = "counter_advanced"

    /**
     * Whether the counter shows its big-shop tools: the owner's word when he
     * gave one, else on only for a book already using any of them — loyalty,
     * prices with tax, a scale label rule, counter mode, staff with a counter
     * role — so turning this in hides nothing a shop already relies on.
     */
    suspend fun counterAdvanced(dao: KhataDao): Boolean {
        when (dao.settingValue(COUNTER_ADVANCED)) {
            "1" -> return true
            "0" -> return false
        }
        if (isOn(dao.settingValue(PRICES_INCLUDE_TAX)) || isOn(dao.settingValue(SCALE_PRICE)) || isOn(dao.settingValue(COUNTER_USED))) return true
        if (dao.settingValue(SCALE_PREFIX) != null) return true
        if (Loyalty.rule(dao) != null) return true
        return runCatching { dao.allStaffForBackup().any { it.isActive && it.role != null } }.getOrDefault(false)
    }

    /** "1" when the counter refuses to sell more than the shelf holds (else it warns). */
    const val BLOCK_NEGATIVE_STOCK = "block_negative_stock"

    /**
     * Counter mode as the book remembers it (21 Oct 2026, the critical
     * review): "1" once any device has been a counter for this book, and the
     * devices the owner has since made their own (comma-separated ids, see
     * [Access.deviceId]). A computer whose own settings file says "not a
     * counter" is still a counter while the book says so and it is not one of
     * the owner's — a settings file anyone at the till can edit no longer
     * decides who the owner is.
     */
    const val COUNTER_USED = "counter_used"
    const val OWNER_DEVICES = "owner_devices"

    /** The owner's PIN and recovery code: kept together, or not at all. */
    val OWNER_PIN_KEYS = setOf(OWNER_PIN_HASH, OWNER_PIN_SALT, OWNER_RECOVERY_HASH, OWNER_RECOVERY_SALT)

    /** Who may do what on this book's devices: kept by a restore whose copy says nothing of them ([KhataDao.restoreAll]). */
    val ACCESS_KEYS = OWNER_PIN_KEYS + setOf(COUNTER_USED, OWNER_DEVICES)

    /**
     * "1" when live sync may also go through Innovation 313's relay on the
     * internet (LiveCloud) while the two devices are not on one WiFi (22 Oct
     * 2026, the review: it was on for everyone while the screens said nothing
     * went to the internet). One switch for the book, so both devices agree;
     * off until the owner turns it on.
     */
    const val LIVE_RELAY = "live_relay"

    /**
     * The highest RK- entry number the book has used, noted before the bin is
     * purged (23 Oct 2026, the review): numbering goes on past it, so a number
     * purged from the top is never given out again.
     */
    const val ENTRY_NUMBER_HIGH = "rk_high"

    fun isOn(v: String?): Boolean = v == "1"
}

/** A fresh row identity: 32 lowercase hex characters, as the migration's randomblob(16) writes them. */
object Uid {
    fun next(): String = UUID.randomUUID().toString().replace("-", "")
}
