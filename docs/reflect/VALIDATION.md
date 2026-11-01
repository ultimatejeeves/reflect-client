# Reflect bootstrap validation

Historical Apollo bootstrap document. Current direction: [Vibepollo migration](HOST_MIGRATION.md) and [Android parity](ANDROID_PARITY.md). Android decoder follow-up: [repair notes](DECODER_RECOVERY.md).

## Android capability diagnostics and PyroWave spike (2026-11-01)

- All four non-root debug ABI APKs build with the new dynamically loaded Vulkan diagnostics library.
- **87 JVM tests pass**, zero failures/errors/skips. This includes 20 new capability/report/XML tests alongside the 67-test decoder-recovery baseline.
- Lint: **0 errors, 458 warnings**, unchanged from the decoder-recovery baseline. API 21/22 guards are lint-checked; report fallback tests run on API 23/28/33 because installed Robolectric does not support API 21.
- `artifacts/reflect-arm64-capability-diagnostics.apk`: all **six** native libraries pass 16 KiB ELF load alignment; SDK zipalign `-c -P 16 4` passes. The APK keeps the existing independent Reflect Dev ID.
- Pinned PyroWave shared library and `pyrowave-device-validation` cross-compile for Android ARM64/API 26. Their ELF load segments have 16 KiB alignment. Dynamic dependencies are the packaged PyroWave library and Android system libraries; C++ is statically linked for this standalone probe.
- Logs: `artifacts/android-capabilities-verification.log`, `artifacts/android-capabilities-alignment.json`, `artifacts/pyrowave-android-reproducible-build.log`.
- No ADB devices connected. No APK installation, native GPU execution, live-host protocol verification, presentation test or Samsung performance measurement has occurred. The common-C pin and production codec negotiation are unchanged.
- [Capability diagnostics, protocol audit and device handoff](ANDROID_CAPABILITIES.md) records remaining acceptance gates. PyroWave is not yet a working Android streaming codec.

## Historical bootstrap results

Recorded November 1, 2026 on Windows, RTX 5090, NVIDIA driver 621.01. This is development validation, not release or end-to-end performance certification.

| Check | Result |
| --- | --- |
| Supplied Artemis baseline | Debug APK compiled before switching upstream. |
| Maintained Reflect Android | All four ABI debug APKs compiled; independent application ID. |
| Android unit tests | 51 passed, zero failures after repairing five stale fixtures. |
| Android lint | Zero errors, 459 warnings. Warnings remain a follow-up backlog. |
| ARM64 package alignment | All five native libraries pass 16 KB ELF alignment inspection; APK passes SDK `zipalign -c -P 16 4`. |
| Host Windows build | Native host, test executable and web assets compiled using UCRT64 GCC. |
| Host selected native tests | 211 run, 209 passed; two configuration documentation/UI consistency failures remain. Includes parser, pairing, display configuration, FEC, URL, logging, environment and locale cases. |
| Production npm audit | Zero reported vulnerabilities in the resolved production graph. |
| Full npm audit | Four development dependency advisories remain in the local `serve` fixture-server chain. |
| CI | Windows and Android workflows configured; remote runs not yet verified. |
| Physical devices / live streams | Not run: no Samsung device attached via ADB. |

Host failures: `ConfigConsistencyTest.AllConfigOptionsExistInAllFiles` and `ConfigConsistencyTest.ConfigOptionsInSameOrderWithinSections`. Examples include undocumented `nvenc_intra_refresh`, `headless_mode`, and `enable_pairing`, missing locale keys, and Audio/Video order differences. The tests remain enabled. A stale GCC coverage-data checksum warning can appear after recompiling instrumented source; it is not a test assertion or proof of coverage completeness.

Host tests must run from `repos/apollo/build/tests`, where CMake supplies source fixtures. Running from the workspace root can produce misleading passes for file-based consistency tests. Hardware encoder, audio, display/input, network-download and external-command integration tests were not included in the selected run.

Run the recorded selection with `scripts/Test-Host.ps1`. It deliberately exits nonzero while the two known consistency failures remain. CI's narrower regression gate covers parser, pairing and display tests and does not imply the entire suite passes.

Local evidence is in the workspace `artifacts` directory: `apollo-build-final.log`, `apollo-unit-tests.xml`, `apollo-unit-tests.log`, `artemis-reflect-verification.log`, and `android-elf-alignment.json`. Artifacts and SDK/compiler downloads are intentionally excluded from Git. The distributable local development APK is `artifacts/reflect-arm64-development.apk`.

No existing host service was replaced or restarted, no GPU/display driver was installed, no APK was installed, and no production release was published. The temporary `R:` folder alias is removed after build/test work. No frame-rate, image-quality or latency improvement has yet been measured on the target devices.
