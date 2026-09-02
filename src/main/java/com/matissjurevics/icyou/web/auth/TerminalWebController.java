package com.matissjurevics.icyou.web.auth;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import com.matissjurevics.icyou.device.GlobalDeviceRegistry;
import com.matissjurevics.icyou.web.WebRequest;
import com.matissjurevics.icyou.web.WebResponse;
import com.matissjurevics.icyou.web.auth.TerminalCredentialStore.Scope;
import com.matissjurevics.icyou.web.demand.WebViewerDemandRegistry;

import net.minecraft.server.MinecraftServer;

/** Authenticates terminal routes before returning any names or metadata. */
public final class TerminalWebController {

    private static final String TERMINAL_PREFIX = "/v1/terminals/";
    private final GlobalDeviceRegistry registry;
    private final TerminalCredentialStore credentials;
    private final WebViewerDemandRegistry demand;

    public TerminalWebController(MinecraftServer server) {
        this(GlobalDeviceRegistry.get(server), TerminalCredentialStore.get(server),
                new WebViewerDemandRegistry());
    }

    TerminalWebController(GlobalDeviceRegistry registry,
                          TerminalCredentialStore credentials) {
        this(registry, credentials, new WebViewerDemandRegistry());
    }

    public TerminalWebController(GlobalDeviceRegistry registry,
                                 TerminalCredentialStore credentials,
                                 WebViewerDemandRegistry demand) {
        this.registry = registry;
        this.credentials = credentials;
        this.demand = demand;
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
        if (parts.length < 4 || !parts[1].equals("cameras")
                || !parts[3].equals("demand")) {
            return WebResponse.notFound();
        }
        UUID cameraId = uuid(parts[2]);
        if (cameraId == null || registry.camera(cameraId)
                .filter(camera -> camera.terminalId().equals(terminalId)).isEmpty()) {
            return WebResponse.notFound();
        }
        var credential = authenticated.get();
        Instant now = Instant.now();
        if (parts.length == 4 && request.method().equals("POST")) {
            var session = demand.open(credential.credentialId(), credential.scope(),
                    terminalId, cameraId, now);
            return WebResponse.json(200,
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
