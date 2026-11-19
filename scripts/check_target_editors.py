#!/usr/bin/env python3
"""Source checks for the Bark, Email, Telegram, Gotify and URL Scheme target editors (Form/TargetForm pattern).

Display-only regressions: these layouts must keep one 16dp keyline (Form.Content), labels inside outlined fields,
stacked 48dp option and switch rows, and question-mark help that opens a Material dialog. Ids, view types and
stored values are guarded separately by the id/tag parity check.
"""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
KT = ROOT / 'app/src/main/kotlin/cn/ppps/forwarder'
A = '{http://schemas.android.com/apk/res/android}'
APP = '{http://schemas.android.com/apk/res-auto}'
LAYOUTS = {
    'fragment_senders_bark.xml': 'BarkFragment.kt',
    'fragment_senders_email.xml': 'EmailFragment.kt',
    'fragment_senders_telegram.xml': 'TelegramFragment.kt',
    'fragment_senders_gotify.xml': 'GotifyFragment.kt',
    'fragment_senders_url_scheme.xml': 'UrlSchemeFragment.kt',
}
ROW_LAYOUTS = ('item_add_recipient.xml',)
TIL = 'com.google.android.material.textfield.TextInputLayout'
EDIT = 'com.google.android.material.textfield.TextInputEditText'
DROPDOWN = 'cn.ppps.forwarder.widget.material.TargetDropdown'
SWITCH = 'cn.ppps.forwarder.widget.material.TargetSwitch'
RADIO = 'com.google.android.material.radiobutton.MaterialRadioButton'
FIELD_STYLES = {'@style/Form.Field', '@style/Form.Field.Next'}
DROPDOWN_STYLES = {'@style/Form.Dropdown', '@style/Form.Dropdown.Next'}


def strings(locale):
    return {e.attrib['name']: ''.join(e.itertext()) for p in (RES / locale).glob('*.xml')
            for e in ET.parse(p).getroot() if e.tag == 'string'}


def tree(name):
    return ET.parse(RES / 'layout' / name).getroot()


def parents(root):
    return {child: parent for parent in root.iter() for child in parent}


def content(root):
    """The scrolling form body (everything except the shared bottom button bar)."""
    return next(e for e in root.iter() if e.tag == 'androidx.core.widget.NestedScrollView')


def ref(value):
    m = re.fullmatch(r'@string/(\w+)', value or '')
    return m.group(1) if m else None


class TargetEditorLayoutTests(unittest.TestCase):
    def test_single_keyline_and_no_legacy_bar_blocks(self):
        for name in LAYOUTS:
            body = content(tree(name))
            inner = list(body)
            self.assertEqual(1, len(inner), name)
            self.assertEqual('@style/Form.Content', inner[0].get('style'), name)
            for e in body.iter():
                style = e.get('style') or ''
                self.assertNotRegex(style, r'BarStyle|rg_style|rg_rb_style|SwitchButtonStyle', (name, e.tag))
                for attr in ('layout_marginStart', 'layout_marginLeft', 'paddingStart', 'paddingLeft', 'layout_margin', 'padding'):
                    if e is not inner[0]:
                        self.assertIsNone(e.get(A + attr), (name, e.tag, attr, e.get(A + 'id')))

    def test_typography_has_no_bold_or_fixed_sizes(self):
        for name in list(LAYOUTS) + list(ROW_LAYOUTS):
            body = content(tree(name)) if name in LAYOUTS else tree(name)
            for e in body.iter():
                self.assertNotEqual('bold', e.get(A + 'textStyle'), (name, e.get(A + 'id')))
                self.assertIsNone(e.get(A + 'textSize'), (name, e.tag, e.get(A + 'id')))

    def test_fields_are_full_width_outlined_with_label_inside(self):
        for name in list(LAYOUTS) + list(ROW_LAYOUTS):
            root = tree(name)
            up = parents(root)
            for e in root.iter():
                if e.tag == TIL:
                    self.assertIn(e.get('style'), FIELD_STYLES, (name, e.get(A + 'hint')))
                    self.assertTrue(ref(e.get(A + 'hint')), (name, 'TextInputLayout without label'))
                    width = e.get(A + 'layout_width')
                    if width == '0dp':
                        # Only beside a 48dp question-mark/remove icon in a help row, never beside a label or a second field.
                        siblings = [s for s in up[e] if s is not e]
                        self.assertEqual(1, len(siblings), (name, e.get(A + 'hint')))
                        self.assertEqual('48dp', siblings[0].get(A + 'layout_width') or self._style_width(siblings[0]), name)
                if e.tag == EDIT:
                    self.assertEqual(TIL, up[e].tag, (name, e.get(A + 'id')))
                    self.assertIsNone(e.get(A + 'hint'), (name, e.get(A + 'id'), 'placeholder instead of label'))
                if e.tag == DROPDOWN:
                    self.assertIn(e.get('style'), DROPDOWN_STYLES, (name, e.get(A + 'id')))
                    self.assertEqual('@style/ThemeOverlay.Material3.AutoCompleteTextView.OutlinedBox', e.get(A + 'theme'), name)
                    self.assertTrue(ref(e.get(A + 'hint')), (name, e.get(A + 'id'), 'dropdown without label'))
                    self.assertNotEqual('0dp', e.get(A + 'layout_width'), name)

    @staticmethod
    def _style_width(view):
        return {'@style/Form.HelpIcon': '48dp', '@style/TargetForm.IconAction': '48dp',
                '@style/TargetForm.IconAction.Field': '48dp'}.get(view.get('style'))

    def test_no_label_textview_beside_a_field(self):
        for name in list(LAYOUTS) + list(ROW_LAYOUTS):
            root = tree(name)
            for group in root.iter():
                if group.get(A + 'orientation') != 'horizontal' and group.get('style') not in ('@style/Form.HelpRow', '@style/TargetForm.TitleRow'):
                    continue
                kids = list(group)
                if any(k.tag in (TIL, DROPDOWN) for k in kids):
                    self.assertFalse([k for k in kids if k.tag == 'TextView'], (name, 'label beside field'))
                    self.assertLessEqual(sum(k.tag in (TIL, DROPDOWN) for k in kids), 1, (name, 'two fields on one row'))

    def test_options_and_switches_are_stacked_48dp_rows(self):
        for name in LAYOUTS:
            for e in tree(name).iter():
                if e.tag == 'RadioGroup':
                    self.assertEqual('@style/Form.OptionGroup', e.get('style'), (name, e.get(A + 'id')))
                    self.assertIsNone(e.get(A + 'orientation'), name)  # vertical from the style
                if e.tag == RADIO:
                    self.assertEqual('@style/Form.Option', e.get('style'), (name, e.get(A + 'id')))
                    self.assertIsNone(e.get(A + 'layout_width'), (name, e.get(A + 'id'), 'width override'))
                    self.assertIsNone(e.get(A + 'layout_weight'), (name, e.get(A + 'id')))
                if e.tag == SWITCH:
                    self.assertEqual('@style/Form.SwitchRow', e.get('style'), (name, e.get(A + 'id')))
                    self.assertTrue(ref(e.get(A + 'text')), (name, e.get(A + 'id'), 'switch without its own label'))
        options = ET.parse(RES / 'values/styles_form.xml').getroot()
        option = next(s for s in options if s.get('name') == 'Form.Option')
        self.assertEqual('rg_rb_style_row', option.get('parent'))
        row = next(s for s in ET.parse(RES / 'values/styles_widget.xml').getroot() if s.get('name') == 'rg_rb_style_row')
        items = {i.get('name'): i.text for i in row}
        self.assertEqual('match_parent', items['android:layout_width'])
        self.assertEqual('48dp', items['android:minHeight'])

    def test_specific_requirements(self):
        email = tree('fragment_senders_email.xml')
        by_id = {e.get(A + 'id', '').split('/')[-1]: e for e in email.iter()}
        up = parents(email)
        # Host and port stacked: separate full-width fields in the same vertical section, never in one row.
        host_row, port_row = up[up[by_id['et_host']]], up[up[by_id['et_port']]]
        self.assertIs(host_row, port_row)  # same section ...
        self.assertNotEqual('horizontal', host_row.get(A + 'orientation'))  # ... stacked
        self.assertEqual('match_parent', self._width(up[by_id['et_port']]))
        # SSL and StartTLS: each its own full-width switch row.
        self.assertIsNot(by_id['sb_ssl'], by_id['sb_startTls'])
        self.assertEqual(up[by_id['sb_ssl']], up[by_id['sb_startTls']])
        self.assertNotEqual('horizontal', up[by_id['sb_ssl']].get(A + 'orientation'))
        # Encryption choices vertical.
        self.assertEqual('@style/Form.OptionGroup', by_id['rg_encryption_protocol'].get('style'))
        # Password field is a password field with its own full-width row.
        self.assertEqual('textPassword', by_id['et_pwd'].get(A + 'inputType'))
        self.assertEqual('match_parent', self._width(up[by_id['et_pwd']]))
        # Add recipient is a 48dp accessible action.
        self.assertEqual('@style/TargetForm.IconAction', by_id['btn_add_recipient'].get('style'))
        self.assertTrue(by_id['btn_add_recipient'].get(A + 'contentDescription'))
        bark = {e.get(A + 'id', '').split('/')[-1]: e for e in tree('fragment_senders_bark.xml').iter()}
        for dropdown in ('sp_level', 'sp_encryption_algorithm'):
            self.assertEqual('match_parent', self._width(bark[dropdown]), dropdown)
        telegram = {e.get(A + 'id', '').split('/')[-1]: e for e in tree('fragment_senders_telegram.xml').iter()}
        for row in ('layoutProxyHost', 'layoutProxyPort', 'layoutProxyAuthenticator'):
            self.assertEqual('vertical', telegram[row].get(A + 'orientation'), row)
            self.assertEqual('gone', telegram[row].get(A + 'visibility'), row)
        self.assertEqual('gone', by_id['layoutServiceSetting'].get(A + 'visibility'))
        self.assertEqual('gone', by_id['layout_openkeychain_sign_key'].get(A + 'visibility'))
        self.assertEqual('gone', by_id['layout_sender_keystore'].get(A + 'visibility'))
        # Fallback toggle: its own switch row beside a question-mark help, default off, with a state line below.
        fallback = by_id['sb_unencrypted_fallback']
        self.assertEqual('false', fallback.get(A + 'checked'))
        self.assertIn(by_id['btn_help_unencrypted_fallback'], list(up[fallback]))
        self.assertIs(up[up[fallback]], up[by_id['tv_unencrypted_fallback_state']])

    @staticmethod
    def _width(view):
        width = view.get(A + 'layout_width')
        if width:
            return width
        styles = {s.get('name'): {i.get('name'): i.text for i in s} for p in (RES / 'values').glob('styles*.xml') for s in ET.parse(p).getroot()}
        name = (view.get('style') or '').replace('@style/', '')
        while name:
            if 'android:layout_width' in styles.get(name, {}):
                return styles[name]['android:layout_width']
            name = name.rsplit('.', 1)[0] if '.' in name else ''
        return None

    def test_spacing_rhythm(self):
        for name in LAYOUTS:
            body = list(content(tree(name)))[0]
            sections = list(body)
            self.assertEqual('@style/Form.Section.First', sections[0].get('style'), name)
            for s in sections[1:]:
                self.assertEqual('@style/Form.Section', s.get('style'), (name, s.get(A + 'id')))
            for e in body.iter():
                top = e.get(A + 'layout_marginTop')
                if top is not None:
                    self.assertIn(top, {'0dp', '4dp', '8dp', '16dp', '24dp'}, (name, e.get(A + 'id'), top))
        styles = {s.get('name'): {i.get('name'): i.text for i in s} for s in ET.parse(RES / 'values/styles_form.xml').getroot()}
        self.assertEqual('24dp', styles['Form.Section']['android:layout_marginTop'])
        self.assertEqual('16dp', styles['Form.Field.Next']['android:layout_marginTop'])
        self.assertEqual('16dp', styles['Form.Content']['android:paddingStart'])
        self.assertEqual('16dp', styles['Form.Content']['android:paddingEnd'])

    def test_help_buttons_are_installed_and_dialogs_exist(self):
        for name, fragment in LAYOUTS.items():
            source = (KT / 'fragment/senders' / fragment).read_text()
            help_ids = [e.get(A + 'id').split('/')[-1] for e in tree(name).iter() if e.get('style') == '@style/Form.HelpIcon']
            self.assertTrue(help_ids, name)
            for hid in help_ids:
                camel = ''.join(p.title() for p in hid.split('_'))
                camel = camel[0].lower() + camel[1:]
                m = re.search(r'FormHelp\.install\(binding!!\.' + camel + r', R\.string\.(\w+), R\.string\.(\w+)\)', source)
                self.assertTrue(m, (fragment, hid))
                for locale in ('values', 'values-en', 'values-nl'):
                    self.assertIn(m.group(1), strings(locale), (locale, m.group(1)))
                    self.assertIn(m.group(2), strings(locale), (locale, m.group(2)))
            for toggle in [e.get(A + 'id').split('/')[-1] for e in tree(name).iter() if e.get('style') == '@style/TargetForm.TagToggle']:
                camel = ''.join(p.title() for p in toggle.split('_'))
                self.assertIn('TargetForm.installTagToggle(binding!!.' + camel[0].lower() + camel[1:], source, (fragment, toggle))
        help_source = (KT / 'widget/material/FormHelp.kt').read_text()
        self.assertIn('MaterialAlertDialogBuilder', help_source)
        self.assertIn('form_help_content_description', help_source)

    def test_tag_grids_keep_ids_with_hidden_legacy_toggle(self):
        # Layout structure only: each tag grid keeps its id and the legacy Show/Hide TagToggle view stays directly
        # before it (ids unchanged). At runtime TargetForm.installTagToggle hides that toggle and shows the grid
        # (Insert data + Preview) directly; that behaviour is asserted below and in
        # test_action_form.test_template_actions_shown_directly, not by this layout check.
        for name in LAYOUTS:
            root = tree(name)
            up = parents(root)
            for grid in [e for e in root.iter() if e.tag == 'GridLayout']:
                self.assertEqual('@style/TargetForm.TagGrid', grid.get('style'), name)
                siblings = list(up[grid])
                before = siblings[siblings.index(grid) - 1]
                self.assertEqual('@style/TargetForm.TagToggle', before.get('style'), (name, grid.get(A + 'id')))
        install = (KT / 'widget/material/TargetForm.kt').read_text()
        install = install[install.index('fun installTagToggle'):install.index('fun wrapDropdown')]
        self.assertIn('toggle.visibility = View.GONE', install)
        self.assertIn('grid.visibility = View.VISIBLE', install)

    def test_copy_is_short_translated_and_free_of_old_defects(self):
        base, en, nl = strings('values'), strings('values-en'), strings('values-nl')
        used = set()
        for name in list(LAYOUTS) + list(ROW_LAYOUTS):
            for e in tree(name).iter():
                for attr in (A + 'text', A + 'hint', APP + 'helperText', A + 'contentDescription'):
                    key = ref(e.get(attr))
                    if key:
                        used.add((key, attr))
        for key, attr in used:
            for locale, table in (('values', base), ('values-en', en), ('values-nl', nl)):
                self.assertIn(key, table, (locale, key))
            text = base[key]
            self.assertNotIn('✱', text, key)
            limit = 60 if attr == APP + 'helperText' else 100
            if key not in ('target_form_sender_key_supporting',):
                self.assertLessEqual(len(text), limit, (key, text))
            self.assertLessEqual(len(nl[key]), limit + 25, (key, nl[key]))
        target_keys = [k for k in base if k.startswith('target_form_')]
        self.assertGreaterEqual(len(target_keys), 30)
        for k in target_keys:
            self.assertEqual(base[k], en[k], k)
            self.assertNotEqual('', nl[k].strip(), k)
            if k.endswith('_dialog'):
                self.assertLessEqual(len(base[k]), 520, k)
        # Legacy keys stay (other layouts and stored data may use them).
        for legacy in ('account', 'servers', 'email_settings', 'bark_call', 'email_to_tips', 'TelegramApiToken', 'TelegramChatIdTips', 'url_scheme_tips', 'custom_template_tips', 'other_mail_type'):
            self.assertIn(legacy, base, legacy)

    def test_transport_wording_matches_code(self):
        base = strings('values')
        bark = (KT / 'utils/sender/BarkUtils.kt').read_text()
        self.assertIn('VerificationCode.extract(content)', bark)  # empty Auto copy copies a found code
        self.assertIn('RandomUtils.getRandomNumbersAndLetters(ivLength)', bark)  # empty IV -> random, sent along
        self.assertIn('BasicAuthInterceptor', bark)
        self.assertIn('BasicAuthInterceptor', (KT / 'utils/sender/GotifyUtils.kt').read_text())
        telegram = (KT / 'utils/sender/TelegramUtils.kt').read_text()
        self.assertIn('setting.apiToken.startsWith("http")', telegram)
        self.assertIn('"https://api.telegram.org/bot" + setting.apiToken + "/sendMessage"', telegram)
        self.assertIn("val specialChars = listOf('-')", telegram)
        scheme = (KT / 'utils/sender/UrlSchemeUtils.kt').read_text()
        for token in ('[from]', '[content]', '[msg]', '[org_content]', '[device_mark]', '[app_version]', '[title]', '[card_slot]', '[receive_time]', '[timestamp]'):
            self.assertIn(token, scheme)
            self.assertIn(token, base['target_form_url_scheme_dialog'])
        self.assertIn('if (TextUtils.isEmpty(template)) template = getString(R.string.tag_from)', (KT / 'entity/MsgInfo.kt').read_text())
        email = (KT / 'utils/sender/EmailUtils.kt').read_text()
        # Encryption modes: no silent plaintext. Unencrypted copies only through the explicit fallback setting
        # (EmailEncryption.kt); a configured signing key is loaded by the same helper for Save and for sending.
        self.assertNotIn('recipientsWithoutCert', email)
        self.assertIn('EmailCrypto.isEncryptionMode(setting.encryptionProtocol)', email)
        self.assertIn('allowUnencryptedFallback = setting.allowUnencryptedFallback', email)
        self.assertIn('EmailKeys.loadSigningKey(protocol, setting.keystore, setting.password)', email)
        policy = (KT / 'utils/mail/EmailEncryption.kt').read_text()
        self.assertIn('if (allowUnencryptedFallback) fallback += Pending(email, reason, detail)', policy)
        self.assertIn('if (signing is SigningKey.Failed)', policy)
        self.assertNotIn('generateCertificate(FileInputStream', policy + email)
        self.assertIn('EmailKeys.loadSigningKey(encryptionProtocol, keystore, password)', (KT / 'fragment/senders/EmailFragment.kt').read_text())

    def test_dropdown_wrap_keeps_order_and_avoids_theme_attrs(self):
        helper = (KT / 'widget/material/TargetForm.kt').read_text()
        self.assertIn('(0 until source.count).map { source.getItem(it).toString() }', helper)
        self.assertNotIn('setSelection', helper)
        item = (RES / 'layout/target_form_dropdown_item.xml').read_text()
        self.assertNotIn('?attr/', item)  # adapters may inflate without the app theme (see PATTERN.md gotchas)
        self.assertIn('android:minHeight="48dp"', item)

    def test_shared_styles_untouched(self):
        widget = (RES / 'values/styles_widget.xml').read_text()
        for name in ('BarStyle', 'rg_style', 'rg_rb_style', 'SwitchButtonStyle'):
            self.assertIn(f'name="{name}"', widget)
        target = ET.parse(RES / 'values/styles_target_form.xml').getroot()
        self.assertTrue(all(s.get('name').startswith('TargetForm') for s in target if s.tag == 'style'))


if __name__ == '__main__':
    unittest.main(verbosity=2)
