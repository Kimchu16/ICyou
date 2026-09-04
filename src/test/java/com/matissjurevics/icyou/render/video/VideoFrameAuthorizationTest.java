package com.matissjurevics.icyou.render.video;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.matissjurevics.icyou.device.CameraRef;
import com.matissjurevics.icyou.render.auth.RenderAgentAuthenticator.Session;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.MediaTransport;
import com.matissjurevics.icyou.render.schedule.RenderScheduler.Assignment;
import com.matissjurevics.icyou.render.schedule.RenderScheduler.AssignmentState;
import com.matissjurevics.icyou.render.video.VideoFrameProtocol.Frame;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

class VideoFrameAuthorizationTest {

    private static final byte[] JPEG = {
            (byte) 0xff, (byte) 0xd8, 1, (byte) 0xff, (byte) 0xd9
    };

    @Test
    void requiresTheExactAcceptedMjpegSessionJobRevisionAndCamera() {
        UUID player = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID job = UUID.randomUUID();
        CameraRef camera = new CameraRef(UUID.randomUUID(), World.OVERWORLD,
                BlockPos.ORIGIN);
        Session session = new Session(sessionId, player, UUID.randomUUID(), 1,
                Set.of(MediaTransport.MJPEG), Instant.EPOCH);
        Assignment accepted = new Assignment(job, 2, camera, player, sessionId,
                AssignmentState.ACCEPTED);
        Frame frame = new Frame(job, 2, camera.deviceId(), 3, 4, JPEG);

        assertTrue(VideoFrameAuthorization.permits(player, session, accepted, frame));
        assertFalse(VideoFrameAuthorization.permits(player, session,
                new Assignment(job, 2, camera, player, sessionId, AssignmentState.ASSIGNED),
                frame));
        assertFalse(VideoFrameAuthorization.permits(player,
                new Session(sessionId, player, UUID.randomUUID(), 1,
                        Set.of(MediaTransport.WEBRTC), Instant.EPOCH), accepted, frame));
        assertFalse(VideoFrameAuthorization.permits(player, session, accepted,
                new Frame(job, 3, camera.deviceId(), 3, 4, JPEG)));
        assertFalse(VideoFrameAuthorization.permits(UUID.randomUUID(), session,
                accepted, frame));
    }
}
