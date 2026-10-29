package com.juanmediahub.usbotgorganizer

import android.app.Application
import com.juanmediahub.usbotgorganizer.di.AppContainer

class OtgExplorerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
