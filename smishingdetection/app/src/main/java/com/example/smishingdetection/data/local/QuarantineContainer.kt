package com.example.smishingdetection.data.local

import android.app.Application
import com.example.smishingdetection.data.local.database.SmishingDetectorDb

class QuarantineContainer: Application() {
    private val quarantineDataSource by lazy {
        SmishingDetectorDb.Companion.getDatabase(this)
            .analyzedMessageDao()
    }
    val quarantineRepository by lazy {
        QuarantineRepository(quarantineDataSource)
    }
}