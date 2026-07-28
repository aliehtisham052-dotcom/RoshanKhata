package com.innovation313.roshankhata

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.innovation313.roshankhata.data.CrashLog
import com.innovation313.roshankhata.data.ScreenSecurity

/**
 * Install the crash recorder before anything else runs, so even a fault in the
 * very first screen leaves a record behind — and hold the one window flag that
 * must not be allowed to miss a screen.
 *
 * ALMOST nothing else belongs here. The last time this file carried app-wide UI
 * behaviour, a single mistake in it cut every screen at once, and the rule
 * since has been that screens are wired one by one, visibly, in their own
 * onCreate. That rule stands.
 *
 * Screen privacy is a deliberate exception, for two reasons:
 *
 *  - It is a SECURITY flag, and the failure modes are not symmetrical. Wiring
 *    it into twenty-four activities by hand means that the day a twenty-fifth
 *    is added and the line is forgotten, there is a screen that silently leaks
 *    the ledger into the recent-apps switcher — and nothing fails, nothing
 *    looks wrong, and no test catches it. Here it cannot be forgotten, because
 *    no activity can opt out of being created.
 *
 *  - It is a window flag, not layout, theme or content. It cannot make a screen
 *    render wrongly; the worst it can do is blank a thumbnail.
 *
 * And it is wrapped, so even that worst case cannot stop a screen opening: if
 * setting a flag ever throws on some device, the ledger still comes up. A
 * privacy flag is not worth an app that will not start.
 */
class RoshanKhataApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        registerActivityLifecycleCallbacks(ScreenSecurityCallbacks())
    }

    private class ScreenSecurityCallbacks : Application.ActivityLifecycleCallbacks {

        /**
         * Before the first frame is drawn, so nothing is ever captured in the
         * gap between a screen appearing and the flag arriving.
         */
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            runCatching { ScreenSecurity.hideFromRecents(activity) }
            runCatching { ScreenSecurity.applyTo(activity) }
        }

        /** Again here, so flipping the switch takes effect without a restart. */
        override fun onActivityResumed(activity: Activity) {
            runCatching { ScreenSecurity.applyTo(activity) }
        }

        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }
}
