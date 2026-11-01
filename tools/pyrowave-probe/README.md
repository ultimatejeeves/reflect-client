# PyroWave offscreen interop probe

This diagnostic executable uses the pinned PyroWave C API without modifying the read-only desktop reference. It generates a deterministic 8-bit YUV image on the PC, encodes it, decodes it on the PC to create a golden image, then lets an Android device decode the same packets without needing a mobile encoder. The PC golden image must remain within 1 luma / 2 chroma code values of the source. Android uses the same tolerance against the golden image.

The custom `RFPW0001` fixture contains width, height, chroma and packet count as little-endian uint32, followed by length-prefixed native PyroWave packets. It is a bounded test container, **not GameStream/Sunshine wire framing**. Dimensions are even, 2–4096; packet count is 1–1024; a packet is at most 16 MiB and total input at most 64 MiB. A process watchdog exits after 30 seconds. No Vulkan validation layers or device-loss recovery are supplied by this standalone probe.

Commands:

```
reflect-pyrowave-probe probe
reflect-pyrowave-probe encode frame.fixture frame.golden 1920 1080 420
reflect-pyrowave-probe decode frame.fixture frame.golden compute
```

`probe` reports device, decoder and encoder creation separately. Encoder failure does not disqualify a decoder. `decode` accepts `auto`, `compute` or `fragment`; `auto` uses the codec's preferred path. The verified SM-S928U compute path passes; the preferred fragment path currently fails the same reference comparison on both that phone and the RTX 5090. Do not silently use it for Android integration.

From the Reflect workspace, run `scripts/Build-PyroWaveInterop.ps1`, then `scripts/Test-PyroWaveInterop.ps1 -Serial DEVICE_ID`. The latter selects compute by default and saves five cases, hashes, GPU identity and logs under ignored `artifacts`. `-DecoderPath fragment` can reproduce the unqualified path separately. Helpers and fixtures stay in `/data/local/tmp/reflect-pyrowave` on the selected device; no root, APK modification or host service restart is needed.

This is CPU upload/readback around GPU coding of a single synthetic frame. A large bit budget avoids quantization masking interoperability errors. It neither models practical streaming bandwidth nor measures steady-state FPS, end-to-end latency, Android Surface output, HDR, packet loss, or thermal behavior. Codec and Granite licenses remain in the pinned upstream source.
