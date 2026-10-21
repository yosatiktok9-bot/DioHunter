package com.paneer.diohunter

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class HunterApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CaptureStore.init(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CaptureVpnService.CHANNEL_ID,
                "Capture",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }
}
