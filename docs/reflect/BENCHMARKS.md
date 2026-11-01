# Reflect benchmark protocol

No performance results have been fabricated. This protocol defines the measurements needed before choosing defaults or claiming an improvement.

## Record the complete setup

For every run, record host/client commit, Windows build, NVIDIA driver, GPU, Android model and firmware, decoder component, codec/profile/bit depth/chroma, capture mode, display resolution/refresh, stream rate, bitrate, preset, multipass, AQ, HDR, frame-pacing policy and network conditions. Keep APK and executable SHA256 values with the results.

Use a wired host and a consistent Wi-Fi location for the primary baseline. Record AP model, band and signal conditions. Disable unrelated downloads. Keep device brightness and thermal starting condition comparable. Test charging and battery-powered operation separately because power/thermal policy can differ.

## Workloads and matrix

| Workload | Baseline settings | Additional dimensions |
| --- | --- | --- |
| Desktop text, scroll and cursor | 1080p60 SDR, HEVC and H.264 | Native aspect/resolution, 120 FPS, fine text quality |
| Repeatable game camera motion | 1080p60 and 1080p120 SDR | 1440p where practical, AV1 vs HEVC, preset/multipass |
| Dark and bright HDR scenes | HEVC Main10, HDR display confirmed | AV1 10-bit if supported, metadata/range validation |
| Static desktop and idle | 60 and 120 FPS settings | CPU wakeups, decoder liveness, power, return to motion |
| Network disturbance | Stable baseline then controlled jitter/loss | Burst loss, reconnect, reduced bandwidth and buffer recovery |
| Lifecycle | 20 launch/disconnect cycles | Rotation, DeX, screen lock, PiP, background/foreground, HDR changes |

Start with 60-second warm-up and three 120-second measured runs in randomized A/B order. Run a 30-minute soak for changes affecting queues, CPU polling, GPU use or codec recovery. Use at least 100 physical input-to-photon samples when comparing interaction latency.

## Metrics

- Host capture duration, capture-to-submit delay, encode duration, packetization/FEC duration, send queue depth and frame age.
- Client receive-to-complete-frame, complete-frame-to-decoder submission, decoder completion, output queue residence, intended presentation and actual presentation callbacks where reliable.
- p50/p95/p99 and maximum for each stage, plus late/dropped/recovered frames, audio underruns and reconnect failures.
- CPU/GPU utilization, encoder utilization, memory growth, phone temperature and battery/power trends.
- Quality: inspect repeatable crops and motion, then use offline quality metrics with aligned source/reference frames where applicable. Text and HDR need separate evaluation from average game-image scores.

Host and Android monotonic clocks are not automatically synchronized. Do not subtract raw timestamps across machines. Stage-local timings and host-provided processing durations are useful; true input-to-photon requires synchronized instrumentation or a high-speed camera/LED setup. A decoder-only latency overlay is not end-to-end latency.

## Acceptance gates

An optimization must improve the intended metric outside run-to-run noise and preserve relevant p95/p99, quality, stability and battery constraints. At 120 FPS the frame interval is about 8.33 ms, but that is not a promise of 8.33 ms total latency.

Reject changes that improve average latency while increasing stalls, corrupt frames, sustained memory, thermal throttling or failed reconnects. Archive both successful and failed experiment settings. Never compare different bitrate, codec or pacing settings without labeling the difference.
