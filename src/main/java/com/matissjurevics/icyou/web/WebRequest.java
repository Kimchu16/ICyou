package com.matissjurevics.icyou.web;

import java.util.Objects;

/** Transport-neutral request data passed into the server control plane. */
public record WebRequest(String method, String path) {
    public WebRequest {
        method = Objects.requireNonNull(method, "method");
        path = Objects.requireNonNull(path, "path");
    }
}
