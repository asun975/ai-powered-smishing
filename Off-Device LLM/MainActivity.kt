package com.example.prototypellm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    // Place your API Key
    private val apiKey = "API KEY"
    private val analyzer = PhishingAnalyzer(apiKey)

    private val smsResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val sender = intent?.getStringExtra("sender")
            val result = intent?.getStringExtra("result")
            val resultText = findViewById<TextView>(R.id.resultText)
            resultText.text = "New SMS from $sender:\n\n$result"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        if (checkSelfPermission(android.Manifest.permission.RECEIVE_SMS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                arrayOf(
                    android.Manifest.permission.RECEIVE_SMS,
                    android.Manifest.permission.READ_SMS
                ),
                100
            )
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val inputMessage = findViewById<EditText>(R.id.inputMessage)
        val analyzeButton = findViewById<Button>(R.id.analyzeButton)
        val resultText = findViewById<TextView>(R.id.resultText)

        analyzeButton.setOnClickListener {
            val userInput = inputMessage.text.toString()

            if (userInput.isBlank()) {
                resultText.text = "Please enter a message."
                return@setOnClickListener
            }

            resultText.text = "Analyzing..."

            CoroutineScope(Dispatchers.IO).launch {
                val result = analyzer.analyzeMessage(userInput)

                withContext(Dispatchers.Main) {
                    resultText.text = result
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(
            smsResultReceiver,
            IntentFilter("com.example.prototypellm.SMS_RESULT"),
            RECEIVER_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(smsResultReceiver)
    }
}
