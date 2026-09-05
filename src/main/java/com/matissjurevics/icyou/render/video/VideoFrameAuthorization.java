package com.matissjurevics.icyou.render.video;

import java.util.Objects;
import java.util.UUID;

import com.matissjurevics.icyou.render.auth.RenderAgentAuthenticator.Session;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.MediaTransport;
import com.matissjurevics.icyou.render.schedule.RenderScheduler.Assignment;
import com.matissjurevics.icyou.render.schedule.RenderScheduler.AssignmentState;
import com.matissjurevics.icyou.render.video.VideoFrameProtocol.Frame;

/** Pure exact-session authorization check for an incoming encoded frame. */
final class VideoFrameAuthorization {

    private VideoFrameAuthorization() {
    }

    static boolean permits(UUID playerId, Session session, Assignment assignment,
                           Frame frame) {
        Objects.requireNonNull(playerId, "playerId");
        if (session == null || assignment == null || frame == null) {
            return false;
        }
        return session.minecraftId().equals(playerId)
                && session.transports().contains(MediaTransport.MJPEG)
                && assignment.state() != AssignmentState.ASSIGNED
                && assignment.jobId().equals(frame.jobId())
                && assignment.revision() == frame.jobRevision()
                && assignment.camera().deviceId().equals(frame.cameraId())
                && assignment.agentId().equals(playerId)
                && assignment.sessionId().equals(session.sessionId());
    }
}
