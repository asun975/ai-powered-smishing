package com.example.smishingdetection.ui.mainActivity

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smishingdetection.data.network.url.model.UrlAnalyzerResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SavedStateViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    // Use getMutableStateFlow to read and write the query directly
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

    fun setLastProcessedId(lastId: Long) {
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
}