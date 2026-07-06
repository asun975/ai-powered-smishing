package com.example.smishingdetection

import android.app.Application
import com.example.smishingdetection.data.local.database.SmishingDetectorDb

class SmishingDetectorApplication: Application() {
    val database by lazy {
        SmishingDetectorDb.getDatabase(this)
    }
}