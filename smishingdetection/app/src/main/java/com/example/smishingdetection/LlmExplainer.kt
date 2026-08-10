package com.example.smishingdetection

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks to the Groq-hosted LLM explanation API. Given a (PII-masked) message,
 * its classification, and its risk score, asks the LLM for a short,
 * human-readable reason why the message was flagged.
 */
class LlmExplainer(private val apiUrl: String) {


    /**
     * Sends the message + classification + risk score to the LLM API and
     * returns its explanation text. Runs on a background thread (Dispatchers.IO)
     * since it makes a real network call. If the request fails for any reason
     * (network error, non-200 response, missing "explanation" field in the
     * response), returns a fallback string instead of throwing — so a broken
     * API call never crashes the app, it just shows a generic message.
     */
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