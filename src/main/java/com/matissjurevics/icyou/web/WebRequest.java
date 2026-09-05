package com.matissjurevics.icyou.web;

import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Transport-neutral request data passed into the server control plane. */
public record WebRequest(String method, String path, Map<String, String> headers,
                         byte[] body) {
    public WebRequest {
        method = Objects.requireNonNull(method, "method");
        path = Objects.requireNonNull(path, "path");
        Map<String, String> normalized = new LinkedHashMap<>();
        Objects.requireNonNull(headers, "headers").forEach((name, value) -> {
            String key = Objects.requireNonNull(name, "header name")
                    .toLowerCase(Locale.ROOT);
            if (normalized.putIfAbsent(key,
                    Objects.requireNonNull(value, "header value")) != null) {
                throw new IllegalArgumentException("Duplicate request header: " + key);
            }
        });
        headers = Map.copyOf(normalized);
        body = Objects.requireNonNull(body, "body").clone();
    }

    public WebRequest(String method, String path, Map<String, String> headers) {
        this(method, path, headers, new byte[0]);
    }

    public WebRequest(String method, String path) {
        this(method, path, Map.of(), new byte[0]);
    }

    @Override
    public byte[] body() {
        return body.clone();
    }

    public Optional<String> header(String name) {
        return Optional.ofNullable(headers.get(
                Objects.requireNonNull(name, "name").toLowerCase(Locale.ROOT)));
    }
}
