package com.example.smishingdetection.ui.mainActivity

import android.content.Intent
import android.os.Build
import android.provider.Contacts
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smishingdetection.AppLifecycleTracker
import com.example.smishingdetection.MessageDetailActivity
import com.example.smishingdetection.data.local.model.AnalyzedMessage
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
    private val savedStateHandle: SavedStateViewModel
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

    private fun checkLatestSms() {
        viewModelScope.launch {
            _smsUiState.value = SmsUiState.Loading
            when(val newMessage = smsRepository.checkLatestSms(savedStateHandle.lastProcessedSmsId as Long)) {
                null -> {
                    // No new messages
                    _smsUiState.value = SmsUiState.Idle
                }
                is SmsMessage -> {
                    savedStateHandle.setLastProcessedId(newMessage.id)
                    savedStateHandle.setSmsBody(newMessage.body)
                    savedStateHandle.setSmsAddress(newMessage.address)
                    _smsUiState.value = SmsUiState.Success( newMessage)
                }
            }
        }
    }

    private fun classify(message: String) {
        viewModelScope.launch {
            _classifierUiState.value = ClassifierUiState.Loading
            val result = smsRepository.classifyMessage(message)

            when(result) {
                is ClassifierApiResult.Success -> {
                    _classifierUiState.value = ClassifierUiState.Success(result.data)
                    savedStateHandle.setLabel(result.data.label)
                    savedStateHandle.setRiskScore(result.data.confidence)
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

    private fun getExplanation(text: String, classification: String, riskScore: Float) {
        viewModelScope.launch {
            _explainerUiState.value = ExplainerUiState.Loading
            val response = smsRepository.explainMessage(ExplainerRequest(text, classification, riskScore))
            when(response) {
                is ExplainerApiResult.Success -> {
                    _explainerUiState.value = ExplainerUiState.Success(response.data)
                    savedStateHandle.setExplanation(response.data.explanation)
                }
                is ExplainerApiResult.ApiError -> {
                    _explainerUiState.value = ExplainerUiState.ApiError(response)
                    Log.d("Explainer", "API error: ${response.statusCode}, ${response.message}")
                }
                is ExplainerApiResult.ExceptionError -> {
                    _explainerUiState.value = ExplainerUiState.Exception(response)
                    Log.d("Explainer", "Exception: ${response.exeception}, ${response.message}")
                }
            }
        }
    }

    private fun scan(url: String) {
        viewModelScope.launch {
            _scanUiState.value = ScanUiState.Loading
            val response = smsRepository.getUrlVerdict(url)
            when(response) {
                is UrlApiResult.Success -> {
                    _scanUiState.value = ScanUiState.Success(response.data)
                    savedStateHandle.setScanResult(response.data)
                }
                is UrlApiResult.ApiError -> {
                    _scanUiState.value = ScanUiState.ApiError(response)
                    Log.d("UrlAnalyzer", "API Error: ${response.statusCode}, ${response.message}")
                }
                is UrlApiResult.ValidationError -> {
                    _scanUiState.value = ScanUiState.Idle
                    Log.d("UrlAnalyzer", "$response.error")
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

    fun refresh() {
        // ui states
        _smsUiState.value = SmsUiState.Idle
        _classifierUiState.value = ClassifierUiState.Idle
        _explainerUiState.value = ExplainerUiState.Idle
        _scanUiState.value = ScanUiState.Idle

        savedStateHandle.resetSavedState()
    }
    // TODO
    fun processMessage() {
        viewModelScope.launch {
            checkLatestSms() // get latest sms using database observer
            val message = savedStateHandle.smsBody.value
            if(message != null) {
                // Use classifier API model and send urls to sandbox
                classify(savedStateHandle.smsBody.value)
                scan(savedStateHandle.smsBody.value)

                // Generate explanation for high and medium risk messages
                if (savedStateHandle.riskLevel.value == "MEDIUM" || savedStateHandle.riskLevel.value == "HIGH") {
                    val riskScore = savedStateHandle.riskScore.value as Float
                    getExplanation(
                        savedStateHandle.smsBody.value,
                        savedStateHandle.label.value,
                        riskScore
                    )
                    // Save to local database
                    val rowId = smsRepository.insertIntoDatabase(
                        savedStateHandle.smsAddress.value,
                        savedStateHandle.smsDate.value,
                        savedStateHandle.smsBody.value,
                        riskScore,
                        savedStateHandle.explanation.value,
                        savedStateHandle.scanResult.value
                    )

                    /** TODO: Send user alert
                    val alert = smsRepository.getMessageById(rowId)

                    if(AppLifecycleTracker.isAppInForeground) {
                        showSmishingDialog(alert)
                    } else {
                        NotificationHelper.sendSmishingNotification(smishingAlert)
                    }
                    */
                }
            }
            refresh()
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