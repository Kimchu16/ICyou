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
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class ServerWebRuntimeTest {

    @Test
    void disabledRuntimeDoesNotBind() {
        ServerWebRuntime runtime = new ServerWebRuntime(null);

        assertFalse(runtime.start(WebServerConfig.DISABLED, request -> WebResponse.notFound()));
        assertEquals(ServerWebRuntime.State.STOPPED, runtime.state());
        assertTrue(runtime.boundAddress().isEmpty());
    }

    @Test
    void startsOnceServesHealthAndStopsCleanly() throws Exception {
        ServerWebRuntime runtime = new ServerWebRuntime(null);
        WebServerConfig config = new WebServerConfig(true, "127.0.0.1", 0);

        WebRequestHandler handler = request -> request.path().equals("/health")
                ? WebResponse.json(200, "{\"status\":\"ok\"}") : WebResponse.notFound();
        assertTrue(runtime.start(config, handler));
        int port = runtime.boundAddress().orElseThrow().getPort();
        assertTrue(runtime.start(config, handler));
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

            assertFalse(runtime.start(config, request -> WebResponse.notFound()));
            assertEquals(ServerWebRuntime.State.FAILED, runtime.state());
            assertEquals(1, failures.get());
            assertTrue(runtime.boundAddress().isEmpty());

            runtime.close();
            assertEquals(ServerWebRuntime.State.STOPPED, runtime.state());
        }
    }

    @Test
    void readsExactBoundedRequestBodies() throws Exception {
        AtomicReference<byte[]> received = new AtomicReference<>();
        ServerWebRuntime runtime = new ServerWebRuntime(null);
        assertTrue(runtime.start(new WebServerConfig(true, "127.0.0.1", 0), request -> {
            received.set(request.body());
            return WebResponse.json(200, "{}");
        }));
        int port = runtime.boundAddress().orElseThrow().getPort();

        assertEquals("HTTP/1.1 200 OK", request(port,
                "POST /offer HTTP/1.1\r\nHost: localhost\r\nContent-Length: 5\r\n\r\nhello"));
        assertTrue(Arrays.equals("hello".getBytes(StandardCharsets.US_ASCII),
                received.get()));
        runtime.close();
    }

    @Test
    void rejectsChunkedMalformedAndOversizedBodies() throws Exception {
        ServerWebRuntime runtime = new ServerWebRuntime(null);
        assertTrue(runtime.start(new WebServerConfig(true, "127.0.0.1", 0),
                request -> WebResponse.json(200, "{}")));
        int port = runtime.boundAddress().orElseThrow().getPort();

        assertEquals("HTTP/1.1 400 Bad Request", request(port,
                "POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n"));
        assertEquals("HTTP/1.1 400 Bad Request", request(port,
                "POST / HTTP/1.1\r\nContent-Length: nope\r\n\r\n"));
        assertEquals("HTTP/1.1 400 Bad Request", request(port,
                "POST / HTTP/1.1\r\nContent-Length: "
                        + (ServerWebRuntime.MAX_REQUEST_BODY_BYTES + 1) + "\r\n\r\n"));
        runtime.close();
    }

    @Test
    void answersBrowserPreflightWithoutCallingProtectedRoutes() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ServerWebRuntime runtime = new ServerWebRuntime(null);
        assertTrue(runtime.start(new WebServerConfig(true, "127.0.0.1", 0), request -> {
            calls.incrementAndGet();
            return WebResponse.notFound();
        }));
        int port = runtime.boundAddress().orElseThrow().getPort();

        assertEquals("HTTP/1.1 204 No Content", request(port,
                "OPTIONS /v1/terminals/x HTTP/1.1\r\nHost: localhost\r\n\r\n"));
        assertEquals(0, calls.get());
        runtime.close();
    }

    private static String request(int port, String raw) throws Exception {
        try (Socket client = new Socket("127.0.0.1", port);
             OutputStreamWriter writer = new OutputStreamWriter(
                     client.getOutputStream(), StandardCharsets.US_ASCII);
             BufferedReader reader = new BufferedReader(new InputStreamReader(
                     client.getInputStream(), StandardCharsets.UTF_8))) {
            writer.write(raw);
            writer.flush();
            return reader.readLine();
        }
    }
}
