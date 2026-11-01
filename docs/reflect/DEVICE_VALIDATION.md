# First connected Samsung validation — 2026-11-01

The phone originally described as an S23 Ultra reports **SM-S928U**, Adreno 750, Android 17/API 37, build `CP2A.260605.016`, security patch `2026-10-05`. Samsung identifies SM-S928U as [Galaxy S24 Ultra](https://doc.samsungmobile.com/SM-S928U/029124240223/eng.html). Record these results against the observed model; they do not validate an S23 Ultra or Tab S9+.

Reflect Dev (`io.github.ultimatejeeves.reflect.debug`) was installed successfully from the previously verified capability APK. The device report was generated through the real settings action and retrieved using debug `run-as`; no share destination was selected and no report was sent externally. Raw reports and device identifiers stay in ignored local artifacts.

## Capability claims

- Display: native 1440×3120, active 120 Hz. Android lists 10/24/30/48/60/80/120 Hz modes at native, 1080×2340 and 720×1560 resolutions. These are reported modes, not proven app-controlled VRR.
- HDR types: HDR10, HLG and HDR10+. No HDR stream/presentation test has run.
- Hardware H.264, HEVC and AV1 decoders are present, including Qualcomm low-latency variants. They report 1920×1080 at 120 FPS as supported. Actual throughput/latency is unmeasured.
- Vulkan: Adreno 750, API 1.3.128, driver integer `2150604801`. Required decoder subgroup operations and compute stages, subgroup-size control/full subgroups, 64–128 subgroup range, 8-bit storage and float16/int16 are advertised. The app classifies this as a candidate, not proven streaming support.

## Runtime PyroWave results

Pinned codec bitstream: `186f0393`. No codec or Granite source changes were needed for these tests.

The original upstream native device-creation test exits 0. Its combined roundtrip test exits 5. A new stage-reporting harness isolates this to **encoder creation**: device and decoder initialization succeed, encoder initialization returns `-1`. The phone's subgroup-operation mask lacks `CLUSTERED`, required by this encoder but not by the decoder. This explains why an encode/decode roundtrip is an inappropriate decoder-only acceptance gate on this phone.

The new harness generates packets and golden decoded pixels on the **RTX 5090**, then decodes the packets on the **Adreno 750**, with CPU readback for comparison. Host reference generation separately checks its result against the deterministic source image. This tests the codec directly, not a running Vibepollo session or its RTP/FEC/framing layers.

| 8-bit test image | Chroma | Android compute decode | Maximum error vs desktop golden, Y/Cb/Cr |
| --- | --- | --- | --- |
| 128×72 | 4:2:0 | Pass | 1 / 1 / 1 |
| 1920×1080 | 4:2:0 | Pass | 1 / 1 / 1 |
| 1920×1080 | 4:4:4 | Pass | 1 / 1 / 1 |
| 3120×1440 | 4:2:0 | Pass | 1 / 1 / 1 |
| 3120×1440 | 4:4:4 | Pass | 1 / 1 / 1 |

Acceptance tolerance is 1 luma / 2 chroma code values; no samples exceeded it. This is approximate agreement, not bit-exact decoding or lossless video. The host reference differs from the original source by at most 1 luma / 2 chroma values for these fixtures.

The codec's preferred mobile **fragment path fails** the 128×72 reference comparison: maximum errors 37/24/29, with 6,563 samples outside tolerance on the phone. Explicit fragment decoding on the desktop also fails (same maximum errors, 6,535 samples outside tolerance). This is evidence of a reproducible discrepancy in this pinned codec/C API offscreen path; it does not isolate the precise shader, precision, or readback cause and is not evidence of a Samsung-only driver bug. The first Android integration must explicitly choose the tested compute path until the discrepancy is resolved.

## Next gates

1. Move packet-aware receive/decode ownership into the native Android pipeline and adapt common-C/JNI timing units. Keep existing H.264/HEVC/AV1 behavior intact.
2. Present GPU-decoded frames to an Android Surface with bounded buffering and lifecycle handling. No live PyroWave selector is exposed yet.
3. Validate 10-bit/HDR and useful bitrate/error settings, real transport loss/repair, source cadence and sustained thermals. Single-frame high-budget fixtures do not establish FPS, bandwidth or latency.
4. Repeat capability and interop tests on the Tab S9+. The tablet has not been connected or tested.

The reproducible harness is in `repos/artemis/tools/pyrowave-probe`; workspace scripts are `Build-PyroWaveInterop.ps1` and `Test-PyroWaveInterop.ps1`. Local evidence includes `artifacts/phone-capabilities.local.json`, the stage/fragment logs, and timestamped `artifacts/pyrowave-interop-*` folders containing fixture hashes, GPU identities and per-case output. The installed host was not restarted or reconfigured during phone testing.
