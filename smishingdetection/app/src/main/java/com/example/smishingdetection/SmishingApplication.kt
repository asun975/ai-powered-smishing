package com.example.smishingdetection

import android.app.Application

class SmishingApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
    }
}