#!/usr/bin/env python3
"""Display-only UI regression checks; protocol keys and provider IDs are immutable."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
KT = ROOT / 'app/src/main/kotlin/cn/ppps/forwarder'

class UiTests(unittest.TestCase):
    def test_target_terminology(self):
        for locale in ('values', 'values-en', 'values-nl'):
            strings = {e.attrib['name']: ''.join(e.itertext()) for p in (RES / locale).glob('*.xml') for e in ET.parse(p).getroot() if e.tag == 'string'}
            self.assertEqual(strings['menu_senders'], 'Bestemmingen' if locale == 'values-nl' else 'Targets')
            for key in ('add_sender', 'edit_sender', 'clone_sender', 'select_sender', 'delete_sender_tips', 'sender_disabled', 'task_sender_tips'):
                self.assertNotRegex(strings[key], r'(?i)\b(sender|senders|afzender|kanaal|kanalen)\b')
            self.assertIn('afzender' if locale == 'values-nl' else 'Sender', strings['sender_smime_keystore'])

    def test_acronym_display(self):
        bad = re.compile(r'\b(?:Sms|Http|Https|Json|Url|Api|Smtp|Mqtt|Sim|Html|Tcp|Udp|Ssl|Tls)\b|HttpServer|ApiToken')
        for locale in ('values', 'values-en', 'values-nl'):
            for p in (RES / locale).glob('*.xml'):
                for e in ET.parse(p).getroot():
                    if e.tag == 'string' and not ('regex' in e.attrib['name'] or e.attrib['name'].startswith(('tag_', 'url_'))):
                        self.assertIsNone(bad.search(''.join(e.itertext())), f'{locale}/{e.attrib["name"]}')

    def test_square_checkboxes_not_radios(self):
        count = 0
        for p in (RES / 'layout').glob('*.xml'):
            text = p.read_text()
            self.assertNotIn('com.xuexiang.xui.widget.button.SmoothCheckBox', text)
            count += text.count('cn.ppps.forwarder.widget.material.TargetCheckBox')
        self.assertGreater(count, 50)
        self.assertIn('com.google.android.material.radiobutton.MaterialRadioButton', (RES / 'layout/fragment_senders_webhook.xml').read_text())
        self.assertIn(': MaterialCheckBox(context, attrs)', (KT / 'widget/material/MaterialControls.kt').read_text())

    def test_item_list_coverage_and_validation(self):
        source = (KT / 'utils/ItemRows.kt').read_text()
        for field in ('et_phone_numbers', 'et_mobiles', 'et_app_list', 'et_app_notify_blacklist', 'et_regex_replace', 'et_safe_phone', 'et_value'):
            self.assertIn('R.id.' + field, source)
        self.assertIn('if (validate(showErrors = false))', source)
        self.assertIn('if (!it.validate()) valid = false', source)
        for p in ('senders/SmsFragment.kt', 'client/ContactAddFragment.kt', 'client/SmsSendFragment.kt', 'action/SendSmsFragment.kt', 'action/SettingsFragment.kt', 'action/NotificationFragment.kt', 'RulesEditFragment.kt', 'condition/MsgFragment.kt'):
            self.assertIn('ItemRows.validateAll', (KT / 'fragment' / p).read_text())

    def test_material_renderers(self):
        for p in (RES / 'layout').glob('*.xml'):
            self.assertNotIn('<com.xuexiang.xui.widget.', p.read_text(), str(p))
        self.assertIn('Theme.Material3.DayNight.NoActionBar', (RES / 'values/styles.xml').read_text())
        self.assertIn('MaterialToolbar', (KT / 'widget/material/TitleBar.kt').read_text())
        self.assertIn('MaterialAlertDialogBuilder', (KT / 'widget/material/MaterialDialog.kt').read_text())
        self.assertIn('BottomNavigationView', (RES / 'layout/activity_main.xml').read_text())
        self.assertIn('NavigationRailView', (RES / 'layout-w600dp/activity_main.xml').read_text())

    def test_item_rows_view_ownership(self):
        source = (KT / 'utils/ItemRows.kt').read_text()
        self.assertNotIn('WeakHashMap', source)
        self.assertIn('source.setTag(R.id.item_rows_controller, this)', source)
        self.assertIn('removeOnGlobalLayoutListener', source)
        self.assertIn('TextInputLayout', source)
        self.assertIn('MaterialButton', source)

    def test_local_editor_apply_not_send(self):
        android = '{http://schemas.android.com/apk/res/android}'
        paths = list((RES / 'layout').glob('fragment_tasks_action_*.xml')) + list((RES / 'layout').glob('fragment_tasks_condition_*.xml'))
        self.assertEqual(len(paths), 22)
        for p in paths:
            button = next(e for e in ET.parse(p).getroot().iter() if e.get(android + 'id') == '@+id/btn_save')
            self.assertEqual(button.get(android + 'text'), '@string/m3_apply', p.name)
        for name in ('sms_send', 'wol_send'):
            p = RES / 'layout' / ('fragment_client_' + name + '.xml')
            self.assertIn('android:text="@string/send"', p.read_text())

    def test_settings_detail_routes_and_socket_widths(self):
        android = '{http://schemas.android.com/apk/res/android}'
        settings = ET.parse(RES / 'layout/fragment_settings.xml').getroot()
        ids = {e.get(android+'id','').split('/')[-1] for e in settings.iter()}
        source = (KT / 'fragment/SettingsNavigation.kt').read_text()
        routes = re.findall(r'Entry\("([a-z_]+)"', source)
        self.assertEqual(len(routes), 24)
        self.assertEqual(len(set(routes)), 24)
        for route in routes:
            self.assertIn('detail_'+route, ids)
        for key in ('sb_enable_sms','sb_enable_phone','sb_enable_app_notify','et_safe_phone','et_app_list','et_app_notify_blacklist','et_subid_sim1','et_subid_sim2','et_sms_template','rg_main_languages','xsb_retry_times','xsb_timeout'):
            self.assertIn(key, ids)
        self.assertIn('TextAppearance_Material3_BodyLarge', source)
        self.assertIn('TextAppearance_Material3_BodyMedium', source)
        self.assertIn('settings_on', source)
        socket = ET.parse(RES / 'layout/fragment_senders_socket.xml').getroot()
        for element in socket.iter():
            if element.get(android+'id') in {'@+id/'+name for name in ('et_inCharset','et_outCharset','et_address','et_port','et_username','et_password')}:
                self.assertEqual('match_parent',element.get(android+'layout_width'))
        self.assertNotIn('SharedPreference(', source)

    def test_webhook_no_sensitive_logging(self):
        transport = (KT / 'utils/sender/WebhookUtils.kt').read_text()
        self.assertNotRegex(transport, r'Log\.[diwe]\(')
        self.assertNotIn('.addInterceptor(LoggingInterceptor', transport)
        self.assertNotIn('setting.webServer +=', transport)
        self.assertNotIn('XHttpSDK.debug()', (KT / 'utils/sdkinit/XBasicLibInit.kt').read_text())

if __name__ == '__main__':
    unittest.main(verbosity=2)
