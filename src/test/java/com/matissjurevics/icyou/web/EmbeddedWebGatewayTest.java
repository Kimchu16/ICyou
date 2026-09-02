package com.matissjurevics.icyou.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class EmbeddedWebGatewayTest {

    @Test
    void gatewayPassesTransportNeutralRequestsAndResponses() throws Exception {
        AtomicReference<WebRequest> received = new AtomicReference<>();
        WebGateway gateway = new EmbeddedWebGateway(null);
        assertTrue(gateway.start(new WebServerConfig(true, "127.0.0.1", 0), request -> {
            received.set(request);
            return WebResponse.json(200, "{\"route\":\"custom\"}");
        }));
        assertTrue(gateway.isRunning());
        int port = gateway.boundAddress().orElseThrow().getPort();

        try (Socket client = new Socket("127.0.0.1", port);
             OutputStreamWriter writer = new OutputStreamWriter(
                     client.getOutputStream(), StandardCharsets.US_ASCII);
             BufferedReader reader = new BufferedReader(new InputStreamReader(
                     client.getInputStream(), StandardCharsets.UTF_8))) {
            writer.write("GET /custom HTTP/1.1\r\nHost: localhost\r\n\r\n");
            writer.flush();
            assertEquals("HTTP/1.1 200 OK", reader.readLine());
        }

        assertEquals(new WebRequest("GET", "/custom"), received.get());
        gateway.close();
        assertFalse(gateway.isRunning());
    }

    @Test
    void responseValuesAreDefensiveAndRejectHeaderInjection() {
        byte[] body = "safe".getBytes(StandardCharsets.UTF_8);
        WebResponse response = new WebResponse(200, "text/plain", body,
                Map.of("Cache-Control", "no-store"));
        body[0] = 'X';
        byte[] returned = response.body();
        returned[0] = 'Y';

        assertEquals("safe", new String(response.body(), StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> new WebResponse(
                200, "text/plain\r\nInjected: yes", new byte[0], Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new WebResponse(
                200, "text/plain", new byte[0], Map.of("Safe", "yes\r\nInjected: yes")));
        assertThrows(IllegalArgumentException.class, () -> new WebResponse(
                200, "text/plain", new byte[0], Map.of("Content-Length", "999")));
    }
}
