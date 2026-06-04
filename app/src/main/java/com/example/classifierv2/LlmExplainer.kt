package com.example.classifierv2

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class LlmExplainer(private val apiUrl: String) {

    suspend fun explain(text: String, classification: String, riskScore: Float): String =
        withContext(Dispatchers.IO) {
            Log.d("LlmExplainer", "Getting explanation for: $text")

            try {
                val connection = URL(apiUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 30000
                connection.readTimeout = 30000
                connection.doOutput = true

                val jsonInputString = JSONObject().apply {
                    put("text", text)
                    put("classification", classification)
                    put("risk_score", riskScore.toDouble())
                }.toString()

                Log.d("LlmExplainer", "Request: $jsonInputString")

                connection.outputStream.use { os ->
                    val input = jsonInputString.toByteArray(Charsets.UTF_8)
                    os.write(input, 0, input.size)
                }

                val responseCode = connection.responseCode
                Log.d("LlmExplainer", "Response code: $responseCode")

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    Log.d("LlmExplainer", "API Response: $response")

                    val jsonResponse = JSONObject(response)
                    if (jsonResponse.has("explanation")) {
                        return@withContext jsonResponse.getString("explanation")
                    }
                }

            } catch (e: Exception) {
                Log.e("LlmExplainer", "Exception: ${e.javaClass.simpleName} - ${e.message}")
            }

            return@withContext "Could not generate explanation."
        }
}