# Roadmap

## Core 0.1 — Bootstrap

Goal: establish a clean, testable project structure before importing runtime binaries.

- [x] Define three-layer architecture.
- [x] Define upstream/fork policy.
- [ ] Create Android project skeleton.
- [ ] Define component manifest schema.
- [ ] Define device capability model.
- [ ] Define game profile schema.
- [ ] Add diagnostics contract.
- [ ] Add CI/build validation.

## Core 0.2 — Minimal Runtime

Goal: launch a Windows executable reliably.

- [ ] Integrate rootfs/glibc baseline.
- [ ] Integrate Box64 baseline.
- [ ] Integrate Wine baseline.
- [ ] Create/delete/start containers.
- [ ] Execute `.exe` inside a container.
- [ ] Capture Wine and Box64 logs.

## Core 0.3 — Graphics and Audio

Goal: establish stable rendering and sound.

- [ ] Add WineD3D fallback.
- [ ] Add initial DXVK path.
- [ ] Add initial Mali-safe renderer path.
- [ ] Add VKD3D only after Vulkan capability validation.
- [ ] Add ALSA/audio integration.
- [ ] Validate fullscreen/window handling.

## Core 0.4 — Input and Profiles

- [ ] Touch controls.
- [ ] Physical controller support.
- [ ] Per-game launch profiles.
- [ ] Device-aware presets.
- [ ] Manual advanced overrides.

## Core 0.5 — Component Packs

- [ ] Base Pack.
- [ ] Graphics Pack.
- [ ] Compatibility Pack.
- [ ] Legacy Pack.
- [ ] Component install/update/rollback.
- [ ] Checksums and license metadata.

## Core 0.6 — Auto Compatibility

- [ ] Detect PE architecture.
- [ ] Infer DirectX/runtime path.
- [ ] Recommend renderer and translation layer.
- [ ] Safe fallback chain.
- [ ] Crash/failure classification.

## 1.0 criteria

Version 1.0 is not reached until the project can reproducibly run a representative game/test matrix across at least:

- ARM64 Android device with Mali GPU;
- ARM64 Android device with Adreno GPU;
- DX9 title;
- DX11 title;
- 32-bit Windows executable where supported;
- 64-bit Windows executable;
- controller and touch input;
- audio;
- diagnostic export;
- clean component rollback.
