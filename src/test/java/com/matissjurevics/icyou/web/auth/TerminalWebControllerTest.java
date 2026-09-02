package com.matissjurevics.icyou.web.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.matissjurevics.icyou.device.CameraRef;
import com.matissjurevics.icyou.device.GlobalDeviceRegistry;
import com.matissjurevics.icyou.device.TerminalRef;
import com.matissjurevics.icyou.web.WebRequest;
import com.matissjurevics.icyou.web.WebResponse;
import com.matissjurevics.icyou.web.auth.TerminalCredentialStore.Scope;
import com.matissjurevics.icyou.web.demand.WebViewerDemandRegistry;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

class TerminalWebControllerTest {

    private static final RegistryKey<World> WORLD = RegistryKey.of(
            RegistryKeys.WORLD, Identifier.of("icyou", "auth_test"));

    @Test
    void hidesTerminalExistenceUntilAValidScopedTokenIsPresented() {
        GlobalDeviceRegistry registry = new GlobalDeviceRegistry();
        TerminalRef terminal = new TerminalRef(UUID.randomUUID(), WORLD, new BlockPos(0, 64, 0));
        registry.registerTerminal(terminal, UUID.randomUUID());
        registry.registerCamera(new CameraRef(UUID.randomUUID(), WORLD, new BlockPos(1, 64, 0)),
                terminal.deviceId(), "Private \"Gate\"");
        String slug = registry.slug(terminal.deviceId());
        TerminalCredentialStore credentials = new TerminalCredentialStore();
        String token = credentials.issue(terminal.deviceId(), Scope.VIEWER).token();
        TerminalWebController controller = new TerminalWebController(registry, credentials);

        WebResponse missing = controller.handle(new WebRequest(
                "GET", "/v1/terminals/" + slug));
        WebResponse wrong = controller.handle(new WebRequest(
                "GET", "/v1/terminals/not-real",
                Map.of("Authorization", "Bearer " + token)));
        assertEquals(404, missing.status());
        assertEquals(wrong.status(), missing.status());
        assertEquals(wrong.body().length, missing.body().length);

        WebResponse allowed = controller.handle(new WebRequest(
                "GET", "/v1/terminals/" + slug,
                Map.of("authorization", "Bearer " + token)));
        String json = new String(allowed.body(), StandardCharsets.UTF_8);
        assertEquals(200, allowed.status());
        assertTrue(json.contains("Private \\\"Gate\\\""));
        assertTrue(json.contains(terminal.deviceId().toString()));
    }

    @Test
    void ownerTokensCanViewAndRevocationImmediatelyDeniesAccess() {
        GlobalDeviceRegistry registry = new GlobalDeviceRegistry();
        TerminalRef terminal = new TerminalRef(UUID.randomUUID(), WORLD, BlockPos.ORIGIN);
        registry.registerTerminal(terminal, UUID.randomUUID());
        String slug = registry.slug(terminal.deviceId());
        TerminalCredentialStore credentials = new TerminalCredentialStore();
        var issued = credentials.issue(terminal.deviceId(), Scope.OWNER);
        TerminalWebController controller = new TerminalWebController(registry, credentials);
        WebRequest request = new WebRequest("GET", "/v1/terminals/" + slug,
                Map.of("authorization", "Bearer " + issued.token()));

        assertEquals(200, controller.handle(request).status());
        assertTrue(credentials.revoke(terminal.deviceId(), issued.credentialId()));
        assertEquals(404, controller.handle(request).status());
        assertEquals(200, controller.handle(new WebRequest("GET", "/health")).status());
        assertFalse(controller.handle(new WebRequest("POST", "/health")).status() == 200);
    }

    @Test
    void authenticatedDemandCanBeOpenedRenewedAndClosed() {
        GlobalDeviceRegistry registry = new GlobalDeviceRegistry();
        TerminalRef terminal = new TerminalRef(UUID.randomUUID(), WORLD, BlockPos.ORIGIN);
        CameraRef camera = new CameraRef(UUID.randomUUID(), WORLD, new BlockPos(1, 64, 0));
        registry.registerTerminal(terminal, UUID.randomUUID());
        registry.registerCamera(camera, terminal.deviceId(), "Demand camera");
        String slug = registry.slug(terminal.deviceId());
        TerminalCredentialStore credentials = new TerminalCredentialStore();
        var issued = credentials.issue(terminal.deviceId(), Scope.VIEWER);
        WebViewerDemandRegistry demand = new WebViewerDemandRegistry();
        TerminalWebController controller = new TerminalWebController(
                registry, credentials, demand);
        String base = "/v1/terminals/" + slug + "/cameras/"
                + camera.deviceId() + "/demand";
        Map<String, String> auth = Map.of("authorization", "Bearer " + issued.token());

        WebResponse opened = controller.handle(new WebRequest("POST", base, auth));
        String json = new String(opened.body(), StandardCharsets.UTF_8);
        UUID sessionId = UUID.fromString(json.substring(
                json.indexOf(':') + 2, json.length() - 2));
        assertEquals(200, opened.status());
        assertTrue(demand.hasDemand(camera.deviceId(), java.time.Instant.now()));
        assertEquals(200, controller.handle(new WebRequest(
                "PUT", base + '/' + sessionId, auth)).status());
        assertEquals(200, controller.handle(new WebRequest(
                "DELETE", base + '/' + sessionId, auth)).status());
        assertFalse(demand.hasDemand(camera.deviceId(), java.time.Instant.now()));
        assertEquals(404, controller.handle(new WebRequest("POST", base,
                Map.of("authorization", "Bearer invalid"))).status());
    }
}
