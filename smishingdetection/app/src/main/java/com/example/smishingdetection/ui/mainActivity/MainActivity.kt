package com.example.smishingdetection.ui.mainActivity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.smishingdetection.ui.quarantine.MessageDetailActivity
import com.example.smishingdetection.R
import com.example.smishingdetection.ui.quarantine.SuspiciousMessagesActivity
import com.example.smishingdetection.data.local.model.AnalyzedMessage
import com.example.smishingdetection.data.smishingalert.NotificationHelper
import com.example.smishingdetection.data.sms.SmsContentObserver
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var urlAnalyzerTextView: TextView
    private lateinit var smsTextView: TextView
    private lateinit var resultTextView: TextView
    private lateinit var explanationTextView: TextView
    private val smsViewModel : SmsViewModel by viewModels { SmsViewModel.Factory }
    private lateinit var smsContentObserver: SmsContentObserver

    private lateinit var notificationHelper: NotificationHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        notificationHelper = NotificationHelper

        Log.d("MainActivity", "========== APP STARTED ===========")

        // Set up main UI
        smsTextView = findViewById(R.id.smsTextView)
        resultTextView = findViewById(R.id.resultTextView)
        explanationTextView = findViewById(R.id.explanationTextView)
        urlAnalyzerTextView = findViewById(R.id.urlAnalyzerTextView)
        resultTextView.text = "⏳ Setting up..."

        // FAB opens the suspicious messages inbox
        findViewById<FloatingActionButton>(R.id.fabInbox).setOnClickListener {
            startActivity(Intent(this, SuspiciousMessagesActivity::class.java))
        }
        requestPermissions()
        notificationHelper.createNotificationChannel(applicationContext)
        lifecycleScope.launch {
            repeatOnLifecycle((Lifecycle.State.STARTED)) {
                smsViewModel.processMessage()
                launch {
                    smsViewModel.smsUiState.collect { uiState ->
                        renderSmsView(uiState)
                    }
                }
                launch {
                     smsViewModel.classifierUiState
                        .collect { uiState ->
                            renderClassifierView(uiState)
                        }
                }
                launch {
                    smsViewModel.explainerUiState.collect { uiState ->
                        renderExplainerView((uiState))
                    }
                }
                launch {
                    smsViewModel.scanUiState.collect { uiState ->
                        renderUrlView(uiState)
                    }
                }
                launch {
                    smsViewModel.showAlertEvent.collect { alert ->
                        showSmishingDialog(alert)
                    }
                }
                launch {
                    smsViewModel.sendUserAlert.collect { alert ->
                        notificationHelper.sendSmishingNotification(applicationContext, alert)
                    }
                }
            }
        }
    }


    override fun onResume() {
        super.onResume()
        // TODO
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
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 999) {
            if (grantResults.all { it != PackageManager.PERMISSION_GRANTED }) {
                resultTextView.text = "❌ Permissions needed!"
            } else {
                smsContentObserver.register()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        smsContentObserver.unregister()
    }

    private fun showSmishingDialog(alert: AnalyzedMessage) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("⚠️ Suspicious SMS Detected")
        builder.setMessage(
            "This message may be a phishing attempt.\n\n" +
                    "From: ${alert.phoneNumber}\n" +
                    "Risk: ${alert.status} (${String.format("%.0f", alert.riskScore)}%)\n\n" +
                    "Reason: $alert.explanation"
        )
        builder.setPositiveButton("View Details") { _, _ ->
            val intent = Intent(this, MessageDetailActivity::class.java).apply {
                putExtra("phone", alert.phoneNumber)
                putExtra("date", alert.date)
                putExtra("message", alert.message)
                putExtra("risk_score", alert.riskScore.toString())
                putExtra("status", alert.status)
                putExtra("explanation", alert.explanation)
                putExtra("id", alert.id)
                putExtra("url_scan_result", alert.urlScanResult)
            }
            startActivity(intent)
        }
        builder.setNegativeButton("Dismiss", null)
        builder.show()
    }

    private fun renderSmsView(state: SmsUiState) {
        when(state) {
            is SmsUiState.Success -> {
                 smsTextView.text = "From: ${state.smsMessage.address}\n\nMessage: ${state.smsMessage.body}"
            }
            is SmsUiState.Error -> {
                smsTextView.text = "Error: Smishing Detector could not read SMS message. Skipping!"
            }
            is SmsUiState.Idle -> {
                smsTextView.text = ""
            }
            is SmsUiState.Loading -> {
                smsTextView.text = "Processing new messages..."
            }
        }
    }

    private fun renderClassifierView(state: ClassifierUiState) {
        when(state) {
            is ClassifierUiState.Success -> {
                val riskScorePercent = state.result.confidence
                when(state.result.riskLevel) {
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
            is ClassifierUiState.Idle -> {
                resultTextView.text = "Waiting for SMS messages"
            }
            is ClassifierUiState.Loading -> {
                resultTextView.text = "🔍 Analyzing..."
            }
            is ClassifierUiState.ApiError -> {
                resultTextView.text = state.message.message
            }
            is ClassifierUiState.Exception -> {
                resultTextView.text = state.error.exception
            }
        }
    }

    private fun renderExplainerView(state: ExplainerUiState) {
        when(state) {
            is ExplainerUiState.Success -> {
                explanationTextView.text = "💬 ${state.explanation.explanation}"
                explanationTextView.setTextColor(getColor(android.R.color.holo_red_light))
            }
            is ExplainerUiState.Idle -> {
                explanationTextView.text = ""
            }
            is ExplainerUiState.Loading -> {
                explanationTextView.text = ""
            }
            is ExplainerUiState.ApiError -> {
                explanationTextView.text = state.error.message
            }
            is ExplainerUiState.Exception -> {
                explanationTextView.text = state.error.exeception
            }
        }
    }

    private fun renderUrlView(state: ScanUiState) {
        when (state) {
            is ScanUiState.Success -> {
                val resultString = "🔗 URLSCAN.IO Sandbox \nURL: ${state.scanResult.url}\nOverall Score: ${state.scanResult.score}\n Overall Verdict: ${state.scanResult.malicious}"
                urlAnalyzerTextView.text = resultString
            }

            is ScanUiState.Idle -> {
                urlAnalyzerTextView.text = ""
            }

            is ScanUiState.Loading -> {
                urlAnalyzerTextView.text = "Loading scan results..."
            }

            is ScanUiState.ApiError -> {
                urlAnalyzerTextView.text = state.error.message
            }

            is ScanUiState.Exception -> {
                urlAnalyzerTextView.text = state.exception.message
            }
        }
    }
}