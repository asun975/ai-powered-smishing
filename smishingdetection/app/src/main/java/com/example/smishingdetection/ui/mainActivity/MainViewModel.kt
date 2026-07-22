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
import com.example.smishingdetection.data.local.QuarantineRepository
import com.example.smishingdetection.data.local.model.AnalyzedMessage
import com.example.smishingdetection.data.network.classifier.NetworkClassifierApiRepository
import retrofit2.HttpException
import com.example.smishingdetection.data.network.classifier.model.ClassifyResult
import com.example.smishingdetection.data.network.explainer.NetworkExplainerApiRepository
import com.example.smishingdetection.data.network.explainer.model.ExplainerRequest
import com.example.smishingdetection.data.network.explainer.model.ExplainerResponse
import com.example.smishingdetection.data.network.url.NetworkUrlApiRepository
import com.example.smishingdetection.data.network.url.model.UrlAnalyzerResponse
import com.example.smishingdetection.data.sanitizer.InvalidInputException
import com.example.smishingdetection.data.sms.DefaultSmsRespository
import com.example.smishingdetection.data.sms.SmsMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.SocketTimeoutException

sealed interface SmsUiState {
    data class Success(val smsMessage: SmsMessage): SmsUiState
    object Error : SmsUiState
    object Idle: SmsUiState
    object Loading: SmsUiState
}

sealed interface ClassifierUiState {
    data class Success(val result: ClassifyResult): ClassifierUiState
    object Error: ClassifierUiState
    object Idle: ClassifierUiState
    object Loading: ClassifierUiState
}

sealed interface ExplainerUiState {
    data class Success(val explanation: ExplainerResponse): ExplainerUiState
    object Error: ExplainerUiState
    object Idle: ExplainerUiState
    object Loading: ExplainerUiState
}

sealed interface ScanUiState {
    data class Success(val scanResult: UrlAnalyzerResponse): ScanUiState
    data class Error(val error: String): ScanUiState
    object Idle: ScanUiState
    object Loading: ScanUiState
}

class MainViewModel (
    private val defaultQuarantineRepository: QuarantineRepository,
    private val smsRepository: DefaultSmsRespository,
    private val networkClassifierApiRepository: NetworkClassifierApiRepository,
    private val networkExplainerApiRepository: NetworkExplainerApiRepository,
    private val networkUrlApiRepository: NetworkUrlApiRepository,
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
            when(val newMessage = smsRepository.getLatestSms(lastProcessedSmsId.value)) {
                null -> {
                    Log.d("MainActivity", "null id returned by content provider")
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
            try {
                val response = networkClassifierApiRepository.classify(message)
                _classifierUiState.value = ClassifierUiState.Success(response)
            } catch (e: HttpException) {
                val statusCode = e.code()
                val errorBody = e.response()?.errorBody()?.string()
                Log.d("SmishingClassifier", "${e.javaClass.simpleName} ${e.javaClass.fields}")
                _classifierUiState.value = ClassifierUiState.Error
            } catch(e: Exception) {
                val exception = e.javaClass.simpleName.toString()
                val message = e.message.toString()
                Log.e("SmishingClassifier", "Exception: $exception - $message")
                _classifierUiState.value = ClassifierUiState.Error
            }
        }
    }

    private fun getExplanation(text: String, classification: String, riskScore: Float) {
        viewModelScope.launch {
            try {
                val response = networkExplainerApiRepository.explain(
                    ExplainerRequest(
                        text,
                        classification,
                        riskScore
                    )
                )
                _explainerUiState.value = ExplainerUiState.Success(response)
            } catch (e: HttpException) {
                val statusCode = e.code()
                val errorBody = e.response()?.errorBody() ?: e.message()
                _explainerUiState.value = ExplainerUiState.Error
            } catch (e: Exception) {
                val exception = e.javaClass.simpleName.toString()
                val message = e.message.toString()
                Log.e("LlmExplainer", "Exception: $exception - $message")
                _explainerUiState.value = ExplainerUiState.Error
            }

        }
    }

    private fun scan(message: String) {
        viewModelScope.launch {
            try {
                val response = networkUrlApiRepository.getVerdict(message)
                _scanUiState.value = ScanUiState.Success(response)

            } catch (e: NoSuchElementException) {
                Log.d("UrlAnalyzer", "No URL(s) found.")
                _scanUiState.value = ScanUiState.Idle

            } catch (e: InvalidInputException) {
                Log.d("UrlAnalyzer", "${e.message}")
                _scanUiState.value = ScanUiState.Idle

            } catch(e: SocketTimeoutException) {
                Log.e("UrlAnalyzer", "Exception: ${e.localizedMessage}")
                _scanUiState.value = ScanUiState.Error("Scan timed out.")

            } catch(e: ConnectException) {
                Log.e("UrlAnalyzer", "Exception: ${e.printStackTrace()}.")
                _scanUiState.value = ScanUiState.Error("Server is not available.")

            } catch(e: Exception) {
                Log.e("UrlAnalyzer", "Exception: ${e.javaClass.simpleName} - ${e.message}\nCause: ${e.cause}\nTrace: ${e.printStackTrace()}")
                _scanUiState.value = ScanUiState.Error("Debug Mode: ${e.toString()}, ${e.message.toString()}")

            } catch(e: HttpException) {
                val statusCode = e.code()
                Log.d("UrlAnalyzer", "HTTP $statusCode: ${e.response()?.message() ?: e.message()}")
                _scanUiState.value = ScanUiState.Error("${e.message()}")
            }
        }
    }

    fun refresh() {
        // ui states
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
            _classifierUiState.value = ClassifierUiState.Idle
            _explainerUiState.value = ExplainerUiState.Idle
            _scanUiState.value = ScanUiState.Idle

            checkLatestSms() // get latest sms using database observer
            val message = smsBody.value
            if(message.isNotEmpty()) {
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
                    val rowId = defaultQuarantineRepository.insertMessage(
                        smsAddress.value,
                        smsDate.value,
                        smsBody.value,
                        riskScore.toDouble(),
                        explanation.value,
                        scanResult.value
                    )

                    // TODO: Send user alert
                    val analyzedMessage = defaultQuarantineRepository.getMessageById(rowId)

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
                val quarantineRepository =
                    (this[ViewModelProvider
                        .AndroidViewModelFactory.Companion.APPLICATION_KEY] as MyApplication)
                        .defaultQuarantineRepository
                val networkClassifierApiRepository =
                    (this[ViewModelProvider
                        .AndroidViewModelFactory.Companion.APPLICATION_KEY] as MyApplication)
                        .networkClassifierApiRepository
                val networkExplainerApiRepository =
                    (this[ViewModelProvider
                        .AndroidViewModelFactory.Companion.APPLICATION_KEY] as MyApplication)
                        .networkExplainerApiRepository
                val networkUrlApiRepository =
                    (this[ViewModelProvider
                        .AndroidViewModelFactory.Companion.APPLICATION_KEY] as MyApplication)
                        .urlRepository
                val smsRepository =
                    (this[ViewModelProvider
                        .AndroidViewModelFactory.Companion.APPLICATION_KEY] as MyApplication)
                        .defaultSmsRepository
                val savedStateHandle = SavedStateHandle()
                MainViewModel(
                    quarantineRepository,
                    smsRepository,
                    networkClassifierApiRepository,
                    networkExplainerApiRepository,
                    networkUrlApiRepository,
                    savedStateHandle)
            }
        }
    }
}