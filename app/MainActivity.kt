package com.example.smishingdetector
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.Telephony
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "SmishingDetector"
        private const val SMS_PERMISSION_CODE = 111
        private const val API_URL = "http://10.0.2.2:8000/analyze"
    }
    private lateinit var smsTextView: TextView
    private lateinit var predictionBadge: TextView
    private lateinit var riskScoreView: TextView
    private lateinit var riskProgressBar: ProgressBar
    private lateinit var explanationView: TextView
    private lateinit var loadingLayout: View
    private lateinit var resultCard: CardView
    private lateinit var markSafeButton: Button
    private lateinit var blockButton: Button
    private lateinit var db: DatabaseHelper
    private var currentMessageBody = ""
    private var currentStatus = "safe"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        smsTextView      = findViewById(R.id.smsTextView)
        predictionBadge  = findViewById(R.id.predictionBadge)
        riskScoreView    = findViewById(R.id.riskScoreView)
        riskProgressBar  = findViewById(R.id.riskProgressBar)
        explanationView  = findViewById(R.id.explanationView)
        loadingLayout    = findViewById(R.id.loadingLayout)
        resultCard       = findViewById(R.id.resultCard)
        markSafeButton   = findViewById(R.id.markSafeButton)
        blockButton      = findViewById(R.id.blockButton)
        db = DatabaseHelper(this)
        markSafeButton.setOnClickListener {
            if (currentMessageBody.isNotEmpty()) {
                db.markAsSafe(currentMessageBody)
                currentStatus = "safe"
                predictionBadge.text = "SAFE"
                predictionBadge.setBackgroundColor(Color.parseColor("#4CAF50"))
                riskScoreView.text = riskScoreView.text.toString()
                    .replace("⛔ Quarantined", "✅ Safe")
                    .replace("⚠️ Caution", "✅ Safe")
                markSafeButton.visibility = View.GONE
                Toast.makeText(this, "Message marked as safe", Toast.LENGTH_SHORT).show()
            }
        }
        blockButton.setOnClickListener {
            if (currentMessageBody.isNotEmpty()) {
                db.blockMessage(currentMessageBody)
                currentStatus = "blocked"
                predictionBadge.text = "BLOCKED"
                predictionBadge.setBackgroundColor(Color.parseColor("#424242"))
                riskScoreView.text = riskScoreView.text.toString()
                    .replace("⛔ Quarantined", "🚫 Blocked")
                    .replace("⚠️ Caution", "🚫 Blocked")
                    .replace("✅ Safe", "🚫 Blocked")
                markSafeButton.visibility = View.GONE
                blockButton.visibility = View.GONE
                Toast.makeText(this, "Message blocked successfully", Toast.LENGTH_SHORT).show()
            }
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECEIVE_SMS),
                SMS_PERMISSION_CODE
            )
        } else {
            registerSmsReceiver()
        }
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SMS_PERMISSION_CODE
            && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            registerSmsReceiver()
        }
    }
    private fun registerSmsReceiver() {
        val br = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
                for (sms in messages) {
                    val sender = sms.displayOriginatingAddress ?: "Unknown"
                    val body   = sms.displayMessageBody ?: continue
                    currentMessageBody = body
                    smsTextView.text = "From: $sender\n\nMessage: $body"
                    resultCard.visibility      = View.GONE
                    markSafeButton.visibility  = View.GONE
                    blockButton.visibility     = View.GONE
                    loadingLayout.visibility   = View.VISIBLE
                    Thread {
                        val cached = db.findByMessage(body)
                        if (cached != null) {
                            runOnUiThread { showCachedResult(cached) }
                            return@Thread
                        }
                        val result = callApi(body)
                        runOnUiThread { showApiResult(sender, body, result) }
                    }.start()
                }
            }
        }
        registerReceiver(br, IntentFilter("android.provider.Telephony.SMS_RECEIVED"))
    }
    // ── API ───────────────────────────────────────────────────────────────────
    private fun callApi(message: String): JSONObject? {
        return try {
            val connection = URL(API_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 15_000
            connection.readTimeout    = 15_000
            OutputStreamWriter(connection.outputStream).use {
                it.write(JSONObject().put("message", message).toString())
            }
            if (connection.responseCode == HttpURLConnection.HTTP_OK)
                JSONObject(connection.inputStream.bufferedReader().readText())
            else {
                Log.e(TAG, "API returned HTTP ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "API call failed: ${e.message}", e)
            null
        }
    }
    // ── Display ───────────────────────────────────────────────────────────────
    private fun showApiResult(sender: String, body: String, result: JSONObject?) {
        loadingLayout.visibility = View.GONE
        if (result == null) { showError(); return }
        val skipped     = result.getBoolean("skipped")
        val prediction  = if (skipped) "SAFE" else result.getString("prediction")
        val riskScore   = if (skipped) 0.0    else result.getDouble("risk_score")
        val explanation = result.getString("explanation")
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        try {
            db.insertMessage(
                phoneNumber = sender,
                date        = timestamp,
                message     = body,
                riskScore   = riskScore,
                prediction  = prediction,
                explanation = explanation,
            )
        } catch (e: Exception) {
            Log.e(TAG, "DB insert failed: ${e.message}", e)
        }
        renderUI(prediction, riskScore, explanation, fromCache = false, status = DatabaseHelper.statusFromScore(riskScore))
    }
    private fun showCachedResult(cached: Map<String, String>) {
        loadingLayout.visibility = View.GONE
        val prediction  = cached[DatabaseHelper.COL_PREDICTION] ?: "SAFE"
        val riskScore   = cached[DatabaseHelper.COL_RISK_SCORE]?.toDoubleOrNull() ?: 0.0
        val explanation = cached[DatabaseHelper.COL_EXPLANATION] ?: ""
        val status      = cached[DatabaseHelper.COL_STATUS] ?: DatabaseHelper.statusFromScore(riskScore)
        renderUI(prediction, riskScore, explanation, fromCache = true, status = status)
    }
    private fun renderUI(
        prediction: String,
        riskScore: Double,
        explanation: String,
        fromCache: Boolean,
        status: String,
    ) {
        currentStatus = status
        // Badge
        val badgeText = if (currentStatus == "blocked") "BLOCKED" else prediction
        predictionBadge.text = if (fromCache) "$badgeText  (cached)" else badgeText
        predictionBadge.setBackgroundColor(
            when {
                currentStatus == "blocked" -> Color.parseColor("#424242")
                prediction == "SPAM" && currentStatus == "quarantined" -> Color.parseColor("#D32F2F")
                prediction == "SPAM" -> Color.parseColor("#F44336")
                else -> Color.parseColor("#388E3C")
            }
        )
        // Risk score text + progress bar
        val statusLabel = when (currentStatus) {
            "quarantined" -> "⛔ Quarantined"
            "caution"     -> "⚠️ Caution"
            "blocked"     -> "🚫 Blocked"
            else          -> "✅ Safe"
        }
        riskScoreView.text = "Risk Score: ${"%.1f".format(riskScore)} / 100   $statusLabel"
        riskProgressBar.progress = riskScore.toInt()
        // Colour the progress bar based on risk
        val progressColor = when (currentStatus) {
            "quarantined" -> Color.parseColor("#D32F2F")
            "caution"     -> Color.parseColor("#FF9800")
            "blocked"     -> Color.parseColor("#424242")
            else          -> Color.parseColor("#4CAF50")
        }
        riskProgressBar.progressTintList =
            android.content.res.ColorStateList.valueOf(progressColor)
        // Explanation
        explanationView.text = explanation
        // Show result card
        resultCard.visibility = View.VISIBLE
        // Mark as Safe button only for non-safe, non-blocked messages
        markSafeButton.visibility =
            if (currentStatus != "safe" && currentStatus != "blocked") View.VISIBLE else View.GONE
        // Block button hidden once the message is already blocked
        blockButton.visibility =
            if (currentStatus != "blocked") View.VISIBLE else View.GONE
    }
    private fun showError() {
        predictionBadge.text = "ERROR"
        predictionBadge.setBackgroundColor(Color.GRAY)
        riskScoreView.text = "Could not reach the analysis server.\nMake sure api.py is running."
        riskProgressBar.progress = 0
        explanationView.text = ""
        resultCard.visibility = View.VISIBLE
        markSafeButton.visibility = View.GONE
        blockButton.visibility = View.GONE
    }
}
