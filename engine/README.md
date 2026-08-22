# Engine Layer

The engine layer owns runtime policy and orchestration. Android UI code should request high-level operations from this layer instead of directly manipulating Wine, Box64 or graphics components.

## Core 0.1 implemented

- `HardwareCapabilities` — normalized Android/GPU/RAM/Vulkan/OpenGL/ABI model.
- `DeviceProfiler` — probe abstraction that converts platform data into `HardwareCapabilities`.
- `RendererPolicy` — deterministic renderer selection using upstream Winlator identifiers (`turnip`, `vortek`, `zink`, `virgl`, `gladio`).
- `ComponentRegistry` — component compatibility filtering by Android API, host architecture, guest architecture, GPU family and Vulkan availability.
- `RuntimeManager` — guarded runtime state machine (`IDLE -> PREPARING -> READY -> RUNNING`).
- `EngineSelfTest` — regression coverage for Mali, Adreno, no-Vulkan fallback, host/guest architecture handling and runtime transitions.

## Design boundary

This module is intentionally plain Java. It does not directly depend on Android classes or Winlator UI/container classes. The next integration adapter will implement `DeviceProfiler.Probe` using Android `Build`, memory information and upstream `GPUHelper`, then apply `RendererPolicy.Decision` to Winlator container settings.

This separation keeps hardware policy testable without booting Android and avoids hard-coding fork-specific behavior into the UI.

## Architecture semantics

Component manifests may describe both host and guest architecture:

- Android host: `arm64-v8a`, `armeabi-v7a`.
- Windows guest/runtime: `x86_64`, `x86`.

Only host architectures are matched against the physical device ABI. Guest architectures remain valid on ARM hosts because Box64/Box86/Wine provide the translation/runtime layer.

## Planned services

- GameProfiler
- GraphicsManager integration bridge
- Box64Manager
- WineManager
- AudioManager
- InputManager
- PresetEngine
- CompatibilityManager
- DiagnosticsEngine

Core 0.1 rule: interfaces and data contracts are defined and tested before aggressive optimizations or fork-specific patches are integrated.
