# Remote audio scene

PR 22 captures the vanilla world sounds audible at a remote camera and delivers
ordered sound events to its render agent. It does not play those sounds on the
agent computer or encode audio; PR 23 consumes this bounded handoff for WebRTC.

## Included sounds

The server observes positioned and entity-attached sounds produced by the
authoritative camera world. Weather, blocks, hostile and neutral mobs, ambient
effects, explosions, and nearby-player sounds are included when their vanilla
audible radius reaches the center of the camera block.

Music, records, and the voice category are excluded. Client UI sounds never
enter the server-world journal. Sounds whose source entity is an authenticated
render agent are also excluded, preventing the agent's own actions from feeding
back into a camera. Voice-chat mods remain outside this vanilla sound contract.

## Ordering and bounds

Each non-empty batch carries the exact job UUID, job revision, verified snapshot
sequence, increasing audio-batch sequence, and server world time. Every sound
contains a bounded registry ID, category, world position, volume, pitch, and
random seed so PR 23 can preserve vanilla selection and spatial placement.

The per-world journal holds at most 4,096 sounds in one server tick. A camera
batch holds at most 256 audible sounds and explicitly reports truncation. The
client verifies consecutive batches and retains at most 512 pending sounds per
job, dropping the oldest on overflow. It never creates an unbounded queue.

## Scene and lifecycle binding

Audio starts only after the job's initial scene snapshot is fully delivered.
The server sends it only to the WebRTC-capable render agent assigned to that job
and exact authenticated session.
The client accepts it only while the same job revision and snapshot remain
installed. A gap or scene mismatch fails the job so reassignment starts from a
fresh snapshot and sequence.

Cancellation, replacement, and disconnect remove server sequence state and
client pending events. Until PR 23, the retained events are data only: they do
not enter Minecraft's main sound manager and cannot leak into local gameplay.
