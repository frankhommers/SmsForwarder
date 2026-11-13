# Reviewed English dependency source replacements

This directory contains 56 modified source files from the **same pinned releases** already used by the app, spanning 12 AAR modules. It is not a dependency upgrade or a rewrite of compiled strings. `source-receipts.json` identifies the upstream repositories, exact Git commits or matching-version Maven source archives and archive hashes. `source-files.json` records each original path/hash and modified path/hash. `artifacts.lock.json` pins the original AARs and POMs. The mutable XUI snapshot was resolved to commit `5c31e0bfc8a68d5b27f0c5e768fe726dde781f4b` using its JitPack build log; the original AAR hash is mandatory.

## Rebuild

```bash
source ~/toolchains/android/env.sh
python3 scripts/rebuild_english_dependencies.py
bash ./gradlew clean :app:assembleDebug :app:testDebugUnitTest \
  --no-daemon --max-workers=4 -PisNeedPackage=false -PisNeedClean=false \
  '-Dorg.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8'
python3 scripts/test_languages.py
python3 scripts/check_languages.py --apk /absolute/path/to/apk --strict \
  --report /absolute/path/to/language-inventory.json
```

The script bootstraps the **original** dependency graph solely to obtain the compilation classpath and generated Android R classes. Bootstrap cannot assemble/package an APK. It validates original artifact hashes, compiles the checked-in sources with JDK 17 targeting Java 8, AspectJ **1.9.7** for the five modified woven aspects, and Kotlin **1.7.21** for Cactus. Kotlin's friend-path mechanism allows the single same-module source file to reference upstream internal functions without making them public. It rebuilds only the affected top-level classes and their nested classes, then deterministically repackages AAR/JAR ZIP entries; every unaffected class remains byte-identical. It never edits a constant pool or DEX.

Generated artifacts live in ignored `repository/`. Gradle's exclusive repository filter refuses fallback to upstream for these modules. `preBuild` verifies the recipe, reviewed source hashes and generated AAR/POM hashes; source changes require updating the reviewed receipt and rebuilding. The original POM dependency graph is preserved. Only descriptive prose/developer-name transliteration in POM metadata changed. All other dependencies and binary resource payloads remain unchanged.

## Reviewed change categories

- XUI/XUtil/XHttp/XPage/XAOP/AgentWeb/SmartRefresh/RichText/Cactus: translate diagnostic messages, errors, hints, names, time units, notifications and labels into English. Format placeholders and exception branches are retained. HTTP/TLS validation, permission checks, notification channels, threading and forwarding behavior are not relaxed.
- Lunar calendar: retain the complete numeric lunar table and conversion algorithms; display numeric days/months, English animal names and romanized traditional cyclical-year names. `getChinaDate` returns numeric labels for valid days 1–30, empty for invalid days. `Locale.ENGLISH` replaces the explicit Chinese calendar locale. JVM tests cover a known lunar new year, leap month, labels and zodiac.
- Font measurement: replace the two-character weekday sample with `Wk` and the single-character span sample with `M`; normal text measurement/drawing is unchanged.
- DeviceCompat: localized marketing aliases are replaced with a narrow API-compatible diagnostic identifier based on `Build.MANUFACTURER`/`Build.MODEL`, with duplicate-prefix avoidance. **Marketing-friendly names change to manufacturer/model identifiers.** This does not alter DeviceBrand/DeviceOs or permission decisions. Runtime manufacturer/model values are external data and can themselves contain any language; this is not a promise to censor user/device/provider input.
- Source-only `SlideBack.EdgeMode` annotation is unavailable in the published binary and omitted from the recompiled parameter; runtime validation is unchanged.
- Cactus compiles with the app's existing Kotlin version. Its old anonymous Runnable class becomes compiler-generated lambda bytecode; Android D8 desugars it. The notification behavior remains the same.
- Explanatory upstream CJK comments were omitted from the selected files rather than copied into the cleaned tree; legal copyright/license headers and author identity remain. The original pinned sources and per-file hashes provide the full original documentation. No encoded Chinese text is added to bypass scans.

## Licenses and notices

Apache-2.0: XUI, XUtil, XAOP, XHttp2, XPage, AgentWeb, SmartRefreshLayout, DeviceCompat and Cactus. MIT: RichText (Copyright 2016 zzhoujay). `licenses/` preserves the received license text verbatim. XUtil's tagged source has Apache-2.0 file headers but no root license, so the full standard Apache text is supplied. DeviceCompat's own Apache header says August 2025; it is preserved, not silently repaired. Cactus/RichText matching-version source JARs were obtained from the existing Maven mirror because exact Git tags were unavailable; their repository license files were obtained separately. License text and this notice are included in APK assets.

Upstream authors retained: xuexiangjys/Xue Xiang and contributors; pppscn fork contributors; Justson/AgentWeb contributors; scwang/SmartRefreshLayout contributors; getActivity/Android Lun Zi Ge; zzhoujay; geyifeng/Cactus contributors. Modified files are marked as changed by SmsForwarder Private contributors. This does not resolve the separate SmsForwarder upstream license ambiguity documented in README_PRIVACY.md.

## Verification and bounds

The APK language gate rejects all CJK natural-language DEX strings, even without `--strict`. Exactly 32 existing functional Unicode/regex strings are separately identified by **SHA-256 plus exact attributed owners** in `scripts/functional_unicode_allowlist.json`. This replaces the former broad package/character heuristic with a narrower fail-closed check. These character classes, international URL/TLD patterns and validation rules are not translated or removed. First-party source, compiled XML, resource pools, UTF-8 archive assets, en/nl key/placeholder parity and privacy gates remain enabled.

`EnglishDependencyTest` covers calendar arithmetic/labels, time labels, errors, hex conversion and the AspectJ singleton ABI. The local evidence records two identical dependency-rebuild results, the clean APK build/tests, strict scans, signer, emulator Dutch persistence and visual review of 212 unique dependency raster assets. Static/image review is not exhaustive OCR, native-string decoding, a release-weaving test or real SMS/provider-account testing.
