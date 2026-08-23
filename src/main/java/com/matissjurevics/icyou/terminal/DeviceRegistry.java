package com.matissjurevics.icyou.terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.matissjurevics.icyou.ICyouMod;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

/**
 * World-persisted registry of every ICyou device: cameras, screens and
 * paired wireless screens. Owned by exactly one terminal each; survives
 * terminal destruction so a rebuilt terminal reclaims its devices.
 */
public class DeviceRegistry extends PersistentState {

    public record CameraDevice(int id, String name, BlockPos terminal, BlockPos pos) {}
    public record ScreenDevice(int id, String name, BlockPos terminal, BlockPos pos,
                               int assignedCamId) {}
    public record WirelessDevice(int id, String name, BlockPos terminal) {}

    private static final String KEY = "icyou_devices";

    private int nextId = 1;
    private final List<CameraDevice> cameras = new ArrayList<>();
    private final List<ScreenDevice> screens = new ArrayList<>();
    private final List<WirelessDevice> wireless = new ArrayList<>();

    public DeviceRegistry() {
        super();
    }

    public static DeviceRegistry get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(
                new Type<>(DeviceRegistry::new, DeviceRegistry::readNbt, null), KEY);
    }

    // --- cameras ---

    /** Registers a camera under a terminal. @return the new entry */
    public CameraDevice addCamera(BlockPos terminal, BlockPos pos) {
        var existing = cameraAt(pos);
        if (existing.isPresent()) {
            // Re-linked to another terminal: move ownership.
            var moved = new CameraDevice(existing.get().id(), existing.get().name(),
                    terminal, pos);
            cameras.set(cameras.indexOf(existing.get()), moved);
            markDirty();
            return moved;
        }
        var dev = new CameraDevice(nextId++, "CAM-" + nextId, terminal, pos.toImmutable());
        cameras.add(dev);
        markDirty();
        return dev;
    }

    public void removeCamera(BlockPos pos) {
        cameras.removeIf(c -> c.pos().equals(pos));
        // Screens assigned to it fall back to no signal.
        boolean changed = false;
        for (int i = 0; i < screens.size(); i++) {
            var s = screens.get(i);
            if (findCamera(s.assignedCamId()).filter(c -> c.pos().equals(pos)).isPresent()
                    || s.assignedCamId() >= 0 && cameraById(s.assignedCamId())
                            .map(c -> c.pos().equals(pos)).orElse(false)) {
                screens.set(i, new ScreenDevice(s.id(), s.name(), s.terminal(),
                        s.pos(), -1));
                changed = true;
            }
        }
        if (changed) {
            markDirty();
        }
    }

    public Optional<CameraDevice> cameraAt(BlockPos pos) {
        return cameras.stream().filter(c -> c.pos().equals(pos)).findFirst();
    }

    public Optional<CameraDevice> cameraById(int id) {
        return cameras.stream().filter(c -> c.id() == id).findFirst();
    }

    // --- screens ---

    /** Registers a screen under a terminal. @return the new entry */
    public ScreenDevice addScreen(BlockPos terminal, BlockPos pos) {
        var existing = screenAt(pos);
        if (existing.isPresent()) {
            var moved = new ScreenDevice(existing.get().id(), existing.get().name(),
                    terminal, pos, existing.get().assignedCamId());
            screens.set(screens.indexOf(existing.get()), moved);
            markDirty();
            return moved;
        }
        var dev = new ScreenDevice(nextId++, "SCR-" + nextId, terminal, pos.toImmutable(), -1);
        screens.add(dev);
        markDirty();
        return dev;
    }

    public void removeScreen(BlockPos pos) {
        screens.removeIf(s -> s.pos().equals(pos));
        markDirty();
    }

    public Optional<ScreenDevice> screenAt(BlockPos pos) {
        return screens.stream().filter(s -> s.pos().equals(pos)).findFirst();
    }

    /** Assigns a camera to a screen; both must belong to the same terminal. */
    public boolean assign(int screenId, int cameraId) {
        var screen = screenById(screenId);
        if (screen.isEmpty()) {
            return false;
        }
        if (cameraId >= 0) {
            var cam = cameraById(cameraId);
            if (cam.isEmpty() || !cam.get().terminal().equals(screen.get().terminal())) {
                return false;
            }
        }
        screens.set(screens.indexOf(screen.get()),
                new ScreenDevice(screen.get().id(), screen.get().name(),
                        screen.get().terminal(), screen.get().pos(), cameraId));
        markDirty();
        return true;
    }

    public Optional<ScreenDevice> screenById(int id) {
        return screens.stream().filter(s -> s.id() == id).findFirst();
    }

    // --- wireless screens ---

    /** Registers a paired portable screen. @return the new entry */
    public WirelessDevice addWireless(BlockPos terminal) {
        var dev = new WirelessDevice(nextId++, "WRL-" + nextId, terminal.toImmutable());
        wireless.add(dev);
        markDirty();
        return dev;
    }

    public void removeWireless(int id) {
        wireless.removeIf(w -> w.id() == id);
        markDirty();
    }

    // --- renaming ---

    public boolean rename(int typeId, int id, String name) {
        String safe = name.trim();
        if (safe.isEmpty() || safe.length() > 24) {
            return false;
        }
        if (typeId == 0) {
            var c = cameraById(id); if (c.isEmpty()) return false;
            cameras.set(cameras.indexOf(c.get()),
                    new CameraDevice(id, safe, c.get().terminal(), c.get().pos()));
        } else if (typeId == 1) {
            var s = screenById(id); if (s.isEmpty()) return false;
            screens.set(screens.indexOf(s.get()),
                    new ScreenDevice(id, safe, s.get().terminal(), s.get().pos(),
                            s.get().assignedCamId()));
        } else if (typeId == 2) {
            var w = wireless.stream().filter(x -> x.id() == id).findFirst();
            if (w.isEmpty()) return false;
            wireless.set(wireless.indexOf(w.get()), new WirelessDevice(id, safe,
                    w.get().terminal()));
        } else {
            return false;
        }
        markDirty();
        return true;
    }

    // --- queries ---

    public List<CameraDevice> camerasFor(BlockPos terminal) {
        return cameras.stream().filter(c -> c.terminal().equals(terminal)).toList();
    }

    public List<ScreenDevice> screensFor(BlockPos terminal) {
        return screens.stream().filter(s -> s.terminal().equals(terminal)).toList();
    }

    public List<WirelessDevice> wirelessFor(BlockPos terminal) {
        return wireless.stream().filter(w -> w.terminal().equals(terminal)).toList();
    }

    private Optional<CameraDevice> findCamera(int id) {
        return cameraById(id);
    }

    // --- persistence ---

    private static DeviceRegistry readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        var reg = new DeviceRegistry();
        reg.nextId = nbt.getInt("nextId");
        var camList = nbt.getList("cameras", 10);
        for (int i = 0; i < camList.size(); i++) {
            NbtCompound c = camList.getCompound(i);
            reg.cameras.add(new CameraDevice(c.getInt("id"), c.getString("name"),
                    BlockPos.fromLong(c.getLong("term")), BlockPos.fromLong(c.getLong("pos"))));
        }
        var scrList = nbt.getList("screens", 10);
        for (int i = 0; i < scrList.size(); i++) {
            NbtCompound c = scrList.getCompound(i);
            reg.screens.add(new ScreenDevice(c.getInt("id"), c.getString("name"),
                    BlockPos.fromLong(c.getLong("term")), BlockPos.fromLong(c.getLong("pos")),
                    c.getInt("cam")));
        }
        var wrlList = nbt.getList("wireless", 10);
        for (int i = 0; i < wrlList.size(); i++) {
            NbtCompound c = wrlList.getCompound(i);
            reg.wireless.add(new WirelessDevice(c.getInt("id"), c.getString("name"),
                    BlockPos.fromLong(c.getLong("term"))));
        }
        return reg;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        nbt.putInt("nextId", nextId);
        var camList = new net.minecraft.nbt.NbtList();
        for (var c : cameras) {
            NbtCompound tag = new NbtCompound();
            tag.putInt("id", c.id());
            tag.putString("name", c.name());
            tag.putLong("term", c.terminal().asLong());
            tag.putLong("pos", c.pos().asLong());
            camList.add(tag);
        }
        nbt.put("cameras", camList);

        var scrList = new net.minecraft.nbt.NbtList();
        for (var s : screens) {
            NbtCompound tag = new NbtCompound();
            tag.putInt("id", s.id());
            tag.putString("name", s.name());
            tag.putLong("term", s.terminal().asLong());
            tag.putLong("pos", s.pos().asLong());
            tag.putInt("cam", s.assignedCamId());
            scrList.add(tag);
        }
        nbt.put("screens", scrList);

        var wrlList = new net.minecraft.nbt.NbtList();
        for (var w : wireless) {
            NbtCompound tag = new NbtCompound();
            tag.putInt("id", w.id());
            tag.putString("name", w.name());
            tag.putLong("term", w.terminal().asLong());
            wrlList.add(tag);
        }
        nbt.put("wireless", wrlList);
        return nbt;
    }

    // --- id-based removal (used by GUI + block-break cleanup) ---

    public void removeCameraById(int id) {
        cameras.removeIf(c -> c.id() == id);
        markDirty();
    }

    public void removeScreenById(int id) {
        screens.removeIf(s -> s.id() == id);
        markDirty();
    }

    static {
        ICyouMod.LOGGER.debug("DeviceRegistry loaded");
    }
}
