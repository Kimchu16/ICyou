# Camera overhaul control checklist

This is the authoritative recovery point for the 0.3.0 camera overhaul. Update
it in the commit that changes a roadmap item's status or contract.

## Branch and release control

- Integration: local `feature/camera-overhaul`, based on `main` commit
  `8d3d0b7`. The primary worktree keeps `main`; the Codex worktree owns the
  integration branch.
- Umbrella PR: planned draft `feature/camera-overhaul -> main`; not published.
- Child PRs: preserve the numbered cut points below as verified commits and
  target the integration branch. Prefer squash merge for child PRs and a merge
  commit for the eventual umbrella PR.
- No branches, commits, or PRs have been pushed or published.
- `main` remains the target for ordinary urgent fixes, followed by integration.

## Current decisions and evidence

- Accepted architecture: `ARCHITECTURE.md` and ADR 0001.
- Save schema: 1. Device-reference network protocol: 1.
- Baseline before overhaul edits: `gradlew build` passed on 2026-09-02 with no
  test sources. Gradle/Fabric: Loom 1.7.4, Gradle 8.9, Java 21, Minecraft 1.21.1.
- PR 0 verification: `gradlew test build` passed on 2026-09-02 (three tests).
- PR 1 verification: `gradlew clean test build` passed on 2026-09-02; eight
  tests total (five device-reference tests and three contract tests).
- Current work: stopped after PR 1. Next dependency: PR 2 server-global registry.
- Existing runtime consumers still use position/int identity; conversion is
  intentionally deferred to PR 3.

## Roadmap and dependency status

Status values: `DONE`, `ACTIVE`, `READY` (all dependencies done), `BLOCKED`.

| PR | Responsibility | Status | Depends on |
|---:|---|---|---|
| 0 | Overhaul contracts and test scaffold | DONE | — |
| 1 | Dimension-aware device identity | DONE | 0 |
| 2 | Server-global registry | READY | 1 |
| 3 | Device integration | BLOCKED | 2 |
| 4 | Migration, backup, ambiguity report | BLOCKED | 3 |
| 5 | Ownership and tombstones | BLOCKED | 4 |
| 6 | Server web lifecycle | BLOCKED | 2 |
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

## Version milestones

- 0.3.0-alpha.1: registry and migration (PRs 0–5)
- 0.3.0-alpha.2: web, demand, and chunk leases (PRs 6–12)
- 0.3.0-beta.1: arbitrary-distance video (PRs 13–21)
- 0.3.0-beta.2: render agent and live audio (PRs 16, 22–23)
- 0.3.0-rc.1: hardening (PRs 24–26)
- 0.3.0: same-dimension overhaul (PR 27)
- 0.4.0: cross-dimensional vanilla dimensions
