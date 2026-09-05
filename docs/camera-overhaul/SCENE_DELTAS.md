# Live scene updates

After a render agent verifies its initial scene snapshot, the server sends small,
ordered updates so the camera view stays current without resending whole chunks.

## What changes are sent

- Block and block-entity updates from the camera's 3x3 leased chunk area.
- Lighting updates for changed chunks in that area.
- Entity additions, removals, movement, tracked data, equipment, passengers, and
  other vanilla bootstrap state. A changed entity is removed and replaced within
  one atomic delta.
- World time, time of day, rain, and thunder at least once every 20 ticks.

Authenticated render-agent entities are excluded. The server uses vanilla play
packet encoding for scene data, matching the snapshot format.

## Ordering and limits

Every delta names the exact job revision and snapshot sequence, followed by a
strictly increasing delta sequence. The server sends no more than one delta per
job per tick, and each encoded delta is limited to 512 KiB. Block and lighting
capture is bounded per tick; an overflow cancels the affected job instead of
silently producing an incomplete scene.

The client accepts only the next sequence and holds at most 256 unapplied deltas.
A missing sequence, wrong snapshot, or queue overflow fails the job. The scheduler
then creates a new assignment whose fresh snapshot restores a known-good base.

PR 19 will consume the verified snapshot and queued deltas in an isolated remote
client world. Installing scene data alone does not mark a feed available.
