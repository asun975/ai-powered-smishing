import unittest

from preprocessing import preprocessClassifierText, maskPII

class TestStringMethods(unittest.TestCase):

    sms_media = """Hey!
    [image]
    https://example.com/photo.jpg
    See you soon.
    """
    sms_media_cleaned = """hey see you soon"""

    sms_PII = "My phone number is 123-456-8901, you can contact me at johndoe@proton.com 123 456 789,123-456-789, 123-45-6789 1234567812345678,1234 5678 1234 5678, 1234 5678-1234 5678"
    sms_PII_removed_context = "My phone number is [PHONE], you can contact me at [EMAIL] [ID],[ID], [ID] [CARD],[CARD],[CARD], [CARD]"
    sms_cleaned = "my phone number is you can contact me at"

    def test_data_sanitization(self):
        self.assertEqual(preprocessClassifierText(self.sms_PII), self.sms_cleaned)

    def test_remove_URL(self):
        self.assertTrue(preprocessClassifierText(self.sms_media), self.sms_media_cleaned)

    def test_removePIIWithContext(self):
        self.assertEqual(maskPII(self.sms_PII), self.sms_PII_removed_context)


if __name__ == '__main__':
    unittest.main()