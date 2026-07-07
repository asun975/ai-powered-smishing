package com.example.smishingdetection.data.network.url.model

import kotlinx.serialization.Serializable

/**
 * This data class defines a url verdict that includes
 * the url, malicious status returned by urlscan.io,
 * and overall score returned by urlscan.io
 */
@Serializable
data class UrlAnalyzerResponse(
    val uuid: String,
    val url: String,
    val malicious: Boolean,
    val score: Int
)
