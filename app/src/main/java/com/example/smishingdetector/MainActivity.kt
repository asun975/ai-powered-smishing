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
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // 10.0.2.2 is the Android emulator's alias for the host machine's localhost
    private val API_URL = "http://10.0.2.2:8000/analyze"

    private lateinit var smsTextView: TextView
    private lateinit var predictionBadge: TextView
    private lateinit var riskScoreView: TextView
    private lateinit var explanationView: TextView
    private lateinit var loadingView: TextView
    private lateinit var db: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        smsTextView     = findViewById(R.id.smsTextView)
        predictionBadge = findViewById(R.id.predictionBadge)
        riskScoreView   = findViewById(R.id.riskScoreView)
        explanationView = findViewById(R.id.explanationView)
        loadingView     = findViewById(R.id.loadingView)

        // Open the local SQLite database
        db = DatabaseHelper(this)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECEIVE_SMS),
                111
            )
        } else {
            receiveMSG()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 111 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            receiveMSG()
        }
    }

    fun receiveMSG() {
        val br = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                for (sms in Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                    val sender = sms.displayOriginatingAddress
                    val body   = sms.displayMessageBody

                    smsTextView.text = "From: $sender\n\nMessage: $body"

                    predictionBadge.visibility = View.GONE
                    riskScoreView.visibility   = View.GONE
                    explanationView.visibility = View.GONE
                    loadingView.visibility     = View.VISIBLE

                    Thread {
                        // ── 1. Check local cache first ──────────────────────
                        val cached = db.findByMessage(body)
                        if (cached != null) {
                            runOnUiThread {
                                showCachedResult(cached)
                            }
                            return@Thread
                        }

                        // ── 2. Cache miss — call the API ────────────────────
                        val result = analyzeMessage(body)
                        runOnUiThread { showApiResult(sender, body, result) }
                    }.start()
                }
            }
        }
        registerReceiver(br, IntentFilter("android.provider.Telephony.SMS_RECEIVED"))
    }

    // ── API call ──────────────────────────────────────────────────────────────

    private fun analyzeMessage(message: String): JSONObject? {
        return try {
            val url = URL(API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 10_000
            connection.readTimeout    = 10_000

            val body = JSONObject().put("message", message).toString()
            OutputStreamWriter(connection.outputStream).use { it.write(body) }

            if (connection.responseCode == HttpURLConnection.HTTP_OK)
                JSONObject(connection.inputStream.bufferedReader().readText())
            else
                null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ── Display helpers ───────────────────────────────────────────────────────

    /** Show result from a fresh API response and save it to the database. */
    private fun showApiResult(sender: String, body: String, result: JSONObject?) {
        loadingView.visibility = View.GONE

        if (result == null) {
            showError()
            return
        }

        val prediction  = result.getString("prediction")
        val riskScore   = result.getDouble("risk_score")
        val explanation = result.getString("explanation")
        val skipped     = result.getBoolean("skipped")

        val finalPrediction  = if (skipped) "SAFE" else prediction
        val finalScore       = if (skipped) 0.0    else riskScore
        val finalExplanation = explanation

        // Save to local database so we never call the API again for this message
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        db.insertMessage(
            phoneNumber = sender,
            date        = timestamp,
            message     = body,
            riskScore   = finalScore,
            prediction  = finalPrediction,
            explanation = finalExplanation,
        )

        renderUI(finalPrediction, finalScore, finalExplanation, fromCache = false)
    }

    /** Show result loaded from the local SQLite cache. */
    private fun showCachedResult(cached: Map<String, String>) {
        loadingView.visibility = View.GONE
        val prediction  = cached[DatabaseHelper.COL_PREDICTION] ?: "SAFE"
        val riskScore   = cached[DatabaseHelper.COL_RISK_SCORE]?.toDoubleOrNull() ?: 0.0
        val explanation = cached[DatabaseHelper.COL_EXPLANATION] ?: ""
        renderUI(prediction, riskScore, explanation, fromCache = true)
    }

    private fun renderUI(
        prediction: String,
        riskScore: Double,
        explanation: String,
        fromCache: Boolean,
    ) {
        // Badge colour: red = SPAM, green = SAFE
        predictionBadge.text = if (fromCache) "$prediction (cached)" else prediction
        predictionBadge.setBackgroundColor(
            if (prediction == "SPAM") Color.parseColor("#F44336")
            else Color.parseColor("#4CAF50")
        )
        predictionBadge.visibility = View.VISIBLE

        // Status label under the badge
        val status = DatabaseHelper.statusFromScore(riskScore)
        val statusLabel = when (status) {
            "quarantined" -> "⛔ Quarantined"
            "caution"     -> "⚠️ Caution"
            else          -> "✅ Safe"
        }
        riskScoreView.text = "Risk Score: ${"%.1f".format(riskScore)} / 100   $statusLabel"
        riskScoreView.visibility = View.VISIBLE

        explanationView.text = explanation
        explanationView.visibility = View.VISIBLE
    }

    private fun showError() {
        predictionBadge.text = "ERROR"
        predictionBadge.setBackgroundColor(Color.GRAY)
        predictionBadge.visibility = View.VISIBLE
        riskScoreView.text = "Could not reach the analysis server."
        riskScoreView.visibility = View.VISIBLE
    }
}
