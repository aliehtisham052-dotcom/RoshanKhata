package com.innovation313.roshankhata

import android.app.Application
import com.innovation313.roshankhata.data.CrashLog

/**
 * One job only: install the crash recorder before anything else runs, so even
 * a fault in the very first screen leaves a record behind.
 *
 * Nothing else belongs here. The last time this file existed it carried
 * app-wide UI behaviour, and a single mistake in it cut every screen at once
 * — screens are wired one by one now, visibly, in their own onCreate.
 */
class RoshanKhataApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
