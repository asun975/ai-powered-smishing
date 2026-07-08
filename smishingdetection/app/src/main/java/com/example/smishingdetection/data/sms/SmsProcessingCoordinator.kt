package com.example.smishingdetection.data.sms

import android.os.Build
import com.example.smishingdetection.data.local.QuarantineRepository
import com.example.smishingdetection.data.local.model.AnalyzedMessage
import com.example.smishingdetection.data.network.classifier.NetworkClassifierApiRepository
import com.example.smishingdetection.data.network.explainer.NetworkExplainerApiRepository
import com.example.smishingdetection.data.network.url.NetworkUrlApiRepository
import com.example.smishingdetection.data.network.url.UrlApiRepository
import com.example.smishingdetection.data.sanitizer.InvalidInputException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class SmsProcessingCoordinator(
    private val smsRepository: SmsRepository,
    private val quarantineRepository: QuarantineRepository,
    private val classifierRepository: NetworkClassifierApiRepository,
    private val explainerRepository: NetworkExplainerApiRepository,
    private val urlApiRepository: NetworkUrlApiRepository
) {
    private suspend fun getRiskScore(label: String, confidence: Float): Pair<Float, String> {
        val riskScore = if (label == "SPAM") confidence else (1 - confidence)
        val riskLevel = when {
            riskScore > 0.75 -> "HIGH"
            riskScore >= 0.30 -> "MEDIUM"
            else -> "LOW"
        }
        return Pair(riskScore, riskLevel)
    }

    suspend fun processMessage(message: SmsMessage) {
        val messageBody = message.body

        if (messageBody.isNotEmpty()) {
            // Call smishing classifier API
            val classifier = classifierRepository.classify(messageBody)

            // Calculate risk score and risk level - low, medium, high
            val (riskScore, riskLevel) = getRiskScore(
                classifier.label,
                classifier.confidence)

            // Call url sandbox
            val urlResponse = urlApiRepository.getVerdict(messageBody)

            // Call LLM for risk level medium and high
            if(riskLevel == "HIGH" || riskLevel == "MEDIUM"){
                val explainer = explainerRepository.explain(
                    messageBody,
                    "SPAM",
                            riskScore
                )
                // Format date/timestamp
                val timestamp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                } else {
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        .format(java.util.Date())
                }
                // Format url API response TODO: change db to store each value from API response
                val scanResult =
                    "Scan result returned Malicious:${urlResponse?.malicious} for ${urlResponse?.url} submitted with an overall score of ${urlResponse?.score}"
                // Format phone number
                val sender = message.address ?: "Unknown"

                // Insert analyzed message into quarantine database
                quarantineRepository.insertMessage(
                    sender,
                    timestamp,
                    messageBody,
                    (riskScore*100).toDouble(),
                    explainer.explanation,
                    scanResult
                )
            }
        }
    }
}