package com.matissjurevics.icyou.render.audio;

import java.util.UUID;

import com.matissjurevics.icyou.render.auth.RenderAgentAuthenticator.Session;
import com.matissjurevics.icyou.render.protocol.RenderProtocol.MediaTransport;
import com.matissjurevics.icyou.render.schedule.RenderScheduler.Assignment;
import com.matissjurevics.icyou.render.schedule.RenderScheduler.AssignmentState;

/** Exact authenticated-session check for remote audio delivery. */
final class AudioSceneAuthorization {

    private AudioSceneAuthorization() {
    }

    static boolean permits(UUID playerId, Session session, Assignment assignment) {
        return playerId != null && session != null && assignment != null
                && session.minecraftId().equals(playerId)
                && session.transports().contains(MediaTransport.WEBRTC)
                && assignment.state() != AssignmentState.ASSIGNED
                && assignment.agentId().equals(playerId)
                && assignment.sessionId().equals(session.sessionId());
    }
}
