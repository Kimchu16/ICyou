package com.matissjurevics.icyou.tick;

import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.matissjurevics.icyou.lease.ServerChunkLeaseLifecycle;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

/** Adds only the random ticks omitted by vanilla's player-proximity filter. */
public final class SupplementalRandomTickLifecycle {

    private static final Map<ServerWorld, Set<Long>> VANILLA_TICKS =
            new IdentityHashMap<>();

    private SupplementalRandomTickLifecycle() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(SupplementalRandomTickLifecycle::tickWorld);
        ServerLifecycleEvents.SERVER_STOPPING.register(SupplementalRandomTickLifecycle::stop);
    }

    /** Called by the common server mixin at the start of vanilla's tickChunk. */
    public static synchronized void recordVanillaTick(ServerWorld world, ChunkPos chunk) {
        VANILLA_TICKS.computeIfAbsent(world, ignored -> new LinkedHashSet<>())
                .add(chunk.toLong());
    }

    private static void tickWorld(ServerWorld world) {
        Set<Long> recorded;
        synchronized (SupplementalRandomTickLifecycle.class) {
            recorded = VANILLA_TICKS.remove(world);
        }
        Set<Long> vanillaTicked = recorded == null ? Set.of() : recorded;
        int speed = world.getGameRules().getInt(GameRules.RANDOM_TICK_SPEED);
        if (!SupplementalTickPlanner.enabled(world.getTickManager().shouldTick(), speed)) {
            return;
        }
        ServerChunkLeaseLifecycle.leases(world.getServer()).ifPresent(leases ->
                SupplementalTickPlanner.select(leases.leasedLocations(),
                                world.getRegistryKey(), vanillaTicked,
                                packed -> world.getChunkManager().isChunkLoaded(
                                        ChunkPos.getPackedX(packed), ChunkPos.getPackedZ(packed)))
                        .forEach(chunkPos -> tickRandomBlocksAndFluids(
                                world, world.getChunkManager().getWorldChunk(
                                        chunkPos.x, chunkPos.z), speed)));
    }

    private static void tickRandomBlocksAndFluids(ServerWorld world, WorldChunk chunk,
                                                  int speed) {
        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();
        ChunkSection[] sections = chunk.getSectionArray();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            ChunkSection section = sections[sectionIndex];
            if (!section.hasRandomTicks()) {
                continue;
            }
            int startY = ChunkSectionPos.getBlockCoord(
                    chunk.sectionIndexToCoord(sectionIndex));
            for (int attempt = 0; attempt < speed; attempt++) {
                BlockPos pos = world.getRandomPosInChunk(startX, startY, startZ, 15);
                BlockState block = section.getBlockState(pos.getX() - startX,
                        pos.getY() - startY, pos.getZ() - startZ);
                if (block.hasRandomTicks()) {
                    block.randomTick(world, pos, world.getRandom());
                }
                FluidState fluid = block.getFluidState();
                if (fluid.hasRandomTicks()) {
                    fluid.onRandomTick(world, pos, world.getRandom());
                }
            }
        }
    }

    private static synchronized void stop(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            VANILLA_TICKS.remove(world);
        }
    }
}
