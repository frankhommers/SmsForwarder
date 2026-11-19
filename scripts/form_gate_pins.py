#!/usr/bin/env python3
"""Shared pin/supersession machinery and shared-file contracts for the three form gates.

Used by test_editor_form.py, test_condition_form.py and test_action_form.py (not a gate by itself).

The form gates pin whole files, layout footers and ordered fragment lines by hash. Those milestone pins proved
that one repair lane left a file alone. When a later, intended change makes such a pin obsolete, the gate's
baseline JSON lists it under "superseded_pins" instead of refreshing or dropping the hash:

- the original pin stays where it was, unchanged; the entry repeats it (old_sha256 / old_line_sha1) and the
  meta-test checks both are identical, so the original value is preserved and cannot drift;
- each entry names its finding, why the pin is obsolete, the replacement behavioural assertion(s) (gate test
  methods that read the current source) and the evidence (test sources). Evidence files existing is never
  counted as a replacement: every entry needs at least one replacement assertion in its own gate script;
- the set of superseded keys is also fixed in each gate script, so widening it needs an explicit test change;
- an entry whose file again matches the old hash is reported as stale (restore the strict pin instead);
- every pin that is not listed stays strict, and every pin loop reports all mismatches, not only the first.
"""
from pathlib import Path
import ast
import hashlib
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
KT = ROOT / 'app/src/main/kotlin/cn/ppps/forwarder'
RES = ROOT / 'app/src/main/res'
EVIDENCE_ROOTS = ('app/src/test/', 'app/src/androidTest/', 'scripts/')
COMMON_FIELDS = ('finding', 'superseded_by', 'reason', 'replacement_assertions', 'evidence')
LOCALES = ('values', 'values-en', 'values-nl')


# --- hashing and pin loops -----------------------------------------------------------------------------------
def sha256_bytes(path):
    path = Path(path)
    return hashlib.sha256(path.read_bytes()).hexdigest() if path.exists() else None


def sha256_text(text):
    return hashlib.sha256(text.encode()).hexdigest()


def line_sha1(text):
    return [hashlib.sha1(l.encode()).hexdigest()[:12] for l in text.split('\n')]


def superseded(base, section):
    return base.get('superseded_pins', {}).get(section, {})


def pin_mismatches(pins, observe, skip=()):
    """All pins whose observed value differs. pins: {key: expected}; observe(key) -> value. Never short-circuits."""
    out = []
    for key, expected in pins.items():
        if key in skip:
            continue
        observed = observe(key)
        if observed != expected:
            out.append(f'{key}: pinned {expected}, found {observed}')
    return out


def ordered_missing(base_hashes, new_hashes, skip_indices=()):
    """Baseline indices not matched as an ordered subsequence of new_hashes (greedy, like the original check)."""
    missing, j = [], 0
    for i, h in enumerate(base_hashes):
        if i in skip_indices:
            continue
        k = j
        while k < len(new_hashes) and new_hashes[k] != h:
            k += 1
        if k == len(new_hashes):
            missing.append(i)
        else:
            j = k + 1
    return missing


# --- supersession meta-check ---------------------------------------------------------------------------------
def _methods(script):
    tree = ast.parse(script.read_text())
    return {f'{c.name}.{f.name}' for c in tree.body if isinstance(c, ast.ClassDef)
            for f in c.body if isinstance(f, ast.FunctionDef) and f.name.startswith('test_')}


def supersession_problems(base, gate_script, expected, original, current):
    """Every problem with the superseded_pins section of one gate baseline.

    expected: {section: {key, ...}} for hash pins, or {section: {key: {index: line_sha1}}} for kotlin_line_sha1.
    original: {section: fn(key)} -> the pin value still recorded in the baseline (list of line hashes for lines).
    current: {section: fn(key)} -> the current value (list of line hashes for lines).
    """
    problems = []
    gate_rel = str(Path(gate_script).resolve().relative_to(ROOT))
    sup = base.get('superseded_pins')
    if not isinstance(sup, dict):
        return ['baseline has no superseded_pins section']
    sections = {s for s in sup if not s.startswith('_')}
    if sections != set(expected):
        problems.append(f'superseded sections {sorted(sections)} != expected {sorted(expected)}')
    cache = {}
    for section in sorted(sections & set(expected)):
        entries = sup[section]
        if set(entries) != set(expected[section]):
            problems.append(f'{section}: superseded keys {sorted(entries)} != expected {sorted(expected[section])}')
        for key, e in entries.items():
            tag = f'{section}/{key}'
            lines = section == 'kotlin_line_sha1'
            for f in COMMON_FIELDS + (('old_line_sha1',) if lines else ('old_sha256',)):
                if not e.get(f):
                    problems.append(f'{tag}: missing or empty {f}')
            if lines:
                old = {int(i): h for i, h in e.get('old_line_sha1', {}).items()}
                if key in expected[section] and old != expected[section][key]:
                    problems.append(f'{tag}: superseded lines {old} != expected {expected[section][key]}')
                base_lines = original[section](key) or []
                for i, h in sorted(old.items()):
                    if i >= len(base_lines) or base_lines[i] != h:
                        problems.append(f'{tag}: baseline line {i} is not {h} (original hash not preserved)')
                still = sorted(i for i, h in old.items() if h in set(current[section](key)))
                if still:
                    problems.append(f'{tag}: superseded lines {still} are back in the source; restore them as strict')
            else:
                if e.get('old_sha256') != original[section](key):
                    problems.append(f'{tag}: old_sha256 differs from the pin kept in the baseline (original not preserved)')
                if current[section](key) == e.get('old_sha256'):
                    problems.append(f'{tag}: current value matches the original again; restore the strict pin')
            refs = e.get('replacement_assertions') or []
            for ref in refs:
                m = re.fullmatch(r'(scripts/[\w.]+\.py)::(\w+\.test_\w+)', ref)
                if not m:
                    problems.append(f'{tag}: replacement assertion {ref!r} is not scripts/<gate>.py::<Class>.test_<name>')
                    continue
                script = ROOT / m.group(1)
                if not script.exists():
                    problems.append(f'{tag}: {m.group(1)} does not exist')
                    continue
                cache.setdefault(script, _methods(script))
                if m.group(2) not in cache[script]:
                    problems.append(f'{tag}: replacement assertion {ref} does not exist')
            if not any(r.startswith(gate_rel + '::') for r in refs):
                problems.append(f'{tag}: no replacement assertion in {gate_rel} itself (evidence files do not count)')
            for ev in e.get('evidence') or []:
                if not ev.startswith(EVIDENCE_ROOTS) or not (ROOT / ev).is_file():
                    problems.append(f'{tag}: evidence {ev} is not an existing file under {EVIDENCE_ROOTS}')
    return problems


# --- Kotlin/resource helpers ---------------------------------------------------------------------------------
def kotlin_block(src, marker, start=0):
    """Text of the brace block that follows marker (from its first '{'), or '' when absent."""
    i = src.find(marker, start)
    if i < 0:
        return ''
    o = src.find('{', i)
    depth = 0
    for j in range(o, len(src)):
        depth += {'{': 1, '}': -1}.get(src[j], 0)
        if depth == 0:
            return src[o:j + 1]
    return ''


def strings(locale):
    return {e.get('name'): ''.join(e.itertext()) for p in (RES / locale).glob('*.xml')
            for e in ET.parse(p).getroot() if e.tag == 'string'}


def string_problems(keys, max_len=None, english_default=False):
    tables = {l: strings(l) for l in LOCALES}
    out = []
    for k in keys:
        for l, t in tables.items():
            if not t.get(k, '').strip():
                out.append(f'{l} lacks {k}')
            elif max_len and len(t[k]) > max_len:
                out.append(f'{l}/{k} longer than {max_len}: {t[k]!r}')
        if english_default and k in tables['values'] and k in tables['values-nl']:
            if tables['values'][k] != tables['values-en'].get(k):
                out.append(f'{k}: default and English differ')
            if tables['values'][k] == tables['values-nl'][k]:
                out.append(f'{k}: Dutch not translated')
    return out


# --- contracts for shared files that were pinned by hash ---------------------------------------------------
ITEM_ROWS = 'app/src/main/kotlin/cn/ppps/forwarder/utils/ItemRows.kt'


def item_rows_problems():
    """Labels and row behaviour of ItemRows (replaces the whole-file pin: findings F01, F04, F08)."""
    src = (ROOT / ITEM_ROWS).read_text()
    need = {
        'phone-number lists': 'private val phoneRows = source.id in listOf(R.id.et_safe_phone, R.id.et_phone_numbers, R.id.et_mobiles)',
        'regex replacement list': 'private val regexRows = source.id == R.id.et_regex_replace',
        'regex rows have two fields': 'private val twoFields = pairs || regexRows',
        'regex storage format': 'if (regexRows) ItemListCodec.regexLine(key.text.toString(), value?.text?.toString().orEmpty())',
        'Add label per list (allowed sender / recipient / replacement / parameter / item)':
            'setText(if (source.id == R.id.et_safe_phone) R.string.review_add_allowed_sender else if (phoneRows) R.string.copy_add_recipient '
            'else if (regexRows) R.string.regex_add_replacement else if (pairs) R.string.add_parameter else R.string.add_item)',
        'row field labels': 'hint = context.getString(if (phoneRows) R.string.phone_number else if (regexRows) R.string.regex_find_label '
                            'else if (pairs) R.string.item_key else R.string.item_value)',
        'replacement field label': 'setHint(if (regexRows) R.string.regex_replace_label else R.string.item_value)',
        'phone keypad for phone rows': 'inputType = if (phoneRows && !raw.startsWith("{{")) android.text.InputType.TYPE_CLASS_PHONE',
        'validate key on blur only after an edit': 'key.setOnFocusChangeListener { _, focused -> if (!focused && edited) validate() }',
        'validate value on blur only after an edit': 'value?.setOnFocusChangeListener { _, focused -> if (!focused && edited) validate() }',
    }
    out = [f'ItemRows: {why} missing' for why, s in need.items() if s not in src]
    if not re.search(r'if \(phoneRows\) row\.addView\(com\.google\.android\.material\.checkbox\.MaterialCheckBox\(source\.context\)\.apply \{\s*\n'
                     r'\s*setText\(if \(source\.id == R\.id\.et_safe_phone\) R\.string\.review_text_keyboard else R\.string\.copy_phone_template\)', src):
        out.append('ItemRows: keyboard/template checkbox (phone rows only, allowed-sender label) missing')
    remove = re.search(r'contentDescription = context\.getString\(R\.string\.remove_item\)(.*?)\n\s*\}, LinearLayout\.LayoutParams\(([^\n]*)\)\)\n', src, re.S)
    dp48 = '(48 * source.resources.displayMetrics.density).toInt()'
    if not remove or remove.group(2) != f'{dp48}, {dp48}':
        out.append('ItemRows: labelled remove action is not a 48dp x 48dp target')
    elif 'fields.removeAll { it.first === key }; rows.removeView(row); sync()' not in remove.group(1):
        out.append('ItemRows: remove action no longer removes the row and syncs the backing field')
    out += [f'ItemRows strings: {p}' for p in string_problems(
        ('review_add_allowed_sender', 'review_text_keyboard'), max_len=30, english_default=True)]
    out += [f'ItemRows strings: {p}' for p in string_problems(
        ('copy_add_recipient', 'copy_phone_template', 'regex_add_replacement', 'remove_item', 'phone_number',
         'regex_find_label', 'regex_replace_label'))]
    return out


NOTIFICATION = 'app/src/main/kotlin/cn/ppps/forwarder/fragment/action/NotificationFragment.kt'


def notification_problems():
    """Quiet-hours picker hides the keyboard first; task-sample preview; rows validated only on Apply/Test (F07)."""
    src = (ROOT / NOTIFICATION).read_text()
    out = []
    branch = kotlin_block(src, 'R.id.btn_silent_period ->')
    steps = ('binding!!.root.clearFocus()', 'hideSoftInputFromWindow(binding!!.root.windowToken, 0)', 'OptionsPickerBuilder(')
    pos = [branch.find(s) for s in steps]
    if not branch or min(pos) < 0 or pos != sorted(pos):
        out.append(f'Notification: quiet-hours branch must clear focus and hide the keyboard before OptionsPickerBuilder ({pos})')
    if 'binding!!.tvSilentPeriod.text = txt' not in branch:
        out.append('Notification: quiet-hours picker no longer writes the chosen period')
    grid = 'CommonUtils.createTagButtons(requireContext(), binding!!.glSmsTemplate, binding!!.etSmsTemplate, ruleType)'
    sample = 'cn.ppps.forwarder.widget.material.TemplatePicker.useTaskSample(binding!!.glSmsTemplate)'
    if grid not in src or sample not in src or src.index(sample) < src.index(grid):
        out.append('Notification: template grid must be created and then marked for the task sample')
    if 'if (v.id in listOf(R.id.btn_save, R.id.btn_test) && !cn.ppps.forwarder.utils.ItemRows.validateAll(binding!!.root)) return' not in src:
        out.append('Notification: rows must be validated only on Apply/Test')
    picker = (KT / 'widget/material/TemplatePicker.kt').read_text()
    if 'fun useTaskSample(grid: View) = grid.setTag(R.id.template_sample_context, SampleContext.TASK)' not in picker:
        out.append('TemplatePicker.useTaskSample no longer tags the grid with the task sample')
    return out
