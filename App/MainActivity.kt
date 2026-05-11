package com.example.smslocalai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var aiManager: AIManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        aiManager = AIManager.getInstance(this)

        requestPermissions()

        setContent {
            SmsDetectorUI()
        }
    }
    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.POST_NOTIFICATIONS
        )

        val neededPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (neededPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                neededPermissions.toTypedArray(),
                100
            )
        }
    }
    @Composable
    fun SmsDetectorUI() {

        var inputText by remember { mutableStateOf("") }
        var resultText by remember { mutableStateOf("Waiting...") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {

            Spacer(modifier = Modifier.height(30.dp))

            Text(
                text = "SMS Phishing Detector",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("Enter SMS") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {

                    lifecycleScope.launch {

                        resultText = "Analyzing..."

                        resultText = aiManager.analyzeSMS(inputText)
                    }

                }
            ) {
                Text("Scan SMS")
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = resultText,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
