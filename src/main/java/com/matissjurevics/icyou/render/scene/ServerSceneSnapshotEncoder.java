package com.matissjurevics.icyou.render.scene;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.matissjurevics.icyou.overhaul.CameraOverhaulContracts;
import com.matissjurevics.icyou.render.auth.RenderAgentAuthenticator;
import com.matissjurevics.icyou.render.schedule.RenderScheduler.Assignment;
import com.matissjurevics.icyou.render.scene.SceneSnapshotProtocol.Transfer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.Entity;
import net.minecraft.network.NetworkState;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.state.PlayStateFactories;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.world.chunk.WorldChunk;

/** Captures one 3x3 camera scene as length-delimited vanilla play packets. */
public final class ServerSceneSnapshotEncoder {

    public static final class SnapshotNotReadyException extends Exception {
        public SnapshotNotReadyException(String message) {
            super(message);
        }
    }

    private ServerSceneSnapshotEncoder() {
    }

    public static Transfer capture(MinecraftServer server, Assignment assignment,
                                   ServerPlayerEntity agent,
                                   RenderAgentAuthenticator authentication,
                                   long sequence) throws SnapshotNotReadyException {
        ServerWorld world = server.getWorld(assignment.camera().dimension());
        if (world == null) {
            throw new SnapshotNotReadyException("Camera world is unavailable");
        }
        int centerX = assignment.camera().position().getX() >> 4;
        int centerZ = assignment.camera().position().getZ() >> 4;
        List<Packet<? super ClientPlayPacketListener>> packets = new ArrayList<>();
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                WorldChunk chunk = world.getChunkManager().getWorldChunk(centerX + dx,
                        centerZ + dz);
                if (chunk == null) {
                    throw new SnapshotNotReadyException("Camera chunks are still loading");
                }
                packets.add(new ChunkDataS2CPacket(chunk,
                        world.getChunkManager().getLightingProvider(), null, null));
            }
        }

        int diameter = CameraOverhaulContracts.SIMULATED_CHUNK_DIAMETER;
        int minChunkX = centerX - diameter / 2;
        int minChunkZ = centerZ - diameter / 2;
        Box area = new Box(minChunkX * 16.0, world.getBottomY(), minChunkZ * 16.0,
                (minChunkX + diameter) * 16.0, world.getTopY(),
                (minChunkZ + diameter) * 16.0);
        List<Entity> entities = world.getOtherEntities(null, area,
                entity -> !authentication.isAuthenticated(entity.getUuid()));
        entities.sort(Comparator.comparingInt(Entity::getId));
        for (Entity entity : entities) {
            EntityTrackerEntry tracker = new EntityTrackerEntry(world, entity, 1,
                    true, ignored -> { });
            tracker.sendPackets(agent, packets::add);
        }

        byte[] stream = encodePackets(server, packets);
        return SceneSnapshotProtocol.fragment(assignment.jobId(), assignment.revision(),
                sequence, assignment.camera(), world.getTime(), world.getTimeOfDay(),
                world.getRainGradient(1.0f), world.getThunderGradient(1.0f), stream);
    }

    private static byte[] encodePackets(MinecraftServer server,
                                        List<Packet<? super ClientPlayPacketListener>> packets) {
        NetworkState<ClientPlayPacketListener> state = PlayStateFactories.S2C.bind(
                RegistryByteBuf.makeFactory(server.getRegistryManager()));
        List<byte[]> encoded = new ArrayList<>(packets.size());
        for (Packet<? super ClientPlayPacketListener> packet : packets) {
            ByteBuf buffer = Unpooled.buffer();
            try {
                state.codec().encode(buffer, packet);
                byte[] bytes = new byte[buffer.readableBytes()];
                buffer.getBytes(buffer.readerIndex(), bytes);
                encoded.add(bytes);
            } finally {
                buffer.release();
            }
        }
        return ScenePacketStream.encode(encoded);
    }
}
