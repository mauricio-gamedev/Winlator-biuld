# Winlator Build

Custom Android Windows-compatibility launcher/runtime project based on Winlator, focused on a clean architecture, modular components, strong Mali support, diagnostics, and device/game-aware presets.

## Project status

**Phase:** Core 0.1 — architecture bootstrap

The repository is being built in three layers before runtime integrations are added:

1. **App / Skeleton** — Android UI, launcher, profiles, settings, input, updater, and diagnostics surfaces.
2. **Engine / Middle layer** — device detection, game profiling, runtime orchestration, compatibility policy, presets, graphics/audio/input management.
3. **Runtime Core** — rootfs/glibc, Wine, Box64/Box86, graphics translation layers, Mesa-related components, audio/runtime dependencies.

## Design principles

- Keep upstream changes traceable.
- Prefer modular components over a monolithic APK.
- Do not merge entire forks blindly; import proven patches selectively.
- Treat Mali GPUs as a first-class target.
- Keep safe defaults and advanced controls separate.
- Make diagnostics and rollback available from the beginning.
- Never hide component versions or applied compatibility patches.

## Planned repository layout

```text
Winlator-biuld/
├── app/
├── engine/
├── runtime/
├── components/
├── profiles/
├── patches/
│   ├── upstream/
│   ├── frost/
│   ├── cmod/
│   └── custom/
├── scripts/
├── docs/
├── tests/
└── third_party/
```

## Core 0.1 goal

The first functional milestone must be able to:

- start the Android app;
- create a container;
- start Wine;
- execute a Windows `.exe`;
- run Box64 correctly;
- provide functional audio and input;
- support the initial DX9/DX11 path;
- expose at least one Mali-safe graphics path;
- generate useful diagnostic logs.

Advanced auto-optimization, large game-profile databases, runtime download/update systems, and experimental patches come only after the base runtime is stable.

## Upstream policy

The official Winlator project is the primary upstream reference. Forks such as Frost and historical CMOD work are treated as patch/reference sources only. Every imported patch must retain source attribution, license compatibility, purpose, and test status.
