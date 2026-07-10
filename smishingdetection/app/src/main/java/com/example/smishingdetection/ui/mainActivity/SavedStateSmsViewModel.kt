package com.example.smishingdetection.ui.mainActivity

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SavedStateSmsViewModel(private val savedStateHandle: SavedStateHandle) {
    // Use getMutableStateFlow to read and write the query directly
    private val _quarantineId = savedStateHandle.getMutableStateFlow("quarantineId", -1)
    val query: StateFlow<Int> = _quarantineId.asStateFlow()

    fun setQuery(newRowId: Int) {
        // Updating the MutableStateFlow automatically updates the SavedStateHandle
        _quarantineId.value = newRowId
    }
}