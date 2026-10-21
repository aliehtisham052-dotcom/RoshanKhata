package com.innovation313.roshankhata

import android.os.Bundle
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.ComposeView
import com.google.android.material.appbar.MaterialToolbar
import com.innovation313.roshankhata.data.AndroidWords
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.get
import com.innovation313.roshankhata.screens.RoshanTheme
import com.innovation313.roshankhata.screens.ShiftScreen
import com.innovation313.roshankhata.words.ProvideWords

/**
 * Shifts at the counter (21 Oct 2026): the shared [ShiftScreen] — open the
 * shift with the float, close it with the cash counted, the hours on the
 * clock. Opened from the counter's menu; whoever is signed in sees their own,
 * the owner and a manager everyone's.
 */
class ShiftActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shift)
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        val dark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val words = AndroidWords(this)
        val dao = KhataDatabase.get(this).khataDao()
        findViewById<ComposeView>(R.id.shiftCompose).setContent {
            RoshanTheme(dark = dark) { ProvideWords(words) { ShiftScreen(dao, Modifier.fillMaxSize()) } }
        }
    }
}
