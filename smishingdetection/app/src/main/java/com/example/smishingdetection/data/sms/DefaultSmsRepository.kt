package com.example.smishingdetection.data.sms

import android.content.Intent
import androidx.appcompat.app.AlertDialog
import com.example.smishingdetection.AppLifecycleTracker
import com.example.smishingdetection.MessageDetailActivity
import com.example.smishingdetection.data.local.QuarantineRepository
import com.example.smishingdetection.data.network.classifier.NetworkClassifierApiRepository
import com.example.smishingdetection.data.network.classifier.model.ClassifierResponse
import com.example.smishingdetection.data.network.explainer.NetworkExplainerApiRepository
import com.example.smishingdetection.data.network.explainer.model.ExplainerRequest
import com.example.smishingdetection.data.network.explainer.model.ExplainerResponse
import com.example.smishingdetection.data.network.url.NetworkUrlApiRepository
import com.example.smishingdetection.data.network.url.model.UrlAnalyzerResponse
import com.example.smishingdetection.data.smishingalert.SmishingAlert

interface SmsRepository {
    /*
     * read newest sms message from content provider - update UI loading
     * send message to classifier api - update UI for API status error
     * send message to url sandbox - update UI for API status error
     * send classifier output to LLM - update UI for API status error
     * insert into db
     * update UI - message info
     */

    suspend fun checkLatestSms(lastProcessedId: Long?): SmsMessage?
    suspend fun classifyMessage(message: SmsMessage): ClassifierResponse
    suspend fun explainMessage(request: ExplainerRequest): ExplainerResponse
    suspend fun getUrlVerdict(message: String): UrlAnalyzerResponse?
    suspend fun insertIntoDatabase(
        sender: String,
        date: String,
        message: String,
        riskScore: Double, // TODO: change to float, stay consistent with API
        explanation: String,
        urlVerdict: UrlAnalyzerResponse?
    ): Long
}

class DefaultSmsRepository(
    private val smsProvider: DefaultSmsProvider,
    private val classifierApiRepository: NetworkClassifierApiRepository,
    private val explainerApiRepository: NetworkExplainerApiRepository,
    private val urlApiRepository: NetworkUrlApiRepository,
    private val quarantineRepository: QuarantineRepository

): SmsRepository {
    override suspend fun checkLatestSms(lastProcessedId: Long?): SmsMessage? {
        return smsProvider.getLatestSms(lastProcessedId)
    }

    override suspend fun classifyMessage(message: SmsMessage): ClassifierResponse {
        val messageBody = message.body
        // Call smishing classifier API
        val classifier = classifierApiRepository.classify(messageBody)
        val confidence = classifier.confidence
        val label = classifier.label
        // Calculate risk score and risk level - low, medium, high
        val riskScore = if (label == "SPAM") confidence else (1 - confidence)
        val riskLevel = when {
            riskScore > 0.75 -> "HIGH"
            riskScore >= 0.30 -> "MEDIUM"
            else -> "LOW"
        }
            return ClassifierResponse(
                label,
                riskScore,
                riskLevel
            )
        }

    override suspend fun explainMessage(request: ExplainerRequest): ExplainerResponse {
        return explainerApiRepository.explain(
            request.input,
            "SPAM",
            request.riskScore,
        )
    }

    override suspend fun getUrlVerdict(message: String): UrlAnalyzerResponse? {
        return urlApiRepository.getVerdict(message)
    }

    override suspend fun insertIntoDatabase(
        sender: String,
        date: String,
        message: String,
        riskScore: Double,
        explanation: String,
        urlVerdict: UrlAnalyzerResponse?
    ): Long {
        val rowId = quarantineRepository.insertMessage(
            sender,
            date,
            message,
            riskScore,
            explanation,
            urlVerdict
        )
        return rowId
    }
}
