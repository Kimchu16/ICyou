package com.matissjurevics.icyou.client.agent;

import java.util.List;
import java.util.Objects;

import com.matissjurevics.icyou.client.agent.SceneSnapshotAssembler.CompleteSnapshot;
import com.matissjurevics.icyou.client.mixin.ClientPlayNetworkHandlerAccessor;
import com.matissjurevics.icyou.render.scene.SceneDeltaProtocol.Delta;
import com.matissjurevics.icyou.render.scene.ScenePacketStream;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.network.NetworkState;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.state.PlayStateFactories;
import net.minecraft.world.Difficulty;

/** One isolated vanilla client world owned by a single remote render job. */
public final class RemoteSceneWorld implements AutoCloseable {

    private static final int SCENE_VIEW_DISTANCE = 3;

    private final MinecraftClient client;
    private final ClientPlayNetworkHandler handler;
    private final ClientWorld world;
    private final WorldRenderer renderer;
    private final NetworkState<ClientPlayPacketListener> playState;
    private final long snapshotSequence;
    private final java.util.UUID jobId;
    private final long jobRevision;
    private boolean closed;

    public RemoteSceneWorld(MinecraftClient client, CompleteSnapshot snapshot) {
        this.client = Objects.requireNonNull(client, "client");
        Objects.requireNonNull(snapshot, "snapshot");
        if (client.world == null || client.getNetworkHandler() == null
                || !client.world.getRegistryKey().equals(snapshot.begin().camera().dimension())) {
            throw new IllegalStateException("Remote scene dimension is unavailable");
        }
        handler = client.getNetworkHandler();
        renderer = new WorldRenderer(client, client.getEntityRenderDispatcher(),
                client.getBlockEntityRenderDispatcher(), client.getBufferBuilders());
        ClientWorld.Properties properties = new ClientWorld.Properties(
                Difficulty.NORMAL, false, false);
        world = new ClientWorld(handler, properties, snapshot.begin().camera().dimension(),
                client.world.getDimensionEntry(), SCENE_VIEW_DISTANCE, SCENE_VIEW_DISTANCE,
                client::getProfiler, renderer, false, 0L);
        renderer.setWorld(world);
        renderer.reload(client.getResourceManager());
        playState = PlayStateFactories.S2C.bind(
                RegistryByteBuf.makeFactory(handler.getRegistryManager()));
        snapshotSequence = snapshot.begin().sequence();
        jobId = snapshot.begin().jobId();
        jobRevision = snapshot.begin().jobRevision();
        try {
            applyMetadata(snapshot.begin().worldTime(), snapshot.begin().timeOfDay(),
                    snapshot.begin().rainGradient(), snapshot.begin().thunderGradient());
            applyPacketStream(snapshot.encodedPackets());
            world.runQueuedChunkUpdates();
        } catch (RuntimeException error) {
            close();
            throw error;
        }
    }

    public long snapshotSequence() {
        return snapshotSequence;
    }

    public ClientWorld world() {
        return world;
    }

    public WorldRenderer renderer() {
        return renderer;
    }

    public void apply(Delta delta) {
        requireOpen();
        Objects.requireNonNull(delta, "delta");
        if (!delta.jobId().equals(jobId) || delta.jobRevision() != jobRevision
                || delta.snapshotSequence() != snapshotSequence) {
            throw new IllegalArgumentException("Delta belongs to another snapshot");
        }
        applyMetadata(delta.worldTime(), delta.timeOfDay(), delta.rainGradient(),
                delta.thunderGradient());
        byte[] encodedPackets = delta.encodedPackets();
        if (encodedPackets.length > 0) {
            applyPacketStream(encodedPackets);
        }
        world.runQueuedChunkUpdates();
    }

    public void tick() {
        requireOpen();
        world.tickEntities();
        world.runQueuedChunkUpdates();
        renderer.tick();
    }

    private void applyMetadata(long worldTime, long timeOfDay, float rain, float thunder) {
        world.setTime(worldTime);
        world.setTimeOfDay(timeOfDay);
        world.setRainGradient(rain);
        world.setThunderGradient(thunder);
    }

    private void applyPacketStream(byte[] stream) {
        List<byte[]> packets = ScenePacketStream.decode(stream);
        for (byte[] encoded : packets) {
            ByteBuf buffer = Unpooled.wrappedBuffer(encoded);
            try {
                Packet<? super ClientPlayPacketListener> packet = playState.codec().decode(buffer);
                if (buffer.isReadable()) {
                    throw new IllegalArgumentException("Trailing vanilla packet data");
                }
                if (!RemoteScenePacketPolicy.isAllowed(packet)) {
                    throw new IllegalArgumentException("Packet is not valid remote scene state: "
                            + packet.getClass().getSimpleName());
                }
                applyPacket(packet);
            } finally {
                buffer.release();
            }
        }
    }

    private void applyPacket(Packet<? super ClientPlayPacketListener> packet) {
        ClientPlayNetworkHandlerAccessor accessor =
                (ClientPlayNetworkHandlerAccessor) handler;
        ClientWorld previousClientWorld = client.world;
        ClientWorld previousHandlerWorld = accessor.icyou$getWorld();
        try {
            client.world = world;
            accessor.icyou$setWorld(world);
            if (packet instanceof EntitySpawnS2CPacket spawn) {
                Entity entity = accessor.icyou$createEntity(spawn);
                if (entity == null) {
                    throw new IllegalArgumentException("Unsupported remote entity type");
                }
                entity.onSpawnPacket(spawn);
                world.addEntity(entity);
            } else {
                packet.apply(handler);
            }
        } finally {
            accessor.icyou$setWorld(previousHandlerWorld);
            client.world = previousClientWorld;
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Remote scene world is closed");
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            renderer.setWorld(null);
            renderer.close();
        }
    }
}
