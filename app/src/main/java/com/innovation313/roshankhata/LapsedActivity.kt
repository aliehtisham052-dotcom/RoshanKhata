package com.innovation313.roshankhata

import android.content.Intent
import android.os.Bundle
import androidx.compose.ui.platform.ComposeView
import com.google.android.material.appbar.MaterialToolbar
import com.innovation313.roshankhata.data.AndroidWords
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.get
import com.innovation313.roshankhata.screens.LapsedScreen
import com.innovation313.roshankhata.screens.RoshanTheme
import com.innovation313.roshankhata.words.ProvideWords

/**
 * Customers who stopped coming (3 Nov 2026, [com.innovation313.roshankhata.data.Lapsed]): regulars whose last
 * purchase is long past their usual gap, biggest buyers first. A tap opens the customer, where the owner calls or
 * sends a WhatsApp himself; nothing is sent from here.
 */
class LapsedActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lapsed)
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        val dark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val words = AndroidWords(this)
        val dao = KhataDatabase.get(this).khataDao()
        findViewById<ComposeView>(R.id.lapsedCompose).setContent {
            RoshanTheme(dark = dark) {
                ProvideWords(words) {
                    LapsedScreen(dao, onOpen = { id ->
                        startActivity(Intent(this, PartyDetailActivity::class.java).putExtra(PartyDetailActivity.EXTRA_PARTY_ID, id))
                    })
                }
            }
        }
    }
}
