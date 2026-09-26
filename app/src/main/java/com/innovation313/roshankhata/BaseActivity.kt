package com.innovation313.roshankhata

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.innovation313.roshankhata.data.TextSize
import com.innovation313.roshankhata.ui.TextFit

/**
 * What every screen shares: the owner's text size.
 *
 * Every screen in the app extends this rather than AppCompatActivity, so the
 * size chosen once reaches all of them — there is no screen that forgot.
 *
 * The wrapped context is handed to AppCompat's own attachBaseContext, which
 * then applies the per-app language on top of it. The on-device TextSizeTest
 * checks both survive together — size AND language — because that pairing is
 * the part most likely to break in some future AppCompat.
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(TextSize.wrap(newBase))
    }

    /** After setContentView: let fixed-height buttons grow if the text did. */
    override fun onContentChanged() {
        super.onContentChanged()
        TextFit.relax(findViewById(android.R.id.content))
    }
}
