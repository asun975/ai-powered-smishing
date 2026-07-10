package com.example.smishingdetection.ui.mainActivity

import android.app.Application
import android.content.Context
import com.example.smishingdetection.data.sms.DefaultSmsProvider
import kotlinx.coroutines.Dispatchers

class SmsContainer(
    private val context: Context
): Application() {
    private val ioDispatcher = Dispatchers.IO
    private val defaultSmsProvider =
        DefaultSmsProvider(context.applicationContext, ioDispatcher)

    val smsRepository by lazy {
        com.example.smishingdetection.data.sms.SmsRepository(defaultSmsProvider)
    }
}