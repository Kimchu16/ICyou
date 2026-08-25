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
import com.matissjurevics.icyou.terminal.DeviceRegistry;

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
 * A passive display panel. It is linked to a terminal via the Setup Remote;
 * the camera it shows is assigned through the terminal GUI (drag & drop) and
 * read here from the world's {@link DeviceRegistry}.
 */
public class ScreenBlockEntity extends BlockEntity {

    private BlockPos terminalPos;

    private int syncCounter;
    private final List<FeedBlip> clientBlips = new ArrayList<>();
    private boolean receiving;
    private int lastFacingId;
    private int lastIndex;
    private int lastCount;
    private BlockPos lastCamPos;

    public ScreenBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SCREEN, pos, state);
    }

    public void setTerminal(BlockPos pos) {
        terminalPos = pos.toImmutable();
        markDirty();
    }

    public Optional<BlockPos> getTerminal() {
        return Optional.ofNullable(terminalPos);
    }

    // --- feed syncing (server side) ---

    public static final int SYNC_INTERVAL_TICKS = 10;

    public static void serverTick(World world, BlockPos pos, BlockState state,
                                  ScreenBlockEntity screen) {
        if (++screen.syncCounter < SYNC_INTERVAL_TICKS) {
            return;
        }
        screen.syncCounter = 0;
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }

        DeviceRegistry reg = DeviceRegistry.get(serverWorld);
        Optional<DeviceRegistry.ScreenDevice> scr = reg.screenAt(pos);
        if (scr.isEmpty()) {
            return;
        }

        // DeviceRegistry is the source of truth. This also repairs screens linked
        // before their block entity's terminal field was populated, or while the
        // screen's chunk was unloaded.
        BlockPos terminal = scr.get().terminal();
        if (!terminal.equals(screen.terminalPos)) {
            screen.setTerminal(terminal);
        }
        if (scr.get().assignedCamId() < 0) {
            return;
        }
        Optional<DeviceRegistry.CameraDevice> cam = reg.cameraById(scr.get().assignedCamId());
        if (cam.isEmpty()) {
            return;
        }

        BlockPos camPos = cam.get().pos();
        Direction facing = Direction.NORTH;
        BlockState camState = world.getBlockState(camPos);
        if (camState.getBlock() instanceof CameraBlock) {
            facing = camState.get(CameraBlock.FACING);
        }

        List<FeedBlip> blips = CameraViews.scanBlips(world, camPos, facing);
        List<DeviceRegistry.CameraDevice> terminalCameras = reg.camerasFor(terminal);
        int index = terminalCameras.indexOf(cam.get()) + 1;
        int count = terminalCameras.size();
        FeedDataS2CPayload payload = new FeedDataS2CPayload(
                pos, camPos, facing.getId(), index, count, blips);
        PlayerLookup.tracking(serverWorld, pos)
                .forEach(player -> ServerPlayNetworking.send(player, payload));
    }

    // --- client cache ---

    public void updateClientFeed(List<FeedBlip> blips, BlockPos camPos, int facingId,
                                 int index, int count) {
        clientBlips.clear();
        clientBlips.addAll(blips);
        receiving = true;
        lastFacingId = facingId;
        lastIndex = index;
        lastCount = count;
        lastCamPos = camPos.toImmutable();
    }

    public List<FeedBlip> getClientBlips() {
        return Collections.unmodifiableList(clientBlips);
    }

    public boolean isReceiving() {
        return receiving;
    }

    public BlockPos getLastCamPos() {
        return lastCamPos;
    }

    public int getLastFacingId() {
        return lastFacingId;
    }

    public int getLastIndex() {
        return lastIndex;
    }

    public int getLastCount() {
        return lastCount;
    }

    // --- persistence ---

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        if (terminalPos != null) {
            nbt.putLong("terminal", terminalPos.asLong());
        }
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        if (nbt.contains("terminal")) {
            terminalPos = BlockPos.fromLong(nbt.getLong("terminal"));
        } else {
            terminalPos = null;
        }
    }
}
