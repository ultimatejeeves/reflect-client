# Reflect development roadmap

Historical Apollo bootstrap document. Current direction: [Vibepollo migration](HOST_MIGRATION.md) and [Android parity](ANDROID_PARITY.md). Android decoder follow-up: [repair notes](DECODER_RECOVERY.md).

Build reliability and decoder correctness come first. The initial platform is Windows with RTX 5090, streaming to Galaxy S23 Ultra and Galaxy Tab S9+. Existing AMD/Intel and non-Windows code remains supported in source; expanding hardware validation follows the NVIDIA/Samsung baseline.

## Milestone 1 Establish a trustworthy baseline

Deliverables: reproducible host/client builds, independent fork identity, automated tests, dependency lockfiles, explicit build provenance, and a local capability report from each Samsung device. Resolve the open pairing/parser/decoder defects listed in the review before public release.

Record Android OS/One UI, codec names, supported codec profiles, low-latency feature support, HDR display capabilities, display modes, thermal state, network link and negotiated session settings. Device marketing specifications are not sufficient to select a streaming codec.

Success: both clients pair, launch, disconnect and reconnect repeatedly; switch physical/virtual displays; pass 60 and 120 FPS SDR sessions at 1080p and appropriate native modes; run a 30-minute soak without codec stalls or unbounded memory growth. No latency improvement claim is required to complete this milestone.

## Milestone 2 Repair and simplify Android frame pacing

Replace the renderer's overlapping fast paths with explicit policies: immediate/latest, Balanced via Choreographer, and smoothness. Each output buffer must be released exactly once, including error/recovery paths. Use a bounded, synchronized timing tracker and account separately for received, decoded, dropped, queued and presented frames.

Move decoder liveness detection into the existing recovery protocol. A watchdog must distinguish a stalled decoder from a quiet stream or network outage and must not flush while other threads own buffers. Test recovery, HDR switches, activity surface recreation, PiP, DeX, 60-to-120 Hz changes and returning from background.

Android exposes codec low-latency support, but vendors must implement it. Probe capabilities and test Qualcomm-specific hints one at a time; retain a fallback configuration. [AOSP low-latency decoding](https://source.android.com/docs/core/media/low-latency-media)

Qualcomm lists AV1 playback for Snapdragon 8 Gen 2. That makes AV1 worth testing on this target family, but does not establish its low-latency profile support, HDR behavior or sustained decode rate in the actual Samsung firmware. Compare HEVC and AV1 on-device at matched quality and matched bitrate. [Qualcomm platform specifications](https://www.qualcomm.com/smartphones/products/8-series/snapdragon-8-gen-2-mobile-platform)

## Milestone 3 Modernize NVIDIA encoding with compatibility gates

Apollo currently compiles NVENC SDK 12.0 headers and selects API 11 for H.264/HEVC and API 12 for AV1. Its code explicitly warns that changing SDK headers requires reviewing structure versions and incompatible fields. Keep a working old-API path and feature-probe newer capabilities.

Sunshine is a useful backport source. Review `fb3d85cf` (dynamic SDK selection), `a5af7907` (split-frame encoding), `0db9f73e` (H.264 intra refresh and recovery-point signaling), `5bacfd59` (SPS bitstream restrictions), and `f2c9f34d` (legacy preset/dependency compatibility). Those revisions are available in the local `sunshine` remote. The dynamic-SDK change spans build configuration and many encoder types; it is not a safe single-file cherry-pick.

NVIDIA SDK 13.0 adds Blackwell H.264 high-bit-depth and 4:2:2 encoding plus HEVC 4:2:2. Treat these as desktop-quality experiments after protocol and client capability negotiation exist. Common Android hardware decode paths should not be assumed to accept 4:2:2 or 4:4:4. [NVIDIA 13.0 release notes](https://docs.nvidia.com/video-technologies/video-codec-sdk/13.0/read-me/index.html)

SDK 13.1 documents iterative UHQ encoding, application-allocated CUDA arrays and hierarchical B-frame references. These features target different workloads; iterative encoding and frame reordering are not default choices for interactive streaming. The CUDA-array feature also does not automatically eliminate copies in Apollo's D3D11 desktop-capture path. [NVIDIA 13.1 release notes](https://docs.nvidia.com/video-technologies/video-codec-sdk/13.1/read-me/index.html)

NVIDIA removed legacy presets/rate controls beginning with driver 590. Apollo's native encoder already uses P1–P7 and CBR, so those removals do not by themselves prove this path is broken on driver 621.01. Independently test the bundled FFmpeg path and every supported GPU generation. [NVIDIA deprecation notices](https://docs.nvidia.com/video-technologies/video-codec-sdk/13.1/deprecation-notices/index.html)

Controlled experiments, in order:

1. Sweep P1/P3/P4/P5, single/quarter/full multipass and spatial AQ at identical bitrate. Measure game GPU contention, p95/p99 encode time, visible quality and frame drops.
2. Compare AV1 8/10-bit and HEVC 8/10-bit with client hardware decoder and colorspace confirmed. Keep H.264 compatibility.
3. Evaluate split-frame encoding only where supported and useful. It can change quality and scheduling; higher encoder throughput is not equivalent to lower input-to-photon latency.
4. Add per-session bitrate reconfiguration with network feedback and hysteresis. Distinguish loss, congestion, decoder overload and Wi-Fi interference before reducing quality.
5. Profile capture-to-encode copies and synchronization; consider a small bounded surface pool only if traces show overlap will help without queue growth.

## Milestone 4 Improve HDR and desktop quality

Validate the entire color path: Windows display mode, captured texture format, RGB-to-YUV conversion, range/primaries/transfer signaling, HDR metadata, decoder output and Samsung panel state. Use reference patterns for near-black, highlight clipping, gradients and text. Compare screenshots only where they preserve the relevant color information; camera measurements may be necessary for panel behavior.

Add clear UI explanations for unsupported combinations and a safe SDR fallback. Implement separate gaming and desktop profiles only after measured defaults exist. Desktop text quality may justify higher bitrate or chroma sampling on capable clients, while game latency usually favors 4:2:0.

DLSS, NVIDIA Reflex and frame generation are not drop-in NVENC enhancements. The game owns its rendering pipeline and input sampling; Reflect should measure frame arrival and queueing under those game settings rather than advertise unsupported integration.

## Milestone 5 Network resilience and input quality

Measure wired-host/Wi-Fi-client behavior, burst loss, jitter, congestion and reconnect. Add negotiated adaptive bitrate and FEC, with conservative defaults and a fixed-mode fallback. Avoid replacing the GameStream-compatible transport before there is evidence that it is the bottleneck.

Test controller hotplug, rumble, gyro, mouse capture, relative/absolute cursor modes, keyboard layouts, IME, clipboard permissions and DeX display transitions. Include host disconnect during held keys/buttons and verify release cleanup. Test audio route changes, Bluetooth latency and long-session A/V drift.

## Milestone 6 Release engineering

Create stable release signing, update feeds, crash diagnostics with explicit consent, dependency SBOMs and reproducible native dependencies. Audit installer/service names, data directories, firewall rules, ports and virtual-display driver packaging so Reflect can be installed safely alongside the baseline. Keep licenses and notices, and document driver redistribution rights before distributing an installer.

Working name recommendation: **Reflect Host** is clear and pairs directly with Reflect. **Reflect Relay** or **Reflect Beacon** are alternatives if a more distinctive server name is desired; availability has not been checked.
