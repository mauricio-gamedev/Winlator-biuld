# Profiles

Profiles describe devices and games without hard-coding compatibility policy into UI code.

## Device profiles

Device profiles normalize hardware/runtime capabilities such as ABI, GPU family, RAM, Vulkan/OpenGL ES support and renderer policy. Runtime detection remains authoritative; static profiles provide tested defaults and known exceptions.

## Game profiles

Game profiles describe guest architecture, DirectX/API requirements, preferred translation layers, fallback order, runtime settings and device-specific overrides.

Profiles must validate against the schemas in this directory before being accepted.
