# Engine Layer

The engine layer owns runtime policy and orchestration. Android UI code should request high-level operations from this layer instead of directly manipulating Wine, Box64 or graphics components.

Planned services:

- DeviceProfiler
- GameProfiler
- RuntimeManager
- GraphicsManager
- Box64Manager
- WineManager
- AudioManager
- InputManager
- PresetEngine
- CompatibilityManager
- DiagnosticsEngine

Core 0.1 rule: interfaces and data contracts are defined before aggressive optimizations or fork-specific patches are integrated.
