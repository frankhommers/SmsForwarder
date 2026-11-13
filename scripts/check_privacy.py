#!/usr/bin/env python3
"""Offline privacy regression gates. Optional APK scan does not replace traffic capture."""
from pathlib import Path
import argparse
import re
import subprocess
import unittest
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'app/src/main'
CODE = MAIN / 'kotlin/cn/ppps/forwarder'
DISALLOWED = (b'com.umeng', b'com/umeng', b'MobclickAgent', b'UMConfigure',
              b'xupdate.ppps.cn', b'cx.shouji.360.cn', b'api.ipify.org',
              b'api6.ipify.org', b'libumeng-spy', b'com/xuexiang/xupdate')


def forbidden(data):
    return [x.decode() for x in DISALLOWED if x.lower() in data.lower()]


class PrivacySourceTests(unittest.TestCase):
    def test_no_analytics_or_upstream_probe_code(self):
        paths = list((MAIN / 'kotlin').rglob('*.kt')) + list(ROOT.glob('*.gradle'))
        paths += list((ROOT / 'app').glob('*.gradle'))
        for path in paths:
            if not path.is_file():
                continue
            self.assertEqual([], forbidden(path.read_bytes()), str(path))

    def test_no_native_loader_or_downloader(self):
        app = (CODE / 'App.kt').read_text()
        main = (CODE / 'activity/MainActivity.kt').read_text()
        self.assertIn('val FrpclibInited = false', app)
        self.assertNotIn('installNativeLibraryPath', app)
        self.assertNotIn('downLoad(', main)
        self.assertNotIn('downloadFrpcLib', main)

    def test_local_tips_and_privacy(self):
        common = (CODE / 'utils/CommonUtils.kt').read_text()
        tips = (CODE / 'widget/GuideTipsDialog.kt').read_text()
        self.assertIn('context.assets.open(privacyUrl)', common)
        self.assertIn('context.assets.open("tips.json")', tips)
        self.assertNotIn('XHttp', tips)
        markdown = (CODE / 'fragment/MarkdownFragment.kt').read_text()
        self.assertIn('settings.blockNetworkLoads = true', markdown)
        self.assertNotIn('loadMarkdownFromUrl', markdown)
        self.assertNotRegex((MAIN / 'assets/tips.json').read_text(), r'https?://|<img')

    def test_phone_and_geocoding_offline(self):
        phone = (CODE / 'utils/PhoneUtils.kt').read_text()
        self.assertRegex(phone, r'fun getPhoneArea\(phoneNumber: String\): String = getString\(R.string.unknown_area\)')
        self.assertNotIn('getFromLocation', (CODE / 'service/LocationService.kt').read_text())

    def test_approved_ip_endpoint_and_bounded_request(self):
        ip = (CODE / 'utils/PublicIpLookup.kt').read_text()
        self.assertIn('"https://ip.hommers.it/"', ip)
        self.assertIn('instanceFollowRedirects = false', ip)
        self.assertIn('connectTimeout = 5000', ip)
        self.assertIn('readTimeout = 5000', ip)
        worker = (CODE / 'workers/NetworkWorker.kt').read_text()
        self.assertEqual(1, worker.count('val publicIP = getPublicIP()'))

    def test_english_defaults_and_supported_locales(self):
        def strings(locale):
            return {e.attrib['name']: ''.join(e.itertext()) for e in ET.parse(MAIN / f'res/{locale}/strings.xml').getroot() if e.tag == 'string'}
        default, english = strings('values'), strings('values-en')
        self.assertEqual([], [k for k in english if default.get(k) != english[k]])
        untranslated = {k: v for k, v in default.items() if re.search('[\u2e80-\u9fff\uf900-\ufaff\U00020000-\U000323af]', v)}
        self.assertEqual({}, untranslated)
        self.assertEqual([], list((MAIN / 'res').glob('values-zh*')))
        self.assertEqual(set(default), set(strings('values-nl')))
        app = (CODE / 'App.kt').read_text()
        self.assertIn('MultiLanguages.setAppLanguage(this, Locale.ENGLISH)', app)
        self.assertIn('language_initialized', app)
        self.assertLess(app.index('MultiLanguages.setAppLanguage(this, Locale.ENGLISH)'), app.index('Core.init(this)'))
        self.assertIn('MultiLanguages.clearAppLanguage', (CODE / 'fragment/SettingsFragment.kt').read_text())

    def test_current_source_languages(self):
        from check_languages import source_gate
        self.assertEqual([], source_gate()['failures'])

    def test_distinct_application_id(self):
        self.assertIn('applicationId "nl.hommers.smsforwarder"', (ROOT / 'app/build.gradle').read_text())
        self.assertIn('const val PACKAGE_NAME = cn.ppps.forwarder.BuildConfig.APPLICATION_ID', (CODE / 'utils/Constants.kt').read_text())

    def test_license_unchanged(self):
        expected = subprocess.check_output(['git', 'show', 'HEAD:LICENSE'], cwd=ROOT)
        self.assertEqual(expected, (ROOT / 'LICENSE').read_bytes())

    def test_scan_detects_regressions(self):
        for marker in DISALLOWED:
            self.assertTrue(forbidden(b'prefix ' + marker + b' suffix'))


def scan_apk(path):
    failures = []
    with zipfile.ZipFile(path) as apk:
        for name in apk.namelist():
            if 'libgojni.so' in name or 'umeng' in name.lower():
                failures.append(name)
            if name.endswith(('.dex', '.so', '.xml', '.html', '.js', '.css')):
                markers = forbidden(apk.read(name))
                if markers:
                    failures.append(f'{name}: {markers}')
    if failures:
        raise SystemExit('APK PRIVACY FAIL: ' + '; '.join(failures))
    print(f'APK privacy markers/native-library gate PASS: {path}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--apk', type=Path)
    args = parser.parse_args()
    result = unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(PrivacySourceTests))
    if not result.wasSuccessful():
        raise SystemExit(1)
    if args.apk:
        scan_apk(args.apk)
