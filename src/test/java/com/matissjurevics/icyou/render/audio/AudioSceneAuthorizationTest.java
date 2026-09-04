package com.matissjurevics.icyou.render.audio;

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

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

class AudioSceneAuthorizationTest {

    @Test
    void requiresTheExactAcceptedWebrtcSession() {
        UUID player = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Session webrtc = new Session(sessionId, player, UUID.randomUUID(), 1,
                Set.of(MediaTransport.WEBRTC), Instant.EPOCH);
        CameraRef camera = new CameraRef(UUID.randomUUID(), World.OVERWORLD,
                BlockPos.ORIGIN);
        Assignment accepted = new Assignment(UUID.randomUUID(), 0, camera,
                player, sessionId, AssignmentState.ACCEPTED);

        assertTrue(AudioSceneAuthorization.permits(player, webrtc, accepted));
        assertFalse(AudioSceneAuthorization.permits(player,
                new Session(sessionId, player, UUID.randomUUID(), 1,
                        Set.of(MediaTransport.MJPEG), Instant.EPOCH), accepted));
        assertFalse(AudioSceneAuthorization.permits(player, webrtc,
                new Assignment(accepted.jobId(), 0, camera, player, sessionId,
                        AssignmentState.ASSIGNED)));
        assertFalse(AudioSceneAuthorization.permits(UUID.randomUUID(), webrtc, accepted));
        assertFalse(AudioSceneAuthorization.permits(player, webrtc,
                new Assignment(accepted.jobId(), 0, camera, player, UUID.randomUUID(),
                        AssignmentState.ACCEPTED)));
    }
}
