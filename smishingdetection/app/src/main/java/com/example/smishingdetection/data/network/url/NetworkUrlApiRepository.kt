package com.example.smishingdetection.data.network.url

import com.example.smishingdetection.data.network.url.model.UrlAnalyzerRequest
import com.example.smishingdetection.data.network.url.model.UrlAnalyzerResponse
import com.example.smishingdetection.data.sanitizer.InvalidInputException

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

    private fun getFirstUrl(body: String): String {
        val urls = Regex("""(?i)\b((?:https?://|www\.)[^\s<>"']+)""").findAll(body)
            .map { match ->
                match.value.trimEnd(
                    '.', ',', ';', ':', '!', '?', ')', ']', '}'
                )
            }
            .toList()
        if (urls.firstOrNull() != null) {
            return urls.first()
        }
        throw InvalidInputException("No URL to process.")
    }
    override suspend fun getVerdict(message: String): UrlAnalyzerResponse {
            // Only process the first url
            val url = getFirstUrl(message)
            val request = UrlAnalyzerRequest(url)
            // Return URL verdict from API on success
            return urlApiService.getVerdict(request)
    }
}