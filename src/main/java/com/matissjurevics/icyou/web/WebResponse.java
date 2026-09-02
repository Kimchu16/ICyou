package com.matissjurevics.icyou.web;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/** Transport-neutral response returned by a web request handler. */
public record WebResponse(int status, String contentType, byte[] body,
                          Map<String, String> headers) {
    public WebResponse {
        if (status < 100 || status > 599) {
            throw new IllegalArgumentException("Invalid HTTP status: " + status);
        }
        contentType = Objects.requireNonNull(contentType, "contentType");
        if (contentType.contains("\r") || contentType.contains("\n")) {
            throw new IllegalArgumentException("Content type contains a line break");
        }
        body = Objects.requireNonNull(body, "body").clone();
        headers = Map.copyOf(Objects.requireNonNull(headers, "headers"));
        headers.forEach((name, value) -> {
            String normalizedName = name.toLowerCase(java.util.Locale.ROOT);
            if (name.isBlank() || name.contains(":") || name.contains("\r")
                    || name.contains("\n") || value.contains("\r") || value.contains("\n")
                    || normalizedName.equals("content-length")
                    || normalizedName.equals("content-type")
                    || normalizedName.equals("connection")) {
                throw new IllegalArgumentException("Invalid response header: " + name);
            }
        });
    }

    @Override
    public byte[] body() {
        return body.clone();
    }

    public static WebResponse json(int status, String json) {
        return new WebResponse(status, "application/json; charset=utf-8",
                json.getBytes(StandardCharsets.UTF_8), Map.of("Cache-Control", "no-store"));
    }

    public static WebResponse notFound() {
        return new WebResponse(404, "text/plain; charset=utf-8", new byte[0], Map.of());
    }
}
