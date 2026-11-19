#!/usr/bin/env python3
"""Targeted regressions from actual copy defects; not a substitute for language review."""
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]

def strings(locale):
    return {e.attrib['name']: ''.join(e.itertext()) for p in (ROOT / 'app/src/main/res' / locale).glob('*.xml') for e in ET.parse(p).getroot() if e.tag == 'string'}

class CopyTests(unittest.TestCase):
    def test_reported_and_reviewed_bad_phrases(self):
        bad = re.compile(r'\bsned\b|\bIn Ended\b|Auto close Ntf|Not User Present|\bNo Req\.|\bMin Interval\b|\bSpec\. (?:St|Res|Dev)\.|\bTranspond All\b|\bPkgName\b|\bleaved\b|have4tokens|supportsSM4|zelfstarting|fuzzy overeenkomen', re.I)
        for locale in ('values', 'values-en', 'values-nl'):
            for key, text in strings(locale).items():
                self.assertIsNone(bad.search(text), (locale, key, text))
    def test_required_semantics(self):
        for locale in ('values', 'values-en'):
            text = strings(locale)
            self.assertEqual('Finished Incoming Call', text['incoming_call_ended'])
            self.assertEqual('Targets', text['menu_senders'])
            self.assertIn('Matching Notifications', text['cancel_app_notify'])
            self.assertIn('Phone Is Locked', text['not_user_present'])
            self.assertIn('before forwarding', text['forward_app_notify_tips'])
            self.assertNotIn('successful', text['test_sender_sms'])
            self.assertEqual('Apply', text['m3_apply'])
            self.assertEqual('Send', text['send'])
        self.assertEqual('Bestemmingen', strings('values-nl')['menu_senders'])
        self.assertEqual('Toepassen', strings('values-nl')['m3_apply'])
    def test_examples_match_parser(self):
        for locale in ('values', 'values-en', 'values-nl'):
            text = strings(locale)
            for key in ('mu_rule_sms_tips', 'mu_rule_call_tips', 'mu_rule_app_tips'):
                lines = [x for x in text[key].split(r'\n') if x.lstrip().startswith(('AND ', 'OR '))]
                self.assertEqual(3, len(lines))
                for line in lines:
                    self.assertRegex(line, r'^\s*(AND|OR) IS (PHONE_NUM|MSG_CONTENT|CALL_TYPE|PACKAGE_NAME|INFORM_CONTENT) (EQUALS|CONTAIN) .+$')
    def test_rejected_inactive_editing_copy_removed(self):
        for locale in ('values', 'values-en', 'values-nl'):
            self.assertNotIn('settings_inactive', strings(locale))
        source = (ROOT/'app/src/main/kotlin/cn/ppps/forwarder/utils/DependentControls.kt').read_text()
        for master in ('sb_enable_phone','sb_enable_app_notify','sb_enable_location','sb_enable_bluetooth','sb_enable_sms_command','sb_enable_cactus','sb_sms_template'):
            self.assertIn(master, source)
        self.assertIn('view.isEnabled = enabled', source)
    def test_recipient_validation_and_navigation(self):
        source = (ROOT/'app/src/main/kotlin/cn/ppps/forwarder/utils/ItemRows.kt').read_text()
        self.assertIn('TYPE_CLASS_PHONE', source)
        self.assertIn('copy_add_recipient', source)
        self.assertIn('copy_phone_required', source)
        self.assertNotIn('add(""); validate()', source)
        self.assertNotIn('key.error =', source)
        main = (ROOT/'app/src/main/kotlin/cn/ppps/forwarder/activity/MainActivity.kt').read_text()
        self.assertNotIn('Intent.CATEGORY_HOME', main)
        base = (ROOT/'app/src/main/kotlin/cn/ppps/forwarder/core/BaseActivity.kt').read_text()
        self.assertIn('changePage(clazz)', base)
        self.assertIn('consumeNavigationBack()', base)
        self.assertIn('savedInstanceState != null', base)

if __name__ == '__main__':
    unittest.main(verbosity=2)
