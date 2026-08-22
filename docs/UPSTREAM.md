# Upstream Strategy

The official Winlator source repository is the primary upstream reference for this project.

## Current pinned baseline

- Repository: `https://github.com/brunodev85/winlator-app`
- Branch reference at selection time: `main`
- Pinned commit: `4f55d117fff1542944e5b91f433470445160ce08`
- Selected on: `2026-08-22`
- Upstream commit date: `2026-08-19`
- Baseline reason: this revision includes the latest reviewed upstream work available at project bootstrap, including recent Vortek, X server/shader, gamepad/session and foreground-service changes.

The project must never depend on floating `main` at build time. Upstream updates are reviewed and explicitly repinned.

## Integration model

Winlator Build tracks a known upstream revision and layers local work on top of it. Forks are reference/patch sources, not wholesale merge targets.

Before changing the upstream baseline, record:

- repository URL;
- selected tag/commit;
- date imported;
- license information;
- local deviations;
- required submodules/dependencies;
- migration/test notes.

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

## Fork policy

Winlator Frost and other maintained forks may be studied for proven compatibility/performance ideas. Historical/archived forks such as CMOD may be used only as traceable reference material. Every imported change must keep attribution and license compatibility and remain separately reviewable.

## Runtime components

Any binary/runtime component independent from the Android source additionally uses the component manifest system. A component cannot be promoted to stable without source, version, license, architecture, checksum/packaging data where applicable, compatibility constraints and test status.
