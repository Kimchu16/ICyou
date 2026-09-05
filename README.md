# ICyou

ICyou is a Fabric mod that adds a working CCTV system to Minecraft. Place
cameras around a base, connect them to terminals and screens, and watch live
same-dimension feeds from nearby or far away.

## What 0.3.0 adds

- Stable camera, terminal, and screen identities that survive moves and reloads.
- Same-dimension camera feeds at arbitrary distance through authenticated render
  agents.
- Live video over WebRTC, with MJPEG as a video-only fallback.
- Camera-positioned environmental audio over WebRTC.
- Secure, revocable terminal viewer tokens for web clients.
- Configurable camera, viewer, chunk, and retention limits.
- Backup-first migration from 0.2.0 worlds.
- Short operator commands for limits and system health.

The original in-game screens, terminals, portable screen, setup remote,
channel switching, camera mouse-look, and entity blips remain available.

## Requirements

- Minecraft 1.21.1
- Java 21 or newer
- Fabric Loader 0.16.9 or newer
- Fabric API
- A matching authenticated client when a dedicated server needs an off-screen
  render agent

Linux render-agent clients also need `libpulse.so.0` (`libpulse0` on Ubuntu).

## Install or upgrade

1. Back up the world.
2. Install Fabric Loader and Fabric API for Minecraft 1.21.1.
3. Put `icyou-0.3.0.jar` in the `mods` folder on the server and every joining
   client.
4. Start the server and review its log.

When a 0.2.0 world first starts on 0.3.0, ICyou copies every legacy registry
before converting it. Backups and `migration-report.txt` are stored under
`<world>/icyou-migration-backups/<UTC timestamp>/`. If conversion fails, ICyou
does not replace the original state.

Review any `AMBIGUITY` entries in the report before relying on the migrated
camera network. Portable-screen links upgrade when they are next used.

## Server setup

Web access is disabled by default. The focused operator guides explain each
optional part:

- [Terminal web access and tokens](docs/camera-overhaul/AUTHENTICATION.md)
- [Render-agent setup](docs/camera-overhaul/RENDER_AGENT.md)
- [WebRTC and MJPEG delivery](docs/camera-overhaul/WEBRTC_AV.md)
- [Camera limits](docs/camera-overhaul/ADMIN_LIMITS.md)
- [Status and troubleshooting](docs/camera-overhaul/OBSERVABILITY.md)

Useful permission-level 2 commands:

```text
/icyou limits
/icyou status
/icyou token issue <terminal-slug> viewer
/icyou render-agent issue <player-uuid>
```

Tokens and render-agent secrets are shown once. Store them securely and never
put them in logs or source control.

## Known limits

- Remote rendering in 0.3.0 supports cameras in the render agent's current
  dimension. Cross-dimension rendering is planned for 0.4.0.
- Linux ARM32 is package-verified but not runtime-tested in GitHub CI because a
  standard hosted runner is unavailable.
- Web access binds to loopback by default and should stay behind a trusted
  reverse proxy when exposed outside the host.

## Build and verify

This project uses the bundled Gradle 8.9 wrapper and Java 21.

```bash
./gradlew clean test build nativeSmokeTest
```

The release JAR is written to `build/libs/`. The full deployment coverage is
documented in [Deployment tests](docs/camera-overhaul/DEPLOYMENT_TESTS.md).

## Release

The GitHub workflow builds every pull request and runs dedicated-server and
native WebRTC smoke tests. A `v0.3.0` tag builds the matching versioned JAR,
creates a GitHub release, and optionally uploads to CurseForge when its secrets
are configured.

Use [RELEASE.md](RELEASE.md) for the release checklist and
[RELEASE_CURSEFORGE.md](RELEASE_CURSEFORGE.md) for the CurseForge fields.

## Project

- Mod ID: `icyou`
- Java package: `com.matissjurevics.icyou`
- License: MIT
- [Source](https://github.com/Kimchu16/ICyou)
- [Issue tracker](https://github.com/Kimchu16/ICyou/issues)
