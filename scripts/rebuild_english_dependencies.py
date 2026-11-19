#!/usr/bin/env python3
"""Compile reviewed pinned-source replacements; repackage only their class entries.
Requires the project's existing JDK 17 / Android toolchain and Gradle dependencies.
No constant-pool or DEX rewriting. Unaffected ZIP members are byte-identical.

Resource linkage: the replacements are compiled against NON-final R symbol stubs generated from each
pinned AAR's own R.txt, exactly like an Android library build, never against the app's final R.jar.
javac/kotlinc therefore emit getstatic R$type.name references that bind to the app's R classes at app
link time, so the generated artifacts do not depend on (and cannot go stale after) app resource edits.
Rebuild and --verify-only fail if a source-compiled class inlines an app resource ID (0x7fTTEEEE) or
loses an R reference that the pinned upstream class has.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VENDOR = ROOT / 'third_party/english-dependencies'
WORK = ROOT / '.gradle/english-dependencies'
REPO = VENDOR / 'repository'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def run(args):
    print('+', ' '.join(map(str, args)), flush=True)
    subprocess.run(list(map(str, args)), cwd=ROOT, check=True)


def write_zip(entries):
    out = io.BytesIO()
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, data)
    return out.getvalue()


def constant_pool(data):
    """Parse a class file constant pool (read-only; nothing is ever rewritten)."""
    assert data[:4] == b'\xca\xfe\xba\xbe', 'not a class file'
    count = struct.unpack_from('>H', data, 8)[0]
    pool, i, off = [None] * count, 1, 10
    sizes = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4, 12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
    while i < count:
        tag = data[off]
        if tag == 1:
            n = struct.unpack_from('>H', data, off + 1)[0]
            pool[i] = (1, data[off + 3:off + 3 + n].decode('utf-8', 'replace'))
            off += 3 + n
        else:
            assert tag in sizes, 'unknown constant tag %d' % tag
            body = data[off + 1:off + 1 + sizes[tag]]
            pool[i] = (tag, struct.unpack('>i', body)[0] if tag == 3 else struct.unpack('>HH', body) if tag in (9, 12) else
                       struct.unpack('>H', body)[0] if tag in (7, 8) else None)
            off += 1 + sizes[tag]
            i += 1 if tag in (5, 6) else 0
        i += 1
    return pool


def inlined_resource_ids(data):
    """Integer constants shaped like an app resource ID: package 0x7f, type 0x01-0x3f (e.g. 0x7f0904fd)."""
    return sorted(e[1] for e in constant_pool(data) if e and e[0] == 3 and e[1] >> 24 == 0x7f and 0 < (e[1] >> 16 & 0xff) < 0x40)


def r_references(data):
    """Symbolic field references to any R$type class, e.g. com/xuexiang/xui/R$id.snackbar_text."""
    pool, refs = constant_pool(data), set()
    for e in pool:
        if e and e[0] == 9:
            owner = pool[pool[e[1][0]][1]][1]
            if re.search(r'(^|/)R\$[A-Za-z]+$', owner):
                refs.add(owner + '.' + pool[pool[e[1][1]][1][0]][1])
    return refs


def symbol_source(package, r_txt):
    """Java source of a non-final library R class from an AAR R.txt; values are left to app link time."""
    types = {}
    for line in r_txt.splitlines():
        parts = line.split()
        if len(parts) >= 3 and parts[0] in ('int', 'int[]'):
            types.setdefault(parts[1], {})[parts[2]] = parts[0]
    body = ''.join('  public static final class %s {\n%s  }\n' % (t, ''.join(
        '    public static %s %s;\n' % (kind, name) for name, kind in sorted(fields.items()))) for t, fields in sorted(types.items()))
    return 'package %s;\n\npublic final class R {\n%s}\n' % (package, body)


def compile_resource_symbols(artifacts, lock, out):
    """Compile non-final R stubs for each pinned module package; the stubs are classpath-only."""
    sources = out.parent / (out.name + '-src')
    for d in (out, sources):
        if d.exists():
            shutil.rmtree(d)
        d.mkdir(parents=True)
    for coordinate in sorted(lock):
        with zipfile.ZipFile(artifacts[coordinate]) as aar:
            package = re.search(r'package="([^"]+)"', aar.read('AndroidManifest.xml').decode()).group(1)
            path = sources / package.replace('.', '/') / 'R.java'
            assert not path.exists(), 'Two pinned modules share R package ' + package
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(symbol_source(package, aar.read('R.txt').decode()))
    run(['javac', '-source', '8', '-target', '8', '-encoding', 'UTF-8', '-proc:none', '-nowarn', '-d', out]
        + sorted(sources.rglob('*.java')))


def check_resource_linkage(name, rebuilt, original):
    inlined = inlined_resource_ids(rebuilt)
    assert not inlined, '%s inlines app resource IDs %s; compile against non-final R symbols' % (name, [hex(x) for x in inlined])
    if original is not None:
        lost = r_references(original) - r_references(rebuilt)
        assert not lost, '%s lost symbolic resource references %s' % (name, sorted(lost))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reuse-inputs', action='store_true', help='Developer audit only; inputs must already be exported')
    parser.add_argument('--verify-only', action='store_true', help='Fail if reviewed sources or generated artifacts changed')
    args = parser.parse_args()
    if args.verify_only:
        verified = json.loads((REPO / 'verified.json').read_text())
        assert verified['recipe_sha256'] == digest(Path(__file__).read_bytes()), 'Rebuild recipe changed; rebuild dependencies'
        assert verified['lock_sha256'] == digest((VENDOR / 'artifacts.lock.json').read_bytes())
        assert verified['sources_sha256'] == digest((VENDOR / 'source-files.json').read_bytes())
        assert verified.get('resource_binding') == 'symbolic', 'Generated artifacts predate symbolic resource linkage; rebuild dependencies'
        for source in json.loads((VENDOR / 'source-files.json').read_text()):
            assert digest((VENDOR / source['patched']).read_bytes()) == source['patched_sha256'], source['patched']
        for coordinate, entry in verified['artifacts'].items():
            group, name, version = coordinate.split(':')
            target = REPO / group.replace('.', '/') / name / version
            aar = (target / f'{name}-{version}.aar').read_bytes()
            assert digest(aar) == entry['sha256'], coordinate
            with zipfile.ZipFile(io.BytesIO(aar)) as outer, zipfile.ZipFile(io.BytesIO(outer.read('classes.jar'))) as jar:
                for class_entry in entry['source_compiled_class_entries']:
                    check_resource_linkage(class_entry, jar.read(class_entry), None)
            spec = json.loads((VENDOR / 'artifacts.lock.json').read_text())[coordinate]
            assert digest((target / f'{name}-{version}.pom').read_bytes()) == spec['pom_sha256'], coordinate
        print('Verified reviewed sources, all generated pinned artifacts and symbolic resource linkage')
        return
    WORK.mkdir(parents=True, exist_ok=True)
    if not args.reuse_inputs:
        # Only the pinned dependency classpath is exported; app resources are deliberately not an input.
        run(['bash', './gradlew', ':app:exportEnglishDependencyInputs',
             '-I', str(VENDOR / 'export.init.gradle'), '-PenglishDependencyBootstrap=true',
             '--no-daemon', '--max-workers=4', '-PisNeedPackage=false', '-PisNeedClean=false',
             '-Dorg.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8'])
    inputs = json.loads((WORK / 'inputs.json').read_text())
    lock = json.loads((VENDOR / 'artifacts.lock.json').read_text())
    sources = json.loads((VENDOR / 'source-files.json').read_text())
    for source in sources:
        assert digest((VENDOR / source['patched']).read_bytes()) == source['patched_sha256'], source['patched']
    artifacts = {a['coordinate']: Path(a['file']) for a in inputs['artifacts']}
    for coordinate, expected in lock.items():
        assert digest(artifacts[coordinate].read_bytes()) == expected['aar_sha256'], 'Pinned artifact changed: ' + coordinate
    classes = WORK / 'compiled'
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir()
    cactus_coord = 'com.gyf.cactus:cactus:1.1.3-beta13'
    with zipfile.ZipFile(artifacts[cactus_coord]) as z:
        cactus = WORK / 'cactus-full.jar'
        cactus.write_bytes(z.read('classes.jar'))
    symbols = WORK / 'resource-symbols'
    compile_resource_symbols(artifacts, lock, symbols)
    classpath = os.pathsep.join([str(cactus)] + inputs['boot'] + inputs['jars'] + [str(symbols)])
    run(['javac', '-g', '-source', '8', '-target', '8', '-encoding', 'UTF-8', '-proc:none', '-cp', classpath,
         '-d', classes] + sorted((VENDOR / 'src/java').rglob('*.java')))
    cache = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle'))) / 'caches/modules-2/files-2.1'
    # Upstream ships woven aspects: use its pinned AspectJ compiler, not plain javac.
    # This preserves aspectOf/hasAspect and singleton initialization for release weaving.
    ajc = list(cache.glob('org.aspectj/aspectjtools/1.9.7/*/*.jar'))
    assert len(ajc) == 1
    aspect_sources = sorted((VENDOR / 'src/java/com/xuexiang/xaop/aspectj').glob('*.java'))
    run(['java', '-cp', ajc[0], 'org.aspectj.tools.ajc.Main', '-1.8', '-g', '-encoding', 'UTF-8',
         '-classpath', classpath, '-d', classes] + aspect_sources)
    compiler = []
    for group, name, version in [('org.jetbrains.kotlin', n, '1.7.21') for n in
                                  ['kotlin-compiler-embeddable', 'kotlin-stdlib', 'kotlin-reflect', 'kotlin-script-runtime', 'kotlin-daemon-embeddable']]:
        found = list(cache.glob(f'{group}/{name}/{version}/*/*.jar'))
        assert len(found) == 1, (name, found)
        compiler += found
    for group, name in [('org.jetbrains.intellij.deps', 'trove4j'), ('org.jetbrains', 'annotations')]:
        found = sorted(cache.glob(f'{group}/{name}/*/*/*.jar'))
        assert found, name
        compiler.append(found[0])
    run(['java', '-cp', os.pathsep.join(map(str, compiler)), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
         '-no-stdlib', '-no-reflect', '-Xfriend-paths=' + str(cactus), '-jvm-target', '1.8', '-module-name', 'cactus',
         '-classpath', classpath, '-d', classes] + sorted((VENDOR / 'src/kotlin').rglob('*.kt')))
    report = {}
    generated = {str(p.relative_to(classes)): p.read_bytes() for p in classes.rglob('*.class')}
    used = set()
    for coordinate, spec in lock.items():
        group, name, version = coordinate.split(':')
        with zipfile.ZipFile(artifacts[coordinate]) as aar:
            entries = {n: aar.read(n) for n in aar.namelist()}
        with zipfile.ZipFile(io.BytesIO(entries['classes.jar'])) as jar:
            original = {n: jar.read(n) for n in jar.namelist()}
        roots = spec['classes']
        def affected(n):
            return any(n == c + '.class' or n.startswith(c + '$') for c in roots)
        replaced = {n: b for n, b in generated.items() if affected(n)}
        assert all(c + '.class' in replaced for c in roots)
        for n, b in replaced.items():
            check_resource_linkage(n, b, original.get(n))
        removed = sorted(n for n in original if affected(n))
        assert all(c + '.class' in original for c in roots)
        modified = {n: b for n, b in original.items() if not affected(n)}
        modified.update(replaced)
        assert all(modified[n] == b for n, b in original.items() if not affected(n))
        entries['classes.jar'] = write_zip(modified)
        # License notices are carried into the AAR and separately into the app assets.
        entries['assets/english-dependencies/NOTICE.md'] = (VENDOR / 'README.md').read_bytes()
        for license_file in (VENDOR / 'licenses').iterdir():
            entries['assets/english-dependencies/' + license_file.name] = license_file.read_bytes()
        target = REPO / group.replace('.', '/') / name / version
        target.mkdir(parents=True, exist_ok=True)
        data = write_zip(entries)
        (target / f'{name}-{version}.aar').write_bytes(data)
        pom = VENDOR / 'poms' / f'{group}.{name}-{version}.pom'
        assert digest(pom.read_bytes()) == spec['pom_sha256']
        shutil.copyfile(pom, target / f'{name}-{version}.pom')
        used.update(replaced)
        report[coordinate] = {'sha256': digest(data), 'removed_class_entries': removed,
                              'source_compiled_class_entries': sorted(replaced), 'unaffected_class_entries': len(original)-len(removed)}
    assert used == set(generated), sorted(set(generated)-used)
    (WORK / 'rebuild-report.json').write_text(json.dumps(report, indent=2) + '\n')
    verified = {'recipe_sha256': digest(Path(__file__).read_bytes()),
                'lock_sha256': digest((VENDOR / 'artifacts.lock.json').read_bytes()),
                'sources_sha256': digest((VENDOR / 'source-files.json').read_bytes()),
                'resource_binding': 'symbolic', 'artifacts': report}
    (REPO / 'verified.json').write_text(json.dumps(verified, indent=2) + '\n')
    print('Rebuilt', len(report), 'pinned artifacts from', len(sources), 'reviewed source files')


if __name__ == '__main__':
    main()
