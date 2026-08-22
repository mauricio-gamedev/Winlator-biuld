# RootFS Android Validation

This validation belongs to Core 0.2. Do not start Box64/Wine/game validation until this gate passes.

## Validation build identity

The CI validation overlay deliberately installs as a separate Android application:

- application id: `com.winlator.buildtest`
- app label: `Winlator Build Test`
- upstream Java/JNI namespace remains `com.winlator`
- FileProvider authority is derived from the validation application id
- Java internal-storage path is isolated under `/data/data/com.winlator.buildtest`
- native cache, Vortek socket, and Gladio X11 socket paths use the same validation application sandbox

This is a temporary validation identity, not the final product package decision.

## Build gate

Workflow: `Runtime integration validation`

Required jobs before device testing:

1. `runtime-plan-transaction`
   - compiles the pure Java engine and integration stubs
   - runs runtime/RootFS self-tests
   - validates the fail-fast upstream patcher and its idempotency
2. `patched-android-build`
   - checks out the pinned Winlator upstream submodule
   - installs Android SDK 35, NDK 24.0.8215888, and CMake 3.22.1
   - prepares the overlay
   - builds `:app:assembleDebug`
   - uploads artifact `winlator-build-rootfs-validation-debug`

Do not install an APK from a failed or cancelled run.

## Device test — first pass

Use the validation APK only. Keep the official Winlator installed and untouched.

1. Install `Winlator Build Test` and confirm it appears as a separate app.
2. Launch it with enough free internal storage for a staged RootFS plus the 64 MiB safety margin.
3. On first launch, the RootFS maintenance controller should show the system-files preloader while work runs off the UI thread.
4. The app must not expose a partially extracted RootFS as version 22. The version marker is written only after libc and the ARM64 dynamic loader are found in staging.
5. After activation, the active RootFS is validated again before old-root cleanup.
6. Close and reopen the validation app. A healthy RootFS v22 should take the no-op path rather than reinstalling again.
7. In Settings, use the existing reinstall-system-files action once. It should route through the transactional repair path and return to a valid RootFS v22.
8. Reopen the official Winlator and verify its existing app data/containers are unchanged.

## Failure rules

Stop the device test and preserve evidence if any of these happen:

- the validation APK attempts to replace the official Winlator;
- the official Winlator data changes;
- RootFS maintenance loops on every launch;
- extraction/activation reports a failure;
- the app becomes launchable with an incomplete RootFS;
- a `.winlator-build-rootfs-transaction` recovery error is reported;
- the validation app crashes during the RootFS swap;
- reinstall-system-files removes validation-app home data unexpectedly.

Do not continue to Box64 after any RootFS failure.

## Interruption recovery — later subtest

Do not intentionally kill the app during the first pass. After normal install and repair both pass, interruption recovery can be tested separately:

- an interruption in `ACTIVATING` must restore the previous RootFS;
- an interruption in `COMMITTING` must keep the already validated new RootFS, rerun post-install metadata reset, and finish old-backup cleanup;
- `home` and `opt/installed-wine` must remain intact across recovery.

The pure-Java and integration self-tests already exercise these transaction semantics. The Android test exists to confirm filesystem/Android behavior on the real device.
