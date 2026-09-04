package com.matissjurevics.icyou.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

class WebRequestTest {

    @Test
    void normalizesHeadersAndDefendsBodyBytes() {
        byte[] body = {1, 2, 3};
        WebRequest request = new WebRequest("POST", "/", Map.of("Content-Type", "x"), body);
        body[0] = 9;
        byte[] read = request.body();
        read[1] = 9;

        assertEquals("x", request.header("content-type").orElseThrow());
        assertArrayEquals(new byte[] {1, 2, 3}, request.body());
    }
}
