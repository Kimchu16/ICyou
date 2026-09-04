package com.matissjurevics.icyou.render.scene;

import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

/** Bounded per-tick journal of block and lighting changes relevant to scenes. */
public final class SceneChangeJournal {

    public static final int MAX_BLOCK_CHANGES = 8192;
    public static final int MAX_LIGHT_CHUNKS = 256;

    public record Changes(Set<BlockPos> blocks, Set<ChunkPos> lightChunks,
                          boolean overflowed) {
        public Changes {
            blocks = Set.copyOf(Objects.requireNonNull(blocks, "blocks"));
            lightChunks = Set.copyOf(Objects.requireNonNull(lightChunks, "lightChunks"));
        }
    }

    private static final class MutableChanges {
        private final Set<BlockPos> blocks = new LinkedHashSet<>();
        private final Set<ChunkPos> lightChunks = new LinkedHashSet<>();
        private boolean overflowed;
    }

    private static final Map<ServerWorld, MutableChanges> CHANGES = new IdentityHashMap<>();

    private SceneChangeJournal() {
    }

    public static synchronized void recordBlock(ServerWorld world, BlockPos position) {
        MutableChanges changes = CHANGES.computeIfAbsent(
                Objects.requireNonNull(world, "world"), ignored -> new MutableChanges());
        BlockPos immutable = Objects.requireNonNull(position, "position").toImmutable();
        if (changes.blocks.contains(immutable)) {
            return;
        }
        if (changes.blocks.size() >= MAX_BLOCK_CHANGES) {
            changes.overflowed = true;
            return;
        }
        changes.blocks.add(immutable);
    }

    public static synchronized void recordLight(ServerWorld world, ChunkPos chunk) {
        MutableChanges changes = CHANGES.computeIfAbsent(
                Objects.requireNonNull(world, "world"), ignored -> new MutableChanges());
        ChunkPos requiredChunk = Objects.requireNonNull(chunk, "chunk");
        if (changes.lightChunks.contains(requiredChunk)) {
            return;
        }
        if (changes.lightChunks.size() >= MAX_LIGHT_CHUNKS) {
            changes.overflowed = true;
            return;
        }
        changes.lightChunks.add(requiredChunk);
    }

    public static synchronized Map<ServerWorld, Changes> drain(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        Map<ServerWorld, Changes> result = new IdentityHashMap<>();
        for (ServerWorld world : server.getWorlds()) {
            MutableChanges changes = CHANGES.remove(world);
            if (changes != null) {
                result.put(world, new Changes(changes.blocks, changes.lightChunks,
                        changes.overflowed));
            }
        }
        return Map.copyOf(result);
    }

    public static synchronized void clear(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            CHANGES.remove(world);
        }
    }
}
