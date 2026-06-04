package com.example.classifierv2

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var smsTextView: TextView
    private lateinit var resultTextView: TextView
    private lateinit var explanationTextView: TextView
    private lateinit var classifier: SmishingClassifier
    private lateinit var explainer: LlmExplainer          // NEW
    private var smsReceiver: BroadcastReceiver? = null
    private var smsObserver: ContentObserver? = null
    private var lastProcessedSmsId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d("MainActivity", "========== APP STARTED ==========")

        smsTextView = findViewById(R.id.smsTextView)
        resultTextView = findViewById(R.id.resultTextView)
        explanationTextView = findViewById(R.id.explanationTextView)

        val apiUrl = "https://totoro2211-classifierv2.hf.space/classify"
        val llmUrl = "https://totoro2211-smishing-llm-groq.hf.space/explain"  // NEW

        classifier = SmishingClassifier(apiUrl)
        explainer = LlmExplainer(llmUrl)                  // NEW

        resultTextView.text = "⏳ Setting up..."

        requestPermissions()
    }

    private fun requestPermissions() {
        Log.d("MainActivity", "Checking permissions...")

        val permissionsNeeded = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.RECEIVE_SMS)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.READ_SMS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), 999)
        } else {
            startBothDetectionMethods()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 999) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (allGranted) {
                startBothDetectionMethods()
            } else {
                resultTextView.text = "❌ Permissions needed!"
            }
        }
    }

    private fun startBothDetectionMethods() {
        Log.d("MainActivity", "========== STARTING DUAL DETECTION ==========")
        resultTextView.text = "✅ Ready! Waiting for SMS...\n(Using 2 detection methods)"
        Toast.makeText(this, "SMS Detector Active (Dual Mode)!", Toast.LENGTH_SHORT).show()

        startBroadcastReceiver()
        startDatabaseObserver()
    }

    private fun startBroadcastReceiver() {
        Log.d("MainActivity", "Starting broadcast receiver...")

        smsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d("MainActivity", "========== BROADCAST RECEIVED ==========")

                try {
                    if (intent?.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
                        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

                        for (sms in messages) {
                            val body = sms.displayMessageBody
                            val sender = sms.displayOriginatingAddress ?: "Unknown"

                            Log.d("MainActivity", "Broadcast detected: from $sender")
                            processSmsMessage(sender, body, "BROADCAST")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Broadcast error: ${e.message}", e)
                }
            }
        }

        val filter = IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION).apply {
            priority = IntentFilter.SYSTEM_HIGH_PRIORITY
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(smsReceiver, filter)
        }

        Log.d("MainActivity", "Broadcast receiver registered!")
    }

    private fun startDatabaseObserver() {
        Log.d("MainActivity", "Starting database observer...")

        smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                Log.d("MainActivity", "========== DATABASE CHANGED ==========")
                checkLatestSms()
            }
        }

        contentResolver.registerContentObserver(
            Telephony.Sms.CONTENT_URI,
            true,
            smsObserver!!
        )

        Log.d("MainActivity", "Database observer registered!")
    }

    private fun checkLatestSms() {
        try {
            val cursor: Cursor? = contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                null,
                null,
                "${Telephony.Sms.DATE} DESC LIMIT 1"
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val idIndex = it.getColumnIndex(Telephony.Sms._ID)
                    val senderIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
                    val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)

                    val smsId = if (idIndex >= 0) it.getString(idIndex) else null
                    val sender = if (senderIndex >= 0) it.getString(senderIndex) else "Unknown"
                    val body = if (bodyIndex >= 0) it.getString(bodyIndex) else ""

                    Log.d("MainActivity", "Database check: ID=$smsId from $sender")

                    if (smsId != null && smsId != lastProcessedSmsId) {
                        lastProcessedSmsId = smsId
                        processSmsMessage(sender, body, "DATABASE")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Database check error: ${e.message}", e)
        }
    }

    private fun processSmsMessage(sender: String, body: String, source: String) {
        Log.d("MainActivity", "---------- PROCESSING SMS ($source) ----------")
        Log.d("MainActivity", "From: $sender")
        Log.d("MainActivity", "Message: $body")

        runOnUiThread {
            smsTextView.text = "From: $sender\n\nMessage: $body\n\n(Detected via: $source)"
            resultTextView.text = "🔍 Analyzing..."
            Toast.makeText(this, "SMS Detected via $source!", Toast.LENGTH_SHORT).show()
        }

        lifecycleScope.launch {
            classifyMessage(body)
        }
    }

    private suspend fun classifyMessage(body: String) {
        Log.d("MainActivity", "========== CLASSIFICATION START ==========")
        try {
            val (label, confidence) = classifier.classify(body)
            val percentage = (confidence * 100).toInt()

            Log.d("MainActivity", "Result: $label ($percentage%)")

            if (label == "ERROR") {
                runOnUiThread {
                    resultTextView.text = "❌ Error analyzing\nCheck internet"
                    resultTextView.setTextColor(getColor(android.R.color.darker_gray))
                }
                return
            }

            // Show classification result while LLM loads
            runOnUiThread {
                when (label) {
                    "SPAM" -> {
                        resultTextView.text = "⚠️ SMISHING DETECTED!\n\n$percentage% confidence"
                        resultTextView.setTextColor(getColor(android.R.color.holo_red_light))
                        explanationTextView.text = "💬 Getting explanation..."
                        explanationTextView.setTextColor(getColor(android.R.color.darker_gray))
                        Toast.makeText(this@MainActivity, "SMISHING DETECTED!", Toast.LENGTH_LONG).show()
                    }
                    "SAFE" -> {
                        resultTextView.text = "✅ Message appears safe\n\n$percentage% confidence"
                        resultTextView.setTextColor(getColor(android.R.color.holo_green_dark))
                        explanationTextView.text = "💬 Getting explanation..."
                        explanationTextView.setTextColor(getColor(android.R.color.darker_gray))
                    }
                }
            }

            // Call LLM for explanation
            val explanation = explainer.explain(body, label, confidence)
            Log.d("MainActivity", "Explanation: $explanation")

            runOnUiThread {
                explanationTextView.text = "💬 $explanation"
                when (label) {
                    "SPAM" -> explanationTextView.setTextColor(getColor(android.R.color.holo_red_light))
                    "SAFE" -> explanationTextView.setTextColor(getColor(android.R.color.holo_green_dark))
                }
            }

        } catch (e: Exception) {
            Log.e("MainActivity", "Classification error: ${e.message}", e)
            runOnUiThread {
                resultTextView.text = "❌ Error: ${e.message}"
            }
        }
        Log.d("MainActivity", "========== CLASSIFICATION END ==========")
    }

    override fun onDestroy() {
        super.onDestroy()

        smsReceiver?.let {
            try {
                unregisterReceiver(it)
                Log.d("MainActivity", "Broadcast receiver unregistered")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error unregistering receiver: ${e.message}")
            }
        }

        smsObserver?.let {
            try {
                contentResolver.unregisterContentObserver(it)
                Log.d("MainActivity", "Database observer unregistered")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error unregistering observer: ${e.message}")
            }
        }
    }
}