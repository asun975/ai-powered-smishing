package com.example.smishingdetection.data.sms

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.activity.contextaware.ContextAware

class SmsContentObserver(
    private val context: Context,
    private val onSmsChanged: (Uri?) -> Unit
) : ContentObserver(Handler(Looper.getMainLooper())) {

    fun register() {
        context.contentResolver.registerContentObserver(
            Telephony.Sms.CONTENT_URI,
            true, // observe descendants (inbox, sent, etc)
            this
        )
    }

    fun unregister() {
        context.contentResolver.unregisterContentObserver(this)
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        onSmsChanged(uri)
    }
}