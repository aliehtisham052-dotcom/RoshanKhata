package com.innovation313.roshankhata

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.innovation313.roshankhata.data.AutoBackup
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
        registerActivityLifecycleCallbacks(BackgroundWatcher(this))
    }

    /**
     * Notice when the app leaves the screen, so the ledger can be backed up.
     *
     * The rule at the top of this file says app-wide behaviour does not belong
     * here, and this is the second deliberate exception, for the same shape of
     * reason as the first: there is no screen that owns "the app was closed".
     * Wiring it into one activity would miss every other way out, and wiring it
     * into all of them would be the exact fragility the rule guards against.
     *
     * Counting started activities rather than using ProcessLifecycleOwner keeps
     * a dependency out for a behaviour this small. A rotation stops one
     * activity and starts the next, so the count dips and rises without ever
     * reaching zero — which is right: rotating is not leaving.
     *
     * It only ENQUEUES work. Nothing is uploaded on this thread, nothing
     * blocks the app closing, and every condition worth checking is checked
     * later, in the worker.
     */
    private class BackgroundWatcher(private val app: Application) :
        Application.ActivityLifecycleCallbacks {

        private var started = 0

        override fun onActivityStarted(activity: Activity) {
            started++
        }

        override fun onActivityStopped(activity: Activity) {
            started--
            if (started <= 0) {
                started = 0
                runCatching { AutoBackup.onAppBackgrounded(app) }
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
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
