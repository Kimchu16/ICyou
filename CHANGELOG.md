# Changelog

## 0.3.0 - Camera system overhaul

ICyou 0.3.0 rebuilds the camera system for stable, secure, long-distance feeds
while keeping the familiar in-game devices.

### Highlights

- Added stable, dimension-aware IDs for cameras, terminals, and screens.
- Added same-dimension rendering at arbitrary distance through authenticated
  render agents.
- Added WebRTC video and live camera-positioned audio, plus MJPEG fallback.
- Added secure terminal viewer tokens with immediate revocation.
- Added configurable limits and concise `/icyou limits` and `/icyou status`
  operator commands.
- Added a backup-first 0.2.0 migration with a readable ambiguity report.
- Added release-JAR, dedicated-server, and native runtime checks across Windows,
  Linux x64/ARM64, and macOS Intel/ARM64.

### Upgrade notes

- Back up the world before upgrading.
- The first 0.3.0 start copies legacy registry files into
  `<world>/icyou-migration-backups/<UTC timestamp>/` before conversion.
- Read `migration-report.txt` and resolve any `AMBIGUITY` entries.
- Install the 0.3.0 JAR on the server and every joining client.
- Configure and authenticate at least one render agent for remote feeds on a
  dedicated server.

### Known limits

- Remote rendering is same-dimension only. Cross-dimension rendering is planned
  for 0.4.0.
- Linux render agents require `libpulse.so.0`.
- Linux ARM32 native files are package-verified but not runtime-tested in the
  hosted CI matrix.
