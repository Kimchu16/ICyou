package com.matissjurevics.icyou.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Owns one server-side listener and closes every resource deterministically. */
public final class ServerWebRuntime implements AutoCloseable {

    public enum State { STOPPED, STARTING, RUNNING, STOPPING, FAILED }

    private final Consumer<Throwable> failureHandler;
    private final Set<Socket> openClients = ConcurrentHashMap.newKeySet();
    private volatile State state = State.STOPPED;
    private ServerSocket socket;
    private ExecutorService clients;
    private Thread acceptThread;
    private WebRequestHandler requestHandler;

    public ServerWebRuntime(Consumer<Throwable> failureHandler) {
        this.failureHandler = failureHandler == null ? ignored -> { } : failureHandler;
    }

    public synchronized boolean start(WebServerConfig config, WebRequestHandler handler) {
        if (state == State.RUNNING) {
            return true;
        }
        if (state == State.STARTING || state == State.STOPPING) {
            return false;
        }
        state = State.STARTING;
        requestHandler = Objects.requireNonNull(handler, "handler");
        if (!config.enabled()) {
            state = State.STOPPED;
            return false;
        }
        try {
            ServerSocket listener = new ServerSocket();
            listener.setReuseAddress(true);
            listener.bind(new InetSocketAddress(config.bind(), config.port()));
            socket = listener;
            clients = Executors.newVirtualThreadPerTaskExecutor();
            state = State.RUNNING;
            acceptThread = Thread.ofPlatform().daemon().name("icyou-web-accept")
                    .start(this::acceptLoop);
            return true;
        } catch (IOException | RuntimeException error) {
            state = State.FAILED;
            closeResources();
            failureHandler.accept(error);
            return false;
        }
    }

    public State state() {
        return state;
    }

    public synchronized Optional<InetSocketAddress> boundAddress() {
        return socket == null ? Optional.empty()
                : Optional.of((InetSocketAddress) socket.getLocalSocketAddress());
    }

    private void acceptLoop() {
        try {
            while (state == State.RUNNING) {
                Socket client = socket.accept();
                openClients.add(client);
                ExecutorService executor = clients;
                if (executor != null) {
                    try {
                        executor.execute(() -> handle(client));
                    } catch (RuntimeException rejected) {
                        client.close();
                        openClients.remove(client);
                    }
                } else {
                    client.close();
                    openClients.remove(client);
                }
            }
        } catch (IOException error) {
            if (state == State.RUNNING) {
                state = State.FAILED;
                closeResources();
                failureHandler.accept(error);
            }
        }
    }

    private void handle(Socket socket) {
        try (Socket client = socket;
             OutputStream output = client.getOutputStream()) {
            client.setSoTimeout(5_000);
            String request = readRequestLine(client.getInputStream());
            WebResponse response = route(request);
            writeResponse(output, response);
            output.flush();
        } catch (IOException ignored) {
            // A disconnected health-check client needs no server action.
        } finally {
            openClients.remove(socket);
        }
    }

    private WebResponse route(String requestLine) {
        if (requestLine == null) {
            return WebResponse.notFound();
        }
        String[] parts = requestLine.split(" ", 3);
        if (parts.length != 3 || !parts[2].startsWith("HTTP/")) {
            return WebResponse.notFound();
        }
        try {
            WebResponse response = requestHandler.handle(new WebRequest(parts[0], parts[1]));
            return response == null ? WebResponse.notFound() : response;
        } catch (RuntimeException error) {
            failureHandler.accept(error);
            return WebResponse.json(500, "{\"error\":\"internal_error\"}");
        }
    }

    private static void writeResponse(OutputStream output, WebResponse response)
            throws IOException {
        byte[] body = response.body();
        StringBuilder headers = new StringBuilder("HTTP/1.1 ")
                .append(response.status()).append(' ').append(reason(response.status()))
                .append("\r\nContent-Type: ").append(response.contentType())
                .append("\r\nContent-Length: ").append(body.length)
                .append("\r\nConnection: close\r\n");
        response.headers().forEach((name, value) -> headers.append(name).append(": ")
                .append(value).append("\r\n"));
        headers.append("\r\n");
        output.write(headers.toString().getBytes(StandardCharsets.US_ASCII));
        output.write(body);
    }

    private static String reason(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 500 -> "Internal Server Error";
            default -> "Response";
        };
    }

    private static String readRequestLine(InputStream input) throws IOException {
        StringBuilder line = new StringBuilder();
        for (int count = 0; count < 2_048; count++) {
            int next = input.read();
            if (next < 0 || next == '\n') {
                return line.toString();
            }
            if (next != '\r') {
                line.append((char) next);
            }
        }
        return null;
    }

    @Override
    public synchronized void close() {
        if (state == State.STOPPED) {
            return;
        }
        state = State.STOPPING;
        closeResources();
        state = State.STOPPED;
    }

    private void closeResources() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            socket = null;
        }
        if (clients != null) {
            for (Socket client : openClients) {
                try {
                    client.close();
                } catch (IOException ignored) {
                }
            }
            openClients.clear();
            clients.shutdownNow();
            try {
                clients.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            clients = null;
        }
        acceptThread = null;
        requestHandler = null;
    }
}
