package com.matissjurevics.icyou.web;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.matissjurevics.icyou.render.video.ServerVideoFrameStore.PublishedFrame;

/** Writes a latest-frame MJPEG stream without allocating a per-viewer queue. */
public final class MjpegStream implements WebResponse.StreamingBody {

    public static final String BOUNDARY = "icyou-frame";
    public static final String CONTENT_TYPE = "multipart/x-mixed-replace; boundary=" + BOUNDARY;
    private static final long POLL_MILLIS = 100;

    private record Sequence(java.util.UUID jobId, long revision, long frame) {
    }

    private final BooleanSupplier authorized;
    private final Supplier<Optional<PublishedFrame>> latestFrame;

    public MjpegStream(BooleanSupplier authorized,
                       Supplier<Optional<PublishedFrame>> latestFrame) {
        this.authorized = Objects.requireNonNull(authorized, "authorized");
        this.latestFrame = Objects.requireNonNull(latestFrame, "latestFrame");
    }

    @Override
    public void write(OutputStream output) throws IOException {
        Objects.requireNonNull(output, "output");
        Sequence sent = null;
        while (authorized.getAsBoolean()) {
            PublishedFrame frame = latestFrame.get().orElse(null);
            if (frame != null) {
                Sequence sequence = new Sequence(frame.jobId(), frame.jobRevision(),
                        frame.sequence());
                if (!sequence.equals(sent)) {
                    writePart(output, frame);
                    output.flush();
                    sent = sequence;
                }
            }
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        output.write(("--" + BOUNDARY + "--\r\n")
                .getBytes(StandardCharsets.US_ASCII));
    }

    private static void writePart(OutputStream output, PublishedFrame frame)
            throws IOException {
        byte[] jpeg = frame.jpeg();
        String header = "--" + BOUNDARY + "\r\n"
                + "Content-Type: image/jpeg\r\n"
                + "Content-Length: " + jpeg.length + "\r\n"
                + "X-Frame-Sequence: " + frame.sequence() + "\r\n\r\n";
        output.write(header.getBytes(StandardCharsets.US_ASCII));
        output.write(jpeg);
        output.write("\r\n".getBytes(StandardCharsets.US_ASCII));
    }
}
