import unittest

from src.preprocessing import strip_pii, is_media_only, is_trivial, should_skip, clean_for_distilbert, clean_for_llm

class TestStringMethods(unittest.TestCase):

    test_message = """
    Hey!
    [image]
    https://example.com/photo.jpg
    See you soon.
    """
    cleaned_text = "Hey!"

    def test_upper(self):
        self.assertEqual('foo'.upper(), 'FOO')

    def test_isupper(self):
        self.assertTrue('FOO'.isupper())
        self.assertFalse('Foo'.isupper())

    def test_split(self):
        s = 'hello world'
        self.assertEqual(s.split(), ['hello', 'world'])
        # check that s.split fails when the separator is not a string
        with self.assertRaises(TypeError):
            s.split(2)

if __name__ == '__main__':
    unittest.main()