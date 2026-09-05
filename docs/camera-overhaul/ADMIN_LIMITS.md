# Camera admin limits

PR 24 makes the camera system's server resource ceilings configurable without
trusting client claims. Limits are loaded once per logical server run from
`config/icyou-camera-limits.properties`; restart the server after changing the
file. If the file is missing, the documented defaults apply. If any value or
relationship is invalid, the complete file is rejected and safe defaults are
used.

## Settings

| Property | Default | Allowed |
|---|---:|---:|
| `limits.registered-cameras` | 64 | 1-4096 |
| `limits.active-cameras` | 4 | 1-64 |
| `limits.viewers-per-camera` | 8 | 1-16 |
| `limits.total-viewers` | 16 | 1-256 |
| `limits.simulated-chunk-diameter` | 3 | odd values 1-7 |
| `limits.resource-grace-seconds` | 30 | 1-300 |

The active-camera limit cannot exceed the registered-camera limit. The
per-camera viewer limit cannot exceed the total viewer limit. Active cameras
multiplied by the square of the chunk diameter cannot exceed 256 simulated
chunks; this hard relationship prevents individually valid settings from
creating an unsafe combined lease budget.

For example:

```properties
limits.registered-cameras=100
limits.active-cameras=8
limits.viewers-per-camera=12
limits.total-viewers=40
limits.simulated-chunk-diameter=5
limits.resource-grace-seconds=60
```

## Enforcement

- The registry blocks new camera links and restorations at the configured
  registered-camera ceiling. Existing saved cameras are preserved if an admin
  lowers the limit below the current count; new links remain blocked until the
  count falls below the limit.
- The scheduler never owns more than the configured number of active or
  retaining jobs across all render agents.
- Viewer demand is deduplicated before limits are checked. A new viewer over
  either ceiling receives HTTP `429` with `viewer_limit_reached`; renewals for
  an existing exact viewer session continue to work.
- WebRTC uses the configured total as its server-wide peer limit while retaining
  a hard 16-peer ceiling per render agent.
- Chunk leases, initial scene snapshots, entity selection, and scene deltas all
  use the same configured odd chunk diameter.
- Final demand keeps its job and leases for the configured grace period before
  deterministic cleanup.

Operators can run `/icyou limits` to see the values loaded for the current
server run. The command is read-only; changes remain file-and-restart operations
so related subsystems cannot observe a partially changed limit set.
