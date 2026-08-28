package com.matissjurevics.icyou.client.stream;

/** Latest encoded frame for one camera. Immutable; shared across threads. */
public record StreamFrame(byte[] jpeg, long timestamp) {}
