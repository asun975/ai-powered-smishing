package com.example.prototypellm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.*

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION == intent.action) {
            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    for (smsMessage in Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                        val sender = smsMessage.displayOriginatingAddress
                        val messageBody = smsMessage.messageBody

                        Log.d("SMS_RECEIVER", "From: $sender")
                        Log.d("SMS_RECEIVER", "Message: $messageBody")

                        val apiKey = "AIzaSyAPBj13y91nEOuvRFCdP4XOxcpi154qfOw"
                        val analyzer = PhishingAnalyzer(apiKey)
                        val result = analyzer.analyzeMessage(messageBody)

                        Log.d("PHISHING_RESULT", "Result for $sender: $result")

                        // Send result to MainActivity
                        val resultIntent = Intent("com.example.prototypellm.SMS_RESULT")
                        resultIntent.putExtra("sender", sender)
                        resultIntent.putExtra("result", result)
                        context.sendBroadcast(resultIntent)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}