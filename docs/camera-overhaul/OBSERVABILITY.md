# Camera observability

PR 25 gives operators a small, privacy-safe view of the live camera system.
Run `/icyou status` from the server console or as a permission-level 2 operator.
The command is read-only and reports five short lines:

- overall state: `idle`, `running`, or `degraded`;
- registered cameras and feed lifecycle counts;
- viewers, render agents, jobs, ready jobs, and WebRTC peers;
- leased chunks, cached video frames, and web-listener state;
- failure counters accumulated since this server started.

`idle` means there is no active or retained camera work. `running` means work is
starting, available, or being retained. `degraded` means at least one demanded
feed is unavailable. The server logs only changes between these states, sampled
every five seconds, so a persistent condition does not flood the log.

## Failure counters

The in-memory counters cover viewer-capacity rejections, rejected WebRTC offers,
invalid or stale video frames, and scene snapshot or delta failures. Counters
are monotonic for one server run, saturate instead of overflowing, and reset on
restart. They are diagnostic totals, not durable billing or audit records.

## Privacy boundary

The status command and state-change logs contain counts only. They never expose
camera or terminal names, UUIDs, positions, dimensions, slugs, credentials,
viewer identities, SDP, or media. The unauthenticated `/health` endpoint remains
the generic `{"status":"ok"}` response and does not expose the operator snapshot.

Configuration values remain available through `/icyou limits`; use that command
beside `/icyou status` when diagnosing a capacity issue.
