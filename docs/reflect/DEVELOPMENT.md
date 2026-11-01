# Reflect development setup

Workspace: `D:\Projects\Systems\Reflect Streamer`. Open `Reflect.code-workspace`. Component Git histories remain separate. Use `origin` for Reflect and `upstream` for Nonary/Vibepollo on the host and maintained ClassicOldSong Artemis on Android; `supplied` retains the originally linked client remote. `sunshine` and `moonlight` are reference remotes for selective backports.

## Toolchain

- Git for Windows, official Node.js, Java 21 and the Android SDK were already installed.
- Gradle wrappers download Gradle 8.13 for maintained Artemis; the initial supplied baseline used 8.7.
- The Android build installed its requested NDK 27.0.12077973 and Build Tools 35. Compile SDK 36 was present. Existing accepted Android SDK licenses were used.
- MSYS2 UCRT64 is isolated under `.tools/msys64`. Its GCC, CMake, Ninja, OpenSSL, curl, MinHook, miniupnpc, oneVPL, Opus, cppwinrt, NSIS and pkgconf packages were installed. Exact package versions should be captured alongside release builds; the package manager is rolling, not a hermetic toolchain pin.
- Apollo fetches its required Boost 1.89 with the repository's SHA256 and uses recursively pinned submodules. Official Node.js avoids the MSYS2 Node runtime issue documented upstream.

## Commands

```powershell
# Reinstall a missing local host compiler environment
.\scripts\Bootstrap-Host.ps1

# Capture host toolchain and hardware metadata locally
.\scripts\Get-Environment.ps1

# Build Android and run its unit tests and lint
.\scripts\Build-Client.ps1 -Verify

# Compile host and native test executable
.\scripts\Build-Host.ps1 -Tests

# Remove the temporary R: folder alias after builds finish
.\scripts\Exit-BuildEnvironment.ps1
```

The R: alias is visible in Explorer while present. It maps this exact folder and is not a disk allocation or partition. Scripts refuse to overwrite a different drive/mapping. Another unused drive can be selected with `-DriveLetter`; changing it requires reconfiguring build directories that cache absolute paths. Keep the alias available while using artifacts or test fixtures that contain R: paths.

`local.properties` is generated locally with the existing SDK path and remains ignored. Do not commit SDK paths, debug/release keystores or signing passwords.

Android APKs: `repos/artemis/app/build/outputs/apk/nonRoot_game/debug/`. Use the ARM64 build for both Samsung targets. Build logs and saved APKs under `artifacts` are local-only. No APK has been installed on either target by this setup.

Host executable: `repos/apollo/build/vibepollo/sunshine.exe` when compilation succeeds. The binary still uses the inherited executable name. Do not install it over the active Apollo service during development. Tests that enumerate or change audio/display/input devices need separate selection from pure unit tests.

The Vibepollo web interface builds from `src_assets/common/assets/web` and `web-legacy`; CMake manages both locked builds. `npm audit --omit=dev` checks the production graph; the complete audit also includes the local HTTP fixture server. Never use `npm audit fix --force` without reviewing API and major-version changes.

## Fork maintenance

Create a feature branch from `reflect/main`; keep implementation, regression tests and notes together. Fetch upstream without blindly updating submodules to their remote heads. For a reviewed upstream merge, run `git submodule update --init --recursive` afterward and record the new source revisions.

The original supplied client branch is a comparison baseline only. GitHub may display the original fork-parent relationship because the fork was created before switching to maintained Artemis; the actual source ancestry and configured upstream now use ClassicOldSong's maintained branch.

Public release preparation still requires signing, production branding, package/service identity separation, driver redistribution review, hardware streaming tests and release smoke tests. A successful compile is not a release certification.

## Current baseline

See HOST_MIGRATION.md and ANDROID_PARITY.md. Older verification counts in this workspace apply to the Apollo bootstrap, not the Vibepollo migration. New native builds use `build/vibepollo`.

The Vibepollo host builds from the physical workspace path and does not need the R: alias. Build-Host.ps1 -Tests builds the host and four selected pure test targets; Test-Host.ps1 runs them. A full native/test/packaging build remains a separate qualification task.
