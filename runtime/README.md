# Runtime Core

The runtime core contains versioned low-level components used to execute Windows software on Android.

Planned component families:

- rootfs / glibc
- Wine
- Box64 / Box86
- DXVK
- VKD3D
- WineD3D
- VirGL
- Vortek
- Gladio
- Mesa-related renderer/driver components
- audio runtime
- compatibility utilities

Binary artifacts must not be committed without source, version, license, checksum and compatibility metadata.

Core 0.1 prioritizes a minimal reproducible runtime before experimental performance patches.
