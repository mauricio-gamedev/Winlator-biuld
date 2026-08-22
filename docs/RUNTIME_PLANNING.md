# Core 0.2 Runtime Planning Gate

This document defines the boundary between hardware/runtime planning and real container mutation.

## Completed foundation

- Pinned upstream runtime component catalog.
- Immutable `RuntimePlan`.
- Explicit `LaunchRequirements`; game auto-detection is intentionally not part of this stage.
- Renderer selection from `HardwareCapabilities`.
- Wine, Box64, DirectX wrapper and component selection.
- DXVK compatibility rule aligned with the pinned upstream baseline.
- Invalid-plan rejection before runtime start.
- Runtime state machine accepts validated plans.

## Pinned baseline represented by the catalog

- Wine 10.10
- Box64 0.4.4
- Turnip 26.1.0
- Vortek 2.1
- Zink 22.2.5
- VirGL 23.1.9
- Gladio 1.1
- DXVK 2.4.1 / 1.10.3
- VKD3D 2.14.1
- WineD3D 10.10

## Current policy

- Adreno + usable Vulkan: Turnip + Gladio.
- Mali + usable Vulkan: Vortek + Gladio; never auto-select Turnip.
- No usable Vulkan 1.1: OpenGL renderer path and WineD3D for DirectX 9-11/unknown workloads.
- Vortek + Vulkan 1.3 or newer: DXVK 2.4.1.
- Vortek below Vulkan 1.3: DXVK 1.10.3.
- Turnip: DXVK 2.4.1.
- DirectX 12 without a usable Vulkan path: invalid plan.
- Devices at or below 4 GiB RAM: Box64 CONSERVATIVE.
- Other devices: Box64 INTERMEDIATE.

## Deliberately deferred

The planning gate does not yet:

- mutate a Winlator container;
- install or download runtime packs;
- detect a game's PE architecture or DirectX version;
- apply aggressive performance presets;
- add fork-specific optimizations;
- implement recovery/fallback retries during a real launch.

## Next gate

The next stage is a transactional `RuntimePlan` applicator. It must verify the selected components are present, snapshot the container's existing runtime settings, apply the plan, validate the resulting configuration, and support rollback if application fails. Only after that gate is stable should real rootfs/Wine/Box64 execution work continue.
