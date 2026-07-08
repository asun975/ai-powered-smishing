package com.example.smishingdetection.ui.mainActivity

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smishingdetection.data.network.classifier.NetworkClassifierApiRepository
import com.example.smishingdetection.data.network.explainer.NetworkExplainerApiRepository
import com.example.smishingdetection.data.network.url.NetworkUrlApiRepository
import com.example.smishingdetection.data.sms.SmsProcessingCoordinator
import com.example.smishingdetection.data.sms.SmsRepository
import kotlinx.coroutines.launch

/*
 * TODO: Updates Main Activity UI
 */
class SmsViewModel(
    private val smsRepository: SmsRepository,
    private val smsProcessingCoordinator: SmsProcessingCoordinator,
    private val networkUrlApiRepository: NetworkUrlApiRepository,
    private val networkClassifierApiRepository: NetworkClassifierApiRepository,
    private val networkExplainerApiRepository: NetworkExplainerApiRepository,
    private val savedStateHandle: SavedStateHandle
): ViewModel() {
    fun loadMessage() {
        viewModelScope.launch {
            val message = smsRepository.getLatestSms()

            /*
             * show analysis of first message when app opens
             */
        }
    }
    fun refresh() {
        viewModelScope.launch {
            // TODO: Retrieve timestamp of last processed message from dispatchers?
            val messages = smsRepository.getNewSmsSince()
            // update UI state
        }
    }
}