# Android capabilities and PyroWave bring-up

Status: 2026-11-01. Target devices are Galaxy S23 Ultra and Galaxy Tab S9+. Neither has been connected to ADB during this implementation, so no device capability or performance claim is established yet.

Subsequent device session: the connected phone identifies as Galaxy S24 Ultra (SM-S928U). Its capability export and five offscreen PyroWave compute-decoder cases now pass; the preferred fragment path fails pixel comparison. See [device validation](DEVICE_VALIDATION.md) for exact scope and results. The original implementation-time status above is historical.

## Implemented

Reflect settings has **Share device capability report** in the performance category. Collection runs on a worker thread and sharing uses the existing FileProvider. The report stays in the app cache until the user selects a share destination. It contains no serial, host address/name, pairing material, SSID, or account data. Device model, Android build/security patch, codec names and GPU driver identifiers are included deliberately for reproducibility.

Schema 1 reports:

- H.264, HEVC and AV1 decoder profiles/levels, hardware/software/vendor claims where Android exposes them, low-latency support, secure/tunneled-only restrictions and size/rate claims. These are API claims, not measured decode throughput or a selected decoder recommendation.
- Active/supported display modes and HDR type IDs. These do not prove that a stream is HDR, that it presents at a requested rate, or that Android exposes desktop-style VRR.
- A dynamically loaded Vulkan probe: loader/device versions, device/driver IDs, compute queues, subgroup operations/stages, subgroup-size control/full subgroups, 8-bit storage, optional float16/int16, texel-buffer limits and extensions. It creates an instance, queries physical devices, then destroys the instance. It does not create a logical device or submit shaders.
- A conservative PyroWave minimum-feature assessment. Passing the subgroup and storage checks produces `candidate_requires_runtime_decode_test`, never a working-decoder verdict. Missing 8-bit storage requires the pinned codec's fallback threshold of at least 16M texel-buffer elements; actual shader execution and target resolutions still require testing. Encoder-only requirements are not used to disqualify a decoder.
- Optional capability metadata from the most recent paired host launch attempt in the current app process. A fresh process has no host snapshot. This is an advertisement snapshot, not the negotiated stream result. Bitstream identity is explicitly unverified because it arrives through RTSP DESCRIBE, not serverinfo.

No PyroWave codec option or capability is advertised by the Android client yet. Standard codec selection remains unchanged. Vulkan absence is a report result and does not prevent MediaCodec streaming. Reports are best-effort: individual codec queries and subsystem failures are recorded without abandoning other sections.

## Protocol audit

Reference revisions:

- Reflect Android common-C: `c999436858471dfefa7617af3b7dc03ec1644ce4`.
- Nonary desktop: `76ee502543a2305afe06205644611975e3636737`; common-C `3f9c009fe01db573cd29c1f286db5b7fa8a922fc`.
- PyroWave codec: `186f0393b77f7755953b5ecde994bb1cec2e4155`, bitstream `186f0393`.
- Vibepollo host: `1ea3f02717b72de49799cbe06295ea82e236ed60`.

| Boundary | Finding | Required migration |
| --- | --- | --- |
| `/serverinfo` codec mask | Unsigned 32-bit mask; current Java parsed an unrestricted signed long then cast to int. | Now validate decimal uint32 with a checked IOException for malformed required values; preserve unknown bits and legacy absent-field behavior. Optional malformed PyroWave metadata is reported and ignored. |
| Capability vs format bits | PyroWave server bits are `0x00800000` through `0x04000000`; local video formats are `0x010000` through `0x080000`. | Keep these namespaces separate; do not add local formats until receive/decode/render exists. |
| `STREAM_CONFIGURATION` | Desktop adds compression, link Mbps and calibrated pace Mbps. | Recompile all native users together, initialize new members, connect only implemented policies. Standard sessions leave PyroWave fields zero. |
| `DECODE_UNIT` timing | Desktop replaces receive/enqueue milliseconds with microseconds and adds presentation microseconds and RTP timestamp. Android callbacks.c reads `receiveTimeMs`/`enqueueTimeMs` and Java expects ms. | Explicitly convert at the legacy JNI boundary or migrate the whole timing contract with tests. A field rename alone is incorrect. |
| Packet-entry semantics | Desktop adds `BUFFER_TYPE_LOST`, `BUFFER_TYPE_RECORD_START` and `pyrowaveCriticalPackets`. Android currently treats every non-picture entry as a parameter-set NALU. | Route PyroWave to a native packet-aware decoder. Preserve missing-packet and record-boundary metadata; do not concatenate through the MediaCodec byte-array path. |
| Control callbacks | Desktop adds haptic PCM and separate speaker/Steam callback entrypoints; Android uses legacy LiStartConnection and does not register adaptive-trigger output. | Audit/rebuild callback structures and add bounded, device-specific output paths before advertising their capabilities. |
| Framing/bitstream | New codec uses record framing, optional partial recovery and a versioned compression contract. Bitstream identity is carried in RTSP DESCRIBE. | Port framing validation and recorded/corrupt frame tests. Reject unsupported framing/version or fall back before delivering data to the decoder. |
| Calibration | Host advertises wire budget, UDP probe, handshake and burst versions. Host-link speed is not measured path throughput. | Version-aware parser now records claims. Authenticated probes, pacing and total-wire-budget accounting remain to implement. Wi-Fi requires its own loss/jitter tests. |

The common-C submodule is intentionally unchanged. The source review found enough data-layout and semantic differences that a blind submodule update would break the current JNI bridge. Shared-core migration and actual HEVC/AV1 host interoperability tests remain open.

## ARM64 native spike

`scripts/Build-PyroWaveAndroid.ps1` cross-compiles the pinned desktop-vendored codec without editing the reference checkout. It builds the shared codec and `pyrowave-device-validation` with NDK 27, Android API 26, ARM64, static C++ runtime and 16 KiB ELF load alignment. No desktop SDL renderer is included. Source pin/dirty-tree checks prevent accidentally validating a different codec.

```powershell
.\scripts\Build-PyroWaveAndroid.ps1
.\scripts\Test-PyroWaveAndroid.ps1 -Serial DEVICE_ID
.\scripts\Test-PyroWaveAndroid.ps1 -Serial DEVICE_ID -Roundtrip
```

The test uploads only to `/data/local/tmp/reflect-pyrowave`, uses a 30-second internal timeout, and saves output under ignored `artifacts`. It requires an explicitly selected authorized ADB device. It does not connect to or modify the host service.

Without `-Roundtrip`, the upstream program creates/destroys a PyroWave device. With it, the program encodes and decodes a small 34x30 4:2:0 test image and checks output error. This also requires encoder features: failure does not by itself prove decoding is impossible. Neither mode tests Android Surface presentation, host bitstream interoperability, HDR, streaming resolution, thermal stability or throughput.

## Device handoff and next acceptance gates

1. Enable USB debugging and authorize this PC on each Samsung. Install `artifacts/reflect-arm64-capability-diagnostics.apk` using `adb -s DEVICE_ID install -r ...`. Package `io.github.ultimatejeeves.reflect.debug` coexists with Artemis. No APK has been installed by this work yet.
2. Open Reflect Dev settings, share the capability report, and retain one report per device. After generating it, ADB can also read the debug app's `cache/capability-reports/reflect-capabilities.json` using `run-as io.github.ultimatejeeves.reflect.debug`. Local device reports stay out of Git.
3. Run device creation and roundtrip probes. Investigate driver/feature failures before implementing a presenter. Compare a host-generated fixture against desktop-decoded golden output next; a same-device roundtrip alone cannot establish interoperability.
4. Adapt common-C/JNI timestamp and packet-entry contracts, preserving standard-codec tests. Add bounded native record-framing/loss handling and exact bitstream checks.
5. Decode recorded frames on ARM64, then integrate a native Android Surface presenter with resize/rotation/background lifecycle tests. Start with SDR 4:2:0; qualify HDR and 4:4:4 independently.
6. Add live PyroWave transport and calibrated bandwidth/pacing, then measure both Samsung devices at useful resolutions/rates with sustained thermal/network tests. Expose the codec only when this complete path works with safe fallback.

## Verification

The app is built and checked using `Build-Client.ps1 -Verify`; results are recorded in `VALIDATION.md`. JVM tests cover protocol parsing through real XML, legacy/malformed/future capability values, conservative Vulkan assessment, and report fallback on Android API 23/28/33. API 21/22 remain supported by the APK and lint guards but are not available in the installed Robolectric version. No mocked/JVM test executes a Vulkan driver.

Native ARM64 compilation succeeded. The standalone executable depends on its packaged PyroWave shared library plus Android system libraries, with no missing desktop runtime dependency. Device execution is pending ADB connectivity.
