package com.matissjurevics.icyou.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
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

    private static final byte[] HEALTH_RESPONSE = ("HTTP/1.1 200 OK\r\n"
            + "Content-Type: application/json; charset=utf-8\r\n"
            + "Cache-Control: no-store\r\n"
            + "Content-Length: 15\r\n"
            + "Connection: close\r\n\r\n"
            + "{\"status\":\"ok\"}").getBytes(StandardCharsets.UTF_8);
    private static final byte[] NOT_FOUND_RESPONSE = ("HTTP/1.1 404 Not Found\r\n"
            + "Content-Length: 0\r\nConnection: close\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8);

    private final Consumer<Throwable> failureHandler;
    private final Set<Socket> openClients = ConcurrentHashMap.newKeySet();
    private volatile State state = State.STOPPED;
    private ServerSocket socket;
    private ExecutorService clients;
    private Thread acceptThread;

    public ServerWebRuntime(Consumer<Throwable> failureHandler) {
        this.failureHandler = failureHandler == null ? ignored -> { } : failureHandler;
    }

    public synchronized boolean start(WebServerConfig config) {
        if (state == State.RUNNING) {
            return true;
        }
        if (state == State.STARTING || state == State.STOPPING) {
            return false;
        }
        state = State.STARTING;
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
            boolean health = request != null
                    && request.startsWith("GET /health ");
            output.write(health ? HEALTH_RESPONSE : NOT_FOUND_RESPONSE);
            output.flush();
        } catch (IOException ignored) {
            // A disconnected health-check client needs no server action.
        } finally {
            openClients.remove(socket);
        }
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
    }
}
