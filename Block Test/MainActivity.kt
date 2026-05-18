package com.example.blocktest

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.telecom.TelecomManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ActivityCompat.requestPermissions(
            this,
            arrayOf(android.Manifest.permission.RECEIVE_SMS, android.Manifest.permission.READ_SMS),
            100
        )

        val sender = intent.getStringExtra("sender")
        val body = intent.getStringExtra("body")

        if (sender != null) {
            findViewById<TextView>(R.id.tvSender).text = sender
            findViewById<TextView>(R.id.tvBody).text = body

            findViewById<Button>(R.id.btnBlock).setOnClickListener {
                // Copy number to clipboard
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("phone number", sender)
                clipboard.setPrimaryClip(clip)

                // Open blocked numbers screen
                val telecomManager = getSystemService(TELECOM_SERVICE) as TelecomManager
                val blockIntent = telecomManager.createManageBlockedNumbersIntent()
                startActivity(blockIntent)
            }
        }
    }
}
