package com.defectview.app

import android.app.Application
import com.defectview.app.di.AppContainer

class DefectViewApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
