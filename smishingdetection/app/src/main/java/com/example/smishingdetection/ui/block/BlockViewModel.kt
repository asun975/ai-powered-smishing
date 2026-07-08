package com.example.smishingdetection.ui.block

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.telecom.TelecomManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.smishingdetection.ui.block.BlockContainer
import com.example.smishingdetection.data.local.DefaultBlockRepository
import kotlinx.coroutines.launch

class BlockViewModel(
    private val repository: DefaultBlockRepository,
    private val savedStateHandle: SavedStateHandle
): ViewModel() {
    // TODO
    private suspend fun blockNumber() {
        if(db.checkBlockedPhone(phoneNumber)) {
            AlertDialog.Builder(this)
                .setTitle("Block Number")
                .setMessage("$phoneNumber is already in Smishing Detector's Block list!")
                .setPositiveButton("Ok") { _, _ ->
                    finish()
                }
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Block Number")
                .setMessage("$phoneNumber will be copied to your clipboard. You can paste it into the block list that opens.")
                .setPositiveButton("Open Block List") { _, _ ->
                    lifecycleScope.launch {
                        // Copy number to clipboard
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("phone number", phoneNumber)
                        clipboard.setPrimaryClip(clip)

                        // Open system block list
                        val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                        startActivity(telecomManager.createManageBlockedNumbersIntent(), null)

                        // Insert new row into app block list
                        db.addToBlockList(phoneNumber)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    // Define ViewModel factory in a companion object
    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val repository =
                    (this[ViewModelProvider
                        .AndroidViewModelFactory.Companion.APPLICATION_KEY] as BlockContainer)
                        .defaultBlockRepository
                val savedStateHandle = createSavedStateHandle()
                BlockViewModel(repository, savedStateHandle)
            }
        }
    }
}