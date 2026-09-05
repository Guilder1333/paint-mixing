package com.paintmixer.app

import android.app.Application
import com.paintmixer.app.capture.RemoteShutterController
import com.paintmixer.app.di.AppContainer

class PaintMixerApp : Application() {
    lateinit var container: AppContainer
        private set

    /**
     * App-wide singleton, not Activity-scoped: [com.paintmixer.app.capture.ShutterAccessibilityService]
     * is a separate Android component (not part of the Activity) that also needs to reach it.
     */
    val remoteShutter = RemoteShutterController()

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
