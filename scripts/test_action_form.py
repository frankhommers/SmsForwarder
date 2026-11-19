#!/usr/bin/env python3
"""Regression tests for the ActionForm repair of the task editor and ten task-action editors.

Source-level only: proves ids/view classes, defaults, option/slider/field geometry declared in XML, keyline, type,
slider ranges, readout binding order and the unchanged header/footer. It does not prove rendered geometry, slider
drag behaviour or dropdown popups; those need a device capture.
Baseline: scripts/action_form_baseline.json (taken from the original layouts/fragments before the repair).
Pins made obsolete by later intended changes are listed under superseded_pins with their original hash, reason and
replacement assertions (see scripts/form_gate_pins.py); every other pin stays strict and all mismatches are reported.
"""
from pathlib import Path
import hashlib
import json
import re
import sys
import unittest
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parent))
import form_gate_pins as pins  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
KT = ROOT / 'app/src/main/kotlin/cn/ppps/forwarder'
A = '{http://schemas.android.com/apk/res/android}'
APP = '{http://schemas.android.com/apk/res-auto}'
BASE = json.loads((ROOT / 'scripts/action_form_baseline.json').read_text())
NAMES = tuple(BASE['layouts'])
ZERO_WIDTH_STYLES = {'@style/rg_rb_style', '@style/rg_rb_style_wrap'}
SWITCH = 'cn.ppps.forwarder.widget.material.TargetSwitch'
CHECK = 'cn.ppps.forwarder.widget.material.TargetCheckBox'
SLIDER = 'cn.ppps.forwarder.widget.material.TargetSlider'
DROPDOWN = 'cn.ppps.forwarder.widget.material.TargetEditDropdown'
TIL = 'com.google.android.material.textfield.TextInputLayout'
RADIO = 'com.google.android.material.radiobutton.MaterialRadioButton'
# Pins replaced by behavioural assertions (findings F07-F11). Widening this needs an explicit change here.
# Alarm: exactly the seven raw "label:value" summary lines replaced by the readable summary (copy review c83).
SUPERSEDED = {
    'unchanged_sha256': {
        'app/src/main/kotlin/cn/ppps/forwarder/fragment/action/NotificationFragment.kt',
        'app/src/main/kotlin/cn/ppps/forwarder/utils/ItemRows.kt',
        'app/src/main/kotlin/cn/ppps/forwarder/widget/material/TargetForm.kt',
    },
    'footer_sha256': {'action_cleaner'},
    'kotlin_line_sha1': {'Alarm': {347: '2e150eebff0a', 348: '5846cb552779', 350: '7b2bf1e28303', 357: '0def7ff4b818',
                                   358: 'e594d07c8d75', 365: 'b4c8b27666c6', 366: 'bb6acbd9026b'}},
}
FOOTER_ROLES = (('btn_del', '@style/MaterialActionButton.Secondary', '@drawable/ic_delete'),
                ('btn_save', '@style/MaterialActionButton', '@drawable/ic_save'),
                ('btn_test', '@style/MaterialActionButton.Secondary', '@drawable/ic_test'))


def layout(name):
    return RES / 'layout' / f'fragment_tasks_{name}.xml'


def styles():
    out = {}
    for p in (RES / 'values').glob('*.xml'):
        for s in ET.parse(p).getroot().iter('style'):
            out[s.get('name')] = (s.get('parent'), {i.get('name'): (i.text or '').strip() for i in s.iter('item')})
    return out


STYLES = styles()


def resolved(style_ref, attr):
    """Walk explicit and implicit (dotted) parents of a project style."""
    name = style_ref.split('/', 1)[1] if style_ref and style_ref.startswith('@style/') else style_ref
    seen = 0
    while name and name in STYLES and seen < 20:
        parent, items = STYLES[name]
        if attr in items:
            return items[attr]
        name = parent.split('/', 1)[-1] if parent else (name.rsplit('.', 1)[0] if '.' in name else None)
        seen += 1
    return None


def attr(e, a):
    v = e.get(A + a)
    return v if v is not None else resolved(e.get('style'), 'android:' + a)


def sid(e):
    return (e.get(A + 'id') or '').split('/')[-1]


def strings(locale):
    return {e.get('name'): ''.join(e.itertext()) for p in (RES / locale).glob('*.xml') for e in ET.parse(p).getroot() if e.tag == 'string'}


def head_foot(text):
    a = text.index('<androidx.core.widget.NestedScrollView')
    a = text.index('>', a) + 1
    b = text.index('</androidx.core.widget.NestedScrollView>')
    return text[:a], text[b:]


def body(root):
    return list(next(e for e in root.iter() if e.tag == 'androidx.core.widget.NestedScrollView'))[0]


def parents(root):
    return {c: p for p in root.iter() for c in p}


def camel(s):
    return re.sub(r'_([a-z])', lambda m: m.group(1).upper(), s)


class ActionFormTests(unittest.TestCase):
    maxDiff = 3000
    longMessage = False

    # --- contract preservation -------------------------------------------------------------------------------
    def test_ids_and_view_classes_unchanged(self):
        total = 0
        for n in NAMES:
            root = ET.parse(layout(n)).getroot()
            ids = {sid(e): e.tag for e in root.iter() if e.get(A + 'id')}
            for i, tag in BASE['layouts'][n]['ids'].items():
                total += 1
                self.assertIn(i, ids, f'{n}: id {i} removed')
                self.assertEqual(tag, ids[i], f'{n}: {i} changed class')
            extra = set(ids) - set(BASE['layouts'][n]['ids'])
            self.assertTrue(all(i.startswith('action_form_') for i in extra), f'{n}: un-namespaced new ids {extra}')
        self.assertEqual(155, total)

    def test_defaults_visibility_and_input_types_unchanged(self):
        for n in NAMES:
            root = ET.parse(layout(n)).getroot()
            b = BASE['layouts'][n]
            checked = sorted(sid(e) for e in root.iter() if e.get(A + 'checked') == 'true' and e.get(A + 'id'))
            self.assertEqual(b['checked'], checked, n)
            gone = sorted(sid(e) for e in root.iter() if e.get(A + 'visibility') == 'gone' and e.get(A + 'id'))
            self.assertEqual(b['visibility_gone'], gone, n)
            types = {sid(e): e.get(A + 'inputType') for e in root.iter() if e.get(A + 'id') and e.get(A + 'inputType')}
            self.assertEqual(b['input_types'], types, n)

    def test_header_and_footer_byte_identical(self):
        skip = pins.superseded(BASE, 'footer_sha256')
        failures = pins.pin_mismatches({n: BASE['layouts'][n]['header_sha256'] for n in NAMES},
                                       lambda n: pins.sha256_text(head_foot(layout(n).read_text())[0]))
        failures += pins.pin_mismatches({n + ':footer': BASE['layouts'][n]['footer_sha256'] for n in NAMES},
                                        lambda k: pins.sha256_text(head_foot(layout(k[:-7]).read_text())[1]),
                                        {k + ':footer' for k in skip})
        self.assertEqual([], failures, 'root/scroll tag or footer changed: ' + '; '.join(failures))

    def test_footer_button_roles(self):
        """Replaces the Cleaner footer pin (F10): every editor footer is Discard/Apply/Test in that order and role."""
        problems = []
        for n in NAMES:
            f = head_foot(layout(n).read_text())[1]
            buttons = re.findall(r'<com\.google\.android\.material\.button\.MaterialButton\b([^>]*?)/>', f, re.S)
            got = []
            for b in buttons:
                a = dict(re.findall(r'([\w:]+)="([^"]*)"', b))
                got.append((a.get('android:id', '').split('/')[-1], a.get('style'), a.get('android:drawableStart')))
            if tuple(got) != FOOTER_ROLES:
                problems.append(f'{n}: footer buttons {got}')
            if not re.search(r'android:id="@\+id/btn_test"[^>]*android:text="@string/test"', f, re.S):
                problems.append(f'{n}: btn_test is not labelled @string/test')
            if f.count('<com.google.android.material.button.MaterialButton') != 3:
                problems.append(f'{n}: footer does not hold exactly three buttons')
        cleaner = head_foot(layout('action_cleaner').read_text())[1]
        if not re.search(r'android:id="@\+id/btn_del"[^>]*android:text="@string/discard"', cleaner, re.S) or \
                not re.search(r'android:id="@\+id/btn_save"[^>]*android:text="@string/m3_apply"', cleaner, re.S):
            problems.append('action_cleaner: Discard/Apply labels changed')
        self.assertEqual([], problems)

    def test_slider_ranges_unchanged_and_no_dead_attrs(self):
        for n in NAMES:
            root = ET.parse(layout(n)).getroot()
            for s, ranges in BASE['layouts'][n]['sliders'].items():
                e = next(x for x in root.iter() if sid(x) == s)
                self.assertEqual(ranges, {k: e.get(APP + k) for k in ranges}, f'{n}/{s}')
                dead = [a for a in e.attrib if a.startswith(APP + 'xsb_') and a.split('}')[1] not in ('xsb_min', 'xsb_max')]
                self.assertEqual([], dead, f'{n}/{s}')

    # --- geometry declared in XML ------------------------------------------------------------------------------
    def test_one_keyline_no_bold_no_legacy_bars(self):
        banned = ('textStyle', 'textSize', 'layout_marginStart', 'layout_marginEnd', 'paddingStart', 'paddingEnd', 'layout_margin', 'padding')
        for n in NAMES:
            root = ET.parse(layout(n)).getroot()
            content = body(root)
            self.assertEqual('@style/Form.Content', content.get('style'), n)
            for e in content.iter():
                for a in banned:
                    self.assertIsNone(e.get(A + a), f'{n}: {sid(e) or e.tag} sets {a}')
                self.assertNotRegex(e.get('style') or '', r'BarStyle|BarTitleStyle|rg_style|rg_rb_style|SwitchButtonStyle|insertButtonStyle', n)
                for a, v in e.attrib.items():
                    self.assertNotIn('text_size_', v, f'{n}: {a}')
                    self.assertNotIn('config_padding', v, f'{n}: {a}')
                if e.tag == 'TextView':
                    self.assertIsNotNone(e.get('style'), f'{n}: unstyled TextView {e.get(A + "text")}')
                if e.tag == 'View':
                    self.fail(f'{n}: divider View left in the form body')
        self.assertEqual('16dp', resolved('@style/Form.Content', 'android:paddingStart'))
        self.assertEqual('16dp', resolved('@style/Form.Content', 'android:paddingEnd'))

    def test_every_radio_is_a_full_width_stacked_48dp_row(self):
        count = 0
        for n in NAMES:
            root = ET.parse(layout(n)).getroot()
            for g in root.iter('RadioGroup'):
                self.assertEqual('vertical', attr(g, 'orientation'), f'{n}: {sid(g)} not stacked')
            for e in root.iter(RADIO):
                count += 1
                self.assertNotIn(e.get('style'), ZERO_WIDTH_STYLES, f'{n}: {sid(e)}')
                self.assertEqual('@style/Form.Option', e.get('style'), f'{n}: {sid(e)}')
                self.assertEqual('match_parent', attr(e, 'layout_width'), f'{n}: {sid(e)}')
                self.assertIsNone(attr(e, 'layout_weight'), f'{n}: {sid(e)} weighted')
                self.assertEqual('48dp', attr(e, 'minHeight'), f'{n}: {sid(e)}')
        # Same number of radios as the original layouts (none removed or hidden).
        self.assertEqual(sum(1 for b in BASE['layouts'].values() for i, tag in b['ids'].items() if tag == RADIO), count)
        self.assertEqual(21, count)

    def test_settings_no_preference_and_power_options_are_rows(self):
        root = ET.parse(layout('action_settings')).getroot()
        for g, opts in (('rg_accuracy', 3), ('rg_power_requirement', 4)):
            group = next(x for x in root.iter('RadioGroup') if sid(x) == g)
            self.assertEqual(opts, len(list(group)), g)
            self.assertEqual('@style/Form.OptionGroup', group.get('style'), g)

    def test_every_switch_is_a_labelled_full_width_row(self):
        count = 0
        for n in NAMES:
            root = ET.parse(layout(n)).getroot()
            for e in root.iter(SWITCH):
                count += 1
                self.assertEqual('@style/Form.SwitchRow', e.get('style'), f'{n}: {sid(e)}')
                self.assertTrue((e.get(A + 'text') or '').startswith('@string/'), f'{n}: {sid(e)} has no own label')
                self.assertEqual('match_parent', attr(e, 'layout_width'), f'{n}: {sid(e)}')
                self.assertEqual('48dp', attr(e, 'minHeight'), f'{n}: {sid(e)}')
        self.assertEqual(1 + 6 + 3 + 9, count)

    def test_every_checkbox_is_a_labelled_square_row(self):
        count = 0
        for n in NAMES:
            root = ET.parse(layout(n)).getroot()
            ps = parents(root)
            for e in root.iter(CHECK):
                count += 1
                self.assertEqual('@style/Form.CheckRow', e.get('style'), f'{n}: {sid(e)}')
                self.assertTrue((e.get(A + 'text') or '').startswith('@string/'), f'{n}: {sid(e)} has no own label')
                self.assertEqual('vertical', attr(ps[e], 'orientation'), f'{n}: {sid(e)} in a horizontal row')
        self.assertEqual(6 + 2 + 2 + 3, count)
        src = (KT / 'widget/material/MaterialControls.kt').read_text()
        self.assertIn(': MaterialCheckBox(context, attrs)', src)

    def test_settings_duplicate_slider_full_width_with_readout(self):
        root = ET.parse(layout('action_settings')).getroot()
        ps = parents(root)
        e = next(x for x in root.iter(SLIDER) if sid(x) == 'xsb_duplicate_messages_limits')
        self.assertEqual('match_parent', attr(e, 'layout_width'))
        self.assertIsNone(attr(e, 'layout_weight'))
        self.assertEqual('vertical', attr(ps[e], 'orientation'))
        self.assertEqual('match_parent', attr(ps[e], 'layout_width'))

    def test_location_and_alarm_fields_full_width_labelled(self):
        cases = {
            'action_settings': {'et_min_interval': '@string/action_form_min_interval', 'et_min_distance': '@string/action_form_min_distance'},
            'action_alarm': {'et_music_path': '@string/action_form_music_file', 'et_vibration_effect': '@string/action_form_vibration_pattern', 'et_flash_effect': '@string/action_form_flash_pattern'},
            'action_wol': {'et_mac': '@string/action_form_mac_address', 'et_ip': '@string/broadcast_address', 'et_port': '@string/port'},
            'action_send_sms': {'et_msg_content': '@string/msg_content'},
            'edit': {'et_name': '@string/action_form_task_name'},
        }
        for n, fields in cases.items():
            root = ET.parse(layout(n)).getroot()
            ps = parents(root)
            for fid, hint in fields.items():
                e = next(x for x in root.iter() if sid(x) == fid)
                box = ps[e]
                self.assertEqual(TIL, box.tag, f'{n}/{fid}')
                self.assertIn(box.get('style'), ('@style/Form.Field', '@style/Form.Field.Next'), f'{n}/{fid}')
                self.assertEqual('match_parent', attr(box, 'layout_width'), f'{n}/{fid}')
                self.assertEqual(hint, box.get(A + 'hint'), f'{n}/{fid} label')
                self.assertEqual('vertical', attr(ps[box], 'orientation'), f'{n}/{fid} sits in a horizontal row')
                self.assertNotEqual('center', e.get(A + 'textAlignment'), f'{n}/{fid}')
                self.assertIsNone(e.get(A + 'textSize'), f'{n}/{fid}')
        alarm = ET.parse(layout('action_alarm')).getroot()
        btn = next(x for x in alarm.iter() if sid(x) == 'btn_file_picker')
        self.assertEqual('@style/Form.Action', btn.get('style'))

    def test_insert_buttons_at_least_48dp(self):
        root = ET.parse(layout('action_alarm')).getroot()
        buttons = [e for e in root.iter() if sid(e).startswith('bt_insert_')]
        self.assertEqual(5, len(buttons))
        for e in buttons:
            self.assertTrue(e.get('style', '').startswith('@style/ActionForm.InsertButton'), sid(e))
            self.assertEqual('48dp', attr(e, 'minHeight'), sid(e))
            self.assertTrue((e.get(A + 'contentDescription') or '').startswith('@string/action_form_insert_'), sid(e))

    def test_target_dropdowns_full_width_outlined(self):
        for n, dd, rec in (('action_rule', 'sp_rule', 'recycler_rules'), ('action_sender', 'sp_sender', 'recycler_senders'),
                           ('action_task', 'sp_task', 'recycler_tasks'), ('action_settings', 'sp_app', None)):
            root = ET.parse(layout(n)).getroot()
            ps = parents(root)
            e = next(x for x in root.iter(DROPDOWN) if sid(x) == dd)
            self.assertIn(e.get('style'), ('@style/Form.Dropdown', '@style/Form.Dropdown.Next'), dd)
            self.assertEqual('match_parent', attr(e, 'layout_width'), dd)
            self.assertIsNone(attr(e, 'layout_weight'), dd)
            self.assertEqual('@style/ThemeOverlay.Material3.AutoCompleteTextView.OutlinedBox', e.get(A + 'theme'), dd)
            self.assertTrue((e.get(APP + 'es_hint') or '').startswith('@string/'), dd)
            self.assertTrue((e.get(APP + 'helperText') or '').startswith('@string/choose_'), dd)
            self.assertEqual('vertical', attr(ps[e], 'orientation'), f'{dd} beside a label')
            if rec:
                self.assertTrue(any(sid(x) == rec for x in ps[e]), f'{rec} not below {dd}')

    def test_task_editor_name_and_enabled_are_separate(self):
        root = ET.parse(layout('edit')).getroot()
        ps = parents(root)
        name = next(x for x in root.iter() if sid(x) == 'et_name')
        status = next(x for x in root.iter() if sid(x) == 'sb_status')
        self.assertEqual('@string/action_form_task_enabled', status.get(A + 'text'))
        self.assertEqual('true', status.get(A + 'checked'))
        section = ps[ps[name]]
        self.assertIs(section, ps[status])
        self.assertEqual('vertical', attr(section, 'orientation'))
        self.assertNotIn('task_name_status', layout('edit').read_text())
        for kind in ('condition', 'action'):
            row = next(x for x in root.iter() if sid(x) == f'layout_add_{kind}')
            self.assertEqual('@style/ActionForm.AddRow', row.get('style'))
            self.assertEqual('true', attr(row, 'clickable'))
            self.assertEqual('56dp', attr(row, 'minHeight'))

    def test_help_text_below_headings_not_beside(self):
        for n in NAMES:
            root = ET.parse(layout(n)).getroot()
            for e in root.iter('LinearLayout'):
                kids = [k for k in e if k.tag == 'TextView']
                if len(kids) >= 2 and attr(e, 'orientation') in (None, 'horizontal'):
                    self.fail(f'{n}: two TextViews side by side in {sid(e) or e.get("style")}')

    # --- readouts ----------------------------------------------------------------------------------------------
    READOUTS = {
        ('action_settings', 'SettingsFragment.kt'): (('action_form_duplicate_value', 'xsb_duplicate_messages_limits'),),
        ('action_alarm', 'AlarmFragment.kt'): (('action_form_volume_value', 'xsb_volume'), ('action_form_play_times_value', 'xsb_play_times'),
                                               ('action_form_repeat_times_value', 'xsb_repeat_times'), ('action_form_flash_times_value', 'xsb_flash_times')),
        ('action_cleaner', 'CleanerFragment.kt'): (('action_form_days_value', 'xsb_days'),),
        ('action_resend', 'ResendFragment.kt'): (('action_form_hours_value', 'xsb_hours'),),
    }

    def test_every_slider_has_a_bound_readout_directly_above(self):
        sliders = 0
        for n in NAMES:
            sliders += len(BASE['layouts'][n]['sliders'])
        self.assertEqual(sum(len(v) for v in self.READOUTS.values()), sliders)
        for (n, kt), pairs in self.READOUTS.items():
            root = ET.parse(layout(n)).getroot()
            seq = [sid(e) for e in root.iter()]
            src = (KT / 'fragment/action' / kt).read_text()
            for readout, slider in pairs:
                self.assertEqual(seq.index(readout) + 1, seq.index(slider), f'{n}: {readout} not directly above {slider}')
                r, s = camel(readout), camel(slider)
                self.assertIn(f'b.{r}.text = ', src, readout)
                self.assertIn(f'b.{r}.visibility = b.{s}.visibility', src, readout)
                self.assertRegex(src, rf'binding!!\.{s}\.addOnChangeListener', slider)
                # Readout listener must be registered after any setOnSeekBarListener on the same slider (it clears listeners).
                add = src.index(f'binding!!.{s}.addOnChangeListener')
                seek = src.rfind(f'binding!!.{s}.setOnSeekBarListener')
                self.assertGreater(add, seek, f'{slider}: readout listener before setOnSeekBarListener')
                # ...and in initListeners, which BaseFragment runs after initViews (where setDefaultValue restores values).
                self.assertGreater(add, src.index('override fun initListeners'), slider)

    def test_fragment_changes_are_additive_only(self):
        sup = pins.superseded(BASE, 'kotlin_line_sha1')
        failures = []
        for c, hashes in BASE['kotlin_line_sha1'].items():
            new = [hashlib.sha1(l.encode()).hexdigest()[:12] for l in (KT / 'fragment/action' / f'{c}Fragment.kt').read_text().split('\n')]
            # Only listed indices whose baseline hash is exactly the listed one are skipped; the meta-test checks the list.
            skip = {int(i) for i, h in sup.get(c, {}).get('old_line_sha1', {}).items() if int(i) < len(hashes) and hashes[int(i)] == h}
            missing = pins.ordered_missing(hashes, new, skip)
            if missing:
                failures.append(f'{c}: original lines {missing} changed or removed')
        self.assertEqual([], failures)

    def test_alarm_summary_readable_and_patterns_kept(self):
        """Replaces the seven superseded Alarm summary lines (F11)."""
        src = (KT / 'fragment/action/AlarmFragment.kt').read_text()
        start = pins.kotlin_block(src, 'val action = if (binding!!.rgAlarmState.checkedRadioButtonId == R.id.rb_start_alarm)')
        problems = []
        for need in (
            'description.append(getString(R.string.start_alarm))',
            'description.append("\\n").append(getString(R.string.review_alarm_sound, volume))',
            'description.append(" · ").append(if (playTimes == 0) getString(R.string.review_alarm_continuous) else getString(R.string.review_alarm_count, playTimes))',
            'description.append(" · ").append(getString(R.string.review_alarm_custom_audio))',
            'description.append("\\n").append(getString(R.string.review_alarm_vibration))',
            'description.append(" · ").append(if (repeatTimes == 0) getString(R.string.review_alarm_continuous) else getString(R.string.review_alarm_count, repeatTimes))',
            'description.append("\\n").append(getString(R.string.review_alarm_flash))',
            'description.append(" · ").append(if (flashTimes == 0) getString(R.string.review_alarm_continuous) else getString(R.string.review_alarm_count, flashTimes))',
            # Stored effects and defaults are unchanged; only the summary text changed.
            'vibrationEffect.ifEmpty { "---___===___".also { binding!!.etVibrationEffect.setText(it) } }',
            'flashEffect.ifEmpty { "XXOOXXOO".also { binding!!.etFlashEffect.setText(it) } }',
            'flashEffect = flashEffect.toUpperCase(Locale.ROOT).replace("1", "X").replace("0", "O")',
        ):
            if need not in start:
                problems.append(f'Alarm summary lacks {need}')
        if 'return AlarmSetting(description.toString(), action, volume, playTimes, music, repeatTimes, vibrationEffect, flashTimes, flashEffect)' not in src:
            problems.append('Alarm: stored AlarmSetting fields changed')
        for line in (l.strip() for l in src.split('\n') if 'description.append' in l):
            if re.search(r'\b(music|vibrationEffect|flashEffect)\b|\.append\(":"\)|alarm_(volume|play_times|music|vibration_effect|flash_effect|repeat_times)\b', line):
                problems.append(f'Alarm summary exposes a raw value/pattern again: {line}')
        problems += pins.string_problems(('review_alarm_sound', 'review_alarm_continuous', 'review_alarm_count', 'review_alarm_custom_audio',
                                          'review_alarm_vibration', 'review_alarm_flash'), max_len=30, english_default=True)
        fmt = re.compile(r'%(?:\d+\$)?[ds]|%%')
        tables = {l: pins.strings(l) for l in pins.LOCALES}
        for k in ('review_alarm_sound', 'review_alarm_count'):
            if sorted(fmt.findall(tables['values'][k])) != sorted(fmt.findall(tables['values-nl'][k])) or not fmt.findall(tables['values'][k]):
                problems.append(f'{k}: format placeholders differ between default and Dutch')
        self.assertEqual([], problems)

    def test_template_actions_shown_directly(self):
        """Replaces the TargetForm whole-file pin (F09): Insert data/Preview visible, legacy Show/Hide toggle hidden."""
        src = (KT / 'widget/material/TargetForm.kt').read_text()
        body = pins.kotlin_block(src, 'fun installTagToggle(toggle: TextView, grid: View)')
        problems = []
        for need in ('toggle.setOnClickListener(null)', 'toggle.visibility = View.GONE', 'grid.visibility = View.VISIBLE'):
            if need not in body:
                problems.append(f'installTagToggle lacks {need}')
        if re.search(r'target_form_tags_(show|hide)|View\.GONE[^\n]*grid|grid\.visibility = View\.GONE|grid\.visibility = if', body):
            problems.append('installTagToggle can hide the template actions again')
        if 'target_form_tags_show' in src or 'target_form_tags_hide' in src:
            problems.append('TargetForm still renders the Show/Hide template tags label')
        for need in ('(0 until source.count).map { source.getItem(it).toString() }', 'R.layout.target_form_dropdown_item'):
            if need not in pins.kotlin_block(src, 'fun wrapDropdown(dropdown: TextInputLayout)'):
                problems.append(f'wrapDropdown lacks {need}')
        picker = (KT / 'widget/material/TemplatePicker.kt').read_text()
        install = pins.kotlin_block(picker, 'fun install(context: Context, grid: GridLayout, editText: EditText, scene: String, items: List<Item>)')
        for need in ('button(context, R.string.template_insert_data,', 'button(context, R.string.template_preview,', 'addActions(grid, insert, preview)'):
            if need not in install:
                problems.append(f'TemplatePicker.install lacks {need}')
        common = (KT / 'utils/CommonUtils.kt').read_text()
        if 'cn.ppps.forwarder.widget.material.TemplatePicker.install(context, gridLayout, editText, scene, templateTags(scene, excludeButtons))' not in common:
            problems.append('createTagButtons no longer installs the Insert data/Preview actions')
        problems += pins.notification_problems()
        self.assertEqual([], problems)

    def test_item_rows_contract(self):
        """Replaces the ItemRows whole-file pin (F08); same contract as the editor gate."""
        self.assertEqual([], pins.item_rows_problems())

    def test_superseded_pins_are_documented(self):
        footer = lambda n: pins.sha256_text(head_foot(layout(n).read_text())[1])
        alarm = lambda c: [hashlib.sha1(l.encode()).hexdigest()[:12] for l in (KT / 'fragment/action' / f'{c}Fragment.kt').read_text().split('\n')]
        self.assertEqual([], pins.supersession_problems(
            BASE, __file__, SUPERSEDED,
            {'unchanged_sha256': BASE['unchanged_sha256'].get, 'footer_sha256': lambda n: BASE['layouts'].get(n, {}).get('footer_sha256'),
             'kotlin_line_sha1': BASE['kotlin_line_sha1'].get},
            {'unchanged_sha256': lambda p: pins.sha256_bytes(ROOT / p), 'footer_sha256': footer, 'kotlin_line_sha1': alarm}))

    def test_no_new_logging_requests_or_permissions(self):
        risky = re.compile(r'Log\.|logg\(|startService|registerReceiver|XXPermissions|SmsManager|LocationManager|OkHttp|XHttp|URL\(|WorkManager|enqueue|setFragmentResult|printStackTrace')
        for c, hashes in BASE['kotlin_line_sha1'].items():
            old = set(hashes)
            added = [l for l in (KT / 'fragment/action' / f'{c}Fragment.kt').read_text().split('\n') if hashlib.sha1(l.encode()).hexdigest()[:12] not in old]
            self.assertTrue(added, c)
            for line in added:
                self.assertIsNone(risky.search(line), f'{c}: {line}')

    def test_shared_and_excluded_files_untouched(self):
        skip = pins.superseded(BASE, 'unchanged_sha256')
        self.assertEqual([], pins.pin_mismatches(BASE['unchanged_sha256'], lambda p: pins.sha256_bytes(ROOT / p), skip))

    # --- resources --------------------------------------------------------------------------------------------
    def test_new_resources_namespaced_translated_and_short(self):
        locales = {l: strings(l) for l in ('values', 'values-en', 'values-nl')}
        new = {k for k in locales['values'] if k.startswith('action_form_')}
        self.assertEqual(51, len(new))
        for l in ('values-en', 'values-nl'):
            self.assertEqual(new, {k for k in locales[l] if k.startswith('action_form_')}, l)
        fmt = re.compile(r'%(?:\d+\$)?[ds]|%%')
        for k in new:
            self.assertEqual(locales['values'][k], locales['values-en'][k], k)
            self.assertNotEqual(locales['values'][k], locales['values-nl'][k], k)
            self.assertLessEqual(len(locales['values'][k]), 100, k)
            self.assertEqual(sorted(fmt.findall(locales['values'][k])), sorted(fmt.findall(locales['values-nl'][k])), k)
        for s in ET.parse(RES / 'values/styles_action_form.xml').getroot().iter('style'):
            self.assertTrue(s.get('name') == 'ActionForm' or s.get('name').startswith('ActionForm.'), s.get('name'))
        for n in NAMES:
            for ref in re.findall(r'@string/([a-z0-9_]+)', layout(n).read_text()):
                for l, table in locales.items():
                    self.assertIn(ref, table, f'{n}: {l} lacks {ref}')
        for kt in ('SettingsFragment.kt', 'AlarmFragment.kt', 'CleanerFragment.kt', 'ResendFragment.kt'):
            for ref in re.findall(r'R\.string\.(action_form_[a-z0-9_]+)', (KT / 'fragment/action' / kt).read_text()):
                self.assertIn(ref, locales['values'], ref)

    def test_styles_referenced_exist(self):
        for n in NAMES:
            for ref in re.findall(r'@style/([A-Za-z0-9_.]+)', layout(n).read_text()):
                if ref.startswith(('Form', 'ActionForm', 'MaterialActionButton')):
                    self.assertIn(ref, STYLES, f'{n}: missing style {ref}')

    def test_no_zero_width_option_anywhere_in_scope(self):
        for n in NAMES:
            root = ET.parse(layout(n)).getroot()
            for g in root.iter():
                if attr(g, 'orientation') == 'vertical':
                    for k in g:
                        if attr(k, 'layout_width') == '0dp':
                            self.fail(f'{n}: zero-width {k.tag} {sid(k)} in a vertical parent')


if __name__ == '__main__':
    unittest.main(verbosity=2)
