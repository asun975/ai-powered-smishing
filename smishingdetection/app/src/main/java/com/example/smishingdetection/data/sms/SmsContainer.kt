package com.example.smishingdetection.data.sms

import android.app.Application
import android.content.Context
import kotlinx.coroutines.Dispatchers

class SmsContainer(
    private val context: Context
): Application() {
    private val ioDispatcher = Dispatchers.IO
    private val defaultSmsProvider =
        DefaultSmsProvider(context.applicationContext, ioDispatcher)

    val smsRepository by lazy {
        SmsRepository(defaultSmsProvider)
    }
}