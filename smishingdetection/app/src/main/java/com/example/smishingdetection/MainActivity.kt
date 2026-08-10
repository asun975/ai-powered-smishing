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
import android.view.View
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * The app's home screen. Runs in the foreground (or background, via
 * onDestroy-safe listeners) watching for new SMS messages, and pipes each
 * one through: preprocessing -> classifier API -> (if risky) LLM explainer
 * API -> URL scan -> local database -> on-screen result / notification.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var urlAnalyzer: UrlAnalyzer   //talks to the URL API
    private lateinit var urlAnalyzerTextView: TextView  //shows URL scan result, if a URL was found
    private lateinit var smsTextView: TextView  //shows the "From:... Message:... (original uncleaned text)"
    private lateinit var resultTextView: TextView   //shows the risk score from DistilBERT (Low/Medium/High %)
    private lateinit var explanationTextView: TextView  //shows the returned LLM explanation
    private lateinit var classifier: SmishingClassifier //talks to the DistilBERT API
    private lateinit var explainer: LlmExplainer    //talks to the LLM Groq API
    private lateinit var db: DatabaseHelper //the local DB storage
    private var smsObserver: ContentObserver? = null //watches for SMS content changes
    private var lastProcessedSmsId: String? = null  //tracks if the last SMS was already processed
    private var lastProcessedMessageHash: Int? = null   //like lastProcessedSmsId but if the content is the same
    private var isProcessing = false    //so that if 2 messages arrive, only one goes at a time
    private lateinit var sanitizer: Preprocessing.Companion

    //Remembers the most recently analyzed message so refreshLastMessageUI() can look it back up in the DB when the WorkManager
    //finishes the offline retry
    private var currentSender: String? = null
    private var currentMessageBody: String? = null

    /**
     * Runs once when the screen is first created. Wires up all the UI views,
     * creates the classifier/LLM/database/URL-analyzer objects using the
     * configured API endpoints, sets up the "open inbox" button, then asks
     * for permissions (which — once granted — kicks off SMS detection),
     * schedules the offline retry worker, and starts listening for it.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        supportActionBar?.hide()    //hides the default title bar at the top which caused issues on different phones
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)  //refers to showing the activity_main.xml

        Log.d("MainActivity", "========== APP STARTED ===========")

        //Set up the UI
        smsTextView = findViewById(R.id.smsTextView)
        resultTextView = findViewById(R.id.resultTextView)
        explanationTextView = findViewById(R.id.explanationTextView)
        urlAnalyzerTextView = findViewById(R.id.urlAnalyzerTextView)

        //Set up API endpoints from BuildConfig (set per gradle config)
        val apiUrl = BuildConfig.CLASSIFIER_API_URL
        val llmUrl = BuildConfig.LLM_API_URL
        val urlSandboxUrl = BuildConfig.SCAN_API_URL

        classifier = SmishingClassifier(apiUrl)
        explainer = LlmExplainer(llmUrl)
        db = DatabaseHelper(this)
        urlAnalyzer = UrlAnalyzer(urlSandboxUrl)
        sanitizer = Preprocessing

        resultTextView.text = "⏳ Setting up..."

        // Tapping the floating action button (mailbox) opens the "suspicious messages" inbox screen
        findViewById<FloatingActionButton>(R.id.fabInbox).setOnClickListener {
            startActivity(Intent(this, SuspiciousMessagesActivity::class.java))
        }

        requestPermissions()    //starts startBothDetectionMethods() once the permission to read/write are given
        scheduleAnalysisWorker() // Check for any pending messages on startup
        observeWorkerStatus()   //listens to the background worker to finish
    }

    /**
     * Watches WorkManager for the offline-analysis worker (see classifyMessage's
     * "ERROR" branch) finishing, and refreshes the screen once it has.
     */
    private fun observeWorkerStatus() {
        WorkManager.getInstance(applicationContext)
            .getWorkInfosForUniqueWorkLiveData("SmishingAnalysis")
            .observe(this) { workInfos ->
                val workInfo = workInfos?.firstOrNull()
                if (workInfo?.state?.isFinished == true) {
                    Log.d("MainActivity", "WorkManager finished: ${workInfo.state}")
                    refreshLastMessageUI()
                }
            }
    }

    /**
     * After a background retry finishes, re-check the DB for the message we were
     * last showing on screen — if it's now been analyzed (no longer PENDING),
     * update the UI with the real result instead of the "queued" placeholder.
     */
    private fun refreshLastMessageUI() {
        val sender = currentSender ?: return
        val body = currentMessageBody ?: return

        lifecycleScope.launch {
            // Check if this message has been analyzed in the DB
            val analyzed = db.findByMessage(body)
            if (analyzed != null) {
                val status = analyzed[DatabaseHelper.COL_STATUS]
                if (status != DatabaseHelper.STATUS_PENDING) {
                    Log.d("MainActivity", "Refreshing UI with analyzed data for: $sender")
                    updateUIWithAnalyzedMessage(analyzed)
                }
            }
        }
    }

    /**
     * Renders a DB row (an already-analyzed message) onto the risk-verdict,
     * explanation, and URL-scan views — used specifically for the "resume after
     * offline retry" flow, not the normal live-message path.
     */
    private fun updateUIWithAnalyzedMessage(msg: Map<String, String>) {
        val riskScore = msg[DatabaseHelper.COL_RISK_SCORE]?.toDoubleOrNull() ?: 0.0
        val riskCategory = when {
            riskScore > 75.0 -> "HIGH"
            riskScore >= 30.0 -> "MEDIUM"
            else -> "LOW"
        }
        val explanation = msg[DatabaseHelper.COL_EXPLANATION] ?: ""
        val scanResult = msg[DatabaseHelper.COL_URL_SCAN] ?: ""

        runOnUiThread {
            when (riskCategory) {
                "HIGH" -> {
                    resultTextView.text = "⚠️ Detected High Risk of Smishing!\n\n${
                        String.format("%.2f", riskScore)}% Risk Score"
                    resultTextView.setTextColor(getColor(android.R.color.holo_red_light))
                }
                "MEDIUM" -> {
                    resultTextView.text = "⚠️ Signs of Smishing Detected\n\n${
                        String.format("%.2f", riskScore)}% Risk Score"
                    resultTextView.setTextColor(getColor(android.R.color.holo_orange_dark))
                }
                "LOW" -> {
                    resultTextView.text = "✅ Low Risk of Smishing\n\n${
                        String.format("%.2f", riskScore)}% Risk Score"
                    resultTextView.setTextColor(getColor(android.R.color.holo_green_dark))
                }
            }
            explanationTextView.text = if (explanation.isNotEmpty()) "💬 $explanation" else ""
            explanationTextView.setTextColor(if (riskCategory == "LOW") getColor(android.R.color.darker_gray) else getColor(android.R.color.holo_red_light))

            if (scanResult.isNotEmpty() && scanResult != "No Urls found") {
                urlAnalyzerTextView.text = scanResult
                urlAnalyzerTextView.visibility = View.VISIBLE
                findViewById<View>(R.id.urlDivider).visibility = View.VISIBLE
                findViewById<TextView>(R.id.urlScanLabel).visibility = View.VISIBLE
            }
        }
    }

    /**
     * Enqueues (or re-enqueues) the background worker that retries classification
     * for any message stuck in "PENDING" status — used when the classifier API
     * was unreachable at the time a message first arrived (see the offline-mode work).
     * Requires network connectivity to actually run (see Constraints below).
     */
    private fun scheduleAnalysisWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<SmishingAnalysisWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "SmishingAnalysis",
            ExistingWorkPolicy.REPLACE, // Change to REPLACE to trigger it again if new pending arrived
            request
        )
    }

    /** Asks for SMS + (on Android 13+) notification permissions, if not already granted. */
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
            // Already have everything we need — start watching for SMS right away
            startBothDetectionMethods()
        }
    }

    /** Callback for the permission request above (request code 999). */
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

    /**
     * Kicks off SMS detection. Name is a leftover from when this app used both
     * a BroadcastReceiver AND a ContentObserver — it now only starts the
     * database observer, since the broadcast receiver was removed.
     */
    private fun startBothDetectionMethods() {
        Log.d("MainActivity", "========== STARTING DETECTION ==========")
        resultTextView.text = "✅ Ready! Waiting for SMS..."
        startDatabaseObserver()
    }

    /**
     * Registers a ContentObserver on Android's SMS table. Any change to that
     * table triggers onChange(), which then checks what the newest message is.
     */
    private fun startDatabaseObserver() {
        smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                checkLatestSms()
            }
        }
        contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, smsObserver!!)
    }

    /**
     * Queries the SMS table for the single most recent message. If it's one
     * we haven't already processed (by row ID), hands it off to
     * processSmsMessage() for cleaning and analysis.
     */
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
                        processSmsMessage(sender, body, "DATABASE", sanitizer)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Database check error: ${e.message}", e)
        }
    }

    /**
     * The entry point for "a message arrived, now what." Cleans the raw text
     * (extracts any URL, strips PII for the classifier, masks PII for the LLM),
     * then runs deduplication, re-entrancy, and blank-message checks. If none
     * of those bail it out, updates the UI to an "Analyzing..." state and hands
     * off to classifyMessage() on a coroutine.
     */
    private fun processSmsMessage(
        sender: String,
        body: String,
        source: String,
        sanitizer: Preprocessing.Companion
    ) {
        val messageUrl: String? = sanitizer.extractFirstUrl(body)
        val classifierInput = sanitizer.preprocessClassifierText(body)
        val llmInput = sanitizer.preprocessLlmText(body)

        // Simple hash-based deduplication
        val msgHash = (sender + body).hashCode()
        if (msgHash == lastProcessedMessageHash) {
            Log.d("MainActivity", "Duplicate message detected, skipping ($source)")
            return
        }
        lastProcessedMessageHash = msgHash

        if (isProcessing) {
            Log.d("MainActivity", "Already processing, skipping ($source)")
            return
        }

        if (body.isBlank()) {
            Log.d("MainActivity", "Skipping blank/whitespace-only message from $sender ($source)")
            return
        }

        isProcessing = true
        currentSender = sender
        currentMessageBody = body

        Log.d("MainActivity", "---------- PROCESSING SMS ($source) ----------")
        Log.d("MainActivity", "From: $sender")

        runOnUiThread {
            smsTextView.text = "From: $sender\n\nMessage: $body"
            resultTextView.text = "🔍 Analyzing..."
            explanationTextView.text = ""
            urlAnalyzerTextView.text = ""
            urlAnalyzerTextView.visibility = View.GONE
            findViewById<View>(R.id.urlDivider).visibility = View.GONE
            findViewById<TextView>(R.id.urlScanLabel).visibility = View.GONE
        }

        lifecycleScope.launch {
            classifyMessage(sender, body, classifierInput, llmInput, messageUrl)
            isProcessing = false
        }
    }

    /**
     * Converts a classifier label + confidence into a 0-1 risk score and a
     * Low/Medium/High category. Flips the confidence when the label is SAFE,
     * since a confident "SAFE" call means LOW risk, not high.
     */
    private fun getRiskScore(label: String, confidence: Float): Pair<Float, String> {
        val riskScore = if (label == "SPAM") confidence else (1 - confidence)
        val riskCategory = when {
            riskScore > 0.75 -> "HIGH"
            riskScore >= 0.30 -> "MEDIUM"
            else -> "LOW"
        }
        return Pair(riskScore, riskCategory)
    }

    /**
     * Builds and shows the in-app AlertDialog popup for a risky message —
     * used only when the app is in the foreground. Its "View Details" button
     * launches MessageDetailActivity with all the message's data attached.
     */
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

    /**
     * The core pipeline for a new message: classify it, queue it for offline
     * retry if the classifier API is unreachable, get an LLM explanation for
     * risky messages, scan any URL found, save MEDIUM/HIGH risk messages to
     * the database, and finally either show the in-app dialog (foreground) or
     * a system notification (background).
     */
    private suspend fun classifyMessage(
        sender: String,
        originalBody: String,
        classifierInput: String,
        llmInput: String,
        url: String?
    ) {
        try {
            val (label, confidence) = classifier.classify(classifierInput)

            if (label == "ERROR") {
                val timestamp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                } else {
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        .format(java.util.Date())
                }
                db.insertPendingMessage(sender, timestamp, originalBody)
                scheduleAnalysisWorker()

                runOnUiThread {
                    resultTextView.text = "⏳ Offline: Analysis Queued"
                    resultTextView.setTextColor(getColor(android.R.color.darker_gray))
                    explanationTextView.text = "Analysis will resume when internet is available."
                    explanationTextView.setTextColor(getColor(android.R.color.darker_gray))
                }
                return
            }

            val (riskScore, riskCategory) = getRiskScore(label, confidence)

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

            // Call URL sandbox and display result in Main Screen UI
            val (scanStatus, scanResult) = urlAnalyzer.analyzeUrl(url)

            runOnUiThread {
                // Hide UI if no URLs are found in message
                if (scanStatus == ScanStatus.SKIPPED) {
                    // Debug code
                    Log.d("MainActivity", scanResult)
                    urlAnalyzerTextView.visibility = View.GONE
                    findViewById<View>(R.id.urlDivider).visibility = View.GONE
                    findViewById<TextView>(R.id.urlScanLabel).visibility = View.GONE
                } else {
                    // Display scan result or error message to user
                    urlAnalyzerTextView.text = scanResult
                    urlAnalyzerTextView.visibility = View.VISIBLE
                    findViewById<View>(R.id.urlDivider).visibility = View.VISIBLE
                    findViewById<TextView>(R.id.urlScanLabel).visibility = View.VISIBLE
                }
            }
            // Save to database for MEDIUM (caution) and HIGH (quarantined) risk
            if (riskCategory == "MEDIUM" || riskCategory == "HIGH") {
                // Format url scan result
                val urlScanResult =
                    if(scanStatus == ScanStatus.SUCCESS) {
                        scanResult
                    } else {
                        "No scan result available."
                    }
                // Format date
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
                    urlScanResult = urlScanResult
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
                            scanResult = urlScanResult,
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
                        scanResult = Pair(scanStatus, scanResult),
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

    /** Unregisters the SMS observer when the Activity is destroyed, so it doesn't leak. */
    override fun onDestroy() {
        super.onDestroy()
        smsObserver?.let {
            try { contentResolver.unregisterContentObserver(it) } catch (e: Exception) {
                Log.d("MainActivity", "Failed to unregister SMS Observer: ${e.message}")
            }
        }
    }
}
