package com.matissjurevics.icyou.screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.matissjurevics.icyou.camera.CameraBlock;
import com.matissjurevics.icyou.camera.CameraViews;
import com.matissjurevics.icyou.device.CameraRef;
import com.matissjurevics.icyou.device.DeviceLocation;
import com.matissjurevics.icyou.device.GlobalDeviceRegistry;
import com.matissjurevics.icyou.device.ScreenRef;
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
 * A passive display panel. It is linked to a terminal via the Setup Remote;
 * the camera it shows is assigned through the terminal GUI (drag & drop) and
 * read here from the logical server's {@link GlobalDeviceRegistry}.
 */
public class ScreenBlockEntity extends BlockEntity {

    private ScreenRef screenRef;
    private UUID terminalId;

    private int syncCounter;
    private final List<FeedBlip> clientBlips = new ArrayList<>();
    private boolean receiving;
    private int lastFacingId;
    private int lastIndex;
    private int lastCount;
    private CameraRef lastCameraRef;

    public ScreenBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SCREEN, pos, state);
    }

    public void setLink(ScreenRef ref, UUID terminalId) {
        this.screenRef = ref;
        this.terminalId = terminalId;
        markDirty();
    }

    public Optional<ScreenRef> getScreenRef() {
        return Optional.ofNullable(screenRef);
    }

    public Optional<UUID> getTerminalId() {
        return Optional.ofNullable(terminalId);
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

        GlobalDeviceRegistry registry = GlobalDeviceRegistry.get(serverWorld.getServer());
        Optional<GlobalDeviceRegistry.ScreenEntry> screenEntry = registry
                .deviceAt(new DeviceLocation(serverWorld.getRegistryKey(), pos))
                .filter(ScreenRef.class::isInstance)
                .map(ScreenRef.class::cast)
                .flatMap(ref -> registry.screen(ref.deviceId()));
        if (screenEntry.isEmpty()) {
            return;
        }

        var registeredScreen = screenEntry.get();
        if (!registeredScreen.ref().equals(screen.screenRef)
                || !registeredScreen.terminalId().equals(screen.terminalId)) {
            screen.setLink(registeredScreen.ref(), registeredScreen.terminalId());
        }
        if (registeredScreen.assignedCameraId().isEmpty()) {
            return;
        }
        Optional<GlobalDeviceRegistry.CameraEntry> cam = registry.camera(
                registeredScreen.assignedCameraId().orElseThrow());
        if (cam.isEmpty()) {
            return;
        }

        CameraRef cameraRef = cam.get().ref();
        ServerWorld cameraWorld = serverWorld.getServer().getWorld(cameraRef.dimension());
        if (cameraWorld == null) {
            return;
        }
        BlockPos camPos = cameraRef.position();
        Direction facing = Direction.NORTH;
        BlockState camState = cameraWorld.getBlockState(camPos);
        if (camState.getBlock() instanceof CameraBlock) {
            facing = camState.get(CameraBlock.FACING);
        }

        List<FeedBlip> blips = CameraViews.scanBlips(cameraWorld, camPos, facing);
        List<GlobalDeviceRegistry.CameraEntry> terminalCameras = registry.camerasFor(
                registeredScreen.terminalId());
        int index = terminalCameras.indexOf(cam.get()) + 1;
        int count = terminalCameras.size();
        FeedDataS2CPayload payload = new FeedDataS2CPayload(
                registeredScreen.ref(), cameraRef, facing.getId(), index, count, blips);
        PlayerLookup.tracking(serverWorld, pos)
                .forEach(player -> ServerPlayNetworking.send(player, payload));
    }

    // --- client cache ---

    public void updateClientFeed(List<FeedBlip> blips, CameraRef cameraRef, int facingId,
                                 int index, int count) {
        clientBlips.clear();
        clientBlips.addAll(blips);
        receiving = true;
        lastFacingId = facingId;
        lastIndex = index;
        lastCount = count;
        lastCameraRef = cameraRef;
    }

    public List<FeedBlip> getClientBlips() {
        return Collections.unmodifiableList(clientBlips);
    }

    public boolean isReceiving() {
        return receiving;
    }

    public BlockPos getLastCamPos() {
        return lastCameraRef == null ? null : lastCameraRef.position();
    }

    public CameraRef getLastCameraRef() {
        return lastCameraRef;
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
        if (screenRef != null) {
            nbt.put("screenRef", screenRef.toNbt());
        }
        if (terminalId != null) {
            nbt.putUuid("terminalId", terminalId);
        }
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        screenRef = nbt.contains("screenRef")
                ? ScreenRef.fromNbt(nbt.getCompound("screenRef")) : null;
        terminalId = nbt.containsUuid("terminalId") ? nbt.getUuid("terminalId") : null;
    }
}
