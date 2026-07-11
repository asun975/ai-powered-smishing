package com.example.smishingdetection.data.sms

import com.example.smishingdetection.data.local.QuarantineRepository
import com.example.smishingdetection.data.network.classifier.NetworkClassifierApiRepository
import com.example.smishingdetection.data.network.classifier.model.ClassifierApiResult
import com.example.smishingdetection.data.network.classifier.model.ClassifierResponse
import com.example.smishingdetection.data.network.explainer.NetworkExplainerApiRepository
import com.example.smishingdetection.data.network.explainer.model.ExplainerApiResult
import com.example.smishingdetection.data.network.explainer.model.ExplainerRequest
import com.example.smishingdetection.data.network.url.NetworkUrlApiRepository
import com.example.smishingdetection.data.network.url.model.UrlAnalyzerResponse
import com.example.smishingdetection.data.network.url.model.UrlApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.descriptors.StructureKind

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
    suspend fun classifyMessage(message: SmsMessage): ClassifierApiResult
    suspend fun explainMessage(request: ExplainerRequest): ExplainerApiResult
    suspend fun getUrlVerdict(message: String): UrlApiResult?
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

    override suspend fun classifyMessage(message: SmsMessage): ClassifierApiResult {
        val messageBody = message.body

        // Handle successful and failed requests to classifier API
        when (val result = classifierApiRepository.classify(messageBody)) {
            is ClassifierApiResult.Success -> {
                val confidence = result.data.confidence
                val label = result.data.label

                // Calculate risk score and risk level - low, medium, high
                val riskScore = if (label == "SPAM") confidence else (1 - confidence)
                val riskLevel = when {
                    riskScore > 0.75 -> "HIGH"
                    riskScore >= 0.30 -> "MEDIUM"
                    else -> "LOW"
                }
                return ClassifierApiResult.Success(
                    ClassifierResponse(
                        "SPAM",
                        riskScore,
                        riskLevel
                    )
                )
            }
            is ClassifierApiResult.ApiError -> {
                return result   // pass along error response
            }
            is ClassifierApiResult.ExceptionError -> {
                return result
            }
        }
    }

    override suspend fun explainMessage(request: ExplainerRequest): ExplainerApiResult {
        return explainerApiRepository.explain(
            request.input,
            "SPAM",
            request.riskScore,
        )
    }

    override suspend fun getUrlVerdict(message: String): UrlApiResult? {
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
