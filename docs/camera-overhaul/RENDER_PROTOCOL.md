# Render-agent control protocol v1

The logical server and render agents exchange one versioned control envelope in
each direction. Unknown protocol versions and message kinds are rejected; they
are never interpreted as the current version.

## Authentication flow

1. The agent sends `AgentHello` with its credential ID, job capacity, and
   supported media transports. These claims are untrusted until authentication
   succeeds.
2. The server checks that the connecting Minecraft UUID is allowlisted, then
   sends a fresh `AuthChallenge` containing a challenge UUID and 32 random bytes.
3. The agent derives `SHA-256(secret)` from its one-time-issued secret and sends
   an `AuthProof` containing `HMAC-SHA-256(key, protocol version + challenge UUID
   + nonce + Minecraft UUID)`.
4. The server returns `AuthResult`. Only an accepted result carries a transient
   session UUID. Challenges are single-use and expire under PR 14's policy.

No plaintext render-agent secret is sent in a control payload. PR 14 owns the
credential store, allowlist, challenge lifetime, proof validation, and connection
state.

The proof input uses network byte order: the protocol version as a four-byte
signed integer, followed by the challenge UUID's two 64-bit halves, the 32 nonce
bytes, and the connecting Minecraft UUID's two 64-bit halves.

## Job flow

- `JobAssignment` identifies a job UUID, monotonic revision, immutable camera
  reference, and the v1 854×480 at 10 FPS video contract.
- `JobStatus` echoes the job UUID and exact revision with `ACCEPTED`, `AVAILABLE`,
  or `FAILED`. The optional detail is capped at 160 printable characters.
- `JobCancel` identifies the job and revision plus one stable reason: demand
  ended, reassignment, camera movement, or server shutdown.

Receivers ignore stale revisions. A replacement or cancellation increases the
revision. Job IDs and camera IDs are distinct: a new scheduling lifetime gets a
new job ID even when it renders the same camera.

Until 0.4.0, the scheduler must assign only cameras in the render agent's current
dimension. Authentication and scheduling handlers are deliberately outside PR 13.

## Media capability bits

- `WEBRTC` is the primary synchronized media transport.
- `MJPEG` is the video-only fallback and diagnostic transport.

An agent must advertise at least one known transport. Unknown capability bits,
invalid capacities, malformed proof sizes, negative revisions, and unsupported
video parameters are rejected during decoding.
