package com.matissjurevics.icyou.screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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
 * Holds the camera a screen displays. The camera's facing is captured at bind
 * time so the client renderer never needs to load the camera's chunk.
 */
public class ScreenBlockEntity extends BlockEntity {

    private BlockPos linkedCamera;
    private Direction cameraFacing = Direction.NORTH;

    /** Server-side cadence counter for feed syncs. */
    private int syncCounter;
    /** Client-side cache of the latest blips received over the network. */
    private final List<FeedBlip> clientBlips = new ArrayList<>();

    public ScreenBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SCREEN, pos, state);
    }

    public void bind(BlockPos cameraPos, Direction cameraFacing) {
        this.linkedCamera = cameraPos.toImmutable();
        this.cameraFacing = cameraFacing;
        markDirty();
    }

    public Optional<BlockPos> getLinkedCamera() {
        return Optional.ofNullable(linkedCamera);
    }

    public Direction getCameraFacing() {
        return cameraFacing;
    }

    // --- Feed syncing (phase 3) ---

    public static final int SYNC_INTERVAL_TICKS = 10; // two updates per second

    /**
     * Server ticker (wired up by {@code ScreenBlock.getTicker}): scans the
     * linked camera's view cone and pushes a snapshot to every player who can
     * see this screen.
     */
    public static void serverTick(World world, BlockPos pos, BlockState state,
                                  ScreenBlockEntity screen) {
        if (++screen.syncCounter < SYNC_INTERVAL_TICKS) {
            return;
        }
        screen.syncCounter = 0;
        if (!(world instanceof ServerWorld serverWorld) || screen.linkedCamera == null) {
            return;
        }
        List<FeedBlip> blips = CameraViews.scanBlips(
                world, screen.linkedCamera, screen.cameraFacing);
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

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        if (linkedCamera != null) {
            nbt.putLong("camera", linkedCamera.asLong());
            nbt.putString("cam_facing", cameraFacing.getName());
        }
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        if (nbt.contains("camera")) {
            linkedCamera = BlockPos.fromLong(nbt.getLong("camera"));
            cameraFacing = Direction.byName(nbt.getString("cam_facing"));
            if (cameraFacing == null) {
                cameraFacing = Direction.NORTH;
            }
        } else {
            linkedCamera = null;
        }
    }
}
