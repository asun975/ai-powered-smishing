package com.example.smishingdetection

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class SmishingClassifier(private val apiUrl: String) {

    private fun getRiskScore(label: String, confidence: Float): Pair<Double, String> {
        val riskScore = if (label == "SPAM") confidence else (1 - confidence)
        val riskCategory = when {
            riskScore > 0.75 -> "HIGH"
            riskScore >= 0.30 -> "MEDIUM"
            else -> "LOW"
        }
        val riskPercent = (riskScore * 100).toDouble()
        return Pair(riskPercent, riskCategory)
    }
    suspend fun classify(text: String): Pair<String, Float> = withContext(Dispatchers.IO) {
        Log.d("SmishingClassifier", "Classifying: $text")

        try {
            Log.d("SmishingClassifier", "Sending to API: $apiUrl")

            val connection = URL(apiUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.doOutput = true

            // Flask API expects: {"text": "message to classify"}
            val jsonInputString = JSONObject().apply {
                put("text", text)
            }.toString()

            Log.d("SmishingClassifier", "Request: $jsonInputString")

            connection.outputStream.use { os ->
                val input = jsonInputString.toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }

            val responseCode = connection.responseCode
            Log.d("SmishingClassifier", "Response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d("SmishingClassifier", "API Response: $response")

                // Flask response format: {"label": "SPAM", "confidence": 0.95}
                val jsonResponse = JSONObject(response)

                if (jsonResponse.has("label") && jsonResponse.has("confidence")) {
                    val label = jsonResponse.getString("label")
                    val confidence = jsonResponse.getDouble("confidence").toFloat()

                    Log.d("SmishingClassifier", "Result: $label ($confidence)")

                    return@withContext Pair(label, confidence)
                } else if (jsonResponse.has("error")) {
                    val error = jsonResponse.getString("error")
                    Log.e("SmishingClassifier", "API Error: $error")
                }
            } else {
                val errorBody = try {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error body"
                } catch (e: Exception) {
                    "Could not read error: ${e.message}"
                }
                Log.e("SmishingClassifier", "HTTP $responseCode: $errorBody")
            }

        } catch (e: Exception) {
            Log.e("SmishingClassifier", "Exception: ${e.javaClass.simpleName} - ${e.message}")
            e.printStackTrace()
        }

        return@withContext Pair("ERROR", 0f)
    }
}