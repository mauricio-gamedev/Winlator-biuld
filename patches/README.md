# Patch Policy

This directory tracks every modification that differs from the chosen Winlator upstream baseline.

## Sources

```text
patches/
├── upstream/   # Backports or isolated fixes from newer official upstream work
├── frost/      # Selectively imported Winlator Frost ideas/patches
├── cmod/       # Historical CMOD-derived patches retained only when useful and license-compatible
└── custom/     # Winlator Build-specific patches
```

## Rules

1. Never copy an entire fork on top of the project without review.
2. Every imported patch must identify its original project/revision or commit when available.
3. License compatibility must be checked before redistribution.
4. Each patch must state why it exists and what problem it solves.
5. Experimental patches must remain separable and removable.
6. A patch is not considered stable until it passes the relevant test matrix.
7. Mali-specific and Adreno-specific changes must be tagged accordingly instead of being treated as universal.

## Patch metadata

For each non-trivial patch, create a sibling Markdown note containing:

```text
Source:
Source revision:
License:
Area:
Target GPUs/devices:
Problem:
Change:
Risk:
Rollback:
Tests:
Status: experimental | testing | stable
```

## Upstream synchronization

The official Winlator project is the primary upstream reference. Our custom code should be layered on top of a known upstream revision so a future synchronization can identify:

- upstream additions;
- upstream removals;
- local modifications;
- imported fork patches;
- conflicts requiring manual review.

No optimization is worth losing that traceability.
