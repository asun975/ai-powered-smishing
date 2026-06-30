package com.example.smishingdetection

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExtractUrlUnitTest {
    private fun extractUrls(body: String): List<String>{
        return Regex("""(?i)\b((?:https?://|www\.)[^\s<>"']+)""").findAll(body)
            .map { match ->
                match.value.trimEnd(
                    '.', ',', ';', ':', '!', '?', ')', ']', '}'
                )
            }
            .toList()
    }
    @Test
    fun extractUrl_http() {
        val smsText = "You are selected for a government COVID relief payment of 2,500. Claim before deadline: http://gov-relief-canada.info/apply"
        val urlTrue = "http://gov-relief-canada.info/apply"
        // Return list of urls in SMS text
        val urls = extractUrls(smsText)
        assertEquals(urlTrue, urls.firstOrNull())
    }

    @Test
    fun extractUrl_https() {
        val smsText = "You are selected for a government COVID relief payment of 2,500. Claim before deadline: https://gov-relief-canada.info/apply"
        val urlTrue = "https://gov-relief-canada.info/apply"
        // Return list of urls in SMS text
        val urls = extractUrls(smsText)
        assertEquals(urlTrue, urls.firstOrNull())
    }

    @Test
    fun extractUrl_www() {
        val smsText = "You are selected for a government COVID relief payment of 2,500. Claim before deadline: www.gov-relief-canada.info/apply"
        val urlTrue = "www.gov-relief-canada.info/apply"
        // Return list of urls in SMS text
        val urls = extractUrls(smsText)
        assertEquals(urlTrue, urls.firstOrNull())
    }

    @Test
    fun extractUrl_noScheme() {
        val smsText = "You are selected for a government COVID relief payment of 2,500. Claim before deadline: gov-relief-canada.info/apply"
        val urlTrue = "gov-relief-canada.info/apply"
        // Return list of urls in SMS text
        val urls = extractUrls(smsText)
        assertEquals(urlTrue, urls.firstOrNull())
    }

    @Test
    fun extractUrl_multiple() {
        val smsText = "http://netflix-billing-update.com/reactivate, You are selected for a government COVID relief payment of 2,500. Claim before deadline: http://gov-relief-canada.info/apply"
        val urlTrue = listOf("http://netflix-billing-update.com/reactivate", "http://gov-relief-canada.info/apply")
        // Return list of urls in SMS text
        val urls = extractUrls(smsText)
        assertEquals(urlTrue, urls)
    }

    @Test
    fun extractUrl_none() {
        val smsText = "You are selected for a government COVID relief payment of 2,500. Claim before deadline"
        // Return list of urls in SMS text
        val urls = extractUrls(smsText)
        assertEquals(null, urls.firstOrNull())
    }
}