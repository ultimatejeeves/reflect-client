# Vibepollo assessment

November 1, 2026. Inspected `Nonary/Vibepollo` master at `1ea3f02717b72de49799cbe06295ea82e236ed60`. Fetched as `vibepollo/master` in the Apollo checkout, without switching the working tree, installing its services or drivers, or changing Reflect's upstream.

Recommendation: evaluate Vibepollo as the host foundation before implementing more major Apollo extensions. Keep Reflect Android and its independent decoder work. Start with standard HEVC/AV1 interoperability on RTX 5090, Galaxy S23 Ultra and Galaxy Tab S9+, then measure capture, HDR and frame pacing before deciding on migration.

| Planned work | Vibepollo evidence | Implication |
| --- | --- | --- |
| Display automation and recovery | README describes layout recovery, headless handling and per-client virtual displays; source includes a substantial display-helper subsystem. | Avoid independently recreating this before testing its behavior on the actual desktop. |
| Modern Windows capture | README documents service-mode WGC and capture switching for login/UAC. | Direct overlap with capture modernization; performance claims still need measurement. |
| NVIDIA modernization | `src/nvenc/nvenc_api.h` contains driver-version filtering and reviewed API candidates through 13.0; encoder code includes split-encode selection and version-dependent fields. | Already substantially ahead of our Apollo baseline's SDK 12.0 integration. This is source inspection, not a successful local NVENC test. |
| Frame generation and pacing | README documents RTSS/NVIDIA controls, generated-frame capture and Smooth Motion integration. | Relevant to RTX 5090, but generated frames are not a substitute for low input latency. Validate individually. |
| Android decoder correctness | Vibepollo is the host; the linked special-feature client is `Nonary/moonlight-qt`. | Our Artemis/Reflect timing and recovery repairs remain applicable. |
| PyroWave/VRR | Protocol documentation recommends Nonary's desktop client; stock clients negotiate H.264/HEVC/AV1. PyroWave targets high-bandwidth wired LANs. | No automatic PyroWave/VRR support on the Samsung Android clients; a separate port and power/bandwidth study would be needed. |

Compared with the fetched Apollo baseline, Git reports 22 Apollo-only and 2,626 Vibepollo-only commits, and 2,639 changed files. Counts describe ancestry and tree differences, not independent features or verified fixes. This is a substantially different host, so treating it as a small collection of patches would misrepresent the migration effort.

The next evaluation should use an explicit stable tag/commit, record feature availability for that revision, reproduce its build and test gates, and test configuration/driver coexistence deliberately. The master branch and published stable release are distinct; a master README alone does not establish that every feature is present in an installed stable build.

Sources: [Vibepollo](https://github.com/Nonary/Vibepollo), [pinned NVENC API code](https://github.com/Nonary/Vibepollo/blob/1ea3f02717b72de49799cbe06295ea82e236ed60/src/nvenc/nvenc_api.h), [PyroWave protocol](https://github.com/Nonary/Vibepollo/blob/1ea3f02717b72de49799cbe06295ea82e236ed60/docs/pyrowave-protocol.md), [desktop client](https://github.com/Nonary/moonlight-qt).

This is a feature/source comparison, not a full security audit, local build certification, or target-device benchmark of Vibepollo. Reflect's existing forks and baseline remain preserved.
