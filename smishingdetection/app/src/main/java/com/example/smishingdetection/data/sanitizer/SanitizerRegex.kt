package com.example.smishingdetection.data.sanitizer

object SanitizerRegex {
    val phone = Regex(
        """(?:\+\d{1,3}[\s.\-]?(?:\(?\d{1,4}\)?[\s.\-]?)?\d{1,4}[\s.\-]\d{2,4}[\s.\-]\d{2,4}|\(?\d{3}\)?[\s.\-]\d{3}[\s.\-]\d{4})""".trimIndent(),
        setOf(RegexOption.COMMENTS)
    )

    val email = Regex(
        """\b[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}\b"""
    )

    val creditCard = Regex(
        """\b\d{4}[\s\-]?\d{4}[\s\-]?\d{4}[\s\-]?\d{4}\b"""
    )

    val sin = Regex(
        """\b\d{3}[\s\-]\d{3}[\s\-]\d{3}\b"""
    )

    val ssn = Regex(
        """\b\d{3}-\d{2}-\d{4}\b"""
    )

    val emoji = Regex(
        """[\uD83C-\uDBFF\uDC00-\uDFFF]"""
    )
    val bankAccount = Regex("""\s[0-9]{9,18}\s""")
    val mfaCode = Regex("""[0-9]{6}""") // 6-digit MFA
    val url = Regex("""(?i)\b((?:https?://|www\.)[^\s<>"']+)""")
    val ipAddress = Regex("""[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}""")
    val whitespaces = Regex("""\s+""")
    val punctuation = Regex("\\p{Punct}")
}