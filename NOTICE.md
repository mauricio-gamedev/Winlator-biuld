# Winlator Build — Ownership and Attribution Notice

## Project-specific authorship

Winlator Build is maintained as an independent integration and compatibility project.

Original project-specific work in this repository — including architecture decisions, integration code, validation infrastructure, custom patching logic, diagnostics, documentation, compatibility work, and original modifications — is maintained by **Mauricio.dev (@mauricio-gamedev)**.

**Copyright © 2026 Mauricio.dev (@mauricio-gamedev). All rights reserved for original project-specific material, except where another license or copyright notice applies.**

This notice establishes authorship and an audit trail for original material. It does not relicense or claim ownership of third-party code.

## Upstream and third-party software

This repository depends on and/or integrates third-party open-source projects, including but not limited to:

- **Winlator** — primary Android upstream, pinned as a Git submodule.
- **Wine** — Windows compatibility layer/runtime.
- **Box64 / Box86** — x86/x86_64 userspace emulation components.
- Android, Gradle, Android SDK/NDK, graphics, audio, and runtime libraries used by the upstream project or this integration.

All third-party projects retain their original authors, copyright notices, trademarks, and licenses. Their license terms continue to apply to their respective code and binaries.

The upstream Winlator source referenced by this repository includes its own `LICENSE` file. The pinned upstream revision and source location are recorded by the Git submodule metadata and commit history.

## Audit trail

The following repository records are intended to make project-specific changes reviewable and attributable:

- Git commit history;
- pinned `third_party/winlator-app` revision;
- deterministic patch scripts in `scripts/`;
- patcher and integration tests in `tests/`;
- GitHub Actions workflow history;
- release tags and release notes for published APKs;
- diagnostic and compatibility documentation.

When importing third-party patches or techniques, source attribution and applicable license requirements should be retained wherever required.

## Distribution note

Development APKs may bundle or depend on third-party software. Publishing an APK under a Winlator Build release does not transfer ownership of third-party components to Winlator Build or Mauricio.dev (@mauricio-gamedev).
