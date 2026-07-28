package com.example.smishingdetection

import com.example.smishingdetection.Preprocessing.Companion.preprocessLlmText
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class PreprocessLlmTextUnitTest(
    private val input: String,
    private val expected: String
) {
    @Test
    fun testLlmMasking() {
        assertEquals(expected, preprocessLlmText(input))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0} -> {1}")
        fun LlmDataMapping(): Collection<Array<Any>> {
            return listOf(
                arrayOf(
                    "Update your delivery address now: http://canadapost-redelivery.com/track",
                    "Update your delivery address now: [URL]"
                ),
                arrayOf(
                    "Contact support at help@shopify.com for assistance.",
                    "Contact support at [EMAIL] for assistance."
                ),
                arrayOf(
                    "Call us at 416-555-0182 to verify your account.",
                    "Call us at [PHONE] to verify your account."
                ),
                arrayOf(
                    "Your card number 4532 1488 0343 6467 was charged \$50.",
                    "Your card number [CARD] was charged \$50."
                ),
                arrayOf(
                    "Please provide your SIN 123 456 789 for verification.",
                    "Please provide your SIN [ID] for verification."
                ),
                arrayOf(
                    "Your SSN is 123-45-6789 for tax purposes.",
                    "Your SSN is [ID] for tax purposes."
                ),
                arrayOf(
                    "Suspicious login detected from IP 192.168.1.1",
                    "Suspicious login detected from IP [IP]"
                ),
                arrayOf(
                    "Please confirm your account number is 123456789012 to proceed.",
                    "Please confirm your account number is[ACCOUNT]to proceed."
                ),
                arrayOf(
                    "Your verification code is 482913, do not share it.",
                    "Your verification code is [VERIFICATION CODE], do not share it."
                ),
            )
        }
    }
}