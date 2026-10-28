package com.innovation313.roshankhata

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import com.google.android.material.appbar.MaterialToolbar
import com.innovation313.roshankhata.data.AndroidWords
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.get
import com.innovation313.roshankhata.screens.AgingScreen
import com.innovation313.roshankhata.screens.RoshanTheme
import com.innovation313.roshankhata.words.ProvideWords

/**
 * Dues by age (21 Oct 2026): the shared [AgingScreen] — what each party still
 * owes, or is owed, split by how old it is. A tap opens the party; coming
 * back reads the figures again.
 */
class AgingActivity : BaseActivity() {
    private var reload by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_aging)
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        val dark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val words = AndroidWords(this)
        val dao = KhataDatabase.get(this).khataDao()
        findViewById<ComposeView>(R.id.agingCompose).setContent {
            RoshanTheme(dark = dark) {
                ProvideWords(words) {
                    AgingScreen(dao, reload = reload, onParty = { id ->
                        startActivity(Intent(this, PartyDetailActivity::class.java).putExtra(PartyDetailActivity.EXTRA_PARTY_ID, id))
                    })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reload++
    }
}
