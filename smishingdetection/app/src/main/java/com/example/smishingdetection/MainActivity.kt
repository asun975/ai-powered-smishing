package com.example.smishingdetection

import android.content.BroadcastReceiver
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.util.Log
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var smsTextView: TextView
    private lateinit var resultTextView: TextView
    private lateinit var classifier: SmishingClassifier
    private var smsReceiver: BroadcastReceiver? = null
    private var smsObserver: ContentObserver? = null
    private var lastProcessedSmsId: String? = null

    // Static set up
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) // initialize activity
        setContentView(R.layout.activity_main) // UI

        Log.d("MainActivity", "======== App Started ========")
        // Widgets
        smsTextView = findViewById(R.id.smsTextView)
        resultTextView = findViewById(R.id.resultTextView)

        // Generated in Collab code
        val apiURL = "https://[username-space].hf.space/classify"
        classifier = SmishingClassifier(apiURL)

        resultTextView.text = "Setting up"

        requestPermissions()

    }

    // todo override fun onPause()

    //
    private fun requestPermissions() {
        Log.d("MainActivity", "Checking permissions")
        // List of permissions to request
        val permissionsNeeded = mutableListOf<String>()

        // Check app permission for receiving SMS
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.RECEIVE_SMS)
        }
        // Check app permission for reading SMS
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.READ_SMS)
        }
        // Check app permission for post notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        // If permissionsNeeded is not empty request permission from user
        // or start SMS detection methods
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

        if(requestCode===999) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if(allGranted) {
                startBothDetectionMethods()
            } else {
                resultTextView.text = "Permissions needed!"
            }
        }
    }

    /**
     * Start BOTH broadcast receiver AND database observer
     */
    private fun startBothDetectionMethods() {
        Log.d("MainActivity", "========== STARTING DUAL DETECTION ==========")
        resultTextView.text = "Ready! Waiting for SMS...\n(Using 2 detection methods)"
        Toast.makeText(this, "SMS Detector Active (Dual Mode)!", Toast.LENGTH_SHORT).show()

        // Method 1: Broadcast Receiver
        // broadcast receiver doesn't receive sms messages
        startBroadcastReceiver()

        // Method 2: Database Observer
        startDatabaseObserver()
    }

    /**
     * Method 1: Broadcast Receiver (traditional method)
     */
    private fun startBroadcastReceiver() {
        Log.d("MainActivity", "Starting broadcast receiver...")

        smsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d("MainActivity", "========== BROADCAST RECEIVED ==========")

                try {
                    if (intent?.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
                        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                        // Display received messages
                        for (sms in messages) {
                            val body = sms.displayMessageBody
                            val sender = sms.displayOriginatingAddress ?: "Unknown"

                            Log.d("MainActivity", "Broadcast detected: from $sender")
                            val (classifierInput, llmInput) = preprocessSmsMessage(body)
                            processSmsMessage(sender, classifierInput, llmInput, "BROADCAST")
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

    /**
     * Method 2: SMS Database Observer (monitors SMS database for changes)
     */
    private fun startDatabaseObserver() {
        Log.d("MainActivity", "Starting database observer...")

        smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                Log.d("MainActivity", "========== DATABASE CHANGED ==========")
                checkLatestSms()
            }
        }

        // Monitor the SMS inbox URI
        contentResolver.registerContentObserver(
            Telephony.Sms.CONTENT_URI,
            true,
            smsObserver!!
        )

        Log.d("MainActivity", "Database observer registered!")
    }

    /**
     * Check the latest SMS from database
     */
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

                    // Only process if this is a new SMS
                    if (smsId != null && smsId != lastProcessedSmsId) {
                        lastProcessedSmsId = smsId
                        val (classifierInput, llmInput) = preprocessSmsMessage(body)
                        processSmsMessage(sender, classifierInput, llmInput,"DATABASE")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Database check error: ${e.message}", e)
        }
    }

    /**
     * Preprocess SMS message for input to classifier and LLM
     * Data sanitization: remove PII in classifier input and mask PII for LLM to keep context
     * Remove unnecessary characters in classifier input to improve model performance
    **/
    private fun preprocessSmsMessage(body: String): Pair<String, String> {
        val preprocessor = Preprocessing
        val classifierInput = preprocessor.preprocessClassifierText(body)
        val llmInput = preprocessor.preprocessLlmText(body)

        Log.d("MainActivity", "---------- DATA CLEANING MODULE ----------")
        Log.d("MainActivity", "Sanitization (classifier): $classifierInput")
        Log.d("MainActivity", "PII masking (LLM): $llmInput")

        return Pair(classifierInput, llmInput)
    }

    /**
     * Process SMS message (called by either broadcast OR database observer)
     * Log message and change UI for user
     * classify sms message
     * TODO: use llmInput for LLM explainer API Integration
     */
    private fun processSmsMessage(sender: String,
                                  classifierInput: String,
                                  llmInput: String,
                                  source: String) {

        // Do not log SMS text
        Log.d("MainActivity", "---------- PROCESSING SMS ($source) ----------")
        Log.d("MainActivity", "From: $sender")
        Log.d("MainActivity", "Text: $classifierInput")

        runOnUiThread {
            smsTextView.text = "From: $sender\n\nMessage: $llmInput\n\n(Detected via: $source)"
            resultTextView.text = "Analyzing..."
            Toast.makeText(this, "SMS Detected via $source!", Toast.LENGTH_SHORT).show()
        }

        lifecycleScope.launch {
            classifyMessage(classifierInput)
        }
    }

    /**
     * Classify the SMS message
     * TODO: risk score and risk category from classifier output
     */
    private suspend fun classifyMessage(body: String) {
        Log.d("MainActivity", "========== CLASSIFICATION START ==========")
        try {
            val (label, confidence) = classifier.classify(body)
            val riskScore = if (label == "SAFE") {
                100 - confidence * 100
            } else {
                confidence * 100
            }
            //val riskScore = (confidence * 100).toInt()

            Log.d("MainActivity", "Result: $label ($riskScore%)")

            runOnUiThread {
                when (label) {
                    "SPAM" -> {
                        resultTextView.text = "⚠️ SMISHING DETECTED!\n\n${riskScore.roundToInt()}% Risk Score"
                        resultTextView.setTextColor(getColor(android.R.color.holo_red_light))
                        Toast.makeText(this@MainActivity, "SMISHING DETECTED!", Toast.LENGTH_LONG).show()
                    }
                    "SAFE" -> {
                        resultTextView.text = "✅ Message appears safe\n\n${riskScore.roundToInt()}% Risk Score"
                        resultTextView.setTextColor(getColor(android.R.color.holo_green_dark))
                    }
                    "ERROR" -> {
                        resultTextView.text = "❌ Error analyzing\nCheck internet"
                        resultTextView.setTextColor(getColor(android.R.color.darker_gray))
                    }
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

    //Data cleaning for classifier

    override fun onDestroy() {
        super.onDestroy()

        // Unregister broadcast receiver
        smsReceiver?.let {
            try {
                unregisterReceiver(it)
                Log.d("MainActivity", "Broadcast receiver unregistered")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error unregistering receiver: ${e.message}")
            }
        }

        // Unregister database observer
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
