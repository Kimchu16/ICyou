package com.matissjurevics.icyou.web;

import java.net.InetSocketAddress;
import java.util.Optional;

/** Replaceable transport boundary for embedded or future relay web access. */
public interface WebGateway extends AutoCloseable {
    boolean start(WebServerConfig config, WebRequestHandler handler);
    boolean isRunning();
    Optional<InetSocketAddress> boundAddress();
    @Override
    void close();
}
