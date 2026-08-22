# Android App Layer

The Android app layer contains user-facing UI and device integration.

Planned areas:

- Home / game library
- Containers
- Add game / executable
- Performance presets
- Components
- Controls
- Diagnostics
- Settings
- Device capability discovery

Configuration is split into **Auto** and **Advanced** modes. Auto mode must use validated engine policy; Advanced mode may override it explicitly.

Low-level runtime decisions must stay out of UI code so components can be upgraded or rolled back independently.
