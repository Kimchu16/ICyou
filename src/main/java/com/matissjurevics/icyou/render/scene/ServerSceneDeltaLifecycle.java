package com.matissjurevics.icyou.render.scene;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.matissjurevics.icyou.ICyouMod;
import com.matissjurevics.icyou.render.auth.RenderAgentAuthenticator;
import com.matissjurevics.icyou.render.auth.ServerRenderAuthLifecycle;
import com.matissjurevics.icyou.render.schedule.RenderScheduler;
import com.matissjurevics.icyou.render.schedule.RenderScheduler.Assignment;
import com.matissjurevics.icyou.render.schedule.ServerRenderSchedulerLifecycle;
import com.matissjurevics.icyou.render.scene.SceneChangeJournal.Changes;
import com.matissjurevics.icyou.render.scene.ServerSceneSnapshotLifecycle.SnapshotProgress;
import com.matissjurevics.icyou.admin.ServerAdminLimitsLifecycle;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;

/** Sends bounded, ordered scene changes after each initial camera snapshot. */
public final class ServerSceneDeltaLifecycle {

    private static final int METADATA_INTERVAL_TICKS = 20;

    private record JobKey(UUID jobId, long revision, UUID sessionId) {
        private static JobKey of(Assignment assignment) {
            return new JobKey(assignment.jobId(), assignment.revision(),
                    assignment.sessionId());
        }
    }

    private static final class JobState {
        private final long snapshotSequence;
        private final Map<Integer, byte[]> entityFingerprints = new LinkedHashMap<>();
        private final Map<Integer, List<byte[]>> entityPackets = new LinkedHashMap<>();
        private final Set<BlockPos> blocks = new LinkedHashSet<>();
        private final Set<ChunkPos> lightChunks = new LinkedHashSet<>();
        private long nextDeltaSequence = 1;
        private long lastMetadataTick = Long.MIN_VALUE;

        private JobState(long snapshotSequence) {
            this.snapshotSequence = snapshotSequence;
        }
    }

    private static final Map<MinecraftServer, Map<JobKey, JobState>> ACTIVE =
            new IdentityHashMap<>();

    private ServerSceneDeltaLifecycle() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            synchronized (ServerSceneDeltaLifecycle.class) {
                ACTIVE.put(server, new LinkedHashMap<>());
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(ServerSceneDeltaLifecycle::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            synchronized (ServerSceneDeltaLifecycle.class) {
                ACTIVE.remove(server);
            }
            SceneChangeJournal.clear(server);
        });
    }

    private static void tick(MinecraftServer server) {
        Map<JobKey, JobState> states;
        synchronized (ServerSceneDeltaLifecycle.class) {
            states = ACTIVE.get(server);
        }
        RenderScheduler scheduler = ServerRenderSchedulerLifecycle.scheduler(server).orElse(null);
        RenderAgentAuthenticator authentication = ServerRenderAuthLifecycle.authenticator(server)
                .orElse(null);
        if (states == null || scheduler == null || authentication == null) {
            SceneChangeJournal.drain(server);
            return;
        }

        Map<UUID, Assignment> assignments = scheduler.assignments();
        Set<JobKey> activeKeys = assignments.values().stream().map(JobKey::of)
                .collect(java.util.stream.Collectors.toSet());
        states.keySet().retainAll(activeKeys);
        Map<ServerWorld, Changes> changes = SceneChangeJournal.drain(server);
        int diameter = ServerAdminLimitsLifecycle.limits(server)
                .simulatedChunkDiameter();

        for (Assignment assignment : assignments.values().stream()
                .sorted(Comparator.comparing(job -> job.jobId().toString())).toList()) {
            SnapshotProgress progress = ServerSceneSnapshotLifecycle.progress(server, assignment)
                    .orElse(null);
            if (progress == null) {
                continue;
            }
            ServerWorld world = server.getWorld(assignment.camera().dimension());
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(assignment.agentId());
            if (world == null || player == null) {
                continue;
            }
            JobKey key = JobKey.of(assignment);
            JobState state = states.get(key);
            try {
                if (state == null || state.snapshotSequence != progress.sequence()) {
                    state = new JobState(progress.sequence());
                    captureEntityBaseline(server, world, player, authentication, assignment,
                            state, diameter);
                    states.put(key, state);
                }
                Changes worldChanges = changes.get(world);
                if (worldChanges != null && worldChanges.overflowed()) {
                    throw new IllegalStateException("Scene change journal overflowed");
                }
                collectWorldChanges(assignment, worldChanges, state, diameter);
                collectEntityChanges(server, world, player, authentication, assignment, state,
                        diameter);
                if (progress.delivered()) {
                    sendDelta(server, world, player, assignment, state);
                }
            } catch (RuntimeException error) {
                ICyouMod.LOGGER.error("Scene update failed for camera {}",
                        assignment.camera().deviceId(), error);
                states.remove(key);
                scheduler.failJob(assignment.jobId());
            }
        }
    }

    private static void captureEntityBaseline(MinecraftServer server, ServerWorld world,
            ServerPlayerEntity player, RenderAgentAuthenticator authentication,
            Assignment assignment, JobState state, int diameter) {
        for (Entity entity : entities(world, authentication, assignment, diameter)) {
            List<byte[]> packets = encodedEntity(server, world, entity, player);
            state.entityFingerprints.put(entity.getId(), ScenePacketStream.encode(packets));
        }
    }

    private static void collectEntityChanges(MinecraftServer server, ServerWorld world,
            ServerPlayerEntity player, RenderAgentAuthenticator authentication,
            Assignment assignment, JobState state, int diameter) {
        Set<Integer> present = new LinkedHashSet<>();
        for (Entity entity : entities(world, authentication, assignment, diameter)) {
            int entityId = entity.getId();
            present.add(entityId);
            List<byte[]> packets = encodedEntity(server, world, entity, player);
            byte[] fingerprint = ScenePacketStream.encode(packets);
            byte[] previous = state.entityFingerprints.put(entityId, fingerprint);
            if (previous == null) {
                state.entityPackets.put(entityId, packets);
            } else if (!Arrays.equals(previous, fingerprint)) {
                List<byte[]> replacement = new ArrayList<>();
                replacement.add(ServerSceneSnapshotEncoder.encodePacket(server,
                        new EntitiesDestroyS2CPacket(entityId)));
                replacement.addAll(packets);
                state.entityPackets.put(entityId, List.copyOf(replacement));
            }
        }
        for (Integer removedId : new ArrayList<>(state.entityFingerprints.keySet())) {
            if (!present.contains(removedId)) {
                state.entityFingerprints.remove(removedId);
                state.entityPackets.put(removedId, List.of(
                        ServerSceneSnapshotEncoder.encodePacket(server,
                                new EntitiesDestroyS2CPacket(removedId))));
            }
        }
    }

    private static List<Entity> entities(ServerWorld world,
            RenderAgentAuthenticator authentication, Assignment assignment, int diameter) {
        int centerX = assignment.camera().position().getX() >> 4;
        int centerZ = assignment.camera().position().getZ() >> 4;
        int minChunkX = centerX - diameter / 2;
        int minChunkZ = centerZ - diameter / 2;
        Box area = new Box(minChunkX * 16.0, world.getBottomY(), minChunkZ * 16.0,
                (minChunkX + diameter) * 16.0, world.getTopY(),
                (minChunkZ + diameter) * 16.0);
        List<Entity> result = world.getOtherEntities(null, area,
                entity -> !authentication.isAuthenticated(entity.getUuid()));
        result.sort(Comparator.comparingInt(Entity::getId));
        return result;
    }

    private static List<byte[]> encodedEntity(MinecraftServer server, ServerWorld world,
            Entity entity, ServerPlayerEntity player) {
        return ServerSceneSnapshotEncoder.encodePacketList(server,
                ServerSceneSnapshotEncoder.entityPackets(world, entity, player));
    }

    private static void collectWorldChanges(Assignment assignment, Changes changes,
            JobState state, int diameter) {
        if (changes == null) {
            return;
        }
        int centerX = assignment.camera().position().getX() >> 4;
        int centerZ = assignment.camera().position().getZ() >> 4;
        int radius = diameter / 2;
        changes.blocks().stream().filter(position -> within(position.getX() >> 4,
                position.getZ() >> 4, centerX, centerZ, radius)).forEach(state.blocks::add);
        changes.lightChunks().stream().filter(chunk -> within(chunk.x, chunk.z,
                centerX, centerZ, radius)).forEach(state.lightChunks::add);
    }

    private static boolean within(int chunkX, int chunkZ, int centerX, int centerZ,
                                  int radius) {
        return Math.abs(chunkX - centerX) <= radius
                && Math.abs(chunkZ - centerZ) <= radius;
    }

    private static void sendDelta(MinecraftServer server, ServerWorld world,
            ServerPlayerEntity player, Assignment assignment, JobState state) {
        long tick = world.getTime();
        boolean metadataDue = state.lastMetadataTick == Long.MIN_VALUE
                || tick - state.lastMetadataTick >= METADATA_INTERVAL_TICKS;
        if (!metadataDue && state.blocks.isEmpty() && state.lightChunks.isEmpty()
                && state.entityPackets.isEmpty()) {
            return;
        }

        List<Packet<? super ClientPlayPacketListener>> worldPackets = new ArrayList<>();
        for (BlockPos position : state.blocks) {
            worldPackets.add(new BlockUpdateS2CPacket(world, position));
            BlockEntity blockEntity = world.getBlockEntity(position);
            if (blockEntity != null) {
                Packet<? super ClientPlayPacketListener> update = blockEntity.toUpdatePacket();
                if (update != null) {
                    worldPackets.add(update);
                }
            }
        }
        for (ChunkPos chunk : state.lightChunks) {
            worldPackets.add(new LightUpdateS2CPacket(chunk,
                    world.getChunkManager().getLightingProvider(), null, null));
        }
        List<byte[]> encoded = new ArrayList<>(
                ServerSceneSnapshotEncoder.encodePacketList(server, worldPackets));
        state.entityPackets.values().forEach(encoded::addAll);
        byte[] stream = encoded.isEmpty() ? new byte[0] : ScenePacketStream.encode(encoded);
        SceneDeltaProtocol.Delta delta = new SceneDeltaProtocol.Delta(assignment.jobId(),
                assignment.revision(), state.snapshotSequence, state.nextDeltaSequence++,
                world.getTime(), world.getTimeOfDay(), world.getRainGradient(1.0f),
                world.getThunderGradient(1.0f), stream);
        ServerPlayNetworking.send(player, new SceneDeltaS2CPayload(delta));
        state.blocks.clear();
        state.lightChunks.clear();
        state.entityPackets.clear();
        state.lastMetadataTick = tick;
    }
}
