package com.example.smishingdetection

import android.app.Application
import com.example.smishingdetection.data.SmishingDetectorDb

class SmishingDetectorApplication: Application() {
    val database by lazy {
        SmishingDetectorDb.getDatabase(this)
    }
}