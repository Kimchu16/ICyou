package com.matissjurevics.icyou.web;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.function.Consumer;

/** Socket-backed gateway shipped with 0.3.x. */
public final class EmbeddedWebGateway implements WebGateway {
    private final ServerWebRuntime runtime;

    public EmbeddedWebGateway(Consumer<Throwable> failureHandler) {
        runtime = new ServerWebRuntime(failureHandler);
    }

    @Override
    public boolean start(WebServerConfig config, WebRequestHandler handler) {
        return runtime.start(config, handler);
    }

    @Override
    public boolean isRunning() {
        return runtime.state() == ServerWebRuntime.State.RUNNING;
    }

    @Override
    public Optional<InetSocketAddress> boundAddress() {
        return runtime.boundAddress();
    }

    @Override
    public void close() {
        runtime.close();
    }
}
