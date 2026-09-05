# Render scheduling

The logical server owns render jobs. Each demanded camera can have one job, and
each job belongs to one authenticated render-agent session.

## Assignment rules

- Only `ACTIVATING` feeds receive new jobs.
- An agent must be in the camera's dimension. Cross-dimension rendering remains
  outside the 0.3.0 scope.
- The scheduler respects both the agent's authenticated capacity and the global
  four-active-camera limit.
- Existing assignments remain stable instead of moving between equally suitable
  agents. A job ID is new for every later scheduling lifetime.
- Retaining feeds keep their jobs during the 30-second grace period. New demand
  may reclaim a retained slot when a limit would otherwise block activation.

## Status and failure rules

Agents must echo the exact job ID and revision. Updates from another agent,
another authentication session, or an older revision are ignored.

`ACCEPTED` confirms receipt. `AVAILABLE` moves the feed from `ACTIVATING` to
`AVAILABLE`. `FAILED`, disconnect, reauthentication, dimension changes, and
camera movement remove the assignment and make live demand `UNAVAILABLE`
immediately. A failed camera-agent pair is not retried in the same authenticated
session; another suitable agent may take it on the next activation pass.

Cancellation increments the job revision and records whether demand ended, the
job was reassigned, the camera moved, or the server stopped. Grace expiry and
server shutdown release all remaining assignments.
