package com.example.smishingdetection.quarantine

import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.smishingdetection.MessageDetailActivity
import com.example.smishingdetection.data.local.QuarantineContainer
import com.example.smishingdetection.data.local.QuarantineRepository
import com.example.smishingdetection.data.local.model.AnalyzedMessage
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class QuarantineViewModel(
    private val repository: QuarantineRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    // TODO
    private suspend fun saveToDatabase(){
        // Save to database for MEDIUM (caution) and HIGH (quarantined) risk
        if (riskCategory == "MEDIUM" || riskCategory == "HIGH") {
            val timestamp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            } else {
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    .format(java.util.Date())
            }
            val newMessage = AnalyzedMessage(
                id = 0, // database will auto-generate message ID
                phoneNumber = sender,
                date = timestamp,
                message = originalBody,
                riskScore = riskScorePercent.toDouble(),
                explanation = explanation,
                urlScanResult = scanResult ?: ""
            )
            val newMessageId = db.insertMessage(newMessage)
            Log.d("MainActivity", "Saved message to DB: $riskCategory")
    }
    private fun showSmishingDialog(
        analyzedMessage: AnalyzedMessage
    ) {
        val riskScorePercent = analyzedMessage.riskScore * 100
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("⚠️ Suspicious SMS Detected")
        builder.setMessage(
            "This message may be a phishing attempt.\n\n" +
                    "From: ${analyzedMessage.phoneNumber}\n" +
                    "Risk: ${analyzedMessage.status} (${String.format("%.0f", riskScorePercent)}%)\n\n" +
                    "Reason: ${analyzedMessage.explanation}"
        )
        builder.setPositiveButton("View Details") { _, _ ->
            val intent = Intent(this, MessageDetailActivity::class.java).apply {
                putExtra("phone", analyzedMessage.phoneNumber)
                putExtra("date", analyzedMessage.date)
                putExtra("message", analyzedMessage.message)
                putExtra("risk_score", riskScorePercent.toString())
                putExtra("status", analyzedMessage.status)
                putExtra("explanation", analyzedMessage.explanation)
                putExtra("id", analyzedMessage.id.toString())
                putExtra("url_scan_result", analyzedMessage.urlScanResult)
            }
            startActivity(intent)
        }
        builder.setNegativeButton("Dismiss", null)
        builder.show()
    }

    private suspend fun handleActionButton() {
        if (status == "caution") {
            // Move to quarantine — update status in DB (use db.updateStatus function created & also add the toast to notify the user of the changes)
            db.quarantineMessage(messageId)
            Toast.makeText(this, "AnalyzedMessage moved to quarantine!", Toast.LENGTH_SHORT).show()
        } else {
            // Mark as safe — delete from suspicious DB
            db.markAsSafe(messageId)
            Toast.makeText(this, "AnalyzedMessage marked as safe", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Delete AnalyzedMessage")
            .setMessage("Remove this message from the log?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    this@MessageDetailActivity.db.markAsSafe(messageId)
                }
                Toast.makeText(this, "AnalyzedMessage deleted", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadMessages() {
        lifecycleScope.launch {
            db.getByStatus(currentTab).collect { messages ->
                adapter.updateData(messages)
                if (messages.isEmpty()) {
                    // show empty state
                    recyclerView.visibility = View.GONE
                    emptyView.visibility = View.VISIBLE
                    emptyView.text = if (currentTab == "caution")
                        "No caution messages" else "No quarantined messages"
                } else {
                    // show data
                    recyclerView.visibility = View.VISIBLE
                    emptyView.visibility = View.GONE
                }
            }
        }
    }

    private fun openDetail(msg: AnalyzedMessage) {
        val intent = Intent(this, MessageDetailActivity::class.java).apply {
            putExtra("phone", msg.phoneNumber)
            putExtra("date", msg.date)
            putExtra("message", msg.message)
            putExtra("risk_score", msg.riskScore)
            putExtra("status", msg.status)
            putExtra("explanation", msg.explanation)
            putExtra("id", msg.id)
            putExtra("url_scan_result", msg.urlScanResult ?: "")
        }
        startActivity(intent)
    }


    private fun showPopupMenu(msg: AnalyzedMessage, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "Detail")
        popup.menu.add(0, 2, 1, if (currentTab == "caution") "Quarantine" else "Mark Safe")

        popup.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                1 -> openDetail(msg)
                2 -> {
                    val id = msg.id
                    if (currentTab == "caution") {
                        lifecycleScope.launch {
                            db.quarantineMessage(id)
                        }
                        Toast.makeText(this, "AnalyzedMessage moved to quarantine!", Toast.LENGTH_SHORT).show()
                    } else {
                        lifecycleScope.launch {
                            db.markAsSafe(id)
                        }
                        Toast.makeText(this, "AnalyzedMessage marked as safe", Toast.LENGTH_SHORT).show()
                    }
                    loadMessages()
                }
            }
            true
        }
        popup.show()
    }

    // Define ViewModel factory in a companion object
    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val repository =
                    (this[ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY] as QuarantineContainer)
                        .quarantineRepository
                val savedStateHandle = createSavedStateHandle()
                QuarantineViewModel(repository, savedStateHandle)
            }
        }
    }
}