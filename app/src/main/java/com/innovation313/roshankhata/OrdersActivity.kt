package com.innovation313.roshankhata

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.ui.platform.ComposeView
import com.google.android.material.appbar.MaterialToolbar
import com.innovation313.roshankhata.data.AndroidWords
import com.innovation313.roshankhata.data.BusinessProfile
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.WhatsAppNumber
import com.innovation313.roshankhata.data.get
import com.innovation313.roshankhata.screens.OrdersScreen
import com.innovation313.roshankhata.screens.RoshanTheme
import com.innovation313.roshankhata.words.ProvideWords

/** Purchase orders on the phone (POS Phase 6): the shared screen; Send opens WhatsApp to the supplier, or the share sheet. */
class OrdersActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_orders)
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        val dark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val words = AndroidWords(this)
        val dao = KhataDatabase.get(this).khataDao()
        val shop = BusinessProfile.businessName(this)
        findViewById<ComposeView>(R.id.ordersCompose).setContent {
            RoshanTheme(dark = dark) {
                ProvideWords(words) {
                    OrdersScreen(dao, shop, onSend = { phone, text ->
                        val number = phone?.let { WhatsAppNumber.of(it) }
                        val sent = number?.let {
                            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$it?text=" + Uri.encode(text)))) }.isSuccess
                        } ?: false
                        if (!sent) startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), getString(R.string.po_send)))
                    }, onNeed = { p, then ->
                        // A cashier's order waits for a manager's PIN (23 Oct 2026, the review).
                        com.innovation313.roshankhata.ui.AccessUi.allowed(this@OrdersActivity, dao, p, com.innovation313.roshankhata.ui.AccessUi.what(this@OrdersActivity, p)) { then() }
                    }, pickDay = { startAt, onPicked ->
                        // A received batch's expiry, on the app's own calendar (the bill line's picker).
                        val cal = java.util.Calendar.getInstance().apply { timeInMillis = startAt }
                        com.innovation313.roshankhata.ui.BrandDatePickerDialog(
                            this@OrdersActivity,
                            { _, y, m, d ->
                                onPicked(java.util.Calendar.getInstance().apply { set(y, m, d, 0, 0, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis)
                            },
                            cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)
                        ).show()
                    })
                }
            }
        }
    }
}
