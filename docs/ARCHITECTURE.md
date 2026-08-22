# Architecture — Core 0.1

## 1. App / Skeleton

Android-facing layer. It must not own low-level runtime policy.

Planned modules:

- `ui/home`
- `ui/games`
- `ui/containers`
- `ui/settings`
- `ui/components`
- `ui/diagnostics`
- `launcher`
- `profiles`
- `input`
- `updater`
- `device`

The UI exposes two configuration levels:

- **Auto** — safe device/game-aware defaults.
- **Advanced** — explicit control over Wine, Box64, graphics backend, translation layers, environment variables and launch arguments.

## 2. Engine / Middle Layer

The engine owns policy and orchestration. It connects the Android app to runtime components.

Initial services:

- `DeviceProfiler`
- `GameProfiler`
- `RuntimeManager`
- `GraphicsManager`
- `Box64Manager`
- `WineManager`
- `AudioManager`
- `InputManager`
- `PresetEngine`
- `CompatibilityManager`
- `DiagnosticsEngine`

### DeviceProfiler

Collects CPU ABI, Android version, GPU family, Vulkan/OpenGL ES capabilities, RAM and available memory. It produces a normalized hardware capability profile.

### GameProfiler

Stores game/runtime requirements and detects properties such as architecture, DirectX generation and known compatibility constraints.

### RuntimeManager

Owns lifecycle boundaries for containers, Wine processes and component selection. It must avoid hard-coding component versions into UI code.

### GraphicsManager

Chooses and validates graphics paths such as DXVK, VKD3D, WineD3D, VirGL, Vortek, Gladio, Turnip and Zink where appropriate.

### DiagnosticsEngine

Collects launcher, Wine, Box64, DXVK/VKD3D and renderer logs into a single exportable diagnostic package.

## 3. Runtime Core

The runtime core contains low-level binary/runtime components and their manifests.

Planned structure:

```text
runtime/
├── rootfs/
├── glibc/
├── wine/
├── box64/
├── box86/
├── mesa/
├── dxvk/
├── vkd3d/
├── virgl/
├── vortek/
├── gladio/
├── audio/
└── winetricks/
```

## Runtime flow

```text
Android ARM64
    ↓
RootFS / glibc
    ↓
Box64 / Box86 when required
    ↓
Wine
    ↓
DXVK / VKD3D / WineD3D
    ↓
Vulkan / OpenGL graphics path
    ↓
Android GPU driver
```

## Component policy

Runtime binaries should be versioned independently from the Android UI whenever possible. Components need manifests containing:

- component id;
- semantic/display version;
- architecture;
- checksum;
- source/upstream;
- license;
- compatible Android/GPU/runtime constraints;
- test status.

## Mali-first rule

Mali must be tested as a first-class target rather than treated as an unsupported fallback. Renderer selection must distinguish Adreno/Turnip-centric paths from Mali-safe paths and never assume Turnip availability on Mali.

## Stability rule

Core 0.1 prioritizes reproducibility and diagnostics over aggressive optimization. Experimental patches must remain opt-in until validated.
