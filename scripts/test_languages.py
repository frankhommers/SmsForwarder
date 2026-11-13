#!/usr/bin/env python3
"""Fail-closed language regression tests; fixtures are not application text."""
import json
from pathlib import Path
import unittest
from check_languages import has_cjk, is_functional_unicode, source_gate

ROOT = Path(__file__).resolve().parents[1]


class LanguageTests(unittest.TestCase):
    def test_literals_and_escapes(self):
        self.assertTrue(has_cjk(chr(0x4e00)))
        self.assertTrue(has_cjk('\\u' + '4e00'))
        self.assertFalse(has_cjk('Network error / Netwerkfout'))

    def test_unknown_prose_never_gets_package_exemption(self):
        self.assertFalse(is_functional_unicode(chr(0x4e00), ['Landroidx/core/util/PatternsCompat;-><clinit>']))
        self.assertFalse(is_functional_unicode(chr(0x3000), ['Lcom/example/NewError;->message']))
        self.assertFalse(is_functional_unicode(chr(0x4e00), []))

    def test_functional_allowlist_is_exact_and_bounded(self):
        allowed = json.loads((ROOT / 'scripts/functional_unicode_allowlist.json').read_text())
        self.assertEqual(32, len(allowed))
        self.assertTrue(all(len(h) == 64 and e['references'] and e['reason'] for h, e in allowed.items()))

    def test_current_source_and_translation_parity(self):
        self.assertEqual([], source_gate()['failures'])


if __name__ == '__main__':
    unittest.main(verbosity=2)
