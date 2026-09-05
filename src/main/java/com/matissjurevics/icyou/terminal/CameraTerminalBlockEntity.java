package com.matissjurevics.icyou.terminal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.matissjurevics.icyou.device.CameraRef;
import com.matissjurevics.icyou.device.DeviceLocation;
import com.matissjurevics.icyou.device.GlobalDeviceRegistry;
import com.matissjurevics.icyou.device.TerminalRef;
import com.matissjurevics.icyou.registry.ModBlockEntities;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.world.ServerWorld;

/**
 * Thin block entity for the terminal. Device state lives in the world-level
 * {@link GlobalDeviceRegistry}; this entity persists the terminal's stable ID.
 */
public class CameraTerminalBlockEntity extends BlockEntity {

    private TerminalRef terminalRef;

    public CameraTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CAMERA_TERMINAL, pos, state);
    }

    public TerminalRef initialize(ServerWorld world) {
        return initialize(world, null);
    }

    public TerminalRef initialize(ServerWorld world, UUID ownerId) {
        GlobalDeviceRegistry registry = GlobalDeviceRegistry.get(world.getServer());
        if (terminalRef == null) {
            terminalRef = registry.deviceAt(new DeviceLocation(world.getRegistryKey(), pos))
                    .filter(TerminalRef.class::isInstance)
                    .map(TerminalRef.class::cast)
                    .orElseGet(() -> {
                        TerminalRef ref = new TerminalRef(
                                UUID.randomUUID(), world.getRegistryKey(), pos);
                        return ownerId == null ? registry.registerTerminal(ref).ref()
                                : registry.registerTerminal(ref, ownerId).ref();
                    });
            markDirty();
        } else if (registry.terminal(terminalRef.deviceId()).isEmpty()) {
            if (ownerId == null) {
                registry.registerTerminal(terminalRef);
            } else {
                registry.registerTerminal(terminalRef, ownerId);
            }
        }
        if (ownerId != null) {
            registry.claimTerminal(terminalRef.deviceId(), ownerId);
        }
        return terminalRef;
    }

    public Optional<TerminalRef> getTerminalRef() {
        return Optional.ofNullable(terminalRef);
    }

    public List<CameraRef> getCameras(ServerWorld world) {
        if (terminalRef == null) {
            return List.of();
        }
        return GlobalDeviceRegistry.get(world.getServer()).camerasFor(terminalRef.deviceId())
                .stream().map(GlobalDeviceRegistry.CameraEntry::ref).toList();
    }

    public int getCount(ServerWorld world) {
        return getCameras(world).size();
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        if (terminalRef != null) {
            nbt.put("terminalRef", terminalRef.toNbt());
        }
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        terminalRef = nbt.contains("terminalRef")
                ? TerminalRef.fromNbt(nbt.getCompound("terminalRef")) : null;
    }
}
