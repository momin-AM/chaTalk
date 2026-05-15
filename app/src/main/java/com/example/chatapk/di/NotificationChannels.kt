package com.example.chatapk.di

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val MESSAGES = "messages"

    fun create(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        
        // Remove the old beta notification if it exists
        manager.cancel(1337)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        
        // Main messages channel
        val channel = NotificationChannel(
            MESSAGES,
            "Messages",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "New chat message alerts"
        }
        manager.createNotificationChannel(channel)
    }
}
