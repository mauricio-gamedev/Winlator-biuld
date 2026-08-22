# Upstream Strategy

The official Winlator project is the primary upstream reference for this repository.

## Integration model

Winlator Build should track a known upstream revision and layer local work on top of it. Forks are reference/patch sources, not wholesale merge targets.

Before importing an upstream baseline, record:

- repository URL;
- selected tag/commit;
- date imported;
- license information;
- local deviations;
- required submodules/dependencies.

## Update flow

```text
Official Winlator upstream
        ↓
Pinned baseline revision
        ↓
Reviewed upstream/backport patches
        ↓
Reviewed fork-derived patches
        ↓
Winlator Build custom patches
        ↓
Test matrix / release
```

The baseline must remain reproducible. Any binary/runtime component that is independent from the Android source should additionally use the component manifest system.
