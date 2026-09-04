# Remote client worlds

Each accepted render job gets its own small vanilla `ClientWorld`. Its chunks,
lighting, block entities, entities, time, and weather are separate from the world
the render-agent player is standing in.

## Building a world

The client creates a remote world only after the complete starting snapshot has
passed its size and SHA-256 checks. The world uses the current connection's
registry data and the job's same-dimension type, plus its own `WorldRenderer` for
the offscreen-rendering step in PR 20.

Snapshot and delta bytes are decoded with Minecraft's play-packet codec. A strict
allow-list accepts only chunk, block, block-entity, lighting, entity bootstrap,
entity removal, and related entity-state packets. Connection, chat, UI, command,
inventory, and other unrelated packets are rejected.

Vanilla packet handling is redirected to the remote world for one packet at a
time. Both the network handler and client world references are restored in a
`finally` block, including when decoding or application fails. Entity creation is
invoked directly so remote minecarts and similar entities do not play spawn sounds
in the render agent's real world.

## Updates and cleanup

The verified snapshot is applied before any queued live deltas. Deltas must still
match the exact job revision and snapshot sequence. Remote entities and renderer
state tick on the client thread, while server deltas remain authoritative for
blocks, lighting, entity state, time, and weather.

Replacing a snapshot closes its previous renderer. Cancellation, failed jobs,
dimension changes, and disconnects also remove the world and release renderer
resources. Any world creation, decode, or update failure fails the job so the
scheduler can start again from a clean snapshot.

Creating a remote world does not report the feed as available. PR 20 must first
produce real offscreen frames from its exposed world and renderer.
