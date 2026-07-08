package com.example.smishingdetection.data.sanitizer

class UrlApiSanitizer : ApiInputSanitizer {
    override fun sanitize(input: String): String? {
        val urls = Regex("""(?i)\b((?:https?://|www\.)[^\s<>"']+)""").findAll(input)
            .map { match ->
                match.value.trimEnd(
                    '.', ',', ';', ':', '!', '?', ')', ']', '}'
                )
            }
            .toList()
        return urls.firstOrNull()
    }
}