package com.example.smishingdetection.data.sanitizer

import kotlin.jvm.Throws

interface ApiInputSanitizer {
    @Throws(InvalidInputException::class)
    fun sanitize(input: String): String?
}