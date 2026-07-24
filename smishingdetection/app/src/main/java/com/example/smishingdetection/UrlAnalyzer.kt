package com.example.smishingdetection

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.sql.Connection

class UrlAnalyzer(private val baseUrl: String) {
    private val analyzeUrl = baseUrl.plus("/analyze")
    suspend fun analyzeUrl(url: String?): String =
        withContext(Dispatchers.IO) {
            // TODO: reset url analyzer text view for new messages
            if(url.isNullOrBlank()) {
                return@withContext "No Urls found"
            } else {
                Log.d("UrlAnalyzer", "Submitting scan for: $url")
                try {
                    val urlConnection = URL(analyzeUrl).openConnection() as HttpURLConnection
                    urlConnection.requestMethod = "POST"
                    urlConnection.setRequestProperty("Content-Type", "application/json")
                    urlConnection.connectTimeout = 30000
                    urlConnection.readTimeout = 30000
                    urlConnection.doOutput = true

                    val jsonInputString = JSONObject().apply {
                        put("url", url)
                    }.toString()

                    Log.d("UrlAnalyzer",  "Request: $jsonInputString")

                    urlConnection.outputStream.use { os ->
                        val input = jsonInputString.toByteArray(Charsets.UTF_8)
                        os.write(input, 0, input.size)
                    }

                    val responseCode = urlConnection.responseCode
                    val response = if (responseCode == HttpURLConnection.HTTP_OK) {
                        urlConnection.inputStream.bufferedReader().use { it.readText() }
                    } else {
                        urlConnection.errorStream.bufferedReader().use { it.readText() }
                    }
                    Log.d("UrlAnalyzer", "Response code: $responseCode")
                    urlConnection.disconnect() // free up resources
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val jsonResponse = JSONObject(response)
                        if (jsonResponse.has("uuid")) {
                            val uuid = jsonResponse.getString("uuid")
                            val score = jsonResponse.getString("score")
                            val malicious = jsonResponse.get("malicious")
                            val urlSubmitted = jsonResponse.get("url")
                            val scanResult =
                                "Scan result from urlscan.io returned Malicious:${malicious} for $urlSubmitted with an overall score of $score"
                            Log.d("UrlAnalyzer", "urlscan.io returns scan_id: $uuid")
                            return@withContext "$scanResult"
                        }
                    } else {
                        // TODO: Status code error messages from API
                        Log.d("UrlAnalyzer", "API Response: $response")
                        when (responseCode) {
                            HttpURLConnection.HTTP_BAD_REQUEST -> {
                                val errorMessage = JSONObject(response).get("description")
                                return@withContext "Unable to scan URL. urlscan.io returned $errorMessage"
                            }

                            HttpURLConnection.HTTP_INTERNAL_ERROR -> {
                                return@withContext "$response"
                            }
                        }

                        return@withContext "Unable to scan URL. API returned $response"
                    }
                }
                catch (e: SocketTimeoutException) {
                    Log.e("UrlAnalyzer", "Exception: ${e.localizedMessage}")
                    return@withContext "Scan timed out. URL verdict could not be retrieved."
                }
                catch (e: ConnectException) {
                    Log.e("UrlAnalyzer", "Exception: ${e.printStackTrace()}.")
                    return@withContext "URL sandbox is not currently available."

                }
                catch (e: Exception) {
                    Log.e("UrlAnalyzer", "Exception: ${e.javaClass.simpleName} - ${e.message}\nCause: ${e.cause}\nTrace: ${e.printStackTrace()}")
                }
                return@withContext "Unknown API Error."
            }
        }

}