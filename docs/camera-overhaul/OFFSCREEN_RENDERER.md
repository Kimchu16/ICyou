# Offscreen camera rendering

PR 20 turns each isolated remote client world into real video-frame input. It
does not encode or transport video; PR 21 consumes the latest raw frame.

## Render contract

- Resolution: 854x480 RGBA.
- Target cadence: 10 frames per second.
- Field of view: 70 degrees, with a 64-block far plane matching the 3x3 scene.
- Camera position and direction come from the registered camera block in the
  remote world.

Each job owns a depth-enabled framebuffer and reusable GPU readback buffer. The
captured OpenGL image is row-flipped into ordinary top-down RGBA bytes. Only the
latest immutable frame is retained, so slow consumers cannot create an unbounded
queue. Across four jobs this retains at most four raw frames.

## Scheduling and isolation

At most one due remote job renders during a game frame. The oldest due job wins,
which bounds frame cost and prevents starvation. Effective cadence can be lower
if the render-agent client itself runs below the rate needed for all assigned
jobs.

Vanilla nested render passes use a scoped offscreen framebuffer context shared
with the existing local camera renderer. The client's world and projection are
restored after every pass, including failures. Remote and local offscreen passes
cannot nest.

The first frame is published only after vanilla reports terrain rendering
complete. Terrain gets up to 30 seconds of scheduled warm-up attempts. Three
consecutive render or capture failures fail the job and release its framebuffer.

## Availability and cleanup

The render agent reports `AVAILABLE` only after it captures the first complete
frame. Job cancellation, snapshot replacement, failure, or disconnect deletes
the framebuffer and removes the retained frame. PR 21 will encode and deliver
these job- and camera-bound frames without changing this rendering lifecycle.
