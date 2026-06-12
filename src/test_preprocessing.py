import unittest

from preprocessing import keep_english, removeMedia, removePII, removePIIWithContext

class TestStringMethods(unittest.TestCase):

    sms_media = """Hey!
    [image]
    https://example.com/photo.jpg
    See you soon.
    """
    sms_media_removed = """Hey!
    https://example.com/photo.jpg
    See you soon"""

    sms_PII = "My phone number is 123-456-8901, you can contact me at johndoe@proton.com 123 456 789,123-456-789, 123-45-6789 1234567812345678,1234 5678 1234 5678, 1234 5678-1234 5678"
    sms_PII_removed_context = "My phone number is [PHONE], you can contact me at [EMAIL] [ID],[ID], [ID] [CARD],[CARD],[CARD], [CARD]"
    sms_PII_removed = "My phone number is, you can contact me at ,,  ,, "
    sms_keep_english = "My phone number is, you can contact me at"

    def test_keep_english(self):
        self.assertEqual(keep_english(self.sms_PII_removed), self.sms_keep_english)

    def test_removePII(self):
        self.assertTrue(removePII(self.sms_PII), self.sms_PII_removed)
        self.assertFalse(removePII(self.sms_PII), self.sms_PII)

    def test_removePIIWithContext(self):
        self.assertEqual(removePIIWithContext(self.sms_PII), self.sms_PII_removed_context)

    def test_removeMedia(self):
        self.assertEqual(removeMedia(self.sms_media), self.sms_media_removed)


if __name__ == '__main__':
    unittest.main()