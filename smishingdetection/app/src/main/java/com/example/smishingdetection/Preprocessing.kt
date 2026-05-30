package com.example.smishingdetection

import kotlin.text.replace
// todo: extra whitespace, more PII regex,
class Preprocessing {

    companion object {
        // Regex patterns
        private val phone = Regex(
            """(?:
                \+\d{1,3}[\s.\-]?(?:\(?\d{1,4}\)?[\s.\-]?)?\d{1,4}[\s.\-]\d{2,4}[\s.\-]\d{2,4}
                |
                \(?\d{3}\)?[\s.\-]\d{3}[\s.\-]\d{4}
            )""".trimIndent(),
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

        private val mediaTag = Regex(
            """\[(?:image|photo|picture|mms|video|audio|file|attachment|gif|sticker)\]
            |<(?:image|photo|mms)>
            |(?:image|photo|picture|video)\s+(?:attached|sent|received)
            """.trimIndent(),
            setOf(RegexOption.IGNORE_CASE, RegexOption.COMMENTS)
        )

        private val emoji = Regex(
            """[\uD83C-\uDBFF\uDC00-\uDFFF]"""
        )

        private val url = Regex(
            """http\S+"""
        )

        private val nonLetter = Regex(
            """[^a-zA-Z\s]"""
        )

        // Remove PII
        fun removePII(message: String): String {
            return message
                .replace(email, "")
                .replace(creditCard, "")
                .replace(sin, "")
                .replace(ssn, "")
                .replace(phone, "")
                .trim()
        }

        // Data cleaning for DistilBERT classifier
        fun cleanSms(message: String): String {
            var inputString = removePII(message)
            return inputString
                .replace(emoji, "")
                .replace(mediaTag, "")
                .trim()
        }

        // Mask Personal Identifiable Information (PII) for LLM
        fun maskPII(message: String): String {
            return message
                .replace(email, "[EMAIL]")
                .replace(url, "[URL]")
                .replace(creditCard, "[CARD")
                .replace(sin, "[ID]")
                .replace(ssn, "[ID]")
                .replace(phone, "[PHONE]")
                .trim()
        }
    }

}