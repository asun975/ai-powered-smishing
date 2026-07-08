package com.example.smishingdetection.data.network.url

import com.example.smishingdetection.data.network.url.model.UrlAnalyzerRequest
import com.example.smishingdetection.data.network.url.model.UrlAnalyzerResponse

// TODO add error data models for API responses
/**
 * Repository that fetch url verdict from scanApi.
 */
interface UrlApiRepository {
    suspend fun getVerdict(message: String): UrlAnalyzerResponse?
}

/**
 * Network Implementation of Repository that gets url verdict from scanApi.
 */
class NetworkUrlApiRepository(
    private val urlApiService: UrlApiService
) : UrlApiRepository {

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
    override suspend fun getVerdict(message: String): UrlAnalyzerResponse? {
        val urls = extractUrl(message)
        val firstUrl = urls.getOrNull(0)
        if (firstUrl != null) {
            val request = UrlAnalyzerRequest(firstUrl)
            return urlApiService.getVerdict(request)
        }
        return null
    }
}