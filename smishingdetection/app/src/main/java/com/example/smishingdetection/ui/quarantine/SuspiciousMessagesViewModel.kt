package com.example.smishingdetection.ui.quarantine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.smishingdetection.MyApplication
import com.example.smishingdetection.data.local.BlockRepository
import com.example.smishingdetection.data.local.DefaultQuarantineRepository
import com.example.smishingdetection.data.local.model.AnalyzedMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
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
    private val defaultQuarantineRepository: DefaultQuarantineRepository,
    private val blockRepository: BlockRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    // TODO
    private val _toastEvent = MutableStateFlow("")
    val toastEvent = _toastEvent.asSharedFlow()
    private val _suspiciousMessageUiState = MutableStateFlow(SuspiciousMessageUiState())
    val suspiciousMessageUiState = _suspiciousMessageUiState.asStateFlow()

    fun selectTab(tab: MessageTab) {
        _suspiciousMessageUiState.update { it.copy(selectedTab = tab) }
        loadMessages(tab)
    }

    fun moveTabs(id: Long) {
        viewModelScope.launch {
            val currentTab = suspiciousMessageUiState.value.selectedTab
            if (currentTab == MessageTab.CAUTION) {
                defaultQuarantineRepository.quarantineMessage(id)
                _toastEvent.emit("Message moved to quarantine!")
            } else {
                defaultQuarantineRepository.markAsSafe(id)
                _toastEvent.emit("Message marked as safe.")
            }
        }
    }

    fun loadMessages(tab: MessageTab) {
        viewModelScope.launch {
            val messages = defaultQuarantineRepository.getMessagesByStatus(tab.status)
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
                            .defaultQuarantineRepository
                    val savedStateHandle = createSavedStateHandle()
                    SuspiciousMessagesViewModel(
                        quarantineRepository,
                        blockRepository,
                        savedStateHandle
                    )
                }
            }
        }
    }

