package com.example.smishingdetection

import kotlin.text.replace

class Preprocessing {

    companion object {
        // Regex patterns
        private val phone = Regex(
            """(?:\+\d{1,3}[\s.\-]?(?:\(?\d{1,4}\)?[\s.\-]?)?\d{1,4}[\s.\-]\d{2,4}[\s.\-]\d{2,4}|\(?\d{3}\)?[\s.\-]\d{3}[\s.\-]\d{4})""".trimIndent(),
            setOf(RegexOption.COMMENTS)
        )

        private val email = Regex(
            """\b[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}\b"""
        )

        private val creditCard = Regex(
            """\b\d{4}[\s\-]?\d{4}[\s\-]?\d{4}[\s\-]?\d{4}\b"""
        )

        private val sin = Regex(
            """\b\d{3}[\s\-]\d{3}[\s\-]\d{3}\b"""
        )

        private val ssn = Regex(
            """\b\d{3}-\d{2}-\d{4}\b"""
        )

        private val emoji = Regex(
            """[\uD83C-\uDBFF\uDC00-\uDFFF]"""
        )
        private val bankAccount = Regex("""\s[0-9]{9,18}\s""")
        private val mfaCode = Regex("""[0-9]{6}""") // 6-digit MFA
        private val url = Regex("""(?i)\b((?:https?://|www\.)[^\s<>"']+)""")
        private val ipAddress = Regex("""[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}""")
        private val whitespaces = Regex("""\s+""")
        private val punctuation = Regex("\\p{Punct}")

        // Remove Personally Identifiable Information (PII)
        private fun removeSensitiveData(text: String): String {
            val patterns = listOf(
                phone, email, creditCard, sin, ssn, ipAddress, bankAccount, mfaCode, url
            )
            return patterns.fold(text) { result, regex ->
                regex.replace(result, "")
            }
        }

        // Removes PII
        // text preprocessing remove emoji unicode characters, punctuation and extra whitespace
        fun preprocessClassifierText(text: String): String {
            return removeSensitiveData(text).lowercase()
                .replace(Regex("""[^a-z0-9\s]"""), "")
                .replace(emoji, "")
                .replace(punctuation, " ") // prevent words from joining
                .replace(whitespaces, " ") // remove extra whitespaces
                .trim()
        }

        // Replace PII in SMS text with placeholder to keep context
        fun preprocessLlmText(text: String): String {
            return text
                .replace(email, "[EMAIL]")
                .replace(url, "[URL]")
                .replace(creditCard, "[CARD")
                .replace(sin, "[ID]")
                .replace(ssn, "[ID]")
                .replace(phone, "[PHONE]")
                .replace(ipAddress, "[IP]")
                .replace(bankAccount, "[ACCOUNT]")
                .replace(mfaCode, "[VERIFICATION CODE]")
                .trim()
        }
        fun extractFirstUrl(body: String): String? {
            val url = Regex("""(?i)\b((?:https?://|www\.)[^\s<>"']+)""").findAll(body)
                .map { match ->
                    match.value.trimEnd(
                        '.', ',', ';', ':', '!', '?', ')', ']', '}'
                    )
                }
                .toList().firstOrNull()
            return url
        }
    }
}