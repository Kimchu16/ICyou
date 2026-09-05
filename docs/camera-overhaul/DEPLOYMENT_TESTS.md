# Deployment tests

PR 26 tests the release shape that operators and render agents actually run.
The checks are required for pull requests into the camera-overhaul integration
branch and for the final release path.

## Release JAR

`verifyReleaseJar` runs as part of Gradle `check`. It opens the remapped release
JAR and verifies:

- Fabric metadata contains the expected mod ID and resolved version;
- both common and client entrypoint classes are present;
- the base WebRTC library is nested for Fabric Loader;
- Windows x64, Linux x64/ARM64/ARM32, and macOS Intel/ARM64 native JARs are
  present, nontrivial in size, and contain the expected native library.

This fails before artifact upload if packaging silently drops a platform.

## Dedicated server

The Ubuntu deployment job accepts the test EULA, starts the Fabric dedicated
server through Loom, and waits up to three minutes for all of these signals:

- Minecraft reaches its `Done` ready state;
- the backup-first legacy migration path completes for a fresh world;
- validated camera limits load;
- observability reports the camera system as idle.

The script then sends `stop` through standard input and requires a clean exit
within one minute. Its server log is uploaded even when the check fails.

## Native runtime matrix

The dedicated `nativeSmokeTest` creates and disposes a WebRTC peer factory,
synchronization clock, custom video source, and custom audio source on:

| Host | Architecture |
|---|---|
| Windows | x64 |
| Linux | x64 |
| Linux | ARM64 |
| macOS | Intel x64 |
| macOS | ARM64 |

GitHub does not provide a standard hosted Linux ARM32 runner. ARM32 remains a
packaging gate, while the five available desktop/server targets are execution
gates. Native test reports are uploaded on failure.

For a local check, run `gradlew verifyReleaseJar nativeSmokeTest`. The ordinary
unit suite keeps native startup disabled so headless unit environments do not
accidentally substitute for the explicit deployment matrix.
