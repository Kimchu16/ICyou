# Scene snapshots

PR 17 transfers one complete starting scene for each accepted render job. The
snapshot is tied to the exact job ID, job revision, session-owned assignment,
camera reference, and a monotonic snapshot sequence.

## Contents

The server captures the camera chunk and its eight neighbors using Minecraft's
own chunk-with-light packet codec. This preserves section palettes, biomes,
heightmaps, block entities, and sky and block lighting in the same format the
normal client understands.

Entities inside that 3Ã—3 area are bootstrapped with their vanilla spawn,
tracked-data, attribute, velocity, equipment, passenger, and leash packets.
Authenticated render agents are excluded. The snapshot header also carries
world time, time of day, rain, and thunder values.

## Framing and limits

Vanilla play packets are length-delimited into one stream. A snapshot is limited
to 32 MiB, 4,096 packets, and 4 MiB per vanilla packet. The stream is split into
parts of at most 512 KiB. The server sends at most one begin message or part per
tick so an initial scene cannot monopolize one network tick.

The begin message declares the exact byte count, part count, and SHA-256 digest.
The client accepts parts out of order, bounds concurrent partial snapshots to
the four-job limit, and exposes nothing until every part has the exact expected
size and the final digest matches. Conflicting, stale, oversized, truncated, or
orphaned data is rejected and partial state is discarded or bounded.

Chunk leases may still be loading when a job is first accepted, so capture waits
and retries. A permanent capture or size failure cancels that job and makes its
live feed unavailable. Installing a verified snapshot only stages data for PR 19;
it does not report the feed as available before a remote world and renderer exist.
