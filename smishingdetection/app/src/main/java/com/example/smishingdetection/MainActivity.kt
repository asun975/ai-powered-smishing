package com.example.smishingdetection

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.util.Log
import androidx.core.content.ContextCompat
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.smishingdetection.ui.mainActivity.ClassifierUiState
import com.example.smishingdetection.ui.mainActivity.ExplainerUiState
import com.example.smishingdetection.ui.mainActivity.ScanUiState
import com.example.smishingdetection.ui.mainActivity.SmsUiState
import com.example.smishingdetection.ui.mainActivity.SmsViewModel
import kotlinx.coroutines.launch

/*
 * TODO
 * uncouple quarantine db actions
 * add viewModels
 */

class MainActivity : AppCompatActivity() {
    private lateinit var urlAnalyzerTextView: TextView
    private lateinit var smsTextView: TextView
    private lateinit var resultTextView: TextView
    private lateinit var explanationTextView: TextView
    private val CHANNEL_ID = "smishing_alerts"
    private val CHANNEL_NAME = "Smishing Alerts"
    private val smsViewModel : SmsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
        createNotificationChannel(applicationContext, CHANNEL_ID, CHANNEL_NAME)
        smsViewModel.processMessage()
        lifecycleScope.launch {
            repeatOnLifecycle((Lifecycle.State.STARTED)) {
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
            }
        }
    }

    fun createNotificationChannel(context: Context, channelId: String, channelName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for detected smishing messages"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
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
                resultTextView.text = state.error.message
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
                explanationTextView.text = state.error.message
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