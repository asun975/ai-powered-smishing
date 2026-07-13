package com.example.smishingdetection.ui.quarantine

import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.compose.runtime.State
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.text.toLowerCase
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.smishingdetection.MessageDetailActivity
import com.example.smishingdetection.MyApplication
import com.example.smishingdetection.data.local.BlockRepository
import com.example.smishingdetection.data.local.QuarantineRepository
import com.example.smishingdetection.data.local.model.AnalyzedMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SuspiciousMessageUiState(
    val selectedTab: MessageTab = MessageTab.CAUTION,
    val messages: Flow<List<AnalyzedMessage>> = flowOf(emptyList()),
    val isLoading: Boolean = false
)

enum class MessageTab(val status: String) {
    CAUTION("caution"),
    QUARANTINE("quarantine")
}
/*
 * TODO: Updates SuspiciousMessagesActivity and MessageViewDetailActivity
 */
class SuspiciousMessagesViewModel(
    private val quarantineRepository: QuarantineRepository,
    private val blockRepository: BlockRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    // TODO
    private val _toastEvent = MutableStateFlow("")
    val toastEvent = _toastEvent.asSharedFlow()
    private val _suspiciousMessageUiState = MutableStateFlow(SuspiciousMessageUiState())
    val suspiciousMessageUiState = _suspiciousMessageUiState.asStateFlow()
    val messages: StateFlow<List<AnalyzedMessage>> = quarantineRepository.getMessagesByStatus(suspiciousMessageUiState.value.selectedTab.status)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    fun selectTab(tab: MessageTab) {
        _suspiciousMessageUiState.update { it.copy(selectedTab = tab) }
        loadMessages(tab)
    }
    fun moveTabs(id: Long) {
        viewModelScope.launch {
            val currentTab = suspiciousMessageUiState.value.selectedTab
            if (currentTab == MessageTab.CAUTION) {
                quarantineRepository.quarantineMessage(id)
                _toastEvent.emit("AnalyzedMessage moved to quarantine!")
            } else {
                quarantineRepository.markAsSafe(id)
                _toastEvent.emit("AnalyzedMessage marked as safe")
            }
        }
    }
    private fun loadMessages(tab: MessageTab) {
        viewModelScope.launch {
            val messages = quarantineRepository.getMessagesByStatus(tab.status)
            _suspiciousMessageUiState.update {
                it.copy(
                    messages = messages
                )
            }
        }
    }

    // Define ViewModel factory in a companion object
    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val blockRepository =
                    (this[ViewModelProvider
                        .AndroidViewModelFactory.Companion.APPLICATION_KEY] as MyApplication)
                        .defaultBlockRepository
                val quarantineRepository =
                    (this[ViewModelProvider
                        .AndroidViewModelFactory.Companion.APPLICATION_KEY] as MyApplication)
                        .quarantineRepository
                val savedStateHandle = createSavedStateHandle()
                SuspiciousMessagesViewModel(quarantineRepository, blockRepository, savedStateHandle)
            }
        }
    }
}