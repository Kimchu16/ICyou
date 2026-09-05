package com.matissjurevics.icyou.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.matissjurevics.icyou.render.video.ServerVideoFrameStore.PublishedFrame;

class MjpegStreamTest {

    @Test
    void writesEachLatestFrameOnceAndStopsWhenAuthorizationEnds() throws Exception {
        byte[] jpeg = {(byte) 0xff, (byte) 0xd8, 7, (byte) 0xff, (byte) 0xd9};
        PublishedFrame frame = new PublishedFrame(UUID.randomUUID(), 0,
                UUID.randomUUID(), 4, 5, 6, jpeg);
        AtomicInteger checks = new AtomicInteger();
        MjpegStream stream = new MjpegStream(
                () -> checks.getAndIncrement() < 2, () -> Optional.of(frame));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        stream.write(output);

        String body = output.toString(StandardCharsets.ISO_8859_1);
        assertEquals(1, occurrences(body, "Content-Type: image/jpeg"));
        assertTrue(body.contains("X-Frame-Sequence: 4"));
        assertTrue(body.endsWith("--" + MjpegStream.BOUNDARY + "--\r\n"));
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        for (int index = 0; (index = value.indexOf(needle, index)) >= 0;
             index += needle.length()) {
            count++;
        }
        return count;
    }
}
