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
        private val mfaCode = Regex("""[0-9]{6}""")
        private val url = Regex("""http\S+""")
        private val ipAddress = Regex("""[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}""")
        private val whitespaces = Regex("""\s+""")
        private val punctuation = Regex("""""\p{Punct}""")//todo
        // Remove PII
        // url is not removed because model is trained on url strings
        fun blockPII(message: String): String {
            return message
                .replace(email, "")
                .replace(creditCard, "")
                .replace(sin, "")
                .replace(ssn, "")
                .replace(phone, "")
                .replace(ipAddress, "")
                .replace(bankAccount, "")
                .replace(mfaCode, "")
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
                .replace(ipAddress, "[IP]")
                .replace(bankAccount, "[ACCOUNT]")
                .replace(mfaCode, "[6-DIGIT MFA]")
                .trim()
        }

        // Data cleaning
        //todo preserve urls during text preprocessing
        fun cleanSms(message: String): String {
            return blockPII(message)
                .replace(emoji, "")
                .replace(punctuation, "")
                .replace(whitespaces, " ")
                .trim()
        }
    }

}