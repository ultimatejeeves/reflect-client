# Reflect Host migration to Vibepollo

November 1, 2026. User approved Vibepollo as the host baseline and desktop-client functional parity as the Android objective.

Selected source: `Nonary/Vibepollo` commit `1ea3f02717b72de49799cbe06295ea82e236ed60` from master. This is an explicitly pinned development snapshot, not a claim that it is a tested stable release. `sources.json` also pins the desktop client and protocol reference.

The host's `upstream` now points to Nonary/Vibepollo; `apollo` retains ClassicOldSong/Apollo. `origin` remains the user's `ultimatejeeves/reflect-host`. The old Reflect commit `f40d1a60` is preserved on `baseline/reflect-apollo`. The migration starts with Vibepollo's source tree, preserves the old Reflect history as a merge parent, and restores the workspace scripts and historical review notes. No force push or deletion of the old branch is required.

Prior Apollo patches were individually checked. The RTSP bounds guards and rational refresh-rate normalization are still needed and have been adapted with component regression tests. The pairing lifetime fix is already present in the new implementation. Other patches are not blindly reapplied: Vibepollo already has different pairing/session management, version discovery, web build/lockfiles, encoder code and display management. The historical fixes remain review references on the preserved branch; their correctness must be reassessed against the new implementations. Existing Android decoder repairs are independent and retained.

The local directory stays `repos/apollo` to avoid moving the workspace. The new build tree is `repos/apollo/build/vibepollo`, separate from the previous Apollo build. Build-Host.ps1 explicitly identifies a Reflect development build and update repository. PyroWave is enabled; optional browser WebRTC is disabled for this native-host development profile and is not part of Android desktop-client parity. Release packaging/service names remain inherited and require a later coordinated branding migration.

Inherited release, signing, maintainer-server and issue-comment automation is preserved under `.github/upstream-workflows`, outside GitHub's active workflow directory. Reflect's active source-metadata workflow checks the pinned ancestor and parity inventory. It is not a substitute for native compilation or streaming tests. No automatic releases or maintainer notifications are configured by this migration.

The previous host's 209/211 selected test result and compiled executable apply only to the Apollo baseline. They must not be attributed to Vibepollo. Current migration verification and build results are recorded below after execution; no host service, virtual driver or GPU driver is installed by this source migration.

## Verified migration results

- Native host `build/vibepollo/sunshine.exe`, required helper dependencies, PyroWave library and both web entrypoints compiled successfully. Build profile: RelWithDebInfo, PyroWave on, browser WebRTC off, Reflect development version `0.1.0-reflect.dev`.
- Four selected CTest targets passed: `test_fast_nvenc_api`, `test_component_pyrowave_policy`, `test_component_reflect_rtsp_parse`, and `test_component_reflect_refresh_rate`. These are pure tests; no GPU/display/controller runtime tests were run.
- Source-pin verification checked the Vibepollo ancestor and all 62 desktop preference entries against the pinned reference checkout. PowerShell scripts passed syntax parsing.
- Android retains the decoder-recovery change already verified with 67 passing unit tests and lint reporting zero errors. This migration only updates Android documentation and source manifests.
- Local logs: `artifacts/vibepollo-configure-final.log`, `vibepollo-targeted-build.log`, and `vibepollo-component-tests.log`. The earlier full build attempt was narrowed deliberately; it is not a claim that the full suite or release packaging passes.
- An initial configure/build mixed the R: alias and physical compiler include paths. A fresh physical-path configuration resolved that environment issue. The host script now consistently uses physical paths; Android alone needs the NDK alias.

Remaining: on-device capability capture and streaming interoperability, full host test/packaging qualification, Android protocol adaptation, and the workstreams in ANDROID_PARITY.md. No release was published or runtime driver/service installed. The imported upstream automation remains archived; remote Reflect CI has not yet been observed passing.
