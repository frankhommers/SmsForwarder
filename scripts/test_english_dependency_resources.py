#!/usr/bin/env python3
"""Regression: source-rebuilt dependency classes must reference resources symbolically.

Rebuilt classes used to be compiled against the app's final R.jar, so javac inlined app resource IDs.
Any later app resource edit shifted the IDs and the generated AARs silently resolved to other
resources. Requires javac/java (source ~/toolchains/android/env.sh).
"""
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parent))
import rebuild_english_dependencies as rebuild  # noqa: E402

LIBRARY = '''package com.example.lib;
public final class Lib {
  public static int text() { return R.id.snackbar_text; }
  public static int theme() { return R.style.Theme_Phone; }
}
'''
R_TXT = '''int id snackbar_text 0x0
int style Theme_Phone 0x0
int[] styleable Bar { 0x0 }
int styleable Bar_x 0
'''


def app_r(text_id, theme_id):
    """A final app-side R class, as aapt2 generates it at app link time."""
    return ('package com.example.lib;\npublic final class R {\n'
            '  public static final class id { public static final int snackbar_text = %d; }\n'
            '  public static final class style { public static final int Theme_Phone = %d; }\n'
            '  public static final class styleable { public static final int[] Bar = {0}; public static final int Bar_x = 0; }\n'
            '}\n' % (text_id, theme_id))


def javac(src_dir, out, classpath=None):
    out.mkdir(parents=True, exist_ok=True)
    cmd = ['javac', '-source', '8', '-target', '8', '-nowarn', '-d', str(out)]
    if classpath:
        cmd += ['-cp', str(classpath)]
    return subprocess.run(cmd + [str(p) for p in sorted(Path(src_dir).rglob('*.java'))], capture_output=True, text=True)


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)


class ResourceLinkageTests(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix='res-link-'))

    def lib_class(self, symbols_dir, source=LIBRARY):
        write(self.tmp / 'lib-src/com/example/lib/Lib.java', source)
        result = javac(self.tmp / 'lib-src', self.tmp / 'lib-out', symbols_dir)
        return result, self.tmp / 'lib-out/com/example/lib/Lib.class'

    def test_old_recipe_final_r_inlines_ids_and_is_rejected(self):
        write(self.tmp / 'final-src/com/example/lib/R.java', app_r(0x7f0904fd, 0x7f13053a))
        self.assertEqual(0, javac(self.tmp / 'final-src', self.tmp / 'final-out').returncode)
        result, lib = self.lib_class(self.tmp / 'final-out')
        self.assertEqual(0, result.returncode, result.stderr)
        data = lib.read_bytes()
        self.assertEqual([0x7f0904fd, 0x7f13053a], rebuild.inlined_resource_ids(data))
        self.assertEqual(set(), rebuild.r_references(data))
        with self.assertRaisesRegex(AssertionError, 'inlines app resource IDs'):
            rebuild.check_resource_linkage('Lib.class', data, None)

    def test_symbolic_linkage_survives_synthetic_resource_addition(self):
        write(self.tmp / 'stub-src/com/example/lib/R.java', rebuild.symbol_source('com.example.lib', R_TXT))
        self.assertEqual(0, javac(self.tmp / 'stub-src', self.tmp / 'stub-out').returncode)
        result, lib = self.lib_class(self.tmp / 'stub-out')
        self.assertEqual(0, result.returncode, result.stderr)
        data = lib.read_bytes()
        self.assertEqual([], rebuild.inlined_resource_ids(data))
        self.assertEqual({'com/example/lib/R$id.snackbar_text', 'com/example/lib/R$style.Theme_Phone'}, rebuild.r_references(data))
        rebuild.check_resource_linkage('Lib.class', data, data)
        # Link the SAME library bytes against the app R before and after a synthetic resource addition
        # (one extra id and style sort first and shift every following ID by one).
        write(self.tmp / 'main-src/Main.java', 'public class Main { public static void main(String[] a) {'
              ' System.out.println(com.example.lib.Lib.text() + " " + com.example.lib.Lib.theme()); } }')
        for label, ids in (('before', (0x7f0904fd, 0x7f13053a)), ('after-synthetic-addition', (0x7f0904fe, 0x7f13053b))):
            app = self.tmp / label
            write(app / 'src/com/example/lib/R.java', app_r(*ids))
            self.assertEqual(0, javac(app / 'src', app / 'out').returncode)
            self.assertEqual(0, javac(self.tmp / 'main-src', app / 'main', self.tmp / 'lib-out').returncode)
            run = subprocess.run(['java', '-cp', ':'.join(map(str, [app / 'out', self.tmp / 'lib-out', app / 'main'])), 'Main'],
                                 capture_output=True, text=True, check=True)
            self.assertEqual('%d %d' % ids, run.stdout.strip(), label)
        self.assertEqual(data, lib.read_bytes(), 'library class must not need regeneration')

    def test_resource_switch_constants_fail_closed(self):
        """Non-final symbols cannot be case labels: javac refuses instead of inlining a stale value."""
        write(self.tmp / 'stub-src/com/example/lib/R.java', rebuild.symbol_source('com.example.lib', R_TXT))
        self.assertEqual(0, javac(self.tmp / 'stub-src', self.tmp / 'stub-out').returncode)
        source = LIBRARY.replace('public static int text()', 'public static int pick(int v) { switch (v) {'
                                 ' case R.id.snackbar_text: return 1; default: return 0; } }\n  public static int text()')
        result, _ = self.lib_class(self.tmp / 'stub-out', source)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('constant expression required', result.stderr)

    def test_reviewed_sources_have_no_resource_case_labels(self):
        sources = (rebuild.VENDOR / 'src').rglob('*.[jk][at]*')
        offenders = [str(p) for p in sources if 'case R.' in p.read_text(encoding='utf-8')]
        self.assertEqual([], offenders)

    def test_generated_repository_is_symbolic(self):
        verified_path = rebuild.REPO / 'verified.json'
        self.assertTrue(verified_path.exists(), 'run scripts/rebuild_english_dependencies.py first')
        verified = json.loads(verified_path.read_text())
        self.assertEqual('symbolic', verified.get('resource_binding'), 'stale repository: rebuild dependencies')
        checked = 0
        for coordinate, entry in verified['artifacts'].items():
            group, name, version = coordinate.split(':')
            aar = rebuild.REPO / group.replace('.', '/') / name / version / f'{name}-{version}.aar'
            with zipfile.ZipFile(aar) as outer, zipfile.ZipFile(io.BytesIO(outer.read('classes.jar'))) as jar:
                for class_entry in entry['source_compiled_class_entries']:
                    rebuild.check_resource_linkage(class_entry, jar.read(class_entry), None)
                    checked += 1
        self.assertGreater(checked, 100)


if __name__ == '__main__':
    unittest.main(verbosity=2)
