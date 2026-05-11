package com.example.smslocalai

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class AIManager private constructor(context: Context) {

    private val applicationContext = context.applicationContext
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()

    // IMPORTANT: Replace with your actual Groq API key from console.groq.com
    // Groq is free and much easier to set up than Google Cloud.
    private val apiKey = "YOUR_GROQ_API_KEY"
    private val apiUrl = "https://api.groq.com/openai/v1/chat/completions"

    companion object {
        @Volatile
        private var instance: AIManager? = null

        fun getInstance(context: Context): AIManager {
            return instance ?: synchronized(this) {
                instance ?: AIManager(context.applicationContext).also { instance = it }
            }
        }
    }

    suspend fun analyzeSMS(message: String): String = withContext(Dispatchers.IO) {
        if (apiKey == "YOUR_GROQ_API_KEY" || apiKey.isEmpty()) {
            return@withContext "Error: Please set your Groq API Key in AIManager.kt"
        }

        return@withContext try {
            val promptText = """
                You are a phishing detection system.
                Analyze this SMS message.
                Return ONLY this format:
                Classification: SAFE or MALICIOUS
                Confidence: number between 0 and 100
                Reason: short explanation

                Message:
                $message
            """.trimIndent()

            val jsonBody = JsonObject().apply {
                addProperty("model", "llama3-8b-8192")
                add("messages", gson.toJsonTree(listOf(
                    mapOf("role" to "user", "content" to promptText)
                )))
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
                val choices = jsonResponse.getAsJsonArray("choices")
                if (choices != null && choices.size() > 0) {
                    choices[0].asJsonObject
                        .getAsJsonObject("message")
                        .get("content").asString
                } else {
                    "Error: AI returned an empty result."
                }
            } else {
                Log.e("AIManager", "Groq Error: $responseBody")
                "Error: ${response.code} - ${response.message}"
            }
            
        } catch (e: Exception) {
            Log.e("AIManager", "API Error", e)
            "Error: ${e.localizedMessage ?: "Unknown connection error"}. Please check your internet connection."
        }
    }
}
