package com.example.smishingdetection.ui.quarantine

import android.widget.Toast
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.smishingdetection.MyApplication
import com.example.smishingdetection.data.local.BlockRepository
import com.example.smishingdetection.data.local.QuarantineRepository
import com.example.smishingdetection.data.local.model.AnalyzedMessage
import com.example.smishingdetection.data.local.model.BlockedPhoneNumber
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.String

data class DetailUiState(
    val isLoading: Boolean = true,
    val analyzedMessage: AnalyzedMessage? = null,
    val error: String? = "Could not retrieve message!",
    val isBlocked: Boolean = false,
    val isDeleted: Boolean = false,
    val status: DetailViewStatus = DetailViewStatus.CAUTION,
    val unknownSender: Boolean = true
)

class MessageDetailViewModel(
    private val quarantineRepository: QuarantineRepository,
    private val blockRepository: BlockRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _messageDetailUiState = MutableStateFlow(DetailUiState())
    val messageDetailUiState = _messageDetailUiState.asStateFlow()
    private val messageId = savedStateHandle.get<Long>("id") ?: -1

    fun loadMessage(id: Long) {
        viewModelScope.launch {
            val message = quarantineRepository.getMessageById(id)
            val getStatus = when(message.status) {
                "HIGH" -> DetailViewStatus.QUARANTINE
                else -> DetailViewStatus.CAUTION
            }
            if (message.phoneNumber != "Unknown") {
                val sender = message.phoneNumber
                val getBlocked = blockRepository.checkBlockList(sender)
                _messageDetailUiState.update {
                    it.copy(
                        isLoading = false,
                        analyzedMessage = message,
                        status = getStatus,
                        isBlocked = getBlocked
                    )
                }
            } else {
                _messageDetailUiState.update {
                    it.copy(
                        isLoading = false,
                        analyzedMessage = message,
                        status = getStatus,
                        unknownSender = true
                    )
                }
            }

        }
    }
    fun quarantine() {
        viewModelScope.launch {
            quarantineRepository.quarantineMessage(messageId)
            _messageDetailUiState.update {
                it.copy(
                    status = DetailViewStatus.QUARANTINE
                )
            }
        }
        //Toast.makeText(this, "AnalyzedMessage moved to quarantine!", Toast.LENGTH_SHORT).show()
    }

    fun deleteMessage() {
        viewModelScope.launch {
            quarantineRepository.markAsSafe(messageId)
            _messageDetailUiState.update {
                it.copy(
                    isDeleted = true
                )
            }
        }
        //Toast.makeText(this, "AnalyzedMessage marked as safe", Toast.LENGTH_SHORT).show()
    }

    fun blockSender() {
        viewModelScope.launch {
            blockRepository.addToBlockList(messageDetailUiState.value.analyzedMessage!!.phoneNumber)
            _messageDetailUiState.update {
                it.copy(
                    isBlocked = true
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
                MessageDetailViewModel(quarantineRepository, blockRepository, savedStateHandle)
            }
        }
    }

}