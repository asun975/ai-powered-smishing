package com.example.smishingdetection.data.sanitizer

import kotlin.text.lowercase

class ClassifierApiSanitizer : ApiInputSanitizer {

    /*
    * Removes PII
    * text preprocessing remove emoji unicode characters, punctuation and extra whitespace.
     */
    override fun sanitize(input: String): String {
        var sanitized = input.trim()

        require(input.trim().isNotEmpty()) {
            throw InvalidInputException("Input cannot be empty")
        }

        val patterns = listOf(
            SanitizerRegex.phone, SanitizerRegex.email, SanitizerRegex.creditCard, SanitizerRegex.sin,
            SanitizerRegex.ssn, SanitizerRegex.ipAddress, SanitizerRegex.bankAccount,
            SanitizerRegex.mfaCode, SanitizerRegex.url
        )
        sanitized = patterns.fold(sanitized) { result, regex ->
            regex.replace(result, "")
        }

        return sanitized.lowercase()
            .replace(SanitizerRegex.emoji, "")
            .replace(SanitizerRegex.punctuation, " ") // prevent words from joining
            .replace(SanitizerRegex.whitespaces, " ") // remove extra whitespaces
            .trim()
    }
}