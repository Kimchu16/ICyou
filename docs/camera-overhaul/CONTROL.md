# Camera overhaul control checklist

This is the authoritative recovery point for the 0.3.0 camera overhaul. Update
it in the commit that changes a roadmap item's status or contract.

## Branch and release control

- Integration: published `feature/camera-overhaul`, based on `main` commit
  `8d3d0b7`. The primary worktree keeps `main`; the Codex worktree owns the
  integration branch.
- Umbrella PR: draft #9, `feature/camera-overhaul -> main`; baseline CI passed.
- Child PRs: preserve the numbered cut points below as verified commits and
  target the integration branch. Prefer squash merge for child PRs and a merge
  commit for the eventual umbrella PR.
- Publishing authorization: non-`main` branches may be pushed and child PRs may
  be opened/merged without further approval when required CI passes. Direct
  pushes to `main` are forbidden.
- CI is configured for PRs targeting both `main` and the integration branch.
- After a child PR is merged, delete its local and remote branch. Commit subjects
  and bodies should be short, plain-language summaries of the user-visible change.
- `main` remains the target for ordinary urgent fixes, followed by integration.

## Current decisions and evidence

- Accepted architecture: `ARCHITECTURE.md` and ADR 0001.
- Save schema: 1. Device-reference network protocol: 1.
- Baseline before overhaul edits: `gradlew build` passed on 2026-09-02 with no
  test sources. Gradle/Fabric: Loom 1.7.4, Gradle 8.9, Java 21, Minecraft 1.21.1.
- PR 0 verification: `gradlew test build` passed on 2026-09-02 (three tests).
- PR 1 verification: `gradlew clean test build` passed on 2026-09-02; eight
  tests total (five device-reference tests and three contract tests).
- PR 2 verification: `gradlew clean test build` passed locally with 12 tests;
  child PR #10 CI passed and was squash-merged as `718dc63`.
- PR 3 local verification: `gradlew clean test build` passed, followed by the
  payload-codec regression test; 15 tests pass in total.
  the legacy-registry runtime-consumer audit returned no matches.
- PR 3 child PR #11 CI passed and was squash-merged as `d33ae28`; its local and
  remote child branches were deleted.
- Current work: PR 4 migration is active on `feature/cam-04-migration`.
- PR 4 verification: `gradlew clean test build` passed locally with 18 tests
  after adding backup-first conversion, an ambiguity report, migration state,
  and lazy upgrades for legacy item links.
- PR 4 child PR #12 CI passed and was squash-merged as `49d8ed2`; its local and
  remote child branches were deleted.
- PR 5 verification: `gradlew clean test build` passed locally with 22 tests;
  focused registry tests cover ownership, authorization decisions, atomic
  restoration, 30-day expiry, and persistence.
- PR 5 child PR #13 CI passed and was squash-merged as `7cbe091`; its local and
  remote child branches were deleted.
- PR 6 verification: `gradlew clean test build` passed locally with 28 tests;
  six focused tests cover safe config, bind lifecycle, health, failure, and cleanup.
- Current work: PR 6 server web lifecycle is active on
  `feature/cam-06-server-web-lifecycle`.

## Roadmap and dependency status

Status values: `DONE`, `ACTIVE`, `READY` (all dependencies done), `BLOCKED`.

| PR | Responsibility | Status | Depends on |
|---:|---|---|---|
| 0 | Overhaul contracts and test scaffold | DONE | — |
| 1 | Dimension-aware device identity | DONE | 0 |
| 2 | Server-global registry | DONE | 1 |
| 3 | Device integration | DONE | 2 |
| 4 | Migration, backup, ambiguity report | DONE | 3 |
| 5 | Ownership and tombstones | DONE | 4 |
| 6 | Server web lifecycle | ACTIVE | 2 |
| 7 | Web gateway seam | BLOCKED | 6 |
| 8 | Terminal authentication | BLOCKED | 5, 7 |
| 9 | Web demand | BLOCKED | 8 |
| 10 | Unified demand manager | BLOCKED | 9, 3 |
| 11 | Chunk leases | BLOCKED | 10 |
| 12 | Supplemental random ticks | BLOCKED | 11 |
| 13 | Render protocol | BLOCKED | 1, 10 |
| 14 | Render authentication | BLOCKED | 13, 8 |
| 15 | Render scheduler | BLOCKED | 11, 13, 14 |
| 16 | Render agent | BLOCKED | 15 |
| 17 | Scene snapshots | BLOCKED | 15 |
| 18 | Scene deltas | BLOCKED | 17 |
| 19 | Remote client world | BLOCKED | 17, 18 |
| 20 | Offscreen renderer | BLOCKED | 19 |
| 21 | Video delivery | BLOCKED | 9, 15, 20 |
| 22 | Audio scene | BLOCKED | 18, 19 |
| 23 | WebRTC A/V | BLOCKED | 21, 22 |
| 24 | Admin limits | BLOCKED | 10, 15, 21, 23 |
| 25 | Observability | BLOCKED | 24 |
| 26 | Deployment tests | BLOCKED | 4, 16, 23, 25 |
| 27 | Release | BLOCKED | 26 |

## PR 0 acceptance criteria

- [x] Terminology, authority boundaries, environment behavior, lifecycle,
  simulation, media, security, migration, and release scope are documented.
- [x] Default limits and save/network versions exist in one executable contract.
- [x] Lifecycle transition rules are executable and documented.
- [x] A minimal JUnit 5 test source set is wired into the existing Gradle build.
- [x] `gradlew test` and `gradlew build` pass after PR 0 changes.
- [x] PR 0 is recorded as a distinct local commit.

## PR 1 acceptance criteria

- [x] Immutable `CameraRef`, `TerminalRef`, and `ScreenRef` expose stable UUID,
  dimension key, and immutable block position.
- [x] Construction rejects null fields and preserves value semantics.
- [x] Each reference has explicit save and packet serialization contracts using
  schema/protocol version 1 and rejects unknown versions.
- [x] Focused round-trip and invalid-version tests pass.
- [x] No 0.2.0 registry, block entity, item, cache, or payload consumer is
  migrated in this PR.
- [x] PR 1 is recorded as a distinct local commit and control state names PR 2
  as the next dependency.

## PR 2 acceptance criteria

- [x] One registry is persisted per logical server through the Overworld state
  manager while accepting device references from every dimension.
- [x] UUID, dimension-aware location, and terminal-child indexes stay coherent
  across registration, assignment, removal, and save reload.
- [x] Duplicate UUIDs/locations, missing terminals, cross-terminal assignments,
  invalid names, and unsupported save schemas are rejected.
- [x] Registry mutations mark persistent state dirty; loading does not.
- [x] Focused registry and persistence tests pass locally.
- [x] Legacy 0.2.0 `DeviceRegistry` and its consumers remain unchanged for PR 3.
- [x] Child PR CI passes and the PR is squash-merged into the integration branch.

## PR 3 acceptance criteria

- [x] Terminal and screen block entities persist stable typed references/UUIDs.
- [x] Setup Remote and Portable Screen components carry typed references rather
  than bare positions; wireless item IDs are UUIDs.
- [x] Blocks, device actions, subscriptions, snapshots, feed payloads, detached
  views, GUI, HUD, RTT feed sharing, and local stream routing use stable IDs.
- [x] Runtime device lookups resolve the referenced dimension explicitly.
- [x] The legacy 0.2.0 `DeviceRegistry` has no runtime consumers and remains
  intact solely as PR 4 migration input.
- [x] Reference component-codec and registry relinking tests pass locally.
- [x] Every migrated device payload round-trips typed references in a focused test.
- [x] `gradlew clean test build` passes after the full PR 3 change.
- [x] Child PR CI passes and the PR is squash-merged into the integration branch.

## PR 4 acceptance criteria

- [x] Every discovered 0.2.0 registry file is copied byte-for-byte before it is
  loaded for conversion.
- [x] Legacy terminal, camera, screen, name, slug, ownership, and assignment
  data is converted into dimension-aware global registry entries.
- [x] Converted UUIDs are deterministic so a retry produces the same identity.
- [x] Missing, duplicate, and conflicting relationships are recorded in a
  readable migration report while valid entries are retained.
- [x] A saved completion marker makes migration run once per logical server.
- [x] Old item component IDs retain their 0.2.0 codecs and upgrade lazily to new
  typed reference/UUID components when used.
- [x] Focused migration, backup, ambiguity, and persistence tests pass.
- [x] `gradlew clean test build` passes after the full PR 4 change (18 tests).
- [x] Child PR CI passes and the PR is squash-merged into the integration branch.

## PR 5 acceptance criteria

- [x] Player-placed terminals record the placing player's UUID as owner.
- [x] Migrated unowned terminals can be claimed once; owner transfer is an
  explicit registry operation and operators retain management access.
- [x] Terminal use, device mutations, subscriptions, setup links, and portable
  screen pairing reject players who are neither owner nor operator.
- [x] Breaking a registered camera creates a 30-day tombstone that keeps its
  UUID, terminal ownership, name, and screen assignments.
- [x] A linked Setup Remote can restore a tombstoned camera at a new unlinked
  location, including another dimension, without changing its UUID.
- [x] Restoration validates all conflicts before changing state; expiry removes
  old tombstones and clears their screen assignments.
- [x] Owners and tombstones survive save/reload while older schema-1 saves remain
  readable when the new optional fields are absent.
- [x] Focused ownership, authorization, restoration, expiry, and persistence
  tests pass.
- [x] `gradlew clean test build` passes after the full PR 5 change (22 tests).
- [x] Child PR CI passes and the PR is squash-merged into the integration branch.

## PR 6 acceptance criteria

- [x] The web listener belongs to the logical Minecraft server and starts after
  server initialization, not during client startup.
- [x] Server stopping closes the listener socket and client executor; repeated
  start and stop calls are safe.
- [x] The listener is disabled by default and defaults to loopback when enabled.
- [x] Configuration validates bind and port values and treats invalid files as
  a contained listener failure rather than a server crash.
- [x] The common lifecycle has no Minecraft client or rendering dependencies, so
  the same code can run on integrated, LAN, and dedicated servers.
- [x] Until PR 7 adds the gateway seam, the listener exposes only a no-store
  `/health` response and no device metadata.
- [x] Focused tests cover opt-in configuration, idempotent startup, health,
  occupied-port failure, and deterministic cleanup.
- [x] `gradlew clean test build` passes after the full PR 6 change (28 tests).
- [ ] Child PR CI passes and the PR is squash-merged into the integration branch.

## Version milestones

- 0.3.0-alpha.1: registry and migration (PRs 0–5)
- 0.3.0-alpha.2: web, demand, and chunk leases (PRs 6–12)
- 0.3.0-beta.1: arbitrary-distance video (PRs 13–21)
- 0.3.0-beta.2: render agent and live audio (PRs 16, 22–23)
- 0.3.0-rc.1: hardening (PRs 24–26)
- 0.3.0: same-dimension overhaul (PR 27)
- 0.4.0: cross-dimensional vanilla dimensions
