package com.paintmixer.app

import android.app.Application
import com.paintmixer.app.di.AppContainer

class PaintMixerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
