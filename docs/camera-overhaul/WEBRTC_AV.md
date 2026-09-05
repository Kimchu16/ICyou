# WebRTC audio and video

PR 23 adds the primary low-latency camera transport. A browser receives the
same remote camera video and spatial world audio through one WebRTC peer
connection. MJPEG remains available as a video-only fallback.

## Viewer signaling

The viewer must first open an authenticated camera demand session. It then uses
the same bearer credential with these non-trickle signaling routes:

1. Create a browser `RTCPeerConnection`, add receive-only audio and video
   transceivers, create an offer, set it locally, and wait for ICE gathering to
   complete.
2. `POST` the complete SDP offer as UTF-8 to
   `/v1/terminals/{slug}/cameras/{cameraId}/webrtc/{sessionId}`. The response
   contains an opaque `peerId`.
3. Poll `GET .../webrtc/{sessionId}/{peerId}`. A pending answer returns `202`;
   the complete SDP answer returns `200 application/sdp`.
4. Set that answer as the browser's remote description. Continue polling the
   peer URL at least every 20 seconds as its authenticated keepalive; an
   unchanged answer may be ignored. Send `DELETE` when the viewer closes.

All responses are `no-store`. Every request rechecks the credential and exact
terminal, camera, and demand-session tuple. Request bodies are exact-length,
limited to 128 KiB, and do not accept chunked transfer encoding. SDP itself is
limited to 64 KiB. The embedded gateway answers browser CORS preflight and
allows explicit `Authorization` and `Content-Type` headers; bearer tokens are
still required on every non-preflight route.

## Server and agent authorization

The server is a signaling broker; it does not relay media. It opens a peer only
for an accepted or available scheduler assignment whose connected agent has the
exact authenticated session and advertises WebRTC. Answers must match the peer,
job, revision, agent, and session that received the offer.

At most 16 peers exist per server and per agent. A peer expires after 30 seconds
without authenticated polling, matching the viewer-demand timeout. Demand
expiry, credential revocation, job
replacement, session replacement, agent disconnect, explicit closure, and
server shutdown all close its transient state.

## Video and availability

The agent converts the newest 854x480 top-down RGBA frame directly to I420 and
pushes it into one custom video source shared by viewers of that job. It keeps
only the latest existing frame and never adds another video queue. A WebRTC-only
job becomes available after its first frame is successfully pushed. Agents that
also advertise MJPEG retain the PR 21 availability path.

## Spatial audio and synchronization

The agent resolves each seeded vanilla sound definition, decodes its OGG asset
on one bounded worker, and mixes 10 ms frames of 48 kHz, 16-bit stereo PCM.
Distance attenuation uses the server capture radius. Equal-power panning uses
the camera's facing direction; pitch combines the event and selected asset.
There are at most 64 active clips, and decoded work uses a bounded 512-item queue
that discards the oldest item under sustained overload.

Audio and video sources share one native synchronization clock per render job
and tracks from both sources are added to the same peer connection. Remote audio
never enters the render agent's local Minecraft sound manager.

## Runtime dependency

ICyou bundles `dev.onvoid.webrtc:webrtc-java:0.16.0` plus its official Windows
x86-64, Linux x86-64/ARM64/ARM32, and macOS x86-64/ARM64 native artifacts. The
native runtime is loaded only when the local render-agent configuration includes
`webrtc`; startup failure is contained and logged without affecting ordinary
players or MJPEG-only agents. The library is distributed under Apache-2.0; see
the upstream license and notice in the webrtc-java repository.
