package com.example.localllm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class MainActivity : ComponentActivity() {

    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SMSDetectorUI()
        }
    }

    @Composable
    fun SMSDetectorUI() {

        var inputText by remember {
            mutableStateOf("")
        }

        var resultText by remember {
            mutableStateOf("Waiting for analysis...")
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {

            Text(
                text = "SMS Phishing Detector",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = inputText,
                onValueChange = {
                    inputText = it
                },
                label = {
                    Text("Enter SMS message")
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {

                    lifecycleScope.launch {

                        resultText = "Analyzing..."

                        val response = analyzeSMS(inputText)

                        resultText = response
                    }

                }
            ) {
                Text("Analyze Message")
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = resultText,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }

    private suspend fun analyzeSMS(message: String): String {

        return withContext(Dispatchers.IO) {

            try {

                val prompt = """
                    You are a phishing detection AI.

                    Analyze this SMS message.

                    Return ONLY this format:

                    Classification: SAFE or MALICIOUS
                    Confidence: number between 0 and 100
                    Reason: short explanation

                    Message:
                    $message
                """.trimIndent()

                val json = JSONObject().apply {

                    put("model", "gemma2:2b")

                    put(
                        "messages",
                        JSONArray().put(
                            JSONObject().apply {
                                put("role", "user")
                                put("content", prompt)
                            }
                        )
                    )

                    put("stream", false)
                }

                val body = RequestBody.create(
                    "application/json".toMediaType(),
                    json.toString()
                )

                val request = Request.Builder()
                    .url("http://10.0.2.2:11434/api/chat")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()

                val responseBody = response.body?.string()

                if (responseBody != null) {

                    val jsonResponse = JSONObject(responseBody)

                    val messageObject =
                        jsonResponse.getJSONObject("message")

                    return@withContext messageObject.getString("content")
                }

                "No response from AI"

            } catch (e: Exception) {

                e.printStackTrace()

                "Error: ${e.message}"
            }
        }
    }
}