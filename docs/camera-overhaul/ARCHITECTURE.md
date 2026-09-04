# Camera overhaul architecture contract

Status: accepted for the 0.3.x implementation. Changes require an ADR and an
update to `CONTROL.md`.

## Terms and identity

- **Camera**, **terminal**, and **screen** are registered devices. Each has a
  stable UUID and a current `dimension + block position` location.
- A **terminal** owns zero or more cameras and screens. Every camera belongs to
  exactly one terminal. Persisted relationships use UUIDs, never positions.
- A **viewer** is an authenticated web session consuming a feed. A **genuine
  player** is a gameplay player after excluding render agents.
- A **render agent** is an authenticated special spectator that can execute
  render jobs. It never creates demand or genuine-player presence.
- **Demand** is the union of authenticated web viewers and eligible in-world
  screens. A screen is eligible only while its chunk is already loaded and a
  genuine same-dimension player is within 64 blocks. No line-of-sight check is
  made.
- A **chunk lease** is a transient, reference-counted simulation ticket. A
  **render job** is a versioned assignment of one demanded camera to one node.
- A **tombstone** retains a broken camera's identity and ownership for possible
  restoration; the default retention is 30 days.

## Fixed architectural decisions

The logical server is authoritative for identity, ownership, demand, leases,
render scheduling, web sessions, and persistence. Device identity is global to
the server and dimension-aware from its first schema. Client caches, web routes,
and render jobs are projections keyed by stable UUID.

The server hosts v1 web access behind `EmbeddedWebGateway`; callers depend on a
gateway interface so a future `RelayWebGateway` can replace transport without
changing authorization or demand. WebRTC is the primary planned synchronized
A/V transport; MJPEG is a video-only fallback and diagnostic path.

Same-dimension arbitrary-distance rendering is the 0.3.0 scope. References and
terminal relationships can name any dimension immediately, but cross-dimension
render execution is rejected as unsupported until 0.4.0.

## Feed lifecycle

`INACTIVE -> ACTIVATING -> AVAILABLE` is the success path. Failure or loss of a
render node moves a demanded feed to `UNAVAILABLE` immediately, and viewers see
a placeholder. When final demand disappears, `AVAILABLE`, `UNAVAILABLE`, or
`ACTIVATING` moves to `RETAINING`; leases and reusable resources remain for 30
seconds. Renewed demand moves back through `ACTIVATING`. Grace expiry moves to
`INACTIVE` and releases jobs, leases, encoders, and viewer fan-out state.

Singleplayer activation requires demand and a running, unpaused integrated
server; losing window focus or minimizing is not a pause. LAN additionally
requires at least one genuine gameplay player. Dedicated servers may activate
with zero genuine players only when an authorized render agent is connected.

## Simulation and media boundaries

The initial simulated area is the camera chunk plus its eight neighbors.
Existing entities, block entities, redstone, scheduled ticks, fluids, and
vanilla-style random ticks continue where practical. Supplemental processing
must not duplicate vanilla random ticks and must not enable natural mob
spawning. The terminal chunk is not a lease prerequisite.

The initial video contract is 854x480 at 10 FPS. JPEG quality 82 is the default
fallback/debug setting. Normal Minecraft entities, block entities, weather, and
particles take priority over latency refinements. Live audio is captured at the
camera for environmental, block, mob, weather, explosion, and nearby-player
sounds; music, UI, render-agent-local sounds, and voice-chat mods are excluded.

## Version and limit contract

The first overhaul save schema is `1`; the first device-reference network
protocol is `1`. Readers must reject unknown future network versions and must
never silently reinterpret unknown save schemas. Compatible save readers may
migrate older schemas only with the PR 4 backup and ambiguity-report guarantees.

Default ceilings are 64 registered cameras, 4 active cameras, 8 viewers per
camera, 16 viewers total, a 3x3 chunk area, and a 30-second grace period. These
values are centralized in `CameraOverhaulContracts`; runtime configurability is
provided by the validated, server-owned settings in `ADMIN_LIMITS.md`.

## Security and restoration invariants

Terminal pages, names, and camera metadata are undiscoverable without a valid
terminal-scoped credential. Viewer and owner tokens are separate and revocable.
The placing player becomes owner; operators may manage or transfer ownership.
Render-agent authentication requires both an allowed Minecraft UUID and a
server-generated secret.

Restoring a tombstoned camera cancels old jobs and leases, verifies that the
replacement is unlinked, and atomically updates the retained UUID to its new
dimension and position. It may move across dimensions even before rendering
across dimensions is supported.
