package com.example.smishingdetection
import com.example.smishingdetection.Preprocessing.Companion.preprocessLlmText
import com.example.smishingdetection.Preprocessing.Companion.preprocessClassifierText
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class PreprocessingUnitTest(
    private val input: String,
    private val expected: String
) {

    @Test
    fun testPreprocessingMapping() {
        assertEquals(expected, preprocessClassifierText(input))
    }

    companion object {

        @JvmStatic
        @Parameterized.Parameters(name = "{0} -> {1}")
        fun ClassifierDataMapping(): Collection<Array<Any>> {
            return listOf(
                arrayOf("Your one-time verification code for Shopify is 847291. Do not share this code with anyone.", "your one time verification code for shopify is do not share this code with anyone"),
                arrayOf("Your package from Canada Post could not be delivered. Update your delivery address now: http://canadapost-redelivery.com/track", "your package from canada post could not be delivered update your delivery address now"),
                arrayOf("Happy Birthday! Hope you have an amazing day! ❤\uFE0F❤\uFE0F❤\uFE0F", "happy birthday hope you have an amazing day"),
                arrayOf("\tYour Instacart order is ready for pickup at Costco. Show this code at the desk: #8847", "your instacart order is ready for pickup at costco show this code at the desk 8847"),
                arrayOf("영어가 아닌 문자 메시지\u0456\u0121,\u043e ǥthis", "this")
            )
        }
    }
}