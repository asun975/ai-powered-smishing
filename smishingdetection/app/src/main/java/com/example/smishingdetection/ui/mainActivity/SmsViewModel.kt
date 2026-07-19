package com.example.smishingdetection.ui.mainActivity

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.smishingdetection.ui.quarantine.AppLifecycleTracker
import com.example.smishingdetection.MyApplication
import com.example.smishingdetection.data.local.model.AnalyzedMessage
import com.example.smishingdetection.data.network.classifier.model.ClassifierApiResult
import com.example.smishingdetection.data.network.classifier.model.ClassifierResponse
import com.example.smishingdetection.data.network.explainer.model.ExplainerApiResult
import com.example.smishingdetection.data.network.explainer.model.ExplainerRequest
import com.example.smishingdetection.data.network.explainer.model.ExplainerResponse
import com.example.smishingdetection.data.network.url.model.UrlAnalyzerResponse
import com.example.smishingdetection.data.network.url.model.UrlApiResult
import com.example.smishingdetection.data.sms.SmsMessage
import com.example.smishingdetection.data.sms.SmsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

class SmsViewModel (
    private val smsRepository: SmsRepository,
    private val savedStateHandle: SavedStateHandle
): ViewModel() {
    // Smishing alerts
    private val _showAlertEvent = MutableSharedFlow<AnalyzedMessage>()
    val showAlertEvent = _showAlertEvent.asSharedFlow()
    private val _sendUserAlert = MutableSharedFlow<AnalyzedMessage>()
    val sendUserAlert = _sendUserAlert.asSharedFlow()

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

    // Use getMutableStateFlow to read and write the saved states directly
    private val _smsId: MutableStateFlow<Long?> = savedStateHandle.getMutableStateFlow("smsId", null)
    val smsId: StateFlow<Long?> = _smsId.asStateFlow()

    private val _smsBody = savedStateHandle.getMutableStateFlow("smsBody", "")
    val smsBody = _smsBody.asStateFlow()

    private val _smsAddress = savedStateHandle.getMutableStateFlow("smsAddress", "Unknown")
    val smsAddress: StateFlow<String> = _smsAddress.asStateFlow()

    private val _smsDate = savedStateHandle.getMutableStateFlow("smsDate", "")
    val smsDate: StateFlow<String> = _smsDate.asStateFlow()

    private val _lastProcessedSmsId: MutableStateFlow<Long?> = savedStateHandle.getMutableStateFlow("lastProcessedSmsId", null)
    val lastProcessedSmsId: StateFlow<Long?> = _lastProcessedSmsId.asStateFlow()

    private val _riskScore: MutableStateFlow<Float?> = savedStateHandle.getMutableStateFlow("riskScore", null)
    val riskScore: StateFlow<Float?> = _riskScore.asStateFlow()

    private val _label = savedStateHandle.getMutableStateFlow("label", "")
    val label: StateFlow<String> = _label.asStateFlow()

    private val _scanResult: MutableStateFlow<UrlAnalyzerResponse?> = savedStateHandle.getMutableStateFlow("scanResult", null)
    val scanResult: StateFlow<UrlAnalyzerResponse?> = _scanResult.asStateFlow()

    private val _riskLevel: MutableStateFlow<String?> = savedStateHandle.getMutableStateFlow("riskLevel", null)
    val riskLevel: StateFlow<String?> = _riskLevel.asStateFlow()

    private val _explanation = savedStateHandle.getMutableStateFlow("explanation", "")
    val explanation: StateFlow<String> = _explanation.asStateFlow()

    fun setSmsId(newId: Long) {
        // Updating the MutableStateFlow automatically updates the SavedStateHandle
        _smsId.value = newId
    }

    fun setSmsBody(text: String) {
        // Updating the MutableStateFlow automatically updates the SavedStateHandle
        _smsBody.value = text
    }

    fun setSmsAddress(newAddress: String) {
        // Updating the MutableStateFlow automatically updates the SavedStateHandle
        _smsAddress.value = newAddress
    }

    fun setLastProcessedId(lastId: Long?) {
        // Updating the MutableStateFlow automatically updates the SavedStateHandle
        _lastProcessedSmsId.value = lastId
    }

    fun setRiskScore(riskScore: Float) {
        // Updating the MutableStateFlow automatically updates the SavedStateHandle
        _riskScore.value = riskScore
    }

    fun setRiskLevel(riskLevel: String) {
        // Updating the MutableStateFlow automatically updates the SavedStateHandle
        _riskLevel.value = riskLevel
    }

    fun setLabel(classification: String) {
        // Updating the MutableStateFlow automatically updates the SavedStateHandle
        _label.value = classification
    }

    fun setScanResult(verdict: UrlAnalyzerResponse) {
        // Updating the MutableStateFlow automatically updates the SavedStateHandle
        _scanResult.value = verdict
    }

    fun setExplanation(message: String) {
        // Updating the MutableStateFlow automatically updates the SavedStateHandle
        _explanation.value = message
    }

    fun setDate(date: String) {
        // Updating the MutableStateFlow automatically updates the SavedStateHandle
        _smsDate.value = date
    }

    fun resetSavedState() {
        _smsId.value = null
        _smsBody.value = ""
        _smsAddress.value = ""
        _label.value = ""
        _explanation.value = ""
        _scanResult.value = null
        _riskScore.value = 0.toFloat()
        _riskLevel.value = ""
        _smsDate.value = ""
    }

    private fun showDialog(alert: AnalyzedMessage) {
        viewModelScope.launch {
            _showAlertEvent.emit(alert)
        }
    }

    private fun sendUserAlert(alert: AnalyzedMessage) {
        viewModelScope.launch {
            _sendUserAlert.emit(alert)
        }
    }

    private fun checkLatestSms() {
        viewModelScope.launch {
            _smsUiState.value = SmsUiState.Loading
            when(val newMessage = smsRepository.checkLatestSms(lastProcessedSmsId.value)) {
                null -> {
                    Log.d("MainActivity", "null id returned by content provider")
                    _smsUiState.value = SmsUiState.Idle
                }
                is SmsMessage -> {
                    setSmsId(newMessage.id)
                    setSmsBody(newMessage.body)
                    setSmsAddress(newMessage.address)
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
                    setLabel(result.data.label)
                    setRiskScore(result.data.confidence)
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
                    setExplanation(response.data.explanation)
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

    private fun scan(message: String) {
        viewModelScope.launch {
            _scanUiState.value = ScanUiState.Loading
            val response = smsRepository.getUrlVerdict(message)
            when(response) {
                is UrlApiResult.Success -> {
                    _scanUiState.value = ScanUiState.Success(response.data)
                    setScanResult(response.data)
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
        setLastProcessedId(smsId.value)

        resetSavedState()
    }
    // TODO
    fun processMessage() {
        viewModelScope.launch {
            // Debug ui states
            _smsUiState.value = SmsUiState.Idle
            _classifierUiState.value = ClassifierUiState.Idle
            _explainerUiState.value = ExplainerUiState.Idle
            _scanUiState.value = ScanUiState.Idle

            checkLatestSms() // get latest sms using database observer
            val message = smsBody.value
            if(!message.isNullOrEmpty()) {
                // Use classifier API model and send urls to sandbox
                classify(smsBody.value)
                scan(smsBody.value)

                // Generate explanation for high and medium risk messages
                if (riskLevel.value == "MEDIUM" || riskLevel.value == "HIGH") {
                    val riskScore = riskScore.value as Float
                    getExplanation(
                        smsBody.value,
                        label.value,
                        riskScore
                    )
                    // Save to local database
                    val rowId = smsRepository.insertIntoDatabase(
                        smsAddress.value,
                        smsDate.value,
                        smsBody.value,
                        riskScore,
                        explanation.value,
                        scanResult.value
                    )

                    // TODO: Send user alert
                    val analyzedMessage = smsRepository.getMessageById(rowId)

                    if(AppLifecycleTracker.isAppInForeground) {
                        showDialog(analyzedMessage)
                    } else {
                        sendUserAlert(analyzedMessage)
                    }
                }
            }
            refresh()
        }
    }
    // Define ViewModel factory in a companion object
    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val repository =
                    (this[ViewModelProvider
                        .AndroidViewModelFactory.Companion.APPLICATION_KEY] as MyApplication)
                        .smsRepository
                val savedStateHandle = SavedStateHandle()
                SmsViewModel(repository, savedStateHandle)
            }
        }
    }
}