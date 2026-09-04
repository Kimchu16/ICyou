package com.matissjurevics.icyou.web;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Transport-neutral fixed or streaming response returned by a web handler. */
public final class WebResponse {

    @FunctionalInterface
    public interface StreamingBody {
        void write(OutputStream output) throws IOException;
    }

    private final int status;
    private final String contentType;
    private final byte[] body;
    private final Map<String, String> headers;
    private final StreamingBody streamingBody;

    public WebResponse(int status, String contentType, byte[] body,
                       Map<String, String> headers) {
        this(status, contentType, body, headers, null);
    }

    private WebResponse(int status, String contentType, byte[] body,
                        Map<String, String> headers, StreamingBody streamingBody) {
        if (status < 100 || status > 599) {
            throw new IllegalArgumentException("Invalid HTTP status: " + status);
        }
        this.status = status;
        this.contentType = Objects.requireNonNull(contentType, "contentType");
        if (contentType.contains("\r") || contentType.contains("\n")) {
            throw new IllegalArgumentException("Content type contains a line break");
        }
        this.body = Objects.requireNonNull(body, "body").clone();
        this.headers = Map.copyOf(Objects.requireNonNull(headers, "headers"));
        this.headers.forEach((name, value) -> {
            String normalizedName = name.toLowerCase(Locale.ROOT);
            if (name.isBlank() || name.contains(":") || name.contains("\r")
                    || name.contains("\n") || value.contains("\r") || value.contains("\n")
                    || normalizedName.equals("content-length")
                    || normalizedName.equals("content-type")
                    || normalizedName.equals("connection")) {
                throw new IllegalArgumentException("Invalid response header: " + name);
            }
        });
        this.streamingBody = streamingBody;
    }

    public int status() {
        return status;
    }

    public String contentType() {
        return contentType;
    }

    public byte[] body() {
        return body.clone();
    }

    public Map<String, String> headers() {
        return headers;
    }

    public boolean streaming() {
        return streamingBody != null;
    }

    void writeStreamingBody(OutputStream output) throws IOException {
        if (streamingBody == null) {
            throw new IllegalStateException("Response is not streaming");
        }
        streamingBody.write(output);
    }

    public static WebResponse stream(int status, String contentType,
                                     Map<String, String> headers,
                                     StreamingBody streamingBody) {
        return new WebResponse(status, contentType, new byte[0], headers,
                Objects.requireNonNull(streamingBody, "streamingBody"));
    }

    public static WebResponse json(int status, String json) {
        return new WebResponse(status, "application/json; charset=utf-8",
                json.getBytes(StandardCharsets.UTF_8), Map.of("Cache-Control", "no-store"));
    }

    public static WebResponse notFound() {
        return new WebResponse(404, "text/plain; charset=utf-8", new byte[0], Map.of());
    }
}
