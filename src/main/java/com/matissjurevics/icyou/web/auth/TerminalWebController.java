package com.matissjurevics.icyou.web.auth;

import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import java.nio.charset.StandardCharsets;

import com.matissjurevics.icyou.device.GlobalDeviceRegistry;
import com.matissjurevics.icyou.render.video.ServerVideoFrameLifecycle;
import com.matissjurevics.icyou.render.video.ServerVideoFrameStore;
import com.matissjurevics.icyou.web.MjpegStream;
import com.matissjurevics.icyou.web.WebRequest;
import com.matissjurevics.icyou.web.WebResponse;
import com.matissjurevics.icyou.web.auth.TerminalCredentialStore.Scope;
import com.matissjurevics.icyou.web.demand.WebViewerDemandRegistry;
import com.matissjurevics.icyou.render.webrtc.ServerWebRtcSignalingLifecycle;

import net.minecraft.server.MinecraftServer;

/** Authenticates terminal routes before returning any names or metadata. */
public final class TerminalWebController {

    private static final String TERMINAL_PREFIX = "/v1/terminals/";
    private final GlobalDeviceRegistry registry;
    private final TerminalCredentialStore credentials;
    private final WebViewerDemandRegistry demand;
    private final ServerVideoFrameStore video;
    private final MinecraftServer server;

    public TerminalWebController(MinecraftServer server) {
        this(server, GlobalDeviceRegistry.get(server), TerminalCredentialStore.get(server),
                new WebViewerDemandRegistry(),
                ServerVideoFrameLifecycle.store(server).orElseGet(ServerVideoFrameStore::new));
    }

    TerminalWebController(GlobalDeviceRegistry registry,
                          TerminalCredentialStore credentials) {
        this(null, registry, credentials, new WebViewerDemandRegistry(),
                new ServerVideoFrameStore());
    }

    public TerminalWebController(GlobalDeviceRegistry registry,
                                 TerminalCredentialStore credentials,
                                 WebViewerDemandRegistry demand) {
        this(null, registry, credentials, demand, new ServerVideoFrameStore());
    }

    public TerminalWebController(GlobalDeviceRegistry registry,
                                 TerminalCredentialStore credentials,
                                 WebViewerDemandRegistry demand,
                                 ServerVideoFrameStore video) {
        this(null, registry, credentials, demand, video);
    }

    public TerminalWebController(MinecraftServer server,
                                 GlobalDeviceRegistry registry,
                                 TerminalCredentialStore credentials,
                                 WebViewerDemandRegistry demand,
                                 ServerVideoFrameStore video) {
        this.server = server;
        this.registry = registry;
        this.credentials = credentials;
        this.demand = demand;
        this.video = video;
    }

    public WebResponse handle(WebRequest request) {
        if (request.method().equals("GET") && request.path().equals("/health")) {
            return WebResponse.json(200, "{\"status\":\"ok\"}");
        }
        if (!request.path().startsWith(TERMINAL_PREFIX)) {
            return WebResponse.notFound();
        }
        String[] parts = request.path().substring(TERMINAL_PREFIX.length()).split("/", -1);
        String slug = parts[0];
        if (slug.isEmpty() || slug.contains("?")) {
            return WebResponse.notFound();
        }
        String token = bearer(request).orElse(null);
        var authenticated = credentials.authenticate(token);
        if (authenticated.isEmpty()) {
            // Deliberately indistinguishable from an unknown slug.
            return WebResponse.notFound();
        }
        UUID terminalId = authenticated.get().terminalId();
        if (!credentials.permits(terminalId, token, Scope.VIEWER)
                || registry.terminal(terminalId).isEmpty()
                || !registry.slug(terminalId).equals(slug)) {
            return WebResponse.notFound();
        }
        if (parts.length == 1 && request.method().equals("GET")) {
            return WebResponse.json(200, terminalJson(registry, terminalId, slug));
        }
        if (parts.length < 4 || !parts[1].equals("cameras")) {
            return WebResponse.notFound();
        }
        UUID cameraId = uuid(parts[2]);
        if (cameraId == null || registry.camera(cameraId)
                .filter(camera -> camera.terminalId().equals(terminalId)).isEmpty()) {
            return WebResponse.notFound();
        }
        var credential = authenticated.get();
        Instant now = Instant.now();
        if (parts[3].equals("video")) {
            if (parts.length != 5 || !request.method().equals("GET")) {
                return WebResponse.notFound();
            }
            UUID sessionId = uuid(parts[4]);
            if (sessionId == null || demand.renew(sessionId, credential.credentialId(),
                    terminalId, cameraId, now).isEmpty()) {
                return WebResponse.notFound();
            }
            return WebResponse.stream(200, MjpegStream.CONTENT_TYPE,
                    Map.of("Cache-Control", "no-store"), new MjpegStream(
                            () -> credentials.permits(terminalId, token, Scope.VIEWER)
                                    && demand.renew(sessionId, credential.credentialId(),
                                            terminalId, cameraId, Instant.now()).isPresent(),
                            () -> video.latest(cameraId)));
        }
        if (parts[3].equals("webrtc")) {
            return webrtc(request, parts, credential.credentialId(), terminalId,
                    cameraId, now);
        }
        if (!parts[3].equals("demand")) {
            return WebResponse.notFound();
        }
        if (parts.length == 4 && request.method().equals("POST")) {
            var session = demand.tryOpen(credential.credentialId(), credential.scope(),
                    terminalId, cameraId, now).orElse(null);
            return session == null
                    ? WebResponse.json(429, "{\"error\":\"viewer_limit_reached\"}")
                    : WebResponse.json(200,
                            "{\"sessionId\":\"" + session.sessionId() + "\"}");
        }
        if (parts.length != 5) {
            return WebResponse.notFound();
        }
        UUID sessionId = uuid(parts[4]);
        if (sessionId == null) {
            return WebResponse.notFound();
        }
        if (request.method().equals("PUT")) {
            return demand.renew(sessionId, credential.credentialId(), terminalId,
                    cameraId, now).isPresent()
                    ? WebResponse.json(200, "{\"status\":\"renewed\"}")
                    : WebResponse.notFound();
        }
        if (request.method().equals("DELETE")) {
            return demand.close(sessionId, credential.credentialId(), terminalId, cameraId)
                    ? WebResponse.json(200, "{\"status\":\"closed\"}")
                    : WebResponse.notFound();
        }
        return WebResponse.notFound();
    }

    private WebResponse webrtc(WebRequest request, String[] parts,
                               UUID credentialId,
                               UUID terminalId, UUID cameraId, Instant now) {
        if (server == null || parts.length < 5 || parts.length > 6) {
            return WebResponse.notFound();
        }
        UUID viewerSessionId = uuid(parts[4]);
        if (viewerSessionId == null || demand.renew(viewerSessionId,
                credentialId, terminalId, cameraId, now).isEmpty()) {
            return WebResponse.notFound();
        }
        if (parts.length == 5 && request.method().equals("POST")) {
            String offer = new String(request.body(), StandardCharsets.UTF_8);
            Optional<UUID> peerId;
            try {
                peerId = ServerWebRtcSignalingLifecycle.open(
                        server, viewerSessionId, cameraId, offer);
            } catch (IllegalArgumentException error) {
                return WebResponse.notFound();
            }
            return peerId.map(id -> WebResponse.json(200,
                    "{\"peerId\":\"" + id + "\"}"))
                    .orElseGet(WebResponse::notFound);
        }
        if (parts.length != 6) return WebResponse.notFound();
        UUID peerId = uuid(parts[5]);
        var signaling = ServerWebRtcSignalingLifecycle.registry(server).orElse(null);
        if (peerId == null || signaling == null) return WebResponse.notFound();
        if (request.method().equals("GET")) {
            var poll = signaling.poll(peerId, viewerSessionId, cameraId, now)
                    .orElse(null);
            if (poll == null) return WebResponse.notFound();
            if (poll.answerSdp().isEmpty()) {
                return WebResponse.json(202, "{\"status\":\"pending\"}");
            }
            return new WebResponse(200, "application/sdp; charset=utf-8",
                    poll.answerSdp().orElseThrow().getBytes(StandardCharsets.UTF_8),
                    Map.of("Cache-Control", "no-store"));
        }
        if (request.method().equals("DELETE")) {
            var closed = signaling.close(peerId, viewerSessionId, cameraId).orElse(null);
            if (closed == null) return WebResponse.notFound();
            ServerWebRtcSignalingLifecycle.close(server, closed);
            return WebResponse.json(200, "{\"status\":\"closed\"}");
        }
        return WebResponse.notFound();
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private static Optional<String> bearer(WebRequest request) {
        return request.header("authorization").filter(value -> value.startsWith("Bearer "))
                .map(value -> value.substring("Bearer ".length()).trim())
                .filter(value -> !value.isEmpty());
    }

    private static String terminalJson(GlobalDeviceRegistry registry, UUID terminalId,
                                       String slug) {
        StringBuilder json = new StringBuilder("{\"terminalId\":\"")
                .append(terminalId).append("\",\"slug\":\"")
                .append(escape(slug)).append("\",\"cameras\":[");
        boolean first = true;
        for (var camera : registry.camerasFor(terminalId)) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append("{\"id\":\"").append(camera.ref().deviceId())
                    .append("\",\"name\":\"").append(escape(camera.name()))
                    .append("\",\"dimension\":\"")
                    .append(escape(camera.ref().dimension().getValue().toString()))
                    .append("\"}");
        }
        return json.append("]}").toString();
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
