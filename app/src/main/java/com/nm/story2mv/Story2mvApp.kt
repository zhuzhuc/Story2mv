package com.nm.story2mv

import android.app.Application
import com.nm.story2mv.di.AppContainer

class Story2mvApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

