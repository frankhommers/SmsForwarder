#!/usr/bin/env python3
"""Language gates. APK inspection requires androguard; --strict rejects dependency prose too.
No binary rewriting and no CJK exceptions in first-party source are permitted.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
CJK = re.compile('[\u2e80-\u9fff\uf900-\ufaff\U00020000-\U000323af]')
ESCAPE = re.compile(r'\\u([0-9a-fA-F]{4})|\\U([0-9a-fA-F]{8})')
FORMAT = re.compile(r'%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%]')
TOKENS = re.compile(r'\{\{[^}]+\}\}|\[(?:msg|title|from|timestamp)\]')


def has_cjk(text):
    return bool(CJK.search(text) or any(CJK.search(chr(int(m.group(1) or m.group(2), 16))) for m in ESCAPE.finditer(text)))


def is_functional_unicode(value, owners):
    # Exact functional data plus its attributed owners, never a package-wide exemption.
    allowed = json.loads((ROOT / 'scripts/functional_unicode_allowlist.json').read_text())
    entry = allowed.get(hashlib.sha256(value.encode('utf-8')).hexdigest())
    return bool(entry and owners and set(owners) <= set(entry['references']))


def resources(locale):
    result = {}
    for path in (ROOT / 'app/src/main/res' / locale).glob('*.xml'):
        for element in ET.parse(path).getroot():
            if element.tag == 'string':
                key = element.attrib['name']
                assert key not in result, f'Duplicate resource: {locale}/{key}'
                result[key] = ''.join(element.itertext())
    return result


def source_gate():
    failures = []
    names = subprocess.check_output(['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard'], cwd=ROOT).decode().split('\0')
    checked = 0
    for name in sorted(set(names)):
        path = ROOT / name
        if not path.is_file():
            continue
        try:
            text = path.read_text(encoding='utf-8')
        except UnicodeError:
            continue
        checked += 1
        for line, text in enumerate(text.splitlines(), 1):
            if CJK.search(text) or (path.suffix in {'.kt', '.java', '.xml'} and has_cjk(text)):
                failures.append(f'{name}:{line}: CJK text or escaped CJK')
    base, en, nl = (resources(locale) for locale in ('values', 'values-en', 'values-nl'))
    for locale, translated in [('en', en), ('nl', nl)]:
        if set(base) != set(translated):
            failures.append(f'{locale}: resource key mismatch: {sorted(set(base) ^ set(translated))}')
        for key in base.keys() & translated.keys():
            for label, pattern in [('format', FORMAT), ('template', TOKENS)]:
                if Counter(pattern.findall(base[key])) != Counter(pattern.findall(translated[key])):
                    failures.append(f'{locale}/{key}: {label} placeholder mismatch')
            if (key.isupper() or (key.endswith('_regex') and key != 'btn_regex') or key == 'regex_multi_match' or (key.startswith('url_') and key != 'url_scheme_tips') or key == 'other_mail_type') and base[key] != translated[key]:
                failures.append(f'{locale}/{key}: protocol identifier changed')
    if base != en:
        failures.append('Default resource strings must equal English')
    if list((ROOT / 'app/src/main/res').glob('values-zh*')):
        failures.append('Chinese locale directory present')
    # Negative fixtures are constructed numerically, not hidden application prose.
    assert has_cjk(chr(0x4E00)) and has_cjk('\\u' + '4e00')
    return {'text_files_checked': checked, 'resource_keys': len(base), 'failures': failures}


def apk_gate(path):
    from loguru import logger
    logger.remove()
    from androguard.core.apk import APK
    from androguard.core.axml import AXMLPrinter
    from androguard.core.dex import DEX
    apk = APK(str(path))
    arsc = apk.get_android_resources()
    locales = arsc.get_locales(apk.get_package())
    resource_hits = []
    for locale in locales:
        # Include every resource string pool entry below as well, including arrays/layout literals.
        for line in arsc.get_string_resources(apk.get_package(), locale).decode().splitlines():
            if has_cjk(line):
                resource_hits.append({'locale': locale, 'xml': line})
    pool_hits = [arsc.stringpool_main.getString(i) for i in range(arsc.stringpool_main.stringCount)
                 if has_cjk(arsc.stringpool_main.getString(i))]
    dex_hits = []
    for name in apk.get_dex_names():
        dex = DEX(apk.get_file(name))
        references = {}
        for cls in dex.get_classes():
            for field in cls.get_fields():
                initial = field.get_init_value()
                value = initial.get_value() if initial else None
                if isinstance(value, str) and has_cjk(value):
                    references.setdefault(value, set()).add(cls.get_name() + '->' + field.get_name())
            for method in cls.get_methods():
                if not method.get_code():
                    continue
                for instruction in method.get_instructions():
                    if instruction.get_name().startswith('const-string'):
                        value = instruction.get_string()
                        if has_cjk(value):
                            references.setdefault(value, set()).add(cls.get_name() + '->' + method.get_name())
        for value in dex.get_strings():
            if has_cjk(value):
                owners = sorted(references.get(value, []))
                functional = is_functional_unicode(value, owners)
                dex_hits.append({'dex': name, 'text': value, 'references': owners,
                                 'classification': 'functional_unicode_regex' if functional else 'third_party_text_or_unattributed'})
    asset_hits = []
    for name in apk.get_files():
        if name.endswith(('.dex', '.arsc', '.so', '.png', '.jpg', '.webp', '.ttf')):
            continue
        data = apk.get_file(name)
        if name.endswith('.xml') and data[:2] == b'\x03\x00':
            data = AXMLPrinter(data).get_xml()
        try:
            text = data.decode('utf-8')
        except UnicodeError:
            continue
        if has_cjk(text):
            asset_hits.append({'path': name, 'cjk_lines': [l for l in text.splitlines() if has_cjk(l)]})
    own = [h for h in dex_hits if any(o.startswith('Lcn/ppps/forwarder/') for o in h['references'])]
    prose = [h for h in dex_hits if h['classification'] != 'functional_unicode_regex']
    failures = []
    if set(locales) - {'\0\0', 'en', 'nl'} or 'nl' not in locales:
        failures.append('APK locale filtering failed')
    if resource_hits or pool_hits:
        failures.append('APK resource pool contains CJK')
    if own:
        failures.append('First-party DEX contains CJK')
    if prose:
        failures.append('Dependency DEX contains CJK natural-language text')
    if asset_hits:
        failures.append('APK text assets contain CJK')
    return {'sha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'package': apk.get_package(),
            'locales': locales, 'resource_hits': resource_hits, 'resource_pool_hits': pool_hits,
            'dex_hits': dex_hits, 'asset_hits': asset_hits, 'first_party_dex_hits': len(own),
            'dependency_text_strings': len(prose), 'functional_unicode_strings': len(dex_hits)-len(prose),
            'failures': failures, 'strict_zero_chinese_text': not (failures or prose)}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path)
    parser.add_argument('--report', type=Path)
    parser.add_argument('--strict', action='store_true')
    args = parser.parse_args()
    report = {'source': source_gate()}
    if args.apk:
        report['apk'] = apk_gate(args.apk)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print('Source:', json.dumps(report['source']))
    if args.apk:
        print('APK:', json.dumps({k: v for k, v in report['apk'].items() if k not in ('dex_hits', 'asset_hits', 'resource_hits', 'resource_pool_hits')}))
        print('Full CJK inventory:', args.report or 'pass --report to retain the inventory')
    failed = bool(report['source']['failures']) or bool(report.get('apk', {}).get('failures'))
    if args.strict and args.apk and not report['apk']['strict_zero_chinese_text']:
        failed = True
        print('STRICT FAIL: dependency Chinese text remains; not just charset data. No zero-Chinese APK claim.')
    raise SystemExit(1 if failed else 0)
