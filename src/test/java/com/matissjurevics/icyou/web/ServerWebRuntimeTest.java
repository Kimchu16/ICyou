package com.matissjurevics.icyou.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class ServerWebRuntimeTest {

    @Test
    void disabledRuntimeDoesNotBind() {
        ServerWebRuntime runtime = new ServerWebRuntime(null);

        assertFalse(runtime.start(WebServerConfig.DISABLED));
        assertEquals(ServerWebRuntime.State.STOPPED, runtime.state());
        assertTrue(runtime.boundAddress().isEmpty());
    }

    @Test
    void startsOnceServesHealthAndStopsCleanly() throws Exception {
        ServerWebRuntime runtime = new ServerWebRuntime(null);
        WebServerConfig config = new WebServerConfig(true, "127.0.0.1", 0);

        assertTrue(runtime.start(config));
        int port = runtime.boundAddress().orElseThrow().getPort();
        assertTrue(runtime.start(config));
        assertEquals(port, runtime.boundAddress().orElseThrow().getPort());

        try (Socket client = new Socket("127.0.0.1", port);
             OutputStreamWriter writer = new OutputStreamWriter(
                     client.getOutputStream(), StandardCharsets.US_ASCII);
             BufferedReader reader = new BufferedReader(new InputStreamReader(
                     client.getInputStream(), StandardCharsets.UTF_8))) {
            writer.write("GET /health HTTP/1.1\r\nHost: localhost\r\n\r\n");
            writer.flush();
            assertEquals("HTTP/1.1 200 OK", reader.readLine());
        }

        runtime.close();
        runtime.close();
        assertEquals(ServerWebRuntime.State.STOPPED, runtime.state());
        assertTrue(runtime.boundAddress().isEmpty());
    }

    @Test
    void bindFailureIsContainedAndCanBeStopped() throws Exception {
        AtomicInteger failures = new AtomicInteger();
        try (ServerSocket occupied = new ServerSocket(0)) {
            ServerWebRuntime runtime = new ServerWebRuntime(
                    ignored -> failures.incrementAndGet());
            WebServerConfig config = new WebServerConfig(
                    true, "127.0.0.1", occupied.getLocalPort());

            assertFalse(runtime.start(config));
            assertEquals(ServerWebRuntime.State.FAILED, runtime.state());
            assertEquals(1, failures.get());
            assertTrue(runtime.boundAddress().isEmpty());

            runtime.close();
            assertEquals(ServerWebRuntime.State.STOPPED, runtime.state());
        }
    }
}
