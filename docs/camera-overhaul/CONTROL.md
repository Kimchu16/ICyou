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
- Save schema: 1. Device-reference, render-control, and scene-snapshot network
  protocols: 1.
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
- PR 6 child PR #14 CI passed and was squash-merged as `d8e76f1`; its local and
  remote child branches were deleted.
- PR 7 verification: `gradlew clean test build` passed locally with 30 tests;
  gateway tests cover neutral routing, defensive bytes, and safe headers.
- PR 7 child PR #15 CI passed and was squash-merged as `d67f9a0`; its local and
  remote child branches were deleted.
- PR 8 verification: `gradlew clean test build` passed locally with 37 tests;
  authentication tests cover scopes, persistence, revocation, nondisclosure,
  bounded headers, and authenticated metadata.
- PR 8 child PR #16 CI passed and was squash-merged as `9996ce7`; its local and
  remote child branches were deleted.
- PR 9 verification: `gradlew clean test build` passed locally with 42 tests;
  demand tests cover authenticated sessions, deduplication, renewal, closure,
  expiry, revocation, and shared lifecycle state.
- PR 9 child PR #17 CI passed and was squash-merged as `c716551`; its local and
  remote child branches were deleted.
- PR 10 verification: `gradlew clean test build` passed locally with 52 tests;
  focused tests cover source union, screen eligibility, environment gates, and
  feed retention.
- PR 10 child PR #18 CI passed and was squash-merged as `3320e03`; its local and
  remote child branches were deleted.
- PR 11 verification: `gradlew clean test build` passed locally with 58 tests;
  focused tests cover 3x3 areas, shared references, movement, retention, and
  cleanup.
- PR 11 child PR #19 CI passed and was squash-merged as `50c7cb5`; its local and
  remote child branches were deleted.
- PR 12 verification: `gradlew clean test build` passed locally with 62 tests;
  focused tests cover vanilla deduplication, dimensions, and unloaded leases.
- PR 12 child PR #20 CI passed and was squash-merged as `f349f46`; its local and
  remote child branches were deleted.
- PR 13 verification: `gradlew clean test build` passed locally with 68 tests;
  focused tests cover every message, future versions, bounds, and immutability.
- PR 13 child PR #21 CI passed and was squash-merged as `a81c2d2`; its local and
  remote child branches were deleted.
- PR 14 verification: `gradlew clean test build` passed locally with 77 tests;
  focused tests cover UUID and secret checks, expiry, replay, persistence,
  revocation, reauthentication, and disconnect cleanup.
- PR 14 child PR #22 CI passed and was squash-merged as `c5106c5`; its local and
  remote child branches were deleted.
- PR 15 verification: `gradlew clean test build` passed locally with 85 tests;
  focused tests cover limits, dimensions, status authority, failure, disconnect,
  movement, retention, slot reclamation, and shutdown.
- PR 15 child PR #23 CI passed and was squash-merged as `b361b43`; its local and
  remote child branches were deleted.
- PR 16 verification: `gradlew clean test build` passed locally with 98 tests;
  13 focused tests cover fail-closed configuration, UUID-bound handshake, limits,
  dimensions, revisions, status reporting, replacement, and disconnect cleanup.
- PR 16 child PR #24 CI passed and was squash-merged as `7a3b0d0`; its local and
  remote child branches were deleted.
- PR 17 verification: `gradlew clean test build` passed locally with 108 tests;
  focused tests cover bounded codecs, vanilla-packet framing, fragmentation,
  digest assembly, conflicts, job binding, cancellation, and capture failure.
- PR 17 child PR #25 CI passed and was squash-merged as `50aebf2`; its local and
  remote child branches were deleted.
- PR 18 verification: `gradlew clean test build` passed locally with 115 tests;
  focused tests cover codec bounds, defensive bytes, exact ordering, stale and
  mismatched input, cancellation cleanup, and bounded client queues.
- PR 18 child PR #26 CI passed and was squash-merged as `e74e04f`; its local and
  remote child branches were deleted.
- PR 19 verification: `gradlew clean test build` passed locally with 118 tests;
  focused tests cover exact snapshot reuse, replacement cleanup, job retention,
  disconnect cleanup, and the scene-only packet allow-list.
- Current work: PR 19 remote client world is active on
  `feature/cam-19-remote-world`.

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
| 6 | Server web lifecycle | DONE | 2 |
| 7 | Web gateway seam | DONE | 6 |
| 8 | Terminal authentication | DONE | 5, 7 |
| 9 | Web demand | DONE | 8 |
| 10 | Unified demand manager | DONE | 9, 3 |
| 11 | Chunk leases | DONE | 10 |
| 12 | Supplemental random ticks | DONE | 11 |
| 13 | Render protocol | DONE | 1, 10 |
| 14 | Render authentication | DONE | 13, 8 |
| 15 | Render scheduler | DONE | 11, 13, 14 |
| 16 | Render agent | DONE | 15 |
| 17 | Scene snapshots | DONE | 15 |
| 18 | Scene deltas | DONE | 17 |
| 19 | Remote client world | ACTIVE | 17, 18 |
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
- [x] Child PR CI passes and the PR is squash-merged into the integration branch.

## PR 7 acceptance criteria

- [x] Server lifecycle code depends on `WebGateway`, not the embedded socket
  implementation.
- [x] `EmbeddedWebGateway` adapts the PR 6 listener to the gateway contract while
  preserving its start, health, failure, and shutdown behavior.
- [x] Request handlers receive transport-neutral method/path values and return
  transport-neutral status, content type, headers, and bytes.
- [x] Response bodies and headers are defensively copied and validated against
  response-splitting injection.
- [x] Handler failures return a contained 500 response without stopping the
  Minecraft server or listener.
- [x] A future `RelayWebGateway` can implement the same interface without changes
  to lifecycle, authorization, or demand callers.
- [x] Focused tests cover the interface through the embedded adapter, request and
  response translation, routing, defensive copies, and unsafe headers.
- [x] `gradlew clean test build` passes after the full PR 7 change (30 tests).
- [x] Child PR CI passes and the PR is squash-merged into the integration branch.

## PR 8 acceptance criteria

- [x] Viewer and owner credentials are separate, terminal-scoped, independently
  revocable, and owner credentials may satisfy viewer access.
- [x] Tokens contain 256 bits of secure random secret material and are shown only
  when issued; persistent state stores SHA-256 digests, never plaintext tokens.
- [x] Owner/operator commands issue one token, revoke one credential ID, or revoke
  every credential of one scope without broadcasting the secret.
- [x] `AUTHENTICATION.md` explains opt-in configuration, token scope, bearer use,
  one-token and whole-scope revocation, and plaintext handling.
- [x] The embedded gateway parses bounded request headers, normalizes names, and
  rejects duplicates and unsafe control characters.
- [x] Terminal routes authenticate the bearer token before resolving its terminal
  slug and return the same 404 shape for missing, invalid, cross-terminal, revoked,
  or unknown credentials.
- [x] Authenticated viewer or owner tokens can read terminal and camera metadata;
  unauthenticated callers can read only `/health`.
- [x] Focused tests cover scope separation, wrong-terminal denial, malformed
  tokens, revocation, digest-only persistence, metadata nondisclosure, and JSON
  escaping.
- [x] `gradlew clean test build` passes after the full PR 8 change (37 tests).
- [x] Child PR CI passes and the PR is squash-merged into the integration branch.

## PR 9 acceptance criteria

- [x] Only a valid viewer or owner credential for the terminal can open demand
  for one of that terminal's registered cameras.
- [x] Opening demand returns an opaque session UUID and deduplicates repeated
  opens by the same credential and camera.
- [x] Renewal and explicit close require the exact credential, terminal, camera,
  and session tuple.
- [x] Missing clients expire after 30 seconds; renewal extends that deadline.
- [x] Revoking one credential or a whole terminal scope removes its active demand
  immediately.
- [x] Demand is transient, cleared when the server web lifecycle stops, and
  exposed as one shared server source for PR 10.
- [x] Focused tests cover authentication, camera ownership, deduplication, viewer
  counts, renewal, close, timeout, wrong-session denial, and revocation.
- [x] `gradlew clean test build` passes after the full PR 9 change (42 tests).
- [x] Child PR CI passes and the PR is squash-merged into the integration branch.

## PR 10 acceptance criteria

- [x] One transient manager per logical server combines authenticated web-viewer
  counts and eligible in-world screen counts by stable camera UUID.
- [x] A screen creates demand only while its chunk is already loaded and a
  genuine same-dimension player is within 64 blocks; no line-of-sight check or
  chunk load is introduced.
- [x] Integrated, LAN, and dedicated activation rules are represented explicitly;
  paused singleplayer and empty LAN sessions cannot activate feeds, while an
  authorized render agent can support an otherwise empty dedicated server.
- [x] Demand enters `ACTIVATING`; production success/failure can move it to
  `AVAILABLE` or `UNAVAILABLE` without coupling this PR to rendering.
- [x] Final demand enters `RETAINING` for 30 seconds, renewed demand reactivates,
  and expiry returns the feed to `INACTIVE`.
- [x] Focused tests cover source union, counts, range and dimension boundaries,
  unloaded chunks, render-agent exclusion, environment gates, and retention.
- [x] `gradlew clean test build` passes after the full PR 10 change (52 tests).
- [x] Child PR CI passes and the PR is squash-merged into the integration branch.

## PR 11 acceptance criteria

- [x] Every demanded or retaining camera holds an entity-ticking ticket for its
  own chunk and the eight neighboring chunks.
- [x] Lease locations use the camera's dimension and current block position;
  terminal chunks are not leased implicitly.
- [x] Overlapping camera areas share one ticket per chunk through explicit
  reference counts.
- [x] Moving a camera replaces its old dimension/chunk tickets with the new area.
- [x] Tickets remain throughout PR 10's 30-second retention period, then release
  when the feed becomes inactive; missing cameras and server shutdown also clean
  up immediately.
- [x] Focused tests cover the 3x3 boundary, overlaps, movement, inactivity,
  retention, and deterministic cleanup.
- [x] `gradlew clean test build` passes after the full PR 11 change (58 tests).
- [x] Child PR CI passes and the PR is squash-merged into the integration branch.

## PR 12 acceptance criteria

- [x] A common-server hook records every chunk that vanilla already sends through
  `ServerWorld.tickChunk` during the current world tick.
- [x] Only loaded PR 11 lease locations in the matching dimension that were not
  already ticked by vanilla receive supplemental processing.
- [x] Supplemental processing follows the world's `randomTickSpeed`, skips frozen
  or zero-speed worlds, and applies vanilla-style block and fluid random ticks.
- [x] The supplemental path does not call chunk weather, lightning, inhabited-time,
  special-spawner, or natural mob-spawning logic.
- [x] Tick records are transient, consumed after each world tick, and cleared when
  the logical server stops.
- [x] Focused tests cover missing vanilla ticks, duplicate prevention, dimension
  isolation, and not-yet-loaded leases.
- [x] `gradlew clean test build` passes after the full PR 12 change (62 tests).
- [x] Child PR CI passes and the PR is squash-merged into the integration branch.

## PR 13 acceptance criteria

- [x] Render control has its own explicit v1 contract and rejects unknown versions
  and message kinds instead of silently reinterpreting them.
- [x] Direction-specific envelopes cover agent capabilities, challenge/proof/result
  authentication, job assignment, status, and cancellation.
- [x] Authentication messages carry credential/challenge IDs and fixed-size proof
  bytes, never a plaintext render-agent secret.
- [x] Assignments carry distinct job UUIDs, monotonic revisions, immutable camera
  references, and the fixed 854x480 at 10 FPS v1 video contract.
- [x] Capability masks, capacities, proofs, revisions, result/session invariants,
  status details, video fields, enums, and mutable byte arrays are validated or
  defensively copied.
- [x] Both payload codecs are registered without adding authentication, scheduling,
  or client execution handlers ahead of PRs 14–16.
- [x] `RENDER_PROTOCOL.md` documents the handshake, canonical proof input, job
  revision rules, cancellation reasons, media bits, and same-dimension boundary.
- [x] Focused tests round-trip every message and reject malformed or future input.
- [x] `gradlew clean test build` passes after the full PR 13 change (68 tests).
- [x] Child PR CI passes and the PR is squash-merged into the integration branch.

## PR 14 acceptance criteria

- [x] Operator-only commands issue one 256-bit render-agent secret for an explicit
  Minecraft UUID, revoke one credential, or revoke all credentials for that UUID.
- [x] Tokens are shown only when issued; persistent state stores the credential
  UUID, allowlisted Minecraft UUID, SHA-256-derived key, and creation time, never
  the raw secret or token.
- [x] Every hello receives a fresh 32-byte challenge with the same response shape;
  challenges expire after 15 seconds and the first proof attempt consumes them.
- [x] Proof verification binds protocol version, challenge UUID, nonce, and the
  connecting Minecraft UUID and uses a constant-time HMAC comparison.
- [x] Successful sessions are connection-local and transient, force spectator mode,
  and feed PR 10's render-agent predicate so agents create neither screen demand
  nor genuine-player presence.
- [x] Reauthentication, disconnect, shutdown, and credential revocation remove
  sessions; revocation disconnects affected online agents immediately.
- [x] Job-status messages remain inert until authenticated scheduling is added in
  PR 15.
- [x] `RENDER_AUTHENTICATION.md` documents commands, storage, challenge lifetime,
  session behavior, revocation, and demand exclusion.
- [x] Focused tests cover UUID and secret matching, denial, expiry, replay,
  reauthentication, persistence, malformed tokens, revocation, and disconnect.
- [x] `gradlew clean test build` passes after the full PR 14 change (77 tests).
- [x] Child PR CI passes and the PR is squash-merged into the integration branch.

## PR 15 acceptance criteria

- [x] The logical server assigns each activating camera to at most one
  authenticated render-agent session.
- [x] Scheduling is same-dimension and respects both advertised agent capacity
  and the global four-active-camera ceiling.
- [x] Stable jobs survive ordinary reconciliations and the 30-second retention
  period; new demand can reclaim a retained slot when capacity is full.
- [x] Job IDs are distinct from camera IDs, and every cancellation increments
  the assignment revision with a stable reason.
- [x] Only the assigned agent and authentication session may update a job, and
  stale revisions or regressive status updates are ignored.
- [x] Availability updates the demand lifecycle; failure, disconnect, session
  replacement, dimension change, and camera movement make live demand
  unavailable before later reassignment.
- [x] A failed camera-agent pair is not retried during the same authenticated
  session, preventing a failing node from receiving the same job every tick.
- [x] `RENDER_SCHEDULING.md` documents assignment, retention, status, limits,
  cancellation, and failure behavior.
- [x] Focused tests cover limits, dimensions, stale status, availability,
  failure, disconnect, movement, retention, and retained-slot reclamation.
- [x] `gradlew clean test build` passes after the full PR 15 change (85 tests).
- [x] Child PR CI passes and the PR is squash-merged into the integration branch.

## PR 16 acceptance criteria

- [x] Render-agent mode is opt-in and disabled when its local configuration is
  absent, disabled, unreadable, or invalid.
- [x] Configuration accepts a server-issued credential, capacity 1â€“4, and known
  media transports without retaining or logging the plaintext token.
- [x] The client starts authentication only after its player exists, answers the
  server challenge for that Minecraft UUID, and accepts only the matching result.
- [x] Assignments require an authenticated session, the current dimension, a
  newer revision, and available configured capacity.
- [x] Accepted jobs are staged behind an executor boundary for PRs 17â€“20 and
  report `ACCEPTED`, never `AVAILABLE` before a real downstream feed exists.
- [x] Only executor hooks report availability or failure; failure details are
  printable and capped to the protocol limit.
- [x] Newer cancellations stop jobs, stale assignments and cancellations are
  ignored, and disconnect clears the session, revision history, and all jobs.
- [x] `RENDER_AGENT.md` documents safe opt-in setup, credential handling,
  authentication, job limits, cleanup, and the downstream availability boundary.
- [x] Focused tests cover configuration, handshake binding, denial, dimensions,
  capacity, revisions, job status, and disconnect cleanup.
- [x] `gradlew clean test build` passes after the full PR 16 change (98 tests).
- [x] Child PR CI passes and the PR is squash-merged into the integration branch.

## PR 17 acceptance criteria

- [x] Every accepted render job receives a complete snapshot tied to its exact
  job ID, revision, session-owned assignment, camera reference, and sequence.
- [x] The 3Ã—3 camera area uses vanilla chunk-with-light packets, preserving
  blocks, biomes, heightmaps, block entities, and sky and block lighting.
- [x] Entities in the area use vanilla bootstrap packets for spawn state,
  tracked data, attributes, velocity, equipment, passengers, and leashes;
  authenticated render agents are excluded.
- [x] World time, time of day, rain, and thunder are part of the snapshot header.
- [x] Vanilla packets are length-delimited and bounded by per-packet, packet-count,
  snapshot-size, part-size, and concurrent-assembly limits.
- [x] The server sends at most one begin message or snapshot part per tick and
  waits for leased chunks instead of publishing an incomplete scene.
- [x] The client accepts out-of-order parts but installs data only after exact
  sizes, declared total length, and SHA-256 digest all match.
- [x] Stale, conflicting, orphaned, malformed, oversized, and partial transfers
  cannot replace a job's installed snapshot and are kept within bounded memory.
- [x] Capture failure cancels the job and makes live demand unavailable; snapshot
  installation alone never reports a feed as available.
- [x] `SCENE_SNAPSHOTS.md` documents contents, framing, limits, pacing, retry,
  verification, and the remote-world boundary.
- [x] Focused tests cover codec bounds, fragmentation, packet framing, defensive
  copies, out-of-order assembly, digest rejection, concurrency, job binding,
  cancellation cleanup, and scheduler capture failure.
- [x] `gradlew clean test build` passes after the full PR 17 change (108 tests).
- [x] Child PR CI passes and the PR is squash-merged into the integration branch.

## PR 18 acceptance criteria

- [x] Live changes are tied to the exact job revision and verified snapshot, with
  a strictly increasing delta sequence.
- [x] Block, block-entity, light, entity, time, and weather changes are collected
  for the camera's 3x3 scene without idle full-chunk resends.
- [x] Entity additions, removals, and changed vanilla bootstrap state exclude
  authenticated render agents and remain atomic within one delta.
- [x] The server sends at most one delta per job per tick and enforces a 512 KiB
  payload limit plus bounded block and lighting journals.
- [x] The client bounds unapplied deltas at 256 and fails the job on a sequence
  gap, snapshot mismatch, or queue overflow so reassignment starts fresh.
- [x] Metadata-only updates keep time and weather current at least every 20 ticks.
- [x] `SCENE_DELTAS.md` documents contents, ordering, limits, failure recovery,
  and the PR 19 remote-world boundary.
- [x] `gradlew clean test build` passes with 115 focused protocol and ordering tests.
- [x] Child PR CI passes and the PR is squash-merged into the integration branch.

## PR 19 acceptance criteria

- [x] Every installed snapshot creates a separate vanilla client world and world
  renderer tied to the exact job revision and snapshot sequence.
- [x] Remote chunks, lighting, block entities, entities, time, and weather stay
  separate from the render agent player's real client world.
- [x] Scene bytes use the connection's registry-aware vanilla play codec and a
  strict allow-list that rejects non-scene packets.
- [x] The verified snapshot is applied before ordered deltas, and packet handling
  restores both main-world references even when application fails.
- [x] Remote entity creation avoids real-world spawn sounds; remote entities and
  renderer state advance only on the client thread.
- [x] Snapshot replacement, cancellation, failure, dimension loss, and disconnect
  close the previous renderer and remove the remote world.
- [x] Creation or update failure reports the job failed; a remote world alone
  never reports the feed available before PR 20 renders a frame.
- [x] `REMOTE_CLIENT_WORLD.md` documents isolation, allowed packets, lifecycle,
  cleanup, failure recovery, and the PR 20 boundary.
- [x] `gradlew clean test build` passes with 118 focused ownership and policy tests.
- [ ] Child PR CI passes and the PR is squash-merged into the integration branch.

## Version milestones

- 0.3.0-alpha.1: registry and migration (PRs 0–5)
- 0.3.0-alpha.2: web, demand, and chunk leases (PRs 6–12)
- 0.3.0-beta.1: arbitrary-distance video (PRs 13–21)
- 0.3.0-beta.2: render agent and live audio (PRs 16, 22–23)
- 0.3.0-rc.1: hardening (PRs 24–26)
- 0.3.0: same-dimension overhaul (PR 27)
- 0.4.0: cross-dimensional vanilla dimensions
