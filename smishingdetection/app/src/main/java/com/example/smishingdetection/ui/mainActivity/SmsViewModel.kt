package com.example.smishingdetection.ui.mainActivity

import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smishingdetection.AppLifecycleTracker
import com.example.smishingdetection.MessageDetailActivity
import com.example.smishingdetection.data.smishingalert.NotificationHelper
import com.example.smishingdetection.data.smishingalert.SmishingAlert
import com.example.smishingdetection.data.sms.SmsMessage
import com.example.smishingdetection.data.sms.SmsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

sealed interface ApiState {
    object Idle: UploadS
}

/*
 * TODO: Updates Main Activity UI
 */
class SmsViewModel (
    private val smsRepository: SmsRepository,
    private val savedStateHandle: SavedStateHandle
): ViewModel() {

    fun updateClassifier()
    fun updateExplanation()
    fun updateUrlSandbox()
    fun processMessage()
    fun sendSmishingNotification()
    fun showSmishingDialog()

    private val _apiState = MutableStateFlow<ApiState>()
    // Ignore this
    fun processMessage(timestamp: Long?): SmishingAlert? {
        viewModelScope.launch {
            val message = if (timestamp == null) {
                smsRepository.loadOnOpen()
            } else smsRepository.loadMessage()

            if (message != null) {
                val smishingAlert = detectSmishing(message)
                return smishingAlert
            }
            if(AppLifecycleTracker.isAppInForeground) {
                showSmishingDialog(smishingAlert)
            } else {
                NotificationHelper.sendSmishingNotification(smishingAlert)
            }
        }
    }

    fun showSmishingDialog(
        alert: SmishingAlert
    ) {
        val riskScorePercent = alert.riskScore
        val builder = AlertDialog.Builder(kotlin.context)
        builder.setTitle("⚠️ Suspicious SMS Detected")
        builder.setMessage(
            "This message may be a phishing attempt.\n\n" +
                    "From: ${alert.phone}\n" +
                    "Risk: ${alert.riskLevel} (${String.format("%.0f", riskScorePercent)}%)\n\n" +
                    "Reason: ${alert.explanation}"
        )
        builder.setPositiveButton("View Details") { _, _ ->
            val intent = Intent(this, MessageDetailActivity::class.java)
            startActivity(intent)
        }
        builder.setNegativeButton("Dismiss", null)
        builder.show()
    }

    fun processMessage() {

    }
    fun detect(message: SmsMessage): SmishingAlert? {
        val messageBody = message.body

        if (messageBody.isNotEmpty()) {
            // Call smishing classifier API
            val classifier = classifierApiRepository.classify(messageBody)

            // Calculate risk score and risk level - low, medium, high
            val (riskScore, riskLevel) = getRiskScore(
                classifier.label,
                classifier.confidence)

            // Call url sandbox
            val urlResponse = urlApiRepository.getVerdict(messageBody)

            // Call LLM for risk level medium and high
            if(riskLevel == "HIGH" || riskLevel == "MEDIUM"){
                val explainer = explainerApiRepository.explain(
                    messageBody,
                    "SPAM",
                    riskScore
                )
                // Format date/timestamp
                val timestamp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                } else {
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        .format(Date())
                }
                // Format url API response TODO: change db to store each value from API response
                val scanResult =
                    "Scan result returned Malicious:${urlResponse?.malicious} for ${urlResponse?.url} submitted with an overall score of ${urlResponse?.score}"
                // Format phone number
                val sender = message.address ?: "Unknown"

                // Format riskScore (double)
                val riskScorePercent = (riskScore*100)

                // Insert analyzed message into quarantine database
                quarantineRepository.insertMessage(
                    sender,
                    timestamp,
                    messageBody,
                    riskScorePercent.toDouble(),
                    explainer.explanation,
                    scanResult
                )

                // Return notification data
                return SmishingAlert(
                    message.id,
                    sender,
                    timestamp,
                    messageBody,
                    riskScorePercent.toDouble(),
                    riskLevel,
                    explainer.explanation,
                    scanResult
                )
            }
        }
        return null
    }

    fun processMessage(timestamp: Long?): SmishingAlert? {
        viewModelScope.launch {
            val message = if (timestamp == null) {
                smsRepository.loadOnOpen()
            } else smsRepository.loadMessage()

            if (message != null) {
                val smishingAlert = detectSmishing(message)
                return smishingAlert
            }
            if(AppLifecycleTracker.isAppInForeground) {
                showSmishingDialog(smishingAlert)
            } else {
                NotificationHelper.sendSmishingNotification(smishingAlert)
            }
        }
    }

    fun showSmishingDialog(
        alert: SmishingAlert
    ) {
        val riskScorePercent = alert.riskScore
        val builder = AlertDialog.Builder(context)
        builder.setTitle("⚠️ Suspicious SMS Detected")
        builder.setMessage(
            "This message may be a phishing attempt.\n\n" +
                    "From: ${alert.phone}\n" +
                    "Risk: ${alert.riskLevel} (${String.format("%.0f", riskScorePercent)}%)\n\n" +
                    "Reason: ${alert.explanation}"
        )
        builder.setPositiveButton("View Details") { _, _ ->
            val intent = Intent(this, MessageDetailActivity::class.java)
            startActivity(intent)
        }
        builder.setNegativeButton("Dismiss", null)
        builder.show()
    }
}