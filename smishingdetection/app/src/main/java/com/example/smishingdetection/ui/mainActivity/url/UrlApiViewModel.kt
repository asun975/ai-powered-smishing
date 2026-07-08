package com.example.smishingdetection.ui.mainActivity.url

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.smishingdetection.data.network.url.NetworkUrlApiRepository
import com.example.smishingdetection.data.network.url.model.UrlAnalyzerRequest
import com.example.smishingdetection.data.network.url.model.UrlAnalyzerResponse
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/**
 * UI state for the Main Activity
 */
sealed interface ScanUiState {
    data class Success(val urlVerdict: UrlAnalyzerResponse) : ScanUiState
    object Error : ScanUiState
    object Loading : ScanUiState
}

class UrlApiViewModel(
    private val networkUrlApiRepository: NetworkUrlApiRepository
) : ViewModel() {
    /** The mutable State that stores the status of the most recent request */
    var scanUiState: ScanUiState by mutableStateOf(ScanUiState.Loading)
        private set

    fun getVerdict(request: UrlAnalyzerRequest) {
        viewModelScope.launch {
            scanUiState = ScanUiState.Loading
            scanUiState = try {
                ScanUiState.Success(networkUrlApiRepository.getVerdict(request))
            } catch (e: IOException) {
                ScanUiState.Error
            } catch (e: HttpException) {
                ScanUiState.Error
            }
        }
    }

    /**
     * Factory for [UrlScanViewModel] that takes [UrlScanRepository] as a dependency
     */
    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as UrlApiContainer)
                val networkUrlApiRepository = application.networkUrlApiRepository
                UrlApiViewModel(networkUrlApiRepository)
            }
        }
    }
}