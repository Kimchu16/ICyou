package com.matissjurevics.icyou.screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.matissjurevics.icyou.camera.CameraBlock;
import com.matissjurevics.icyou.camera.CameraViews;
import com.matissjurevics.icyou.feed.FeedBlip;
import com.matissjurevics.icyou.network.FeedDataS2CPayload;
import com.matissjurevics.icyou.registry.ModBlockEntities;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * A screen can hold several cameras ("channels") and display one at a time.
 * Sneak-using the screen cycles to the next channel; right-clicking views it.
 */
public class ScreenBlockEntity extends BlockEntity {

    /** Maximum number of cameras one screen can hold. */
    public static final int MAX_CAMERAS = 8;

    /** A camera reference together with its facing, resolved for convenience. */
    public record BoundCamera(BlockPos pos, Direction facing, int index, int count) {}

    private final List<BlockPos> cameras = new ArrayList<>();
    private int currentIndex;
    /** Fallback facing for when the camera's chunk isn't loaded. */
    private Direction currentFacing = Direction.NORTH;

    /** Server-side cadence counter for feed syncs. */
    private int syncCounter;
    /** Client-side cache of the latest blips received over the network. */
    private final List<FeedBlip> clientBlips = new ArrayList<>();

    public ScreenBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SCREEN, pos, state);
    }

    // --- Channel management ---

    /**
     * Adds a camera (if new) and switches the display to it.
     *
     * @return false if the screen already holds {@link #MAX_CAMERAS} cameras
     */
    public boolean bind(BlockPos pos, Direction facing) {
        pos = pos.toImmutable();
        if (!cameras.contains(pos)) {
            if (cameras.size() >= MAX_CAMERAS) {
                return false;
            }
            cameras.add(pos);
        }
        currentIndex = cameras.indexOf(pos);
        currentFacing = facing;
        markDirty();
        return true;
    }

    /**
     * Advances to the next channel (wrapping around) and resolves its facing
     * from the world.
     *
     * @return the newly selected camera, or null if the screen has none
     */
    public BoundCamera cycle(World world) {
        if (cameras.isEmpty()) {
            return null;
        }
        currentIndex = (currentIndex + 1) % cameras.size();
        markDirty();
        return getCurrent(world);
    }

    /** The currently selected camera with its facing, or null if none bound. */
    public BoundCamera getCurrent(World world) {
        if (cameras.isEmpty()) {
            return null;
        }
        int index = Math.floorMod(currentIndex, cameras.size());
        BlockPos pos = cameras.get(index);
        Direction facing = currentFacing;
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof CameraBlock) {
            facing = state.get(CameraBlock.FACING);
        }
        return new BoundCamera(pos, facing, index + 1, cameras.size());
    }

    public int getCount() {
        return cameras.size();
    }

    // --- Feed syncing (phase 3) ---

    public static final int SYNC_INTERVAL_TICKS = 10; // two updates per second

    /**
     * Server ticker (wired up by {@code ScreenBlock.getTicker}): scans the
     * selected camera's view cone and pushes a snapshot to every player who
     * can see this screen.
     */
    public static void serverTick(World world, BlockPos pos, BlockState state,
                                  ScreenBlockEntity screen) {
        if (++screen.syncCounter < SYNC_INTERVAL_TICKS) {
            return;
        }
        screen.syncCounter = 0;
        if (!(world instanceof ServerWorld serverWorld)
                || screen.cameras.isEmpty()) {
            return;
        }
        BoundCamera current = screen.getCurrent(world);
        List<FeedBlip> blips = CameraViews.scanBlips(world, current.pos(), current.facing());
        FeedDataS2CPayload payload = new FeedDataS2CPayload(pos, blips);
        PlayerLookup.tracking(serverWorld, pos)
                .forEach(player -> ServerPlayNetworking.send(player, payload));
    }

    /** Client side: called from the network receiver on the main thread. */
    public void updateClientBlips(List<FeedBlip> blips) {
        clientBlips.clear();
        clientBlips.addAll(blips);
    }

    public List<FeedBlip> getClientBlips() {
        return Collections.unmodifiableList(clientBlips);
    }

    // --- Persistence ---

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        nbt.putLongArray("cameras",
                cameras.stream().mapToLong(BlockPos::asLong).toArray());
        nbt.putInt("index", currentIndex);
        nbt.putString("cam_facing", currentFacing.getName());
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        cameras.clear();
        for (long encoded : nbt.getLongArray("cameras")) {
            cameras.add(BlockPos.fromLong(encoded));
        }
        // Migration: worlds saved before multi-channel screens held one camera.
        if (cameras.isEmpty() && nbt.contains("camera")) {
            cameras.add(BlockPos.fromLong(nbt.getLong("camera")));
        }
        currentIndex = Math.floorMod(nbt.getInt("index"), Math.max(1, cameras.size()));
        Direction parsed = Direction.byName(nbt.getString("cam_facing"));
        currentFacing = parsed != null ? parsed : Direction.NORTH;
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup lookup) {
        // Send the full channel list to clients when the chunk streams in,
        // so the renderer knows the bound camera immediately.
        return createNbt(lookup);
    }
}
