#!/usr/bin/env python3
"""Regression tests for the ConditionForm repair of eight task-condition editors.

Source-level only: proves ids/view classes, option widths, keyline, type, slider ranges and the unchanged footer.
It does not prove rendered geometry; that needs a device capture.
Baseline: scripts/condition_form_baseline.json (taken from the original layouts before the repair).
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
BASE = json.loads((ROOT / 'scripts/condition_form_baseline.json').read_text())
NAMES = ('msg', 'charge', 'to_address', 'leave_address', 'battery', 'lock_screen', 'bluetooth', 'sim')
OPTION_STYLES = {'@style/Form.Option'}
ZERO_WIDTH_STYLES = {'@style/rg_rb_style', '@style/rg_rb_style_wrap'}
RISKY = re.compile(r'startService|registerReceiver|XXPermissions|SmsManager|LocationManager|OkHttp|XHttp|URL\(|startDiscovery|requestLocation')
# Pins replaced by behavioural assertions (findings F04-F06). Widening this set needs an explicit change here.
SUPERSEDED = {
    'unchanged_sha256': {'app/src/main/kotlin/cn/ppps/forwarder/utils/ItemRows.kt'},
    'kotlin_sha256': {'LeaveAddress', 'ToAddress'},
}


def layout(name):
    return RES / 'layout' / f'fragment_tasks_condition_{name}.xml'


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
    """Attribute from the element, else from its style."""
    v = e.get(A + a)
    return v if v is not None else resolved(e.get('style'), 'android:' + a)


def strings(locale):
    return {e.get('name'): ''.join(e.itertext()) for p in (RES / locale).glob('*.xml') for e in ET.parse(p).getroot() if e.tag == 'string'}


def scroll_body(root):
    return next(e for e in root.iter() if e.tag == 'androidx.core.widget.NestedScrollView')


def footer(text):
    end = '</androidx.core.widget.NestedScrollView>'
    return text[text.index(end) + len(end):]


class ConditionFormTests(unittest.TestCase):
    maxDiff = 2000
    longMessage = False

    def test_ids_and_view_classes_unchanged(self):
        for n in NAMES:
            root = ET.parse(layout(n)).getroot()
            ids = {e.get(A + 'id').split('/')[-1]: e.tag for e in root.iter() if e.get(A + 'id')}
            old = BASE['layouts'][n]['ids']
            for i, tag in old.items():
                self.assertIn(i, ids, f'{n}: id {i} removed')
                self.assertEqual(tag, ids[i], f'{n}: {i} changed class')
            extra = set(ids) - set(old)
            self.assertTrue(all(i.startswith('condition_form_') for i in extra), f'{n}: un-namespaced new ids {extra}')

    def test_default_checked_and_initially_hidden_state_unchanged(self):
        for n in NAMES:
            root = ET.parse(layout(n)).getroot()
            checked = sorted(e.get(A + 'id').split('/')[-1] for e in root.iter() if e.get(A + 'checked') == 'true')
            self.assertEqual(BASE['layouts'][n]['checked'], checked, n)
            gone = sorted(e.get(A + 'id').split('/')[-1] for e in root.iter() if e.get(A + 'visibility') == 'gone' and e.get(A + 'id'))
            self.assertEqual(BASE['layouts'][n]['visibility_gone'],
                             [g for g in gone if not g.startswith('condition_form_')], n)

    def test_footer_byte_identical(self):
        observe = lambda n: hashlib.sha256(footer(layout(n).read_text()).encode()).hexdigest()
        failures = pins.pin_mismatches({n: BASE['layouts'][n]['footer_sha256'] for n in NAMES}, observe)
        self.assertEqual([], failures, 'footer changed: ' + '; '.join(failures))

    def test_every_radio_is_a_full_width_stacked_48dp_row(self):
        count = 0
        for n in NAMES:
            root = ET.parse(layout(n)).getroot()
            for group in root.iter('RadioGroup'):
                self.assertEqual('vertical', attr(group, 'orientation'), f'{n}: {group.get(A + "id")} not stacked')
            for e in root.iter('com.google.android.material.radiobutton.MaterialRadioButton'):
                count += 1
                rid = e.get(A + 'id')
                self.assertNotIn(e.get('style'), ZERO_WIDTH_STYLES, f'{n}: {rid}')
                self.assertEqual('match_parent', attr(e, 'layout_width'), f'{n}: {rid} not full width')
                self.assertIsNone(attr(e, 'layout_weight'), f'{n}: {rid} weighted')
                self.assertEqual('48dp', attr(e, 'minHeight'), f'{n}: {rid} under 48dp')
        self.assertEqual(40, count)

    def test_multiple_conditions_option_reachable_in_every_variant(self):
        root = ET.parse(layout('msg')).getroot()
        group = next(g for g in root.iter('RadioGroup') if g.get(A + 'id') == '@+id/rg_filed')
        self.assertEqual('@+id/rb_multi_match', list(group)[-1].get(A + 'id'))
        # The fragment still hides only the variant-specific options, never rb_multi_match.
        self.assertNotIn('rbMultiMatch.visibility', (KT / 'fragment/condition/MsgFragment.kt').read_text())

    def test_charge_checkboxes_full_width_labelled_square(self):
        root = ET.parse(layout('charge')).getroot()
        boxes = [e for e in root.iter() if e.tag == 'cn.ppps.forwarder.widget.material.TargetCheckBox']
        self.assertEqual(19, len(boxes))
        for e in boxes:
            cid = e.get(A + 'id')
            self.assertEqual('@style/Form.CheckRow', e.get('style'), cid)
            self.assertEqual('match_parent', attr(e, 'layout_width'), cid)
            self.assertEqual('48dp', attr(e, 'minHeight'), cid)
            self.assertTrue((e.get(A + 'text') or '').startswith('@string/'), f'{cid} has no own label')
        parents = {c: p for p in root.iter() for c in p}
        for e in boxes:
            self.assertEqual('vertical', attr(parents[e], 'orientation'), e.get(A + 'id'))
        # Square indicator: TargetCheckBox stays a MaterialCheckBox (not a radio/round renderer).
        self.assertIn('class TargetCheckBox', (KT / 'widget/material/MaterialControls.kt').read_text())
        self.assertIn(': MaterialCheckBox(context, attrs)', (KT / 'widget/material/MaterialControls.kt').read_text())

    def test_coordinates_are_full_width_labelled_outlined_fields(self):
        for n in ('to_address', 'leave_address'):
            root = ET.parse(layout(n)).getroot()
            parents = {c: p for p in root.iter() for c in p}
            for fid, hint in (('et_longitude', '@string/longitude'), ('et_latitude', '@string/latitude'), ('et_distance', '@string/condition_form_radius'), ('et_address', '@string/keyword')):
                e = next(x for x in root.iter() if x.get(A + 'id') == '@+id/' + fid)
                box = parents[e]
                self.assertEqual('com.google.android.material.textfield.TextInputLayout', box.tag, f'{n}/{fid}')
                self.assertIn(box.get('style'), ('@style/Form.Field', '@style/Form.Field.Next'), f'{n}/{fid}')
                self.assertEqual('match_parent', attr(box, 'layout_width'), f'{n}/{fid}')
                self.assertEqual(hint, box.get(A + 'hint'), f'{n}/{fid} label')
                self.assertEqual('vertical', attr(parents[box], 'orientation'), f'{n}/{fid} sits in a horizontal row')
                self.assertNotEqual('center', e.get(A + 'textAlignment'), f'{n}/{fid}')
            btn = next(x for x in root.iter() if x.get(A + 'id') == '@+id/btn_current_coordinates')
            self.assertEqual('@style/Form.Action', btn.get('style'))

    def test_bluetooth_device_field_and_discovery_full_width(self):
        root = ET.parse(layout('bluetooth')).getroot()
        parents = {c: p for p in root.iter() for c in p}
        e = next(x for x in root.iter() if x.get(A + 'id') == '@+id/et_device_address')
        self.assertEqual('@style/Form.Field', parents[e].get('style'))
        self.assertEqual('@string/specified_device', parents[e].get(A + 'hint'))
        btn = next(x for x in root.iter() if x.get(A + 'id') == '@+id/btn_start_discovery')
        self.assertEqual('@style/Form.Action', btn.get('style'))

    def test_one_keyline_and_medium_regular_type(self):
        banned_attrs = ('textStyle', 'textSize', 'layout_marginStart', 'layout_marginEnd', 'paddingStart', 'paddingEnd', 'layout_margin', 'padding')
        for n in NAMES:
            root = ET.parse(layout(n)).getroot()
            body = scroll_body(root)
            content = list(body)[0]
            self.assertEqual('@style/Form.Content', content.get('style'), n)
            self.assertEqual('16dp', resolved('@style/Form.Content', 'android:paddingStart'))
            self.assertEqual('16dp', resolved('@style/Form.Content', 'android:paddingEnd'))
            for e in body.iter():
                for a in banned_attrs:
                    self.assertIsNone(e.get(A + a), f'{n}: {e.get(A + "id") or e.tag} sets {a}')
                self.assertNotRegex(e.get('style') or '', r'BarStyle|rg_style|rg_rb_style|SwitchButtonStyle', n)
                for a, v in e.attrib.items():
                    self.assertNotIn('text_size_', v, f'{n}: {a}')
                    if a.startswith((APP + 'xsb_', APP + 'xrs_')):
                        self.assertIn(a.split('}')[1], ('xsb_min', 'xsb_max', 'xrs_min', 'xrs_max'), f'{n}: dead slider attr {a}')
                if e.tag == 'TextView' and e.get('style') is None:
                    self.fail(f'{n}: unstyled TextView {e.get(A + "text")}')

    def test_slider_ranges_unchanged(self):
        for n in NAMES:
            root = ET.parse(layout(n)).getroot()
            for sid, ranges in BASE['layouts'][n]['sliders'].items():
                e = next(x for x in root.iter() if x.get(A + 'id') == '@+id/' + sid)
                self.assertEqual(ranges, {k: e.get(APP + k) for k in ranges}, f'{n}/{sid}')

    def test_slider_readouts_bound_by_fragment(self):
        pairs = {
            'battery': (('condition_form_level_min_value', 'xsb_level_min'), ('condition_form_level_max_value', 'xsb_level_max')),
            'lock_screen': (('condition_form_screen_off_value', 'xsb_time_after_screen_off'), ('condition_form_screen_locked_value', 'xsb_time_after_screen_locked'),
                            ('condition_form_screen_on_value', 'xsb_time_after_screen_on'), ('condition_form_screen_unlocked_value', 'xsb_time_after_screen_unlocked')),
        }
        fragment = {'battery': 'BatteryFragment.kt', 'lock_screen': 'LockScreenFragment.kt'}
        camel = lambda s: re.sub(r'_([a-z])', lambda m: m.group(1).upper(), s)
        for n, items in pairs.items():
            root = ET.parse(layout(n)).getroot()
            seq = [e.get(A + 'id', '').split('/')[-1] for e in root.iter()]
            src = (KT / 'fragment/condition' / fragment[n]).read_text()
            for readout, slider in items:
                self.assertEqual(seq.index(readout) + 1, seq.index(slider), f'{n}: {readout} not directly above {slider}')
                self.assertIn(camel(readout), src)
                self.assertTrue(camel(readout) + '.text = ' in src or ('b.' + camel(readout) + ' to b.' + camel(slider)) in src, readout)
                self.assertTrue(camel(readout) + '.visibility = b.' + camel(slider) + '.visibility' in src or ('b.' + camel(readout) + ' to b.' + camel(slider)) in src, readout)
                listener_block = src[src.rindex('setOnSeekBarListener'):src.index('private fun sync')]
                self.assertIn('addOnChangeListener', listener_block, n)
                self.assertIn(camel(slider), listener_block, f'{n}: {slider} readout listener missing')
            # Readout listeners are added after setOnSeekBarListener, which clears change listeners.
            self.assertGreater(src.index('addOnChangeListener'), src.rindex('setOnSeekBarListener'))

    def test_fragment_changes_are_additive_only(self):
        skip = pins.superseded(BASE, 'kotlin_sha256')
        failures = []
        for c, digest in BASE['kotlin_sha256'].items():
            path = KT / 'fragment/condition' / f'{c}Fragment.kt'
            if c in BASE['kotlin_line_sha1']:
                new = [hashlib.sha1(l.encode()).hexdigest()[:12] for l in path.read_text().split('\n')]
                missing = pins.ordered_missing(BASE['kotlin_line_sha1'][c], new)
                if missing:
                    failures.append(f'{c}: original lines {missing} changed or removed')
            elif c not in skip and digest != pins.sha256_bytes(path):
                failures.append(f'{c} changed')
        self.assertEqual([], failures)

    def test_shared_and_excluded_files_untouched(self):
        skip = pins.superseded(BASE, 'unchanged_sha256')
        self.assertEqual([], pins.pin_mismatches(BASE['unchanged_sha256'], lambda p: pins.sha256_bytes(ROOT / p), skip))

    def test_superseded_pins_are_documented(self):
        fragment = lambda c: pins.sha256_bytes(KT / 'fragment/condition' / f'{c}Fragment.kt')
        self.assertEqual([], pins.supersession_problems(
            BASE, __file__, SUPERSEDED,
            {'unchanged_sha256': BASE['unchanged_sha256'].get, 'kotlin_sha256': BASE['kotlin_sha256'].get},
            {'unchanged_sha256': lambda p: pins.sha256_bytes(ROOT / p), 'kotlin_sha256': fragment}))

    def test_item_rows_contract(self):
        """Replaces the ItemRows whole-file pin (F04); same contract as the editor gate."""
        self.assertEqual([], pins.item_rows_problems())

    def test_location_fragments_delegate_and_stay_local(self):
        """Replaces the To/LeaveAddress whole-file pins (F05, F06), which also kept them out of the risk scan.

        Both fragments delegate to LocationConditionEditor (validation only on Apply, no blur handlers writing 0/1),
        and the only location request left is the explicit, user-confirmed enable-location dialog.
        """
        problems = []
        for c, layout_name, result in (('ToAddress', 'to_address', 'TASK_CONDITION_TO_ADDRESS'), ('LeaveAddress', 'leave_address', 'TASK_CONDITION_LEAVE_ADDRESS')):
            src = (KT / 'fragment/condition' / f'{c}Fragment.kt').read_text()
            for need in ('editor = LocationConditionEditor(', ').also { it.bind() }', 'val applied = editor?.apply() ?: return',
                         'intent.putExtra(KEY_BACK_DATA_CONDITION, applied.json)', f'setFragmentResult({result}, intent)',
                         'if (editor?.fillFrom(HttpServerUtils.apiLocationCache) != true)'):
                if need not in src:
                    problems.append(f'{c}: lacks {need}')
            for banned in ('setOnFocusChangeListener', 'setText("0")', 'setText("1")', 'Log.d(', 'Log.i(', 'Log.w(', 'Log.v('):
                if banned in src:
                    problems.append(f'{c}: contains {banned}')
            risky = [l.strip() for l in src.split('\n') if RISKY.search(l)]
            if risky != ['requireContext().startService(serviceIntent)']:
                problems.append(f'{c}: requests/permissions other than the one confirmed startService: {risky}')
            current = pins.kotlin_block(src, 'R.id.btn_current_coordinates ->')
            positive = pins.kotlin_block(current, '.onPositive')
            if 'requireContext().startService(serviceIntent)' not in positive or 'SettingUtils.enableLocation = true' not in positive:
                problems.append(f'{c}: startService is not inside the btn_current_coordinates confirmation (.onPositive)')
            if not re.search(r'if \(!App\.LocationClient\.isStarted\(\)\) \{\s*\n\s*MaterialDialog\.Builder', current):
                problems.append(f'{c}: enable-location dialog is no longer guarded by LocationClient.isStarted()')
            root = ET.parse(layout(layout_name)).getroot()
            ids = {e.get(A + 'id').split('/')[-1]: e for e in root.iter() if e.get(A + 'id')}
            for fid in ('et_longitude', 'et_latitude'):
                if 'numberSigned' not in (ids[fid].get(A + 'inputType') or '') or 'numberDecimal' not in (ids[fid].get(A + 'inputType') or ''):
                    problems.append(f'{layout_name}/{fid}: inputType is not signed decimal')
            for nid in ('condition_form_location_legacy_notice', 'condition_form_location_address_unavailable'):
                if nid not in ids:
                    problems.append(f'{layout_name}: {nid} missing')
        for rel in ('fragment/condition/LocationConditionEditor.kt', 'entity/condition/LocationConditionRules.kt'):
            hits = [l.strip() for l in (KT / rel).read_text().split('\n') if RISKY.search(l) or re.search(r'\bLog\.\w+\(', l)]
            if hits:
                problems.append(f'{rel}: requests/permissions/logging: {hits}')
        editor = (KT / 'fragment/condition/LocationConditionEditor.kt').read_text()
        rules = (KT / 'entity/condition/LocationConditionRules.kt').read_text()
        for need, where in (('return if (isLegacy) Applied(stored!!.description, eventData!!) else null', 'legacy address JSON returned unchanged'),
                            ('addressOption.isEnabled = false', 'address mode not selectable for new conditions'),
                            ('val result = LocationConditionRules.validate(longitude.text, latitude.text, radius.text)', 'validation on Apply'),
                            ('if (!LocationConditionRules.hasCachedFix(location)) return false', 'absent fix distinguished from 0/0')):
            if need not in editor:
                problems.append(f'LocationConditionEditor: {where} missing')
        if 'FormValidation.show' in pins.kotlin_block(editor, 'private fun refreshDescription'):
            problems.append('LocationConditionEditor: errors shown while typing')
        for need in ('value >= -180.0 && value <= 180.0', 'value >= -90.0 && value <= 90.0', 'value.isFinite() && value > 0.0',
                     'isValidLongitude(location.longitude) && isValidLatitude(location.latitude)'):
            if need not in rules:
                problems.append(f'LocationConditionRules lacks {need}')
        self.assertEqual([], problems)

    def test_new_resources_namespaced_and_translated(self):
        locales = {l: strings(l) for l in ('values', 'values-en', 'values-nl')}
        new = {k for k in locales['values'] if k.startswith('condition_form_')}
        self.assertEqual(15, len(new))
        for l in ('values-en', 'values-nl'):
            self.assertEqual(new, {k for k in locales[l] if k.startswith('condition_form_')}, l)
        for k in new:
            self.assertEqual(locales['values'][k], locales['values-en'][k], k)
            self.assertNotEqual(locales['values'][k], locales['values-nl'][k], k)
            self.assertLessEqual(len(locales['values'][k]), 100, k)
        for p in (RES / 'values').glob('styles_condition_form.xml'):
            for s in ET.parse(p).getroot().iter('style'):
                self.assertTrue(s.get('name') == 'ConditionForm' or s.get('name').startswith('ConditionForm.'), s.get('name'))
        for n in NAMES:
            for ref in re.findall(r'@string/([a-z0-9_]+)', layout(n).read_text()):
                for l, table in locales.items():
                    self.assertIn(ref, table, f'{n}: {l} lacks {ref}')
        for n, kt in (('battery', 'BatteryFragment.kt'), ('lock_screen', 'LockScreenFragment.kt')):
            for ref in re.findall(r'R\.string\.(condition_form_[a-z0-9_]+)', (KT / 'fragment/condition' / kt).read_text()):
                self.assertIn(ref, locales['values'], ref)

    def test_no_new_requests_or_permissions(self):
        risky = re.compile(r'startService|registerReceiver|XXPermissions|SmsManager|LocationManager|OkHttp|XHttp|URL\(|startDiscovery|requestLocation')
        for c in BASE['kotlin_line_sha1']:
            path = KT / 'fragment/condition' / f'{c}Fragment.kt'
            old = set(BASE['kotlin_line_sha1'][c])
            added = [l for l in path.read_text().split('\n') if hashlib.sha1(l.encode()).hexdigest()[:12] not in old]
            self.assertTrue(added, c)
            for line in added:
                self.assertIsNone(risky.search(line), f'{c}: {line}')


if __name__ == '__main__':
    unittest.main(verbosity=2)
