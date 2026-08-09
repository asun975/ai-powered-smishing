package com.example.smishingdetection

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks to the DistilBERT-based classifier API. Given a (PII-stripped)
 * message, asks the API whether it's SPAM or SAFE, along with a confidence
 * score for that call.
 */
class SmishingClassifier(private val apiUrl: String) {

    /**
     * Sends the cleaned message text to the classifier API and returns a
     * (label, confidence) pair. Runs on a background thread (Dispatchers.IO)
     * since it's a real network call. If anything goes wrong — a non-200
     * response, an API-reported error, a missing field in the response, or a
     * thrown exception (e.g. no network, timeout) — this returns
     * Pair("ERROR", 0f) instead of crashing. That special "ERROR" label is
     * what MainActivity checks for to decide whether to queue the message
     * for an offline retry instead of treating it as a real classification.
     */
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