# Reflect codebase review

Review date: November 1, 2026. Source revisions are pinned in `sources.json`. The main conclusion is that the inherited capture and transport foundations are substantial, but build regressions, decoder correctness, input validation, and trustworthy measurements should be addressed before aggressive encoder tuning.

This is a broad architecture and risk review with focused source inspection and executable build/test checks. It is not a claim that every line or vendored dependency has been audited. End-to-end streaming, thermal behavior, HDR accuracy, and device recovery still require the two Samsung clients. Findings below distinguish verified failures, source-level defects, and experiments.

## Repository lineage and build health

- Apollo baseline: `adc5c5a0`, May 21, 2026. Its history diverges from fetched Sunshine by 457 Apollo-side and 893 Sunshine-side commits. These are ancestry counts, not 893 missing independent fixes; merges and equivalent backports make blind merging inappropriate.
- Supplied Artemis baseline: `42eb11ab`, February 3, 2025. Maintained Artemis `c5cf27f4`, September 9, 2026, is 337 commits ahead and a fast-forward. Reflect now uses maintained Artemis with the original baseline preserved.
- Apollo uses C++23, CMake/Ninja, Boost 1.89, FFmpeg and several Git submodules, a Vue/Vite web interface, and native Windows APIs. Windows builds use MSYS2 UCRT64, not the Visual Studio C++ toolchain.
- Maintained Artemis uses Java, JNI/C, AGP 8.13, Gradle 8.13, NDK 27.0.12077973, compile SDK 36, target SDK 34, and minimum SDK 21. Its native protocol library is independently pinned from Apollo's copy.
- Neither checkout included a GitHub Actions workflow at review start. Apollo has native tests; maintained Artemis has Robolectric tests. The older supplied client successfully built on this machine after working around the NDK path-space limitation.
- A clean host compile found a real upstream compile error in AMF profile configuration. The first maintained client test execution ran 51 tests with five failures from stale fixtures. These are recorded as baseline failures, not regressions introduced by Reflect.

## Architecture and areas inspected

| Area | Important files | Responsibility and review observations |
| --- | --- | --- |
| Host startup and configuration | `src/main.cpp`, `config.cpp`, `entry_handler.cpp` | Process initialization, encoder options, web service and platform setup. Configuration provenance and units need to remain explicit. |
| Client authentication and launch | `src/nvhttp.cpp`, `crypto.cpp`, `httpcommon.cpp` | Pairing, client certificate identities, permissions, app launch, clipboard endpoints. First paired client receives full permissions; subsequent clients have restricted defaults. |
| Management UI | `src/confighttp.cpp`, `src_assets/common/assets/web`, `vite.config.js` | Cookie authentication, IP-origin policy, JSON endpoints and Vue UI. Content-type checks are present; cookie/session concurrency and body limits deserve dedicated negative tests. |
| Session negotiation | `src/rtsp.cpp`, `src/rtsp.h` | Parses session capabilities, packet size, video/audio format, refresh and bitrate, then constructs streaming sessions. Highest priority for strict parsing and fuzzing. |
| Capture and conversion | `src/platform/windows/display_base.cpp`, `display_vram.cpp`, `display_ram.cpp` | DXGI desktop duplication, GPU surfaces, cursor composition, color conversion and capture pacing. Existing device-lock starvation mitigation should not be removed casually. |
| Encoder orchestration | `src/video.cpp`, `video_colorspace.cpp` | Encoder probing, per-session encode/capture flow, timestamps, format negotiation and fallback backends. Maintain fallbacks even for an NVIDIA-first product. |
| Native NVENC | `src/nvenc/nvenc_base.cpp`, `nvenc_d3d11*.cpp`, `nvenc_config.h` | Direct D3D11 and CUDA interop, runtime capability queries, P1–P7, ultra-low-latency CBR, RFI, asynchronous completion. SDK ABI compatibility is deliberate. |
| Display lifecycle | `src/display_device.cpp`, `src/platform/windows/virtual_display.cpp`, `third-party/libdisplaydevice` | Virtual screen identity, topology, mode changes and cleanup. This is a separate reliability boundary from encoding. |
| Video transport | `src/stream.cpp`, `network.cpp`, `rswrapper.c`, `third-party/nanors` | RTP packetization, FEC, encrypted control/audio/video, send batching and pacing. There are existing processing-latency timestamps, but no full percentile trace across both devices. |
| Host audio | `src/audio.cpp`, `src/platform/windows/audio.cpp` | Capture, Opus, channel mapping and device switching. Audio buffering and clock drift must be measured independently of video. |
| Host input and processes | `src/input.cpp`, `process.cpp`, `src/platform/windows/input.cpp`, `misc.cpp` | Per-input permission gates, virtual controllers and process launch under user/elevated tokens. Do not interpret configured launch commands as an unauthenticated command execution defect. |
| Client discovery and connection | `PcView.java`, `AppView.java`, `computers`, `discovery`, `nvstream/NvConnection.java` | Host discovery, identity, pairing, connection lifecycle and launch parameters. Large activities make lifecycle behavior expensive to change safely. |
| Android HTTPS | `nvstream/http/NvHTTP.java` | Supports pinned self-signed host certificates plus validated CA certificates. The custom hostname verifier is not an unconditional trust-all implementation. |
| Native Android stream path | `app/src/main/jni/moonlight-core`, `nvstream/jni/MoonBridge.java` | Moonlight common C, ENet, packet reconstruction, crypto, JNI and audio/video callbacks. Native library provenance and fuzz tests are important. |
| Android decoding | `binding/video/MediaCodecDecoderRenderer.java`, `MediaCodecHelper.java` | Vendor decoder hints, SPS patching, recovery, output buffer ownership and Choreographer pacing. Newer low-latency changes introduced correctness risks. |
| Android audio and input | `binding/audio/AndroidAudioRenderer.java`, `binding/input`, `ui/StreamView.java` | AudioTrack buffer fallbacks, gamepad sensors, mouse capture, touch, IME and DeX. Validate switching input devices during an active session. |
| Profiles and assets | `profiles/ProfilesManager.java`, `PosterContentProvider.java`, `grid/assets`, `utils/CacheHelper.java` | Persistent profiles, import/export and externally accessible poster URIs. Validate malformed persisted state and caller-controlled paths. |
| Optional 3D | `utils/Stereo3DRenderer.java`, LiteRT/OpenCV dependencies | Additional GPU work and larger APKs. Keep disabled for core latency benchmarking; measure its own quality/performance separately. |

Paths in this table are relative to the component checkout; client Java paths are under `app/src/main/java/com/limelight`.

## Verified defects and immediate changes

### H1 Host Windows build fails on an absent profile field

**Priority P1, reproduced by compilation, fixed.** `src/video.cpp` referenced `cfg.profile` in AMF H.264 options, but `video::config_t` in `src/video.h` has no such member. Upstream commit `c71ea0a8` introduced the reference. All Windows builds compile this code, even on NVIDIA hardware. Reflect removes the incomplete profile override and retains the separately added AMF coder option. Adding a negotiated profile later needs a protocol change and tests, not an uninitialized field.

### H2 RTSP SETUP and SDP attribute parsing can access outside input bounds

**Priority P1, source-confirmed, guarded with regression tests.** `cmd_setup` advanced the result of `find('=')` even when the separator was absent. `cmd_announce` indexed `val[val.size()-1]` on an empty value and did not validate the colon/name. Reflect adds checked parsing, returns HTTP-style RTSP 400 for malformed fields, and includes an unterminated last SDP line. Parser tests cover normal targets, missing delimiters, empty names/values and trailing whitespace. This patch does not claim to complete RTSP numeric validation or prove remote exploitability.

### H3 Web dependencies are unrepeatable and contain reported advisories

**Priority P1 for dependency hygiene, confirmed by npm audit and build.** `package-lock.json` was ignored, and CMake ran `npm install`. The initial resolved graph reported 23 affected packages, including 16 high-severity entries. Reflect tracks the lockfile, uses `npm ci`, applies same-major updates to Vue, vue-i18n, Vite, serve and the EJS plugin, and removes the unused Codecov bundle-upload plugin. The updated graph reports four affected development packages in the `serve` fixture-server chain. These counts are package advisories, not 23 distinct remotely exploitable host defects. The fixture server is not Apollo's production HTTP server. Production-only audit and final counts are recorded with validation results.

### C1 Latest-frame path bypasses Balanced pacing

**Priority P1, source-confirmed, fixed.** At the start of the maintained renderer loop, `!preferLowerDelays` entered an immediate latest-frame release path without checking `prefs.framePacing`. It could bypass the Choreographer queue even with Balanced selected. Reflect excludes Balanced from that fast path. Physical-device cadence validation remains required.

### C2 Draining output waits for frames that are not ready

**Priority P2, source-confirmed, fixed.** The non-Balanced drain loop called `dequeueOutputBuffer(..., getOutputDequeueTimeoutUs())` even after acquiring a frame. With the default 2,000 microsecond timeout, presentation can wait again for future output. Reflect uses zero timeout only for draining already-ready buffers, preserving the wait for the first output. No measured millisecond improvement is claimed yet.

### C3 Maintained client tests have five stale fixture failures

**Priority P2, reproduced, repaired.** Two navigation tests cast a Material extended floating action button to `ImageButton`; layout inflation used an AppCompat-only theme for Material controls; two startup tests constructed unattached `Application` objects and called `onCreate`. Reflect uses `View` for click assertions, the application's actual theme, and Robolectric's attached application. Test assertions remain enabled.

### C4 Fork identity and build provenance need separation

**Priority P2, fixed for Android development.** Inherited IDs/labels identify Artemis/Diana and its update feed. Reflect uses an independent `io.github.ultimatejeeves.reflect` application ID, `.debug` development suffix, separate root ID, Reflect labels and its own release URL. The Gradle distribution gets an upstream SHA256 pin. Artwork, help text, native symbol names and host installer/service identities still need a coordinated branding pass before public release.

### H7 Pairing destroys its session before final cleanup

**Priority P1, source-confirmed, fixed and regression-tested.** Successful `clientpairingsecret` erased the session from `map_id_sess`, then passed the invalidated reference to `remove_session`. Reflect removes the premature erase and keeps final cleanup after the last use. Pairing tests now exercise actual map ownership and Apollo's named-client registration API; all nine pairing cases pass. Tests suppress state persistence so they do not alter the installed host's client database.

### H8 Integer refresh-rate rules fail after millihertz conversion

**Priority P2, reproduced by 12 display-rule test failures, fixed.** Automatic configuration represented 60 Hz as `Rational{60000,1000}` while remapping rules parsed `60` as `{60,1}`. The display library compares numerator and denominator directly. Reflect reduces automatic rates with `std::gcd` before matching. Fractional rates remain exact; an explicit regression test keeps 59.94 Hz distinct from a 60 Hz rule. Inherited fixtures now supply Apollo's millihertz units instead of Sunshine's Hz units.

### H9 Build paths and inherited test assumptions need repair

**Priority P2, fixed.** Git version queries now run in the source directory. Node resolves the physical workspace path for Vite/npm so the Windows folder alias and physical source path do not conflict. Host pairing tests use the current API and network tests expect Apollo's actual hostname fallback. Android lint fixes address literal translation percentages, API guards, obsolete lint rules, and the back-navigation superclass call without suppressing the lint task.

## Open source findings requiring follow-up

| ID | Priority | Evidence | Consequence and next action |
| --- | --- | --- | --- |
| H4 | P1 | `rtsp.cpp` ANNOUNCE directly assigns integers parsed by `utility.h::from_view`; that helper does no digit or overflow validation. | Strictly parse and range-check dimensions, rates, bitrate, slices, channels and packet size before allocation/arithmetic. Fuzz truncated, negative, huge and nonnumeric values. |
| H5 | P1 | `nvhttp.cpp` parses display mode with `atoi/atof`; `rtsp.cpp` computes warp factor divided by `session.fps`; `nvenc_base.cpp` divides bitrate by requested FPS; `stream.cpp` takes modulo `packetsize - sizeof(NV_VIDEO_PACKET)`. | Zero/invalid session values can reach unsafe arithmetic. Validate at both negotiation boundaries and add encoder/packetizer defensive checks. Do not silently clamp malformed requests into a different session. |
| H6 | P1 | Simple-Web-Server's `Config::max_request_streambuf_size` defaults to maximum `size_t`; host setup does not set it. | Establish explicit request/header/body limits, especially clipboard and JSON endpoints, and test chunked bodies. Authentication after buffering does not bound memory use. |
| C5 | P1 | Renderer `enqueueNsByPtsUs` is an unsynchronized `LongSparseArray` written from input and read/deleted from output; skipped frames are not removed; enqueue timestamp is recorded after codec submission. | Race, unbounded tracking growth and missing/biased latency measurements. Replace with a synchronized bounded tracker, record before queue submission, clear on recovery and account for every discarded frame. |
| C6 | P1 | The `WATCHDOG_C2_SLEEP` block is after `while (!stopping)` and `lastOutputNs` is not refreshed in the output loop. | It cannot recover a stalled running decoder; after stop it can flush unnecessarily. Integrate liveness detection with the existing quiesced recovery protocol, not an unsynchronized flush in a render loop. |
| C7 | P2 | Fast-path frame releases do not consistently update `numFramesOut`/`totalFramesRendered`; statistics aggregate on multiple threads. | Displayed latency/FPS may be misleading. Define submitted, decoded, dropped, queued and presented events separately and use thread-safe accounting. |
| C8 | P1 | Native OpenSSL headers identify 1.1.1q from July 2022; `.a` archives are committed for each ABI. | Rebuild native crypto against a maintained release and document source, options and hashes. Header age is verified; precise archive versions and applicable vulnerabilities need a dependency audit. Java Bouncy Castle updates do not update native OpenSSL. |
| C9 | P2 | `PosterContentProvider.openFile` accepts unmatched URIs, parses app ID without catching format errors, and passes UUID directly into cache path construction. | Add URI/UUID/app ID validation and canonical path containment tests. Do not claim unrestricted arbitrary-file reads: filenames are constrained by app ID plus `.png`. |
| C10 | P2 | `targetSdk` remains 34 while `compileSdk` is 36. | Plan modern target-SDK migration with lifecycle, foreground service, edge-to-edge, predictive back and permission tests. Raising the number alone is insufficient. |
| C11 | P2 | All five packaged ARM64 libraries pass ELF 16 KB load alignment inspection; APK passes `zipalign -c -P 16 4`. | Physical 16 KB runtime testing remains pending; binary alignment alone does not establish runtime compatibility. |
| H10 | P2 | Two configuration consistency integration tests fail: missing documentation/locale entries and differing section order. | Reconcile Apollo-specific options with inherited documentation and UI expectations; do not disable tests to hide the discrepancy. |
| P1 | P1 before release | No end-to-end test harness, release signing, driver-package review, or verified installer identity migration exists yet. | Add capability diagnostics, protocol regression tests and a release gate. Preserve upstream licenses and attribution; review virtual-display driver redistribution terms independently. |

## Performance opportunities and constraints

Apollo already avoids B-frame reordering and lookahead in its native NVENC path, uses one-frame VBV sizing where available, and keeps GPU-resident input surfaces. That is a strong low-latency starting point. The encoder still operates with a single registered input/output-buffer flow and waits for completion; deeper pipelining must not accumulate latency simply to raise throughput.

Capture pacing includes DXGI timeouts, fractional-rate conversion, retry sleeps and a deliberate lock-starvation workaround. Profile these paths under static desktop, rapid cursor movement, full-screen game, HDR switching and reconnect before changing sleeps or lock behavior.

The renderer has overlapping pacing mechanisms, vendor hints, broad `catch (Throwable)` blocks and recovery state. Simplifying those into explicit policies with buffer-ownership tests is likely more valuable initially than adding more low-latency flags. Firmware-specific codec behavior must be observed on the S23 Ultra and Tab S9+.

The full NVIDIA roadmap and measurement criteria are in `ROADMAP.md` and `BENCHMARKS.md`. No end-to-end performance gain is claimed from this review alone.
