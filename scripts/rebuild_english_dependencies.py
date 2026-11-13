#!/usr/bin/env python3
"""Compile reviewed pinned-source replacements; repackage only their class entries.
Requires the project's existing JDK 17 / Android toolchain and Gradle dependencies.
No constant-pool or DEX rewriting. Unaffected ZIP members are byte-identical.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import shutil
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
        for source in json.loads((VENDOR / 'source-files.json').read_text()):
            assert digest((VENDOR / source['patched']).read_bytes()) == source['patched_sha256'], source['patched']
        for coordinate, entry in verified['artifacts'].items():
            group, name, version = coordinate.split(':')
            target = REPO / group.replace('.', '/') / name / version
            assert digest((target / f'{name}-{version}.aar').read_bytes()) == entry['sha256'], coordinate
            spec = json.loads((VENDOR / 'artifacts.lock.json').read_text())[coordinate]
            assert digest((target / f'{name}-{version}.pom').read_bytes()) == spec['pom_sha256'], coordinate
        print('Verified reviewed sources and all generated pinned artifacts')
        return
    WORK.mkdir(parents=True, exist_ok=True)
    if not args.reuse_inputs:
        run(['bash', './gradlew', ':app:processDebugResources', ':app:exportEnglishDependencyInputs',
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
    classpath = os.pathsep.join([str(cactus)] + inputs['boot'] + inputs['jars'] + [str(ROOT / 'app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/R.jar')])
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
                'sources_sha256': digest((VENDOR / 'source-files.json').read_bytes()), 'artifacts': report}
    (REPO / 'verified.json').write_text(json.dumps(verified, indent=2) + '\n')
    print('Rebuilt', len(report), 'pinned artifacts from', len(sources), 'reviewed source files')


if __name__ == '__main__':
    main()
