#!/usr/bin/env python3
"""Offline static regression gates for TLS validation and sensitive logging in first-party sources.

Complements (does not replace) scripts/check_privacy.py and the JVM test TransportSecurityTest.
Source scanning is a regression guard, not proof that no sensitive data reaches logs.
"""
from pathlib import Path
import re
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
CODE = ROOT / 'app/src/main/kotlin/cn/ppps/forwarder'
SOURCES = sorted(CODE.rglob('*.kt'))

# Single-line Log.*/logg calls whose arguments expose credentials, token-bearing URLs, message/rule/target JSON,
# request bodies or provider responses. Each pattern is matched against active (non-comment) code only.
FORBIDDEN_LOGS = [
    (r'Log\.\w+\(.*\$\{?(serverHistory|simInfo|locationOld|locationNew|locationSetting)\b', '', 'client credentials, SIM identity or location condition data'),
    (r'Log\.\w+\(.*\$\{?(smsCommand|cmdList|safePhone|from)\b', '', 'raw SMS command or sender/allowlist'),
    (r'Log\.\w+\(.*App\.SimInfoList', '', 'full SIM inventory'),
    (r'Log\.\w+\(.*\$\{?(number|phoneNumber|mobile|longitude|latitude|location|locationInfoOld|locationInfoNew|locationJsonOld|locationJsonNew|selectionArgs|forwardedValue|distanceOld|distanceNew)\b', '', 'personal phone/location/query/task data'),
    (r'Log\.\w+\(.*\b(simInfo|infoList|bean\.data)\.toString\(\)', '', 'SIM or personal entity dump'),
    (r'Log\.\w+\(.*\$\{?requestUrl', 'utils/sender/', 'target request URL (tokens/keys in path or query)'),
    (r'Log\.\w+\(.*\$\{?getTokenUrl', '', 'token URL with corpsecret'),
    (r'Log\.\w+\(.*proxyPassword', '', 'proxy password'),
    (r'Log\.\w+\(.*\$\{?requestMsg', '', 'request body'),
    (r'Log\.\w+\(TAG, response\)', '', 'provider/server response body'),
    (r'Log\.\w+\(.*\$\{?(stringToSign|sendResponseJson|msgInfoJson|ruleJson|taskActionsJson|jsonStr)\b', '', 'secret/JSON payload'),
    (r'Log\.\w+\(.*\b(senderNew|settingVo|ruleNew|taskNew)(\.\w+)*\.toString\(\)', '', 'full target/rule/task configuration'),
    (r'Log\.\w+\(.*"[^"]*\$\{?(settingVo|eventData|recipients|cert|keyInfo|msgInfo|sender|rule|json|body|response|valueMap)\}?[" ,]', '', 'configuration/message/key material'),
    (r'Log\.\w+\(TAG, (rule|it|smsSendData|smsQueryData|wolData|cloneBean|cloneInfo|callQueryData|contactQueryData|contactData|callInfo|contactInfo)(\.\w+)*\.toString\(\)\)', '', 'entity dump'),
    (r'Log\.\w+\(.*\$\{?(cloneInfo|frpcListSelected|taskListSelected|senderListSelected|ruleListSelected|conditionsList|actionsList|text)\b', '', 'clone/FRP/task/target list or message text'),
    (r'Log\.e\("(calcSign|reqSign)"', '', 'request signature'),
    (r'logg\(".*\$msg\b', 'utils/RuleLine', 'message content in rule trace'),
]
# Exact reviewed exceptions: (relative path, stripped line, reason).
ALLOWED = {
    ('utils/interceptor/NoContentInterceptor.kt', 'Log.d(TAG, response)'):
        'value is the literal "HTTP Status <code> <reason>", not a body',
}


def active_lines(path):
    """Yield (line number, code) with // line comments and /* */ block comments removed (string-unaware)."""
    in_block = False
    for n, line in enumerate(path.read_text(encoding='utf-8').splitlines(), 1):
        code = ''
        i = 0
        while i < len(line):
            if in_block:
                end = line.find('*/', i)
                if end < 0:
                    i = len(line)
                else:
                    in_block, i = False, end + 2
            elif line.startswith('/*', i):
                in_block, i = True, i + 2
            elif line.startswith('//', i):
                break
            else:
                code += line[i]
                i += 1
        if code.strip():
            yield n, code


def find(pattern, scope=''):
    rx = re.compile(pattern)
    hits = []
    for path in SOURCES:
        rel = str(path.relative_to(CODE))
        if scope and scope not in rel:
            continue
        hits += [f'{rel}:{n}: {line.strip()}' for n, line in active_lines(path)
                 if rx.search(line) and (rel, line.strip()) not in ALLOWED]
    return hits


class TransportSecuritySourceTests(unittest.TestCase):
    def test_no_unconditional_certificate_bypass(self):
        self.assertEqual([], find(r'ignoreHttpsCert|setTrustedHosts|setTrustAllHosts|MailSSLSocketFactory|mail\.smtp\.ssl\.trust'))
        self.assertEqual([], find(r'checkServerTrusted\s*\([^)]*\)\s*\{\s*\}|HostnameVerifier\s*\{\s*_\s*,\s*_\s*->\s*true'))
        self.assertEqual([], find(r'onReceivedSslError'))

    def test_shared_http_client_hardened(self):
        init = (CODE / 'utils/sdkinit/XBasicLibInit.kt').read_text()
        self.assertIn('HttpTransportSecurity.apply(XHttp.getInstance())', init)
        self.assertNotRegex(init, r'XHttp\.getInstance\(\)\s*\n\s*\.debug\(')
        self.assertNotRegex(init, r'^\s*XHttpSDK\.debug\(', 'XHttp body logging must stay disabled')
        sec = (CODE / 'utils/sdkinit/HttpTransportSecurity.kt').read_text()
        self.assertIn('.setHostnameVerifier(OkHostnameVerifier.INSTANCE)', sec)
        self.assertIn('.debug(false)', sec)

    def test_smtp_identity_and_starttls_required(self):
        mail = (CODE / 'utils/mail/EmailSender.kt').read_text()
        self.assertEqual(2, mail.count('put("mail.smtp.ssl.checkserveridentity", "true")'))
        self.assertIn('put("mail.smtp.starttls.required", "true")', mail)
        # Bounded SMTP socket I/O; production callers pass the configured request timeout.
        for key in ('connectiontimeout', 'timeout', 'writetimeout'):
            self.assertIn(f'put("mail.smtp.{key}", timeout)', mail)
        utils = (CODE / 'utils/sender/EmailUtils.kt').read_text()
        self.assertEqual(2, utils.count('EmailSender.smtpTimeoutMillis(SettingUtils.requestTimeout)'))

    def test_webhook_status_has_single_writer(self):
        # NoContentInterceptor must not write a log status that can override the response check.
        src = (CODE / 'utils/interceptor/NoContentInterceptor.kt').read_text()
        self.assertNotIn('UpdateLogsWorker', src)
        self.assertNotIn('WorkManager', src)
        self.assertNotIn('NoContentInterceptor(logId)', (CODE / 'utils/sender/WebhookUtils.kt').read_text())

    def test_email_encryption_never_silently_plaintext(self):
        utils = (CODE / 'utils/sender/EmailUtils.kt').read_text()
        policy = (CODE / 'utils/mail/EmailEncryption.kt').read_text()
        self.assertNotIn('recipientsWithoutCert', utils)
        self.assertNotIn('Send plaintext if the certificate cannot be loaded', utils)
        self.assertIn('allowUnencryptedFallback', policy)
        self.assertIn('var allowUnencryptedFallback: Boolean = false', (CODE / 'entity/setting/EmailSetting.kt').read_text())
        # Jakarta Mail protocol debugging prints message text to logcat
        for name in ('SmimeUtils.kt', 'PgpUtils.kt', 'OpenKeychainMailUtils.kt', 'EmailSender.kt'):
            self.assertNotRegex((CODE / 'utils/mail' / name).read_text(), r'\.debug\s*=\s*true', name)
        self.assertNotIn('recipients=${recipientEmails.joinToString()}', (CODE / 'utils/mail/OpenKeychainHelper.kt').read_text())

    def test_request_trace_records_origin_only(self):
        src = (CODE / 'utils/interceptor/LoggingInterceptor.kt').read_text()
        for marker in ('bodyToString', 'headers()', 'Level.BODY', 'Level.PARAM', 'Level.HEADERS', '${request.url()}',
                       '.request().url()}', 'responseBody', '${e.message', 'e.toString()'):
            self.assertNotIn(marker, src, marker)
        self.assertIn('origin(request.url())', src)

    def test_no_sensitive_log_statements(self):
        failures = []
        for pattern, scope, why in FORBIDDEN_LOGS:
            failures += [f'{why}: {hit}' for hit in find(pattern, scope)]
        self.assertEqual([], failures)

    def test_patterns_detect_known_regressions(self):
        samples = [
            'Log.d(TAG, "serverHistory = $serverHistory")',
            'Log.i(TAG, "simInfo = $simInfo")',
            'Log.d(TAG, "locationOld = $locationOld, locationNew = $locationNew, locationSetting = $locationSetting")',
            'Log.d(TAG, "smsCommand = $smsCommand, cmdList = $cmdList")',
            'Log.d(TAG, "safePhone = $safePhone")',
            'Log.d(TAG, "from = $from is not safePhone = $safePhone")',
            'Log.d(TAG, App.SimInfoList.toString())',
            'Log.i(TAG, "SimInfoList = " + App.SimInfoList.toString())',
            'Log.d(TAG, "onMissedCall: $number")',
            'Log.d(TAG, simInfo.toString())',
            'Log.d(TAG, "longitude:$longitude latitude:$latitude")',
            'Log.d(TAG, "selectionArgs = $selectionArgs")',
            'Log.i(TAG, "requestUrl:$requestUrl")', 'Log.d(TAG, senderNew.toString())',
            'Log.d(TAG, settingVo.toString())', 'Log.i(TAG, "requestMsg:$requestMsg")',
            'Log.d("SendLogicWorker", "msgInfoJson: $msgInfoJson, ruleJson: $ruleJson")',
            'Log.i(TAG, "proxyUsername = ${setting.proxyUsername}, proxyPassword = ${setting.proxyPassword}")',
            'Log.d(TAG, "sender = $sender")', 'Log.i(TAG, response)', 'Log.d(TAG, "Json: $json")',
        ]
        for sample in samples:
            self.assertTrue(any(re.search(p, sample) for p, _, _ in FORBIDDEN_LOGS), sample)
        for allowed in ['Log.d(TAG, "sender id=${sender.id} is disabled")', 'Log.d(TAG, "taskId: $taskId")',
                        'Log.e(TAG, e.message.toString())', 'Log.i(TAG, "rule id=$id checked:$mixChecked")']:
            self.assertFalse(any(re.search(p, allowed) for p, _, _ in FORBIDDEN_LOGS), allowed)


if __name__ == '__main__':
    result = unittest.TextTestRunner(verbosity=2).run(
        unittest.defaultTestLoader.loadTestsFromTestCase(TransportSecuritySourceTests))
    sys.exit(0 if result.wasSuccessful() else 1)
