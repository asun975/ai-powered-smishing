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
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import androidx.appcompat.app.AlertDialog

class MainActivity : AppCompatActivity() {
    private lateinit var urlAnalyzer: UrlAnalyzer
    private lateinit var urlAnalyzerTextView: TextView
    private lateinit var smsTextView: TextView
    private lateinit var resultTextView: TextView
    private lateinit var explanationTextView: TextView
    private lateinit var classifier: SmishingClassifier
    private lateinit var explainer: LlmExplainer
    private lateinit var db: DatabaseHelper
    private var smsReceiver: BroadcastReceiver? = null
    private var smsObserver: ContentObserver? = null
    private var lastProcessedSmsId: String? = null
    private var isProcessing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d("MainActivity", "========== APP STARTED ===========")

        smsTextView = findViewById(R.id.smsTextView)
        resultTextView = findViewById(R.id.resultTextView)
        explanationTextView = findViewById(R.id.explanationTextView)
        urlAnalyzerTextView = findViewById(R.id.urlAnalyzerTextView)

        val apiUrl = BuildConfig.CLASSIFIER_API_URL
        val llmUrl = BuildConfig.LLM_API_URL

        classifier = SmishingClassifier(apiUrl)
        explainer = LlmExplainer(llmUrl)
        db = DatabaseHelper(this)
        urlAnalyzer = UrlAnalyzer()

        resultTextView.text = "⏳ Setting up..."

        // FAB opens the suspicious messages inbox
        findViewById<FloatingActionButton>(R.id.fabInbox).setOnClickListener {
            startActivity(Intent(this, SuspiciousMessagesActivity::class.java))
        }

        requestPermissions()
    }

    private fun requestPermissions() {
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
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startBothDetectionMethods()
            } else {
                resultTextView.text = "❌ Permissions needed!"
            }
        }
    }

    private fun startBothDetectionMethods() {
        Log.d("MainActivity", "========== STARTING DUAL DETECTION ==========")
        resultTextView.text = "✅ Ready! Waiting for SMS..."
        startBroadcastReceiver()
        startDatabaseObserver()
    }

    private fun startBroadcastReceiver() {
        smsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
                    val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                    for (sms in messages) {
                        val body = sms.displayMessageBody
                        val sender = sms.displayOriginatingAddress ?: "Unknown"
                        val (classifierInput, llmInput, urls) = preprocessSmsMessage(body)
                        processSmsMessage(sender, body, classifierInput, llmInput, "BROADCAST", urls)
                    }
                }
            }
        }
        registerReceiver(smsReceiver, IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION))
    }

    private fun startDatabaseObserver() {
        smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                checkLatestSms()
            }
        }
        contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, smsObserver!!)
    }

    private fun checkLatestSms() {
        try {
            val cursor: Cursor? = contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                null, null,
                "${Telephony.Sms.DATE} DESC LIMIT 1"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val smsId = it.getString(it.getColumnIndexOrThrow(Telephony.Sms._ID))
                    val sender = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: "Unknown"
                    val body = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""

                    if (smsId != null && smsId != lastProcessedSmsId) {
                        lastProcessedSmsId = smsId
                        val (classifierInput, llmInput, urls) = preprocessSmsMessage(body)
                        processSmsMessage(sender, body, classifierInput, llmInput, "DATABASE", urls)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Database check error: ${e.message}", e)
        }
    }

    private fun preprocessSmsMessage(body: String): Triple<String, String, List<String?>> {
        val preprocessor = Preprocessing
        return Triple(
            preprocessor.preprocessClassifierText(body),
            preprocessor.preprocessLlmText(body),
            preprocessor.extractUrl(body)
        )
    }

    private fun processSmsMessage(
        sender: String,
        originalBody: String,
        classifierInput: String,
        llmInput: String,
        source: String,
        urls: List<String?>
    ) {
        if (isProcessing) {
            Log.d("MainActivity", "Already processing, skipping ($source)")
            return
        }
        isProcessing = true

        Log.d("MainActivity", "---------- PROCESSING SMS ($source) ----------")
        Log.d("MainActivity", "From: $sender")

        runOnUiThread {
            smsTextView.text = "From: $sender\n\nMessage: $llmInput"
            resultTextView.text = "🔍 Analyzing..."
        }

        lifecycleScope.launch {
            classifyMessage(sender, originalBody, classifierInput, llmInput, urls.firstOrNull())
            isProcessing = false
        }
    }

    private fun getRiskScore(label: String, confidence: Float): Pair<Float, String> {
        val riskScore = if (label == "SPAM") confidence else (1 - confidence)
        val riskCategory = when {
            riskScore > 0.75 -> "HIGH"
            riskScore >= 0.30 -> "MEDIUM"
            else -> "LOW"
        }
        return Pair(riskScore, riskCategory)
    }

    private fun showSmishingDialog(
        sender: String,
        originalBody: String,
        riskScorePercent: Float,
        riskCategory: String,
        explanation: String,
        scanResult: String,
        messageId: Long,
        status: String,
        timestamp: String
    ) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("⚠️ Suspicious SMS Detected")
        builder.setMessage(
            "This message may be a phishing attempt.\n\n" +
                    "From: $sender\n" +
                    "Risk: $riskCategory (${String.format("%.0f", riskScorePercent)}%)\n\n" +
                    "Reason: $explanation"
        )
        builder.setPositiveButton("View Details") { _, _ ->
            val intent = Intent(this, MessageDetailActivity::class.java).apply {
                putExtra("phone", sender)
                putExtra("date", timestamp)
                putExtra("message", originalBody)
                putExtra("risk_score", riskScorePercent.toString())
                putExtra("status", status)
                putExtra("explanation", explanation)
                putExtra("id", messageId.toString())
                putExtra("url_scan_result", scanResult)
            }
            startActivity(intent)
        }
        builder.setNegativeButton("Dismiss", null)
        builder.show()
    }

    private suspend fun classifyMessage(
        sender: String,
        originalBody: String,
        classifierInput: String,
        llmInput: String,
        url: String?
    ) {
        try {
            val (label, confidence) = classifier.classify(classifierInput)
            val (riskScore, riskCategory) = getRiskScore(label, confidence)

            if (label == "ERROR") {
                runOnUiThread {
                    resultTextView.text = "❌ Error analyzing\nCheck internet"
                    resultTextView.setTextColor(getColor(android.R.color.darker_gray))
                }
                return
            }

            val riskScorePercent = riskScore * 100

            runOnUiThread {
                when (riskCategory) {
                    "HIGH" -> {
                        resultTextView.text = "⚠️ Detected High Risk of Smishing!\n\n${
                            String.format("%2.2f", riskScorePercent)}% Risk Score"
                        resultTextView.setTextColor(getColor(android.R.color.holo_red_light))
                        explanationTextView.text = "💬 Getting explanation..."
                        explanationTextView.setTextColor(getColor(android.R.color.darker_gray))
                    }
                    "MEDIUM" -> {
                        resultTextView.text = "⚠️ Signs of Smishing Detected\n\n${
                            String.format("%2.2f", riskScorePercent)}% Risk Score"
                        resultTextView.setTextColor(getColor(android.R.color.holo_orange_dark))
                        explanationTextView.text = "💬 Getting explanation..."
                        explanationTextView.setTextColor(getColor(android.R.color.darker_gray))
                    }
                    "LOW" -> {
                        resultTextView.text = "✅ Low Risk of Smishing\n\n${
                            String.format("%2.2f", riskScorePercent)}% Risk Score"
                        resultTextView.setTextColor(getColor(android.R.color.holo_green_dark))
                        explanationTextView.text = ""
                    }
                }
            }
            // Get LLM explanation for medium/high risk
            var explanation = ""
            if (riskCategory == "HIGH" || riskCategory == "MEDIUM") {
                explanation = explainer.explain(llmInput, label, riskScore)
                Log.d("MainActivity", "Explanation: $explanation")

                runOnUiThread {
                    explanationTextView.text = "💬 $explanation"
                    explanationTextView.setTextColor(getColor(android.R.color.holo_red_light))
                }
            }
            // After getting the explanation, call the URL analyzer:
            val scanResult = urlAnalyzer.analyzeUrl(url)

            // Then display it somewhere — add a TextView for it, or append to explanationTextView:
            runOnUiThread {
                if (!scanResult.isNullOrBlank() && scanResult != "No Urls found") {
                    urlAnalyzerTextView.text = "🔗 URL Scan: $scanResult"
                }
            }
            // Save to database for MEDIUM (caution) and HIGH (quarantined) risk
            if (riskCategory == "MEDIUM" || riskCategory == "HIGH") {
                val timestamp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                } else {
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        .format(java.util.Date())
                }
                val prediction = if (label == "SPAM") "SPAM" else "SAFE"
                val newMessageId = db.insertMessage(
                    phoneNumber = sender,
                    date = timestamp,
                    message = originalBody,
                    riskScore = riskScorePercent.toDouble(),
                    prediction = prediction,
                    explanation = explanation,
                    urlScanResult = scanResult ?: ""
                )
                val status = DatabaseHelper.statusFromScore(riskScorePercent.toDouble())
                Log.d("MainActivity", "Saved message to DB: $riskCategory")

                if (AppLifecycleTracker.isAppInForeground) {
                    runOnUiThread {
                        showSmishingDialog(
                            sender = sender,
                            originalBody = originalBody,
                            riskScorePercent = riskScorePercent,
                            riskCategory = riskCategory,
                            explanation = explanation,
                            scanResult = scanResult ?: "",
                            messageId = newMessageId,
                            status = status,
                            timestamp = timestamp
                        )
                    }
                } else {
                    NotificationHelper.sendSmishingNotification(
                        context = this,
                        sender = sender,
                        riskCategory = riskCategory,
                        riskScorePercent = riskScorePercent,
                        explanation = explanation,
                        messageId = newMessageId,
                        originalBody = originalBody,
                        timestamp = timestamp,
                        scanResult = scanResult ?: "",
                        status = status
                    )
                }
            }

        } catch (e: Exception) {
            Log.e("MainActivity", "Classification error: ${e.message}", e)
            runOnUiThread {
                resultTextView.text = "❌ Error: ${e.message}"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        smsReceiver?.let {
            try { unregisterReceiver(it) } catch (e: Exception) { }
        }
        smsObserver?.let {
            try { contentResolver.unregisterContentObserver(it) } catch (e: Exception) { }
        }
    }
}