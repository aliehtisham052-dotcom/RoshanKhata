package com.innovation313.roshankhata

import android.app.Application
import com.innovation313.roshankhata.ui.EdgeToEdge

/**
 * The application object, which exists for one job.
 *
 * From Android 15 every screen is drawn behind the status and navigation bars,
 * and from 16 that cannot be turned off. Handling it here means one piece of
 * code covers two dozen screens and any screen added after this — rather than
 * two dozen edits, one of which would have been missed.
 */
class RoshanKhataApp : Application() {
    override fun onCreate() {
        super.onCreate()
        EdgeToEdge.applyToAllScreens(this)
    }
}
