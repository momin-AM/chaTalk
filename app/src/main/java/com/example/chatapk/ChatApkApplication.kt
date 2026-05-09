package com.example.chatapk

import android.app.Application
import com.example.chatapk.di.AppContainer
import com.example.chatapk.di.NotificationChannels

class ChatApkApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationChannels.create(this)
    }
}
