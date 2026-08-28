package com.matissjurevics.icyou.client.stream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.matissjurevics.icyou.ICyouMod;
import com.matissjurevics.icyou.terminal.DeviceRegistry;

import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.system.MemoryUtil;

/**
 * Minimal, dependency-free HTTP server (java.base only — no {@code jdk.httpserver}
 * module, so it works on any launcher JVM). Read-only: serves a terminal's live
 * camera feeds plus snapshots. No mutation endpoints.
 */
public final class StreamServer {

    private static final int STREAM_POLL_MS = 80;
    private static final int STREAM_MAX_IDLE_MS = 60_000;

    private static final String STYLE =
            ":root{--bg:#101014;--panel:#1a1c22;--border:#2e3038;--text:#e8e8f0;"
            + "--dim:#8a8a94;--accent:#30ff60;--blue:#4fc8ff;--off:#f87171}"
            + "*{box-sizing:border-box}body{background:var(--bg);color:var(--text);"
            + "font-family:ui-monospace,SFMono-Regular,Menlo,monospace;margin:0;padding:0}"
            + "header{background:var(--panel);border-bottom:2px solid var(--accent);"
            + "padding:14px 24px;display:flex;align-items:center;justify-content:space-between}"
            + "header .logo{color:var(--accent);font-weight:700;letter-spacing:2px}"
            + "header a{color:var(--dim)}header a:hover{color:var(--text)}"
            + "main{padding:24px;max-width:1100px;margin:0 auto}"
            + "h1{font-size:20px;margin:0 0 18px;font-weight:600;color:var(--accent)}"
            + "a{color:var(--blue);text-decoration:none}a:hover{text-decoration:underline}"
            + "ul.terms{list-style:none;padding:0;margin:0;display:grid;gap:10px}"
            + "ul.terms li{background:var(--panel);border:1px solid var(--border);"
            + "border-left:3px solid var(--accent);padding:12px 16px;border-radius:6px}"
            + "ul.terms .n{color:var(--dim);margin-left:8px}"
            + ".grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:16px}"
            + ".cam{background:var(--panel);border:1px solid var(--border);padding:12px;"
            + "border-radius:6px}.cam b{color:var(--text)}"
            + ".cam img{width:100%;display:block;background:#000;border-radius:4px;margin-top:10px}"
            + ".on{color:var(--accent)}.off{color:var(--off)}"
            + "p{color:var(--dim)}";

    private static volatile ServerSocket socket;
    private static volatile ExecutorService pool;
    private static volatile boolean running;

    private StreamServer() {}

    public static boolean isRunning() {
        return running;
    }

    public static void start() {
        if (running) {
            return;
        }
        try {
            ServerSocket s = new ServerSocket();
            s.setReuseAddress(true);
            s.bind(new InetSocketAddress(StreamConfig.bind, StreamConfig.port));
            socket = s;
            running = true;
            pool = Executors.newVirtualThreadPerTaskExecutor();
            Thread t = new Thread(StreamServer::acceptLoop, "icyou-stream");
            t.setDaemon(true);
            t.start();
            ICyouMod.LOGGER.info("[stream] listening on http://{}:{} ", StreamConfig.bind,
                    StreamConfig.port);
        } catch (IOException e) {
            ICyouMod.LOGGER.error("[stream] failed to bind {}:{} — streaming disabled",
                    StreamConfig.bind, StreamConfig.port, e);
            running = false;
            socket = null;
        }
    }

    public static void stop() {
        running = false;
        if (pool != null) {
            pool.shutdownNow();
        }
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            socket = null;
        }
    }

    private static void acceptLoop() {
        try {
            while (running) {
                Socket s = socket.accept();
                pool.execute(() -> handle(s));
            }
        } catch (IOException e) {
            // socket closed during stop()
        }
    }

    private static void handle(Socket s) {
        try (Socket c = s; InputStream in = c.getInputStream(); OutputStream out = c.getOutputStream()) {
            String path = readPath(in);
            if (path != null && path.startsWith("/")) {
                route(path, out);
            }
        } catch (IOException ignored) {
            // client gone
        }
    }

    /** Reads the request line + headers, returns the request path (no query). */
    private static String readPath(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cur = 0;
        int[] tail = {'\r', '\n', '\r', '\n'};
        while (cur < tail.length) {
            int b = in.read();
            if (b == -1) {
                return null;
            }
            sb.append((char) b);
            if (b == tail[cur]) {
                cur++;
            } else {
                cur = (b == tail[0]) ? 1 : 0;
            }
        }
        String req = sb.toString();
        String line = req.substring(0, req.indexOf("\r\n"));
        String[] parts = line.split(" ");
        if (parts.length < 2 || !parts[0].equals("GET")) {
            return null;
        }
        String uri = parts[1];
        int q = uri.indexOf('?');
        return q >= 0 ? uri.substring(0, q) : uri;
    }

    // --- routing ---

    private static void route(String path, OutputStream out) throws IOException {
        String[] seg = path.split("/");
        try {
            if (path.equals("/") || path.isEmpty()) {
                handleTerminalList(out);
            } else if (seg.length >= 3 && seg[2].equals("stream")) {
                handleStream(out, parseCamKey(seg[3]));
            } else if (seg.length >= 3 && seg[2].equals("snapshot")) {
                handleSnapshot(out, parseCamKey(seg[3]));
            } else if (seg.length == 2 && !seg[1].isEmpty()
                    && !seg[1].equals("stream") && !seg[1].equals("snapshot")) {
                handleTerminalPage(out, seg[1]);
            } else {
                send(out, 404, "text/html", page("Not found", "<p>Not found.</p>"));
            }
        } catch (RuntimeException e) {
            ICyouMod.LOGGER.error("[stream] request failed: {}", path, e);
            send(out, 500, "text/html", page("Error", "<p>Server error.</p>"));
        }
    }

    private static long parseCamKey(String s) {
        return Long.parseLong(s);
    }

    // --- terminal list (GET /) ---

    private static void handleTerminalList(OutputStream out) throws IOException {
        String rows = onClient(() -> {
            MinecraftServer srv = MinecraftClient.getInstance().getServer();
            if (srv == null) {
                return null;
            }
            DeviceRegistry reg = DeviceRegistry.get(srv.getOverworld());
            StringBuilder sb = new StringBuilder();
            for (BlockPos t : reg.terminalPositions()) {
                String slug = reg.ensureSlug(t);
                int cams = reg.camerasFor(t).size();
                sb.append("<li><a href=\"/").append(slug).append("\">")
                        .append(esc(slug)).append("</a><span class=\"n\">")
                        .append(cams).append(" cameras</span></li>");
            }
            return sb.toString();
        });
        if (rows == null) {
            send(out, 200, "text/html", page("ICyou", "<p>This client isn't hosting a world.</p>"));
            return;
        }
        send(out, 200, "text/html", page("ICyou terminals", "<ul class=\"terms\">" + rows + "</ul>"));
    }

    // --- terminal page (GET /<slug>) ---

    private static void handleTerminalPage(OutputStream out, String slug) throws IOException {
        String body = onClient(() -> {
            MinecraftServer srv = MinecraftClient.getInstance().getServer();
            if (srv == null) {
                return null;
            }
            ServerWorld world = srv.getOverworld();
            DeviceRegistry reg = DeviceRegistry.get(world);
            BlockPos terminal = findTerminalBySlug(reg, slug);
            if (terminal == null) {
                return "\u0000NOTFOUND";
            }
            return terminalPageHtml(reg, terminal, world, slug);
        });
        if (body == null) {
            send(out, 200, "text/html", page("ICyou", "<p>This client isn't hosting a world.</p>"));
        } else if (body.equals("\u0000NOTFOUND")) {
            send(out, 404, "text/html", page("Unknown", "<p>No terminal with that slug.</p>"));
        } else {
            send(out, 200, "text/html", body);
        }
    }

    private static BlockPos findTerminalBySlug(DeviceRegistry reg, String slug) {
        for (BlockPos t : reg.terminalPositions()) {
            if (reg.ensureSlug(t).equals(slug)) {
                return t;
            }
        }
        return null;
    }

    private static String terminalPageHtml(DeviceRegistry reg, BlockPos terminal,
                                           ServerWorld world, String slug) {
        var cams = reg.camerasFor(terminal);
        StringBuilder rows = new StringBuilder();
        for (var c : cams) {
            boolean online = isCameraOnline(world, c.pos());
            rows.append("<div class=\"cam\"><b>").append(esc(c.name())).append("</b> ")
                    .append(online ? "<span class=on>live</span>" : "<span class=off>no signal</span>")
                    .append("<img src=\"/").append(slug).append("/stream/")
                    .append(c.pos().asLong()).append("\" loading=\"lazy\"></div>");
        }
        if (rows.length() == 0) {
            rows.append("<p>No cameras linked to this terminal.</p>");
        }
        return page(slug, "<div class=\"grid\">" + rows + "</div>");
    }

    private static boolean isCameraOnline(ServerWorld world, BlockPos pos) {
        try {
            return world.getBlockState(pos).getBlock()
                    instanceof com.matissjurevics.icyou.camera.CameraBlock;
        } catch (Throwable t) {
            return false;
        }
    }

    // --- stream (GET /<slug>/stream/<camKey>) ---

    private static void handleStream(OutputStream out, long camKey) throws IOException {
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: multipart/x-mixed-replace; "
                + "boundary=frame\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        long lastTs = Long.MIN_VALUE;
        long idleStart = System.currentTimeMillis();
        try {
            while (System.currentTimeMillis() - idleStart < STREAM_MAX_IDLE_MS) {
                StreamFrame f = StreamStore.get(camKey);
                if (f != null && f.timestamp() != lastTs) {
                    byte[] jpg = f.jpeg();
                    out.write(("--frame\r\nContent-Type: image/jpeg\r\nContent-Length: "
                            + jpg.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    out.write(jpg);
                    out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                    lastTs = f.timestamp();
                    idleStart = System.currentTimeMillis();
                } else {
                    Thread.sleep(STREAM_POLL_MS);
                }
            }
        } catch (IOException ignored) {
            // client disconnected
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    // --- snapshot (GET /<slug>/snapshot/<camKey>.jpg) ---

    private static void handleSnapshot(OutputStream out, long camKey) throws IOException {
        StreamFrame f = StreamStore.get(camKey);
        if (f == null) {
            send(out, 404, "text/html", "no frame");
            return;
        }
        sendBytes(out, 200, "image/jpeg", f.jpeg());
    }

    // --- helpers ---

    private static <T> T onClient(Supplier<T> f) {
        CompletableFuture<T> cf = new CompletableFuture<>();
        MinecraftClient.getInstance().execute(() -> {
            try {
                cf.complete(f.get());
            } catch (Throwable t) {
                cf.completeExceptionally(t);
            }
        });
        try {
            return cf.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            ICyouMod.LOGGER.error("[stream] client-thread call failed", e);
            return null;
        }
    }

    private static void send(OutputStream out, int code, String contentType, String body)
            throws IOException {
        sendBytes(out, code, contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void sendBytes(OutputStream out, int code, String contentType, byte[] body)
            throws IOException {
        out.write(("HTTP/1.1 " + code + " " + reason(code) + "\r\nContent-Type: "
                + contentType + "\r\nContent-Length: " + body.length
                + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(body);
    }

    private static String reason(int code) {
        return switch (code) {
            case 200 -> "OK";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 500 -> "Internal Server Error";
            default -> "OK";
        };
    }

    private static String page(String title, String body) {
        return "<!doctype html><html><head><meta charset=utf-8><meta name=viewport "
                + "content=\"width=device-width,initial-scale=1\"><title>" + esc(title)
                + "</title><style>" + STYLE + "</style></head><body>"
                + "<header><span class=\"logo\">ICYOU</span><a href=\"/\">terminals</a></header>"
                + "<main><h1>" + esc(title) + "</h1>" + body + "</main></body></html>";
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
