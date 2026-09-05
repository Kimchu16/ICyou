# Video delivery

PR 21 moves complete offscreen frames from a render agent to authenticated web
viewers. It provides MJPEG video only. PR 23 adds synchronized WebRTC audio and
video without replacing this diagnostic fallback.

## Encoding and agent backpressure

The render agent encodes the latest 854x480 RGBA frame as a quality-82 JPEG on a
single background worker. It allows one encode in flight and remembers only the
newest raw frame for each job. A slow encoder therefore lowers effective frame
rate instead of building a queue.

Every JPEG is limited to 2 MiB and carries its job UUID, job revision, camera
UUID, frame sequence, and capture time. The agent sends availability only after
the first JPEG for that exact job has been encoded and handed to the network.
Three consecutive encoding failures fail and release the job.

## Server authorization and storage

The server accepts a frame only when all of these values still match:

- the connected player's authenticated render-agent session;
- an advertised MJPEG transport capability;
- the scheduler's assigned agent and session UUIDs;
- an accepted or already-available job UUID and revision; and
- the assigned camera UUID.

Invalid or stale input is ignored. The server keeps only the highest frame
sequence for an exact job and only the current job for a camera. Scheduler
cancellation, replacement, and shutdown remove retained bytes.

## Authenticated MJPEG stream

A viewer first opens the existing authenticated demand route, then requests:

`GET /v1/terminals/{slug}/cameras/{cameraId}/video/{sessionId}`

The same bearer credential, terminal, camera, and opaque demand-session UUID must
match. The response is `multipart/x-mixed-replace` with `Cache-Control: no-store`
and no fixed content length. While connected, the stream renews and rechecks the
credential and demand session. Revocation, expiry, explicit closure, server
shutdown, or client disconnect ends it.

Each viewer polls the shared latest frame and writes only a changed job revision
or sequence. There is no per-viewer frame queue, so a slow viewer skips frames
and cannot make retained memory grow.

## WebRTC boundary

This route has no audio, negotiation, peer connection, or relay behavior. PR 22
adds remote audio scene data; PR 23 combines audio and video over WebRTC. MJPEG
remains a bounded video-only fallback for agents that explicitly advertise it.
