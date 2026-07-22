package com.example.smishingdetection.data.network.url

import android.util.Log
import com.example.smishingdetection.data.network.url.model.UrlAnalyzerRequest
import com.example.smishingdetection.data.network.url.model.UrlAnalyzerResponse
import com.example.smishingdetection.data.sanitizer.InvalidInputException
import okhttp3.HttpUrl
import org.json.JSONObject
import retrofit2.HttpException
import retrofit2.Response
import java.net.ConnectException
import java.net.SocketTimeoutException

/**
 * Repository that fetch url verdict from urlApiService.
 */
interface UrlApiRepository {
    suspend fun getVerdict(message: String): UrlAnalyzerResponse
}

/**
 * Network Implementation of Repository that gets url verdict from scanApi.
 */
class NetworkUrlApiRepository(
    private val urlApiService: UrlApiService
): UrlApiRepository {

    private fun extractUrl(body: String): List<String?> {
        val urls = Regex("""(?i)\b((?:https?://|www\.)[^\s<>"']+)""").findAll(body)
            .map { match ->
                match.value.trimEnd(
                    '.', ',', ';', ':', '!', '?', ')', ']', '}'
                )
            }
            .toList()
        return urls
    }
    override suspend fun getVerdict(message: String): UrlAnalyzerResponse {
            // Only process the first url
            val url = extractUrl(message).first()

            // No URL found in message
            if(url.isNullOrEmpty()) {
                throw InvalidInputException("No URLs found.")

            } else {
                val request = UrlAnalyzerRequest(url)
                // Return URL verdict from API on success
                return urlApiService.getVerdict(request)
            }

    }
}