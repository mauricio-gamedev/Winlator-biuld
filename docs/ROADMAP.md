# Roadmap

## Core 0.1 — Bootstrap

Goal: establish a clean, testable project structure before importing runtime binaries.

- [x] Define three-layer architecture.
- [x] Define upstream/fork policy.
- [ ] Create Android project skeleton.
- [x] Define component manifest schema.
- [x] Define device capability model.
- [x] Define game profile schema.
- [ ] Add diagnostics contract.
- [x] Add CI/build validation.
- [x] Pin official Winlator upstream revision.
- [x] Add pure-Java renderer/runtime policy layer.

## Core 0.2 — Minimal Runtime

Goal: launch a Windows executable reliably.

Runtime planning and safety gates:

- [x] Build pinned runtime component catalog.
- [x] Build device-aware RuntimePlanner.
- [x] Add runtime component inventory/readiness checks.
- [x] Add transactional container apply/rollback.
- [x] Add RuntimeExecutionCoordinator.
- [x] Block invalid plans before container mutation.
- [x] Surface rollback failure separately.

RootFS / GLIBC baseline:

- [x] Pin RootFS baseline to upstream version 22.
- [x] Record rootfs/rootfs-patches upstream asset provenance.
- [x] Inspect RootFS version strictly for the pinned build.
- [x] Verify presence of libc.so.6 and the ARM64 glibc loader.
- [x] Verify rootfs_patches.tzst availability before launch readiness.
- [x] Keep rootfs.tzst as install/repair readiness rather than a launch requirement.
- [ ] Integrate safe RootFS install/repair execution.
- [ ] Validate RootFS installation on an Android device.

Remaining Core 0.2 runtime work:

- [ ] Integrate rootfs/glibc baseline end-to-end.
- [ ] Integrate Box64 baseline.
- [ ] Integrate Wine baseline.
- [ ] Create/delete/start containers through the new coordinator.
- [ ] Execute `.exe` inside a container.
- [ ] Capture Wine and Box64 logs.

## Core 0.3 — Graphics and Audio

Goal: establish stable rendering and sound.

- [ ] Add WineD3D fallback end-to-end.
- [ ] Add initial DXVK path end-to-end.
- [ ] Add initial Mali-safe renderer path end-to-end.
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
