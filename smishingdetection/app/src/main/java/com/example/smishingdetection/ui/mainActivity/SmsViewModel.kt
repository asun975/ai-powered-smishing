package com.example.smishingdetection.ui.mainActivity

import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.util.copy
import com.example.smishingdetection.AppLifecycleTracker
import com.example.smishingdetection.MessageDetailActivity
import com.example.smishingdetection.data.network.classifier.model.ClassifierApiResult
import com.example.smishingdetection.data.network.classifier.model.ClassifierResponse
import com.example.smishingdetection.data.network.explainer.model.ExplainerApiResult
import com.example.smishingdetection.data.network.explainer.model.ExplainerRequest
import com.example.smishingdetection.data.network.explainer.model.ExplainerResponse
import com.example.smishingdetection.data.network.url.model.UrlAnalyzerResponse
import com.example.smishingdetection.data.network.url.model.UrlApiResult
import com.example.smishingdetection.data.smishingalert.NotificationHelper
import com.example.smishingdetection.data.smishingalert.SmishingAlert
import com.example.smishingdetection.data.sms.SmsMessage
import com.example.smishingdetection.data.sms.SmsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

sealed interface SmsUiState {
    data class Success(val smsMessage: SmsMessage): SmsUiState
    object Error : SmsUiState
    object Idle: SmsUiState
    object Loading: SmsUiState
}

sealed interface ClassifierUiState {
    data class Success(val result: ClassifierResponse): ClassifierUiState
    data class ApiError(val message: ClassifierApiResult.ApiError): ClassifierUiState
    data class Exception(val error: ClassifierApiResult.ExceptionError): ClassifierUiState
    object Idle: ClassifierUiState
    object Loading: ClassifierUiState
}

sealed interface ExplainerUiState {
    data class Success(val explanation: ExplainerResponse): ExplainerUiState
    data class ApiError(val error: ExplainerApiResult.ApiError): ExplainerUiState
    data class Exception(val error: ExplainerApiResult.ExceptionError): ExplainerUiState
    object Idle: ExplainerUiState
    object Loading: ExplainerUiState
}

sealed interface ScanUiState {
    data class Success(val scanResult: UrlAnalyzerResponse): ScanUiState
    data class ApiError(val error: UrlApiResult.ApiError): ScanUiState
    data class Exception(val exception: UrlApiResult.ExceptionError): ScanUiState
    object Idle: ScanUiState
    object Loading: ScanUiState
}
/*
 * TODO: Updates Main Activity UI
 */
class SmsViewModel (
    private val smsRepository: SmsRepository,
    private val savedStateHandle: SavedStateHandle
): ViewModel() {
    // UI state for sms provider
    private val _smsUiState =
        MutableStateFlow<SmsUiState>(SmsUiState.Idle)
    val smsUiState: StateFlow<SmsUiState> = _smsUiState

    // UI state for classifier
    private val _classifierUiState =
        MutableStateFlow<ClassifierUiState>(ClassifierUiState.Idle)
    val classifierUiState: StateFlow<ClassifierUiState> = _classifierUiState

    // UI state for explainer
    private val _explainerUiState =
        MutableStateFlow<ExplainerUiState>(ExplainerUiState.Idle)
    val explainerUiState: StateFlow<ExplainerUiState> = _explainerUiState

    // UI state for URL scanning
    private val _scanUiState =
        MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanUiState: StateFlow<ScanUiState> = _scanUiState

    // Saved states
    private val _lastProcessedId: MutableStateFlow<Long?> = MutableStateFlow(null)
    val lastProcessedId: StateFlow<Long?> = _lastProcessedId

    private val _processingMessage: MutableStateFlow<String?> = MutableStateFlow(null)
    val processingMessage: StateFlow<String?> = _processingMessage

    private val _classifierResponse: MutableStateFlow<ClassifierResponse?> = MutableStateFlow(null)
    val classifierResponse: StateFlow<ClassifierResponse?> = _classifierResponse

    private val _explainer: MutableStateFlow<ExplainerResponse?> = MutableStateFlow(null)
    val explainer: StateFlow<ExplainerResponse?> = _explainer

    private val _urlVerdict: MutableStateFlow<UrlAnalyzerResponse?> = MutableStateFlow(null)
    val urlVerdict: StateFlow<UrlAnalyzerResponse?> = _urlVerdict

    fun checkLatestSms() {
        viewModelScope.launch {
            _smsUiState.value = SmsUiState.Loading
            when(val newMessage = smsRepository.checkLatestSms(lastProcessedId.value)) {
                null -> {
                    // No new messages
                    _smsUiState.value = SmsUiState.Idle
                }
                is SmsMessage -> {
                    _lastProcessedId.value = newMessage.id
                    _processingMessage.value = newMessage.body
                    _smsUiState.value = SmsUiState.Success( newMessage)
                }
            }
        }
    }

    fun detectSmishing(message: SmsMessage) {
        viewModelScope.launch {
            _classifierUiState.value = ClassifierUiState.Loading
            val result = smsRepository.classifyMessage(message)

            when(result) {
                is ClassifierApiResult.Success -> {
                    _classifierUiState.value = ClassifierUiState.Success(result.data)
                    _classifierResponse.value = result.data
                }
                is ClassifierApiResult.ApiError -> {
                    _classifierUiState.value = ClassifierUiState.ApiError(result)
                    Log.d("SmishingClassifier", "API error: ${result.statusCode}, status code: ${result.statusCode}")
                }
                is ClassifierApiResult.ExceptionError -> {
                    _classifierUiState.value = ClassifierUiState.Exception(result)
                    Log.d("SmishingClassifier", "Exception e: ${result.exception}, ${result.message}")
                }
            }
        }
    }

    fun getExplanation(request: ExplainerRequest) {
        viewModelScope.launch {
            _explainerUiState.value = ExplainerUiState.Loading
            val response = smsRepository.explainMessage(request)
            when(response) {
                is ExplainerApiResult.Success -> {
                    _explainerUiState.value = ExplainerUiState.Success(response.data)
                    _explainer.value = response.data
                }
                is ExplainerApiResult.ApiError -> {
                    _explainerUiState.value = ExplainerUiState.ApiError(response)
                    Log.d("Explainer", "API error: ${response.statusCode}, ${response.message}")
                }
                is ExplainerApiResult.ExceptionError -> {
                    _explainerUiState.value = ExplainerUiState.Exception(response)
                    Log.d("Explainer", "API error: ${response.exeception}, ${response.message}")
                }
            }
        }
    }

    fun scan(url: String) {
        viewModelScope.launch {
            _scanUiState.value = ScanUiState.Loading
            val response = smsRepository.getUrlVerdict(url)
            when(response) {
                is UrlApiResult.Success -> {
                    _scanUiState.value = ScanUiState.Success(response.data)
                    _urlVerdict.value = response.data
                }
                is UrlApiResult.ApiError -> {
                    _scanUiState.value = ScanUiState.ApiError(response)
                    Log.d("UrlAnalyzer", "API Error: ${response.statusCode}, ${response.message}")
                }
                is UrlApiResult.ValidationError -> {
                    _scanUiState.value = ScanUiState.Idle
                    Log.d("UrlAnalyzer", "${response.error}")
                }
                is UrlApiResult.ExceptionError -> {
                    _scanUiState.value = ScanUiState.Exception(response)
                    Log.d("UrlAnalyzer", "Exception: ${response.exception}, ${response.message}")
                }

                else -> { // no urls found
                    _scanUiState.value = ScanUiState.Idle
                }
            }
        }
    }

    // TODO
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