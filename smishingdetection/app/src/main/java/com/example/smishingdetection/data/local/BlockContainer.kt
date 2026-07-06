package com.example.smishingdetection.data.local

import android.app.Application
import com.example.smishingdetection.data.local.database.SmishingDetectorDb

class BlockContainer: Application() {
    private val blockDataSource by lazy {
        SmishingDetectorDb.Companion.getDatabase(this)
            .blockedPhoneNumberDao()
    }
    val defaultBlockRepository by lazy {
        DefaultBlockRepository(blockDataSource)
    }
}