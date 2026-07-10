package com.example.smishingdetection.data.network.url

import android.util.Log
import com.example.smishingdetection.data.network.url.model.UrlAnalyzerRequest
import com.example.smishingdetection.data.network.url.model.UrlAnalyzerResponse
import com.example.smishingdetection.data.network.url.model.UrlApiResult
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
    suspend fun getVerdict(message: String): UrlApiResult
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
    override suspend fun getVerdict(message: String): UrlApiResult {
        try {
            // Only process the first url
            val url = extractUrl(message).first()

            // No URL found in message
            if(url.isNullOrEmpty()) {
                return UrlApiResult.ValidationError("No URL found to scan.")

            } else {
                val request = UrlAnalyzerRequest(url)
                // Return URL verdict from API on success
                return UrlApiResult.Success(urlApiService.getVerdict(request))
            }
        } catch(e: SocketTimeoutException) {
            Log.e("UrlAnalyzer", "Exception: ${e.localizedMessage}")
            return UrlApiResult.ApiError(408, "Scan timed out.")

        } catch(e: ConnectException) {
            Log.e("UrlAnalyzer", "Exception: ${e.printStackTrace()}.")
            return UrlApiResult.ApiError(503, "Server is not available.")

        } catch(e: Exception) {
            Log.e("UrlAnalyzer", "Exception: ${e.javaClass.simpleName} - ${e.message}\nCause: ${e.cause}\nTrace: ${e.printStackTrace()}")
            return UrlApiResult.ExceptionError(e.toString(), e.message.toString())

        } catch(e: HttpException) {
            val statusCode = e.code()
            return UrlApiResult.ApiError(statusCode, e.response()?.message() ?: e.message())
        }
    }
}