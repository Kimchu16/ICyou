# Render-agent client

The normal ICyou client can opt into render-agent mode. It stays disabled unless
the operator supplies a server-issued credential in the local client config.

## Setup

1. On the server, run `/icyou render-agent issue <player-uuid>`.
2. On that player's client, create `config/icyou-render-agent.properties`:

   ```properties
   agent.enabled=true
   agent.token=icyou_render_<credential-id>_<secret>
   agent.capacity=1
   agent.transports=mjpeg
   ```

3. Restart the client and connect with the matching Minecraft account.

Capacity must be 1â€“4. Supported transport names are `mjpeg` and `webrtc`,
separated by commas. Configuration changes take effect after a client restart.
The token is parsed into credential ID and derived key at startup; the plaintext
token is not retained or logged. Missing or invalid configuration leaves the
agent disabled.

## Connection and jobs

After joining, the agent sends its credential ID, capacity, and transports. It
answers the server's fresh challenge using the account UUID and derived key.
Only an accepted response enables jobs for that connection.

Assignments are accepted only while authenticated, in the client's current
dimension, and below the configured capacity. The client ignores stale revisions
and clears all jobs on disconnect. Server cancellations must advance the known
revision before they can stop a job.

PR 16 stages accepted jobs and reports `ACCEPTED`. It deliberately does not report
`AVAILABLE` by itself. The later scene and offscreen-rendering stages call the
explicit available or failed hooks when a real feed exists, so the server never
advertises placeholder work as live video.
