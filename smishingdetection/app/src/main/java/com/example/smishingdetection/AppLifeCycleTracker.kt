package com.example.smishingdetection

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Meant to track whether the app is currently in the foreground, so
 * MainActivity can decide between showing an in-app dialog (foreground) or a
 * system notification (background) for a risky message. Implements
 * DefaultLifecycleObserver, which requires something to register it against
 * a Lifecycle (e.g. ProcessLifecycleOwner) for onStart()/onStop() to ever
 * actually fire.
 */
object AppLifecycleTracker : DefaultLifecycleObserver {
    /** True while the app is in the foreground, false while backgrounded. Read-only outside this file. */
    var isAppInForeground = false
        private set

    /** Called when the app (or whatever Lifecycle this is registered to) comes to the foreground. */
    override fun onStart(owner: LifecycleOwner) {
        isAppInForeground = true
    }

    /** Called when the app (or whatever Lifecycle this is registered to) goes to the background. */
    override fun onStop(owner: LifecycleOwner) {
        isAppInForeground = false
    }
}