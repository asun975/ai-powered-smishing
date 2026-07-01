package com.example.smishingdetection

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.content.ClipData
import android.content.ClipboardManager
import android.telecom.TelecomManager

class MessageDetailActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private var messageId: Long = -1
    private var status: String = "caution"
    private var phoneNumber: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_message_detail)

        supportActionBar?.apply {
            title = "Message Detail"
            setDisplayHomeAsUpEnabled(true)
        }

        db = DatabaseHelper(this)

        // Read extras
        phoneNumber = intent.getStringExtra("phone") ?: "Unknown"
        val date = intent.getStringExtra("date") ?: ""
        val message = intent.getStringExtra("message") ?: ""
        val riskScoreStr = intent.getStringExtra("risk_score") ?: "0"
        status = intent.getStringExtra("status") ?: "caution"
        val explanation = intent.getStringExtra("explanation") ?: ""
        messageId = intent.getStringExtra("id")?.toLongOrNull() ?: -1
        val urlScanResult = intent.getStringExtra("url_scan_result") ?: ""

        val tvDate: TextView = findViewById(R.id.tvDate)
        val tvPhone: TextView = findViewById(R.id.tvPhone)
        val tvMessage: TextView = findViewById(R.id.tvMessage)
        val tvRiskScore: TextView = findViewById(R.id.tvRiskScore)
        val tvExplanation: TextView = findViewById(R.id.tvExplanation)
        val btnAction: Button = findViewById(R.id.btnAction)
        val btnBlock: Button = findViewById(R.id.btnBlock)
        val btnDelete: ImageButton = findViewById(R.id.btnDelete)
        val tvUrlScan: TextView = findViewById(R.id.tvUrlScan)

        tvDate.text = date
        tvPhone.text = "Phone number: $phoneNumber"
        tvMessage.text = message
        tvUrlScan.text = urlScanResult.ifBlank { "No URL found in message." }

        val score = riskScoreStr.toDoubleOrNull() ?: 0.0
        tvRiskScore.text = String.format("%.0f%%", score)

        tvExplanation.text = explanation.ifBlank { "No explanation available." }

        // Action button label depends on status
        btnAction.text = if (status == "caution") "Quarantine" else "Mark as Safe"

        btnAction.setOnClickListener {
            handleActionButton()
        }

        btnBlock.setOnClickListener {
            blockNumber()
        }

        btnDelete.setOnClickListener {
            confirmDelete()
        }
    }

    private fun handleActionButton() {
        if (status == "caution") {
            // Move to quarantine — update status in DB (use db.updateStatus function created & also add the toast to notify the user of the changes)
            //TODO
            Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show()
        } else {
            // Mark as safe — delete from suspicious DB
            db.deleteMessage(messageId)
            Toast.makeText(this, "Message marked as safe", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    private fun blockNumber() {
        AlertDialog.Builder(this)
            .setTitle("Block Number")
            .setMessage("$phoneNumber will be copied to your clipboard. You can paste it into the block list that opens.")
            .setPositiveButton("Open Block List") { _, _ ->
                // Copy number to clipboard
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("phone number", phoneNumber)
                clipboard.setPrimaryClip(clip)

                // Open system block list
                val telecomManager = getSystemService(TELECOM_SERVICE) as TelecomManager
                val blockIntent = telecomManager.createManageBlockedNumbersIntent()
                startActivity(blockIntent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Delete Message")
            .setMessage("Remove this message from the log?")
            .setPositiveButton("Delete") { _, _ ->
                db.deleteMessage(messageId)
                Toast.makeText(this, "Message deleted", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}