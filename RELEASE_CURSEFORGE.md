# CurseForge release details

Use this page when publishing ICyou 0.3.0 on CurseForge. The automated upload
runs only when both `CURSEFORGE_API_TOKEN` and `CURSEFORGE_PROJECT_ID` are set.
Otherwise, upload the same verified JAR manually.

## Project fields

**Name:** ICyou

**Summary:** A secure CCTV system with live same-dimension camera feeds for
Minecraft Fabric servers.

**Description:**

```markdown
## ICyou - CCTV for Minecraft

ICyou adds a working camera network to Minecraft. Place cameras, connect them
to terminals and screens, and watch same-dimension feeds from nearby or far
away.

### Features

- In-game screens, camera terminals, a portable screen, and a setup remote
- Stable device links that survive moves and server reloads
- Live WebRTC video and camera-positioned environmental audio
- MJPEG video fallback
- Secure, revocable web viewer access
- Configurable resource limits and simple operator status commands
- Backup-first upgrades from ICyou 0.2.0

Remote feeds use an authenticated Minecraft render-agent client. Cross-dimension
rendering is not included in 0.3.0.

### Requirements

- Minecraft 1.21.1
- Java 21 or newer
- Fabric Loader 0.16.9 or newer
- Fabric API

License: MIT
Source and issues: https://github.com/Kimchu16/ICyou
```

Set the license to **MIT**, the loader to **Fabric**, the supported Minecraft
version to **1.21.1**, and Fabric API as a **required dependency**. Use the
existing mod icon or a larger version of the same artwork.

## File fields

- File: `build/libs/icyou-0.3.0.jar`
- Display name: `ICyou 0.3.0`
- Release type: `Release`
- Changelog: copy the `0.3.0` section from `CHANGELOG.md`
- Supported version: `1.21.1`
- Loader: `Fabric`

Before upload, complete every item in `RELEASE.md` and confirm the JAR checksum
matches the artifact produced by the green release workflow.
