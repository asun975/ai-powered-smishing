package com.example.smishingdetection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.provider.Telephony.Sms.Intents

class SMSMessageTracking : BroadcastReceiver() {
    override fun onReceive(p0: Context?, p1: Intent?) {
        if (p1?.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(p1)
            for (sms in messages) {
                val body = sms.displayMessageBody
                val sender = sms.displayOriginatingAddress ?: "Unknown"
                val (classifierInput, llmInput, urls) = preprocessSmsMessage(body)
                processSmsMessage(sender, body, classifierInput, llmInput, "BROADCAST", urls)
            }
        }
    }
}