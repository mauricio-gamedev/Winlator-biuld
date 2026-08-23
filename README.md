# Winlator Build

Custom Android Windows-compatibility launcher/runtime project based on Winlator, focused on a clean architecture, modular components, strong Mali support, diagnostics, and device/game-aware presets.

> **Development status:** experimental / pre-release. APKs published during Core 0.1 are validation builds and may contain incomplete runtime paths.

## Project status

**Phase:** Core 0.1 — runtime bootstrap and Android integration

The project has moved beyond the initial repository skeleton and is now validating the real Android runtime path end-to-end.

### Current validated gates

- Android application builds successfully through CI.
- Upstream Winlator source is pinned as a submodule and patched deterministically.
- RootFS discovery and validation are working.
- Box64 is packaged, executable, and launches on ARM64 Android.
- Wine baseline files and prefix preparation are present.
- Minimal containers can be created and activated.
- `XEnvironment`, guest launcher, graphics preparation, audio preparation, and WinHandler initialization are reached.
- Persistent Session Gate diagnostics survive the guest session closing.
- The current blocking issue has been isolated to the Wine loader handoff/re-execution path under Box64 on rootless Android.
- A Box64 `wine-preloader` bootstrap path is currently being validated to remove that handoff failure.

### Immediate milestone

Core 0.1 is not considered complete until a container can open the Wine desktop and remain alive reliably. After that gate is stable, work expands to graphics translation, audio/input validation, game execution, presets, Mali optimization, and compatibility work.

## Architecture

The repository is organized around three main layers:

1. **App / Integration** — Android UI, launcher, profiles, settings, input, updater, diagnostics, and upstream integration surfaces.
2. **Engine / Middle layer** — device detection, game profiling, runtime orchestration, compatibility policy, presets, graphics/audio/input management.
3. **Runtime Core** — RootFS/glibc, Wine, Box64/Box86, graphics translation layers, Mesa-related components, audio/runtime dependencies.

## Design principles

- Keep upstream changes traceable and reproducible.
- Prefer modular components over a monolithic APK.
- Do not merge entire forks blindly; import proven changes selectively.
- Treat Mali GPUs as a first-class target.
- Keep safe defaults and advanced controls separate.
- Make diagnostics and rollback available early.
- Never hide component versions or applied compatibility patches.
- Do not declare a runtime gate solved until it has been reproduced on-device.
- Keep CI, runtime instrumentation, and user-facing behavior separable so diagnostics can be removed cleanly from production builds.

## Repository layout

```text
Winlator-biuld/
├── app/
├── engine/
├── runtime/
├── components/
├── profiles/
├── patches/
├── scripts/
├── docs/
├── tests/
├── validation/
└── third_party/
```

## Core 0.1 goal

The first functional milestone must be able to:

- start the Android app;
- create a container;
- start and keep a Wine session alive;
- show the Wine desktop;
- execute a Windows `.exe`;
- run Box64 correctly;
- provide functional audio and input;
- support the initial DX9/DX11 path;
- expose at least one Mali-safe graphics path;
- generate useful diagnostic logs.

Advanced auto-optimization, large game-profile databases, runtime download/update systems, and experimental performance patches come only after the base runtime is stable.

## Development APKs and Releases

GitHub Actions remains the source of truth for validation builds. APKs selected for wider testing should also be published in **GitHub Releases as pre-releases**, with a version/tag, commit reference, current known issues, and test status.

Development releases are not stable releases. Until Core 0.1 reaches its functional milestone, release notes should clearly identify the APK as experimental.

## Ownership, copyright, and audit trail

Project-specific architecture, original integration code, validation infrastructure, custom patches, documentation, and other original modifications in this repository are maintained by **@astromg01**.

**Copyright © 2026 @astromg01. All rights reserved for original project-specific material, except where another license or copyright notice applies.**

This repository also incorporates and depends on third-party/open-source software. Winlator, Wine, Box64, Android/Gradle components, graphics libraries, and any other upstream projects retain their respective authors, copyrights, trademarks, and licenses. Project ownership claims do **not** supersede third-party licenses.

The Git history, pinned upstream commit, patcher tests, CI runs, and `NOTICE.md` together form the project audit trail for project-specific changes and upstream attribution.

## Upstream policy

The official Winlator project is the primary upstream reference and is pinned through `third_party/winlator-app`. Forks and other compatibility projects may be used as patch/reference sources only. Every imported change should retain source attribution, license compatibility, purpose, and test status.

See [`NOTICE.md`](NOTICE.md) for ownership and attribution details.
