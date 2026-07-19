package com.example.smishingdetection.ui.quarantine

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.telecom.TelecomManager
import android.view.MenuItem
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.smishingdetection.R
import kotlinx.coroutines.launch

class MessageDetailActivity : AppCompatActivity() {
    private var messageId: Long = -1
    private var status: String = "caution"
    private var phoneNumber: String = ""
    private val viewModel: MessageDetailViewModel by viewModels {
        MessageDetailViewModel.Factory
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)


        setContentView(R.layout.activity_message_detail)
        supportActionBar?.hide()
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }
        supportActionBar?.apply {
            title = "AnalyzedMessage Detail"
            setDisplayHomeAsUpEnabled(true)
        }
        val tvDate: TextView = findViewById(R.id.tvDate)
        val tvPhone: TextView = findViewById(R.id.tvPhone)
        val tvMessage: TextView = findViewById(R.id.tvMessage)
        val tvRiskScore: TextView = findViewById(R.id.tvRiskScore)
        val tvExplanation: TextView = findViewById(R.id.tvExplanation)
        val btnAction: Button = findViewById(R.id.btnAction)
        val btnBlock: Button = findViewById(R.id.btnBlock)
        val btnDelete: ImageButton = findViewById(R.id.btnDelete)
        val tvUrlScan: TextView = findViewById(R.id.tvUrlScan)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.loadMessage(messageId)

                viewModel.messageDetailUiState.collect { state ->
                    tvDate.text = state.analyzedMessage?.date
                    tvPhone.text = state.analyzedMessage?.phoneNumber
                    tvMessage.text = state.analyzedMessage?.message
                    tvUrlScan.text =
                        state.analyzedMessage?.urlScanResult?.ifBlank { "No scan result available." }

                    val score = state.analyzedMessage?.riskScore
                    tvRiskScore.text = String.format("%.0f%%", score)

                    tvExplanation.text =
                        state.analyzedMessage?.explanation?.ifBlank { "No explanation available." }

                    // Action button depends on status
                    val messageStatus = state.status
                    btnAction.text =
                        if (messageStatus == DetailViewStatus.QUARANTINE) "Quarantine" else "Mark as Safe"
                    btnAction.setOnClickListener {
                        if (messageStatus == DetailViewStatus.CAUTION) {
                            // Move to quarantine — update status in DB (use db.updateStatus function created & also add the toast to notify the user of the changes)
                            viewModel.quarantine()
                        } else {
                            // Mark as safe — delete from suspicious DB
                            viewModel.deleteMessage()
                        }
                        finish()
                    }
                    val sender: String = state.analyzedMessage?.phoneNumber ?: "Unknown"
                    btnBlock.setOnClickListener {
                        if (!state.isBlocked && (sender != "Unknown")) {
                            viewModel.blockSender() // write to database
                            AlertDialog.Builder(this as Context)
                                .setTitle("Block Number")
                                .setMessage("$sender will be copied to your clipboard. You can paste it into the block list that opens.")
                                .setPositiveButton("Open Block List") { _, _ ->
                                    lifecycleScope.launch {
                                        // Copy number to clipboard
                                        val clipboard =
                                            getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip =
                                            ClipData.newPlainText("phone number", phoneNumber)
                                        clipboard.setPrimaryClip(clip)

                                        // Open system block list
                                        val telecomManager =
                                            getSystemService(TELECOM_SERVICE) as TelecomManager
                                        startActivity(
                                            telecomManager.createManageBlockedNumbersIntent(),
                                            null
                                        )
                                    }
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        } else {// error
                            AlertDialog.Builder(this as Context?)
                                .setTitle("Block Number")
                                .setMessage("$sender is already in Smishing Detector's Block list!")
                                .setPositiveButton("Ok") { _, _ ->
                                    finish()
                                }
                                .show()
                        }
                    }

                    btnDelete.setOnClickListener {
                        AlertDialog.Builder(this as Context)
                            .setTitle("Delete AnalyzedMessage")
                            .setMessage("Remove this message from the log?")
                            .setPositiveButton("Delete") { _, _ ->
                                viewModel.deleteMessage()
                                Toast.makeText(this, "AnalyzedMessage deleted", Toast.LENGTH_SHORT)
                                    .show()
                                finish()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }

                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}