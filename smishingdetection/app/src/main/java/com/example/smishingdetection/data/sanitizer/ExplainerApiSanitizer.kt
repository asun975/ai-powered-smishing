package com.example.smishingdetection.data.sanitizer

class ExplainerApiSanitizer : ApiInputSanitizer {
    override fun sanitize(input: String): String {

        require(input.trim().isNotEmpty()) {
            throw InvalidInputException("Input cannot be empty")
        }

        return input
            .replace(SanitizerRegex.email, "[EMAIL]")
            .replace(SanitizerRegex.url, "[URL]")
            .replace(SanitizerRegex.creditCard, "[CARD")
            .replace(SanitizerRegex.sin, "[ID]")
            .replace(SanitizerRegex.ssn, "[ID]")
            .replace(SanitizerRegex.phone, "[PHONE]")
            .replace(SanitizerRegex.ipAddress, "[IP]")
            .replace(SanitizerRegex.bankAccount, "[ACCOUNT]")
            .replace(SanitizerRegex.mfaCode, "[VERIFICATION CODE]")
            .trim()
    }
}