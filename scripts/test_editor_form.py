#!/usr/bin/env python3
"""Source regressions for the EditorForm repair of the last six small editors.

Layouts: fragment_senders_sms, fragment_senders_socket, fragment_tasks_action_notification,
fragment_tasks_action_frpc, fragment_tasks_condition_network, fragment_tasks_condition_cron.
Source-level only: ids/view classes, defaults, initial visibility, keyline, type, row shape, footer, copy and
behaviour hooks. It does not prove rendered geometry or taps; that needs a device capture.
Baseline: scripts/editor_form_baseline.json (taken from the original layouts before the repair).
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
import check_security  # noqa: E402  (FORBIDDEN_LOGS / active_lines: one source of truth for sensitive-log patterns)
import form_gate_pins as pins  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
KT = ROOT / 'app/src/main/kotlin/cn/ppps/forwarder'
A = '{http://schemas.android.com/apk/res/android}'
APP = '{http://schemas.android.com/apk/res-auto}'
BASE = json.loads((ROOT / 'scripts/editor_form_baseline.json').read_text())
LAYOUTS = {
    'senders_sms': 'fragment_senders_sms', 'senders_socket': 'fragment_senders_socket',
    'action_notification': 'fragment_tasks_action_notification', 'action_frpc': 'fragment_tasks_action_frpc',
    'condition_network': 'fragment_tasks_condition_network', 'condition_cron': 'fragment_tasks_condition_cron',
}
FRAGMENTS = {'Sms': 'senders/SmsFragment.kt', 'Socket': 'senders/SocketFragment.kt', 'Notification': 'action/NotificationFragment.kt',
             'Frpc': 'action/FrpcFragment.kt', 'Network': 'condition/NetworkFragment.kt', 'Cron': 'condition/CronFragment.kt'}
TIL = 'com.google.android.material.textfield.TextInputLayout'
RADIO = 'com.google.android.material.radiobutton.MaterialRadioButton'
SWITCH = 'cn.ppps.forwarder.widget.material.TargetSwitch'
DROPDOWNS = ('cn.ppps.forwarder.widget.material.TargetDropdown', 'cn.ppps.forwarder.widget.material.TargetEditDropdown')
END = '</androidx.core.widget.NestedScrollView>'
# Pins replaced by behavioural assertions (findings F01-F03). Widening this set needs an explicit change here.
SUPERSEDED = {'unchanged_sha256': {
    'app/src/main/kotlin/cn/ppps/forwarder/utils/ItemRows.kt',
    'app/src/main/kotlin/cn/ppps/forwarder/workers/ActionWorker.kt',
    'app/src/main/kotlin/cn/ppps/forwarder/utils/sender/SmsUtils.kt',
}}


def path(n):
    return RES / 'layout' / f'{LAYOUTS[n]}.xml'


def tree(n):
    return ET.parse(path(n)).getroot()


def styles():
    out = {}
    for p in (RES / 'values').glob('*.xml'):
        for s in ET.parse(p).getroot().iter('style'):
            out[s.get('name')] = (s.get('parent'), {i.get('name'): (i.text or '').strip() for i in s.iter('item')})
    return out


STYLES = styles()


def resolved(style_ref, name):
    s = style_ref.split('/', 1)[1] if style_ref and style_ref.startswith('@style/') else style_ref
    for _ in range(20):
        if not s or s not in STYLES:
            return None
        parent, items = STYLES[s]
        if name in items:
            return items[name]
        s = parent.split('/', 1)[-1] if parent else (s.rsplit('.', 1)[0] if '.' in s else None)
    return None


def attr(e, a):
    v = e.get(A + a)
    return v if v is not None else resolved(e.get('style'), 'android:' + a)


def strings(locale):
    return {e.get('name'): ''.join(e.itertext()) for p in (RES / locale).glob('*.xml') for e in ET.parse(p).getroot() if e.tag == 'string'}


def body(root):
    return next(e for e in root.iter() if e.tag == 'androidx.core.widget.NestedScrollView')


class EditorFormTests(unittest.TestCase):
    maxDiff = 3000
    longMessage = False

    def test_ids_and_view_classes_unchanged(self):
        for n in LAYOUTS:
            ids = {e.get(A + 'id').split('/')[-1]: e.tag for e in tree(n).iter() if e.get(A + 'id')}
            for i, tag in BASE['layouts'][n]['ids'].items():
                self.assertIn(i, ids, f'{n}: id {i} removed')
                self.assertEqual(tag, ids[i], f'{n}: {i} changed class')
            extra = set(ids) - set(BASE['layouts'][n]['ids'])
            self.assertTrue(all(i.startswith('editor_form_') for i in extra), f'{n}: un-namespaced new ids {extra}')

    def test_defaults_and_initial_visibility_unchanged(self):
        for n in LAYOUTS:
            root = tree(n)
            checked = sorted(e.get(A + 'id').split('/')[-1] for e in root.iter() if e.get(A + 'checked') == 'true')
            self.assertEqual(BASE['layouts'][n]['checked'], checked, n)
            gone = sorted(e.get(A + 'id').split('/')[-1] for e in root.iter() if e.get(A + 'visibility') == 'gone' and e.get(A + 'id'))
            self.assertEqual(BASE['layouts'][n]['visibility_gone'], [g for g in gone if not g.startswith('editor_form_')], n)

    def test_footer_byte_identical(self):
        def footer(n):
            text = path(n).read_text()
            return hashlib.sha256(text[text.index(END) + len(END):].encode()).hexdigest()
        failures = pins.pin_mismatches({n: BASE['layouts'][n]['footer_sha256'] for n in LAYOUTS}, footer)
        self.assertEqual([], failures, 'footer changed: ' + '; '.join(failures))

    def test_one_keyline_no_side_labels_and_material_type(self):
        banned = ('textStyle', 'textSize', 'layout_marginStart', 'layout_marginEnd', 'paddingStart', 'paddingEnd', 'layout_margin', 'padding')
        for n in LAYOUTS:
            scroll = body(tree(n))
            self.assertEqual('@style/Form.Content', list(scroll)[0].get('style'), n)
            parents = {c: p for p in scroll.iter() for c in p}
            for e in scroll.iter():
                for a in banned:
                    if a == 'layout_marginStart' and e.get(A + 'id') == '@+id/btn_silent_period':
                        continue  # trailing button gap after the time text (same as the rules editor), not a keyline
                    self.assertIsNone(e.get(A + a), f'{n}: {e.get(A + "id") or e.tag} sets {a}')
                self.assertNotRegex(e.get('style') or '', r'BarStyle|rg_style|rg_rb_style|SwitchButtonStyle|Widget\.Material3\.TextInputLayout', n)
                for v in e.attrib.values():
                    self.assertNotIn('text_size_', v, n)
                if e.tag == 'TextView':
                    self.assertTrue(e.get('style') or (e.get(A + 'textAppearance') or '').startswith('@style/TextAppearance.Material3.'), f'{n}: unstyled TextView {e.get(A + "id") or e.get(A + "text")}')
                # No label beside a field or dropdown: their parent is always a vertical stack.
                if e.tag == TIL or e.tag in DROPDOWNS:
                    self.assertIn(e.get('style'), ('@style/Form.Field', '@style/Form.Field.Next', '@style/Form.Dropdown', '@style/Form.Dropdown.Next'), f'{n}: {e.tag}')
                    self.assertEqual('match_parent', attr(e, 'layout_width'), f'{n}: field not full width')
                    self.assertNotEqual('horizontal', attr(parents[e], 'orientation'), f'{n}: field sits in a horizontal row')
                    self.assertTrue(e.get(A + 'hint') or e.get(APP + 'es_hint'), f'{n}: field has no label inside the outline')
                if e.tag in DROPDOWNS:
                    self.assertEqual('@style/ThemeOverlay.Material3.AutoCompleteTextView.OutlinedBox', e.get(A + 'theme'), n)

    def test_options_and_switches_are_stacked_full_width_48dp_rows(self):
        counts = {}
        for n in LAYOUTS:
            root = tree(n)
            for g in root.iter('RadioGroup'):
                self.assertEqual('vertical', attr(g, 'orientation'), f'{n}: {g.get(A + "id")}')
            for e in root.iter(RADIO):
                counts[n] = counts.get(n, 0) + 1
                self.assertEqual('@style/Form.Option', e.get('style'), f'{n}: {e.get(A + "id")}')
                self.assertEqual('match_parent', attr(e, 'layout_width'))
                self.assertEqual('48dp', attr(e, 'minHeight'))
                self.assertIsNone(attr(e, 'layout_weight'), f'{n}: weighted option')
            for e in root.iter(SWITCH):
                sid = e.get(A + 'id')
                self.assertEqual('@style/Form.SwitchRow', e.get('style'), f'{n}: {sid}')
                self.assertTrue((e.get(A + 'text') or '').startswith('@string/'), f'{n}: {sid} label is not the switch text (tap would miss)')
                self.assertEqual('match_parent', attr(e, 'layout_width'))
                self.assertEqual('48dp', attr(e, 'minHeight'))
        self.assertEqual({'senders_sms': 3, 'senders_socket': 8, 'action_notification': 3, 'action_frpc': 2, 'condition_network': 8, 'condition_cron': 35}, counts)

    def test_notify_regex_rows_keep_two_fields(self):
        root = tree('action_notification')
        e = next(x for x in root.iter() if x.get(A + 'id') == '@+id/et_regex_replace')
        parents = {c: p for p in root.iter() for c in p}
        self.assertNotEqual(TIL, parents[e].tag, 'backing field must stay outside an outline (ItemRows anchors on it)')
        src = (KT / 'fragment/action/NotificationFragment.kt').read_text()
        self.assertIn('if (v.id in listOf(R.id.btn_save, R.id.btn_test) && !cn.ppps.forwarder.utils.ItemRows.validateAll(binding!!.root)) return', src)
        self.assertNotIn('v.id != R.id.btn_del && !cn.ppps.forwarder.utils.ItemRows.validateAll', src, 'unfinished row blocks the quiet-hours picker')
        rows = (KT / 'utils/ItemRows.kt').read_text()
        self.assertIn('private val regexRows = source.id == R.id.et_regex_replace', rows)
        self.assertIn('R.string.regex_replace_label', rows)
        # Quiet-hours picker hides the keyboard before opening; the template preview uses a task sample (F07).
        self.assertEqual([], pins.notification_problems())

    def test_item_rows_contract(self):
        """Replaces the ItemRows whole-file pin (F01): list kinds, Add/row labels, blur validation, 48dp remove."""
        self.assertEqual([], pins.item_rows_problems())

    def test_runtime_contracts_kept(self):
        """Replaces the ActionWorker/SmsUtils pins (F02, F03): SMS dispatch and SIM selection kept, no SIM dump."""
        worker = (KT / 'workers/ActionWorker.kt').read_text()
        sms = (KT / 'utils/sender/SmsUtils.kt').read_text()
        problems = []
        send = pins.kotlin_block(worker, 'TASK_ACTION_SENDSMS ->')
        for need in ('App.SimInfoList = PhoneUtils.getSimMultiInfo()', 'Log.d(TAG, "SIM inventory refreshed; details omitted")',
                     'val simSlotIndex = smsSetting.simSlot - 1', 'App.SimInfoList[simSlotIndex]?.mSubscriptionId ?: -1',
                     'Manifest.permission.SEND_SMS', 'PhoneUtils.sendSms(mSubscriptionId, mobileList, message)'):
            if need not in send:
                problems.append(f'ActionWorker SMS action lacks {need}')
        for need in ('TASK_ACTION_FRPC ->', 'TASK_ACTION_NOTIFICATION ->'):
            if need not in worker:
                problems.append(f'ActionWorker lacks {need}')
        for need in ('val simSlotIndex = if (setting.simSlot == 0) msgInfo.simSlot else setting.simSlot - 1',
                     'App.SimInfoList = PhoneUtils.getSimMultiInfo()', 'Log.d(TAG, "SIM inventory refreshed; details omitted")',
                     'val mobiles = setting.mobiles.replace(getString(R.string.tag_from), msgInfo.from)',
                     'App.SimInfoList[simSlotIndex]?.mSubscriptionId ?: -1', 'PhoneUtils.sendSms(mSubscriptionId, mobiles, content)'):
            if need not in sms:
                problems.append(f'SmsUtils lacks {need}')
        for rel in ('workers/ActionWorker.kt', 'utils/sender/SmsUtils.kt'):
            for n, code in check_security.active_lines(KT / rel):
                for pattern, scope, why in check_security.FORBIDDEN_LOGS:
                    if (not scope or scope in rel) and re.search(pattern, code):
                        problems.append(f'{rel}:{n}: {why}: {code.strip()}')
                if re.search(r'(Log\.\w+|logg)\(.*SimInfoList', code):
                    problems.append(f'{rel}:{n}: logs the SIM inventory: {code.strip()}')
        self.assertEqual([], problems)

    def test_sms_name_error_inline_and_rows_backing_field(self):
        src = (KT / 'fragment/senders/SmsFragment.kt').read_text()
        self.assertIn('FormValidation.show(binding!!.etName, getString(R.string.invalid_name))', src)
        self.assertNotIn('throw Exception(getString(R.string.invalid_name))', src)
        root = tree('senders_sms')
        seq = [e.get(A + 'id', '').split('/')[-1] for e in root.iter() if e.get(A + 'id')]
        self.assertLess(seq.index('et_mobiles'), seq.index('bt_insert_sender'))
        self.assertLess(seq.index('bt_insert_sender'), seq.index('bt_pick_contact'))
        for bid in ('bt_insert_sender', 'bt_pick_contact'):
            e = next(x for x in root.iter() if x.get(A + 'id') == '@+id/' + bid)
            self.assertEqual('@style/Form.Action', e.get('style'), bid)

    def test_frpc_unsupported_controls_do_not_pretend(self):
        src = (KT / 'fragment/action/FrpcFragment.kt').read_text()
        self.assertIn('cn.ppps.forwarder.App.FrpclibInited', src)
        self.assertIn('editorFormFrpcUnavailable.visibility', src)
        self.assertRegex(src, r'listOf\(b\.rbStartServer, b\.rbStopServer, b\.spFrpc, b\.btnSave, b\.btnTest\)\.forEach \{ it\.isEnabled = false \}')
        self.assertIn('if (!tunnelAvailable && v.id != R.id.btn_del) return', src)
        self.assertIn('frpcListSelected.addAll(settingVo.frpcList.orEmpty())', src)
        # The action type, its stored shape and the worker are kept (no feature or data removed).
        self.assertIn('TASK_ACTION_FRPC', (KT / 'fragment/TasksEditFragment.kt').read_text() + (KT / 'workers/ActionWorker.kt').read_text())
        self.assertIn('var frpcList: List<Frpc>', (KT / 'entity/action/FrpcSetting.kt').read_text())
        root = tree('action_frpc')
        notice = next(x for x in root.iter() if x.get(A + 'id') == '@+id/editor_form_frpc_unavailable')
        self.assertEqual('@string/editor_form_frpc_unavailable', notice.get(A + 'text'))

    def test_cron_inline_check_uses_runner_parser(self):
        src = (KT / 'fragment/condition/CronFragment.kt').read_text()
        check = (KT / 'utils/task/CronScheduleCheck.kt').read_text()
        self.assertIn('import gatewayapps.crondroid.CronExpression', check)
        self.assertIn('CronExpression.validateExpression(expression)', check)
        self.assertIn('cron.getNextValidTimeAfter(time)', check)
        self.assertIn('CronScheduleCheck.nextRuns(expression, Date(), 10)', src)
        self.assertIn('box?.error = message', src)
        self.assertIn('if (!refreshCheck(focusFirstProblem = true)) return', src)
        self.assertNotIn('XToastUtils.error(getString(R.string.invalid_cron_expression', src, 'invalid field still only a toast')
        # Field values are no longer silently reset to * after an invalid entry.
        self.assertNotRegex(src, r'catch \(e: Exception\) \{\n\s+(second|minute|hour|day|month|week|year) = "\*"')
        # The stored expression format and the runner are untouched.
        self.assertIn('expression = "$second $minute $hour $day $month $week $year"', src)
        self.assertIn('return CronSetting(description, expression)', src)
        self.assertNotRegex(src, r'OkHttp|XHttp|URL\(|WorkManager|enqueue', 'local test only')

    def test_fragment_changes_are_additive_only(self):
        failures = []
        for c, hashes in BASE['kotlin_line_sha1'].items():
            lines = [hashlib.sha1(l.encode()).hexdigest()[:12] for l in (KT / 'fragment' / FRAGMENTS[c]).read_text().split('\n')]
            removed = [h for h in hashes if h not in set(lines)]
            allowed = {'Sms': 1, 'Socket': 0, 'Notification': 1, 'Frpc': 4, 'Network': 0, 'Cron': 31}[c]
            if len(set(removed)) > allowed:
                failures.append(f'{c}: {len(set(removed))} original lines changed or removed (allowed {allowed})')
        self.assertEqual([], failures)

    def test_shared_and_excluded_files_untouched(self):
        skip = pins.superseded(BASE, 'unchanged_sha256')
        self.assertEqual([], pins.pin_mismatches(BASE['unchanged_sha256'], lambda p: pins.sha256_bytes(ROOT / p), skip))

    def test_superseded_pins_are_documented(self):
        pinned = BASE['unchanged_sha256']
        self.assertEqual([], pins.supersession_problems(
            BASE, __file__, SUPERSEDED, {'unchanged_sha256': pinned.get}, {'unchanged_sha256': lambda p: pins.sha256_bytes(ROOT / p)}))

    def test_new_resources_namespaced_translated_and_short(self):
        locales = {l: strings(l) for l in ('values', 'values-en', 'values-nl')}
        new = {k for k in locales['values'] if k.startswith('editor_form_')}
        self.assertEqual(27, len(new))
        for l in ('values-en', 'values-nl'):
            self.assertEqual(new, {k for k in locales[l] if k.startswith('editor_form_')}, l)
        for k in new:
            self.assertEqual(locales['values'][k], locales['values-en'][k], k)
            self.assertNotEqual(locales['values'][k], locales['values-nl'][k], k)
            self.assertLessEqual(len(locales['values'][k]), 100, k)
            self.assertLessEqual(len(locales['values-nl'][k]), 100, k)
        for s in ET.parse(RES / 'values/styles_editor_form.xml').getroot().iter('style'):
            self.assertTrue(s.get('name') == 'EditorForm' or s.get('name').startswith('EditorForm.'), s.get('name'))
        for n in LAYOUTS:
            for ref in re.findall(r'@string/([A-Za-z0-9_]+)', path(n).read_text()):
                for l, table in locales.items():
                    self.assertIn(ref, table, f'{n}: {l} lacks {ref}')
        for c, rel in FRAGMENTS.items():
            for ref in re.findall(r'R\.string\.(editor_form_[a-z0-9_]+)', (KT / 'fragment' / rel).read_text()):
                self.assertIn(ref, locales['values'], ref)

    def test_no_new_requests_or_permissions(self):
        risky = re.compile(r'startService|registerReceiver|XXPermissions|SmsManager|OkHttp|XHttp|URL\(|Socket\(|enqueue|sendMsg')
        for c, hashes in BASE['kotlin_line_sha1'].items():
            old = set(hashes)
            for line in (KT / 'fragment' / FRAGMENTS[c]).read_text().split('\n'):
                if hashlib.sha1(line.encode()).hexdigest()[:12] not in old:
                    self.assertIsNone(risky.search(line), f'{c}: {line}')


if __name__ == '__main__':
    unittest.main(verbosity=2)
