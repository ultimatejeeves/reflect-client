# Decoder timing and recovery repair

November 1, 2026. Development branch: `fix/decoder-timing-recovery`.

The inherited client recorded enqueue times after submission into an unsynchronized, unbounded `LongSparseArray`. Output could arrive first, and discarded outputs left records behind. The latest-frame render path swallowed codec exceptions and bypassed the common recovery checkpoint with `continue`. Its output/render counts were incomplete. A watchdog after the render loop could only run during shutdown, and the input-buffer wait loop could postpone joining recovery indefinitely.

The repair adds synchronized bookkeeping capped at 256 pending timestamps. Frame timestamps are registered before submission, consumed on every output dequeue (including discarded frames), removed on failed submission, and cleared during coordinated recovery and cleanup. Codec configuration buffers do not create pending frame records. Decoder averages divide by measured samples, and output statistics updates share a lock with window snapshots/rotation.

Both render paths now reach the recovery checkpoint and count outputs. Failed output-buffer releases reach the existing exception handler rather than being silently retried with uncertain ownership. The input wait loop joins recovery and returns failure to request a fresh IDR frame afterward.

The watchdog now checks during playback. It requires pending frames, at least 1.2 seconds without output progress, and input activity within 250 ms. A blocked input-buffer wait counts as activity; an idle network stream alone does not. Requests use the existing reset/recreate state machine and its retry limit, not a direct output-thread flush. Input, output and Choreographer must quiesce before recovery invalidates buffers. Shutdown does not issue new watchdog requests.

Validation: Android build succeeded for all four debug ABIs. All 67 unit tests passed (51 existing, 16 new); lint reported zero errors and 458 warnings. New tests exercise bounded/concurrent tracking, reordered/missing outputs, stall deadlines, quiet streams, reset cleanup, output-before-submit-return, configuration buffers, input-wait recovery, fast-path recovery/counters, shutdown, and non-retried failed releases. Codec integration tests use Mockito/Robolectric, not a physical decoder.

Local evidence: `artifacts/decoder-recovery-verification.log`. ARM64 build saved as `artifacts/reflect-arm64-decoder-recovery.apk`. Initial bootstrap results in `VALIDATION.md` remain a historical baseline.

Remaining validation: Samsung 60/120 FPS sessions in Balanced and immediate modes, HDR/surface changes, Wi-Fi interruptions, repeated reconnects, and thermal soak. Native calls that themselves never return cannot be rescued by this synchronous loop watchdog. Full presentation-time and frame-drop telemetry remain follow-up work; a release to Surface is not proof that a frame reached the display. No latency improvement is claimed yet.
