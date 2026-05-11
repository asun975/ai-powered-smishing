package com.example.smslocalai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        for (sms in messages) {
            val sender = sms.originatingAddress ?: "Unknown"
            val messageBody = sms.messageBody ?: ""

            Log.d("SmsReceiver", "SMS from: $sender")
            
            // Run AI analysis
            analyzeSMS(context, messageBody)
        }
    }

    private fun analyzeSMS(context: Context, message: String) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val aiManager = AIManager.getInstance(context)
                val result = aiManager.analyzeSMS(message)

                Log.d("SmsReceiver", "AI RESULT: $result")

                NotificationHelper.showNotification(
                    context,
                    "Phishing Scan Result",
                    result
                )
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Error in background analysis", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
