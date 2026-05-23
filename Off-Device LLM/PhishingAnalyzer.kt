package com.example.prototypellm

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class PhishingAnalyzer(private val apiKey: String) {

    private val client = OkHttpClient()

    fun analyzeMessage(message: String): String {
        try {
            val systemPrompt = """
                You are a phishing detection system.
                Analyze this message.
                Return ONLY this format:
                
                Classification: SAFE or MALICIOUS
                Confidence: number between 0 and 100
                Reason: short explanation
            """.trimIndent()

            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", message)
                            })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", systemPrompt)
                        })
                    })
                })
            }.toString()

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(
                    jsonBody.toRequestBody(
                        "application/json".toMediaType()
                    )
                )
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body.string()

            if (!response.isSuccessful) {
                android.util.Log.e("PhishingAnalyzer", "API Error: $responseBody")
                val errorMsg = try {
                    JSONObject(responseBody).getJSONObject("error").getString("message")
                } catch (_: Exception) {
                    "HTTP Error ${response.code}"
                }
                return "Error: $errorMsg"
            }

            val jsonResponse = JSONObject(responseBody)
            val candidates = jsonResponse.getJSONArray("candidates")
            if (candidates.length() > 0) {
                return candidates.getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
            }
            return "No content in response"

        } catch (e: Exception) {
            return "Error: ${e.message}"
        }
    }
}
