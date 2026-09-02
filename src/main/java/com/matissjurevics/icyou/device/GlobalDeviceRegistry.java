package com.matissjurevics.icyou.device;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.matissjurevics.icyou.overhaul.CameraOverhaulContracts;
import com.matissjurevics.icyou.terminal.SlugToken;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

/**
 * Logical-server registry for all placed ICyou devices.
 *
 * <p>The state is stored in the Overworld persistence manager solely to give
 * it one save location per server. References may point at any dimension.</p>
 */
public final class GlobalDeviceRegistry extends PersistentState {

    public static final String PERSISTENCE_KEY = "icyou_global_devices";

    private static final String SCHEMA_VERSION_KEY = "schemaVersion";
    private static final int MAX_NAME_LENGTH = 24;

    private static final Type<GlobalDeviceRegistry> TYPE = new Type<>(
            GlobalDeviceRegistry::new, GlobalDeviceRegistry::readNbt, null);

    private final Map<UUID, TerminalEntry> terminals = new LinkedHashMap<>();
    private final Map<UUID, CameraEntry> cameras = new LinkedHashMap<>();
    private final Map<UUID, ScreenEntry> screens = new LinkedHashMap<>();
    private final Map<UUID, CameraTombstone> cameraTombstones = new LinkedHashMap<>();
    private final Map<UUID, String> slugByTerminal = new LinkedHashMap<>();

    private final Map<UUID, DeviceRef> devicesById = new LinkedHashMap<>();
    private final Map<DeviceLocation, UUID> deviceIdsByLocation = new LinkedHashMap<>();
    private final Map<UUID, LinkedHashSet<UUID>> cameraIdsByTerminal = new LinkedHashMap<>();
    private final Map<UUID, LinkedHashSet<UUID>> screenIdsByTerminal = new LinkedHashMap<>();
    private boolean legacyMigrationComplete;

    public record TerminalEntry(TerminalRef ref, Optional<UUID> ownerId) {
        public TerminalEntry {
            ref = Objects.requireNonNull(ref, "ref");
            ownerId = Objects.requireNonNull(ownerId, "ownerId");
        }
    }

    public record CameraTombstone(CameraRef lastRef, UUID terminalId, String name,
                                  Instant brokenAt) {
        public CameraTombstone {
            lastRef = Objects.requireNonNull(lastRef, "lastRef");
            terminalId = Objects.requireNonNull(terminalId, "terminalId");
            name = validateName(name);
            brokenAt = Objects.requireNonNull(brokenAt, "brokenAt");
        }

        public UUID cameraId() {
            return lastRef.deviceId();
        }
    }

    public record CameraEntry(CameraRef ref, UUID terminalId, String name) {
        public CameraEntry {
            ref = Objects.requireNonNull(ref, "ref");
            terminalId = Objects.requireNonNull(terminalId, "terminalId");
            name = validateName(name);
        }
    }

    public record ScreenEntry(ScreenRef ref, UUID terminalId, String name,
                              Optional<UUID> assignedCameraId) {
        public ScreenEntry {
            ref = Objects.requireNonNull(ref, "ref");
            terminalId = Objects.requireNonNull(terminalId, "terminalId");
            name = validateName(name);
            assignedCameraId = Objects.requireNonNull(assignedCameraId, "assignedCameraId");
        }
    }

    public static GlobalDeviceRegistry get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.getOverworld().getPersistentStateManager()
                .getOrCreate(TYPE, PERSISTENCE_KEY);
    }

    public TerminalEntry registerTerminal(TerminalRef ref) {
        return registerTerminal(ref, Optional.empty());
    }

    public TerminalEntry registerTerminal(TerminalRef ref, UUID ownerId) {
        return registerTerminal(ref, Optional.of(Objects.requireNonNull(ownerId, "ownerId")));
    }

    private TerminalEntry registerTerminal(TerminalRef ref, Optional<UUID> ownerId) {
        String slug;
        do {
            slug = SlugToken.generate();
        } while (slugByTerminal.containsValue(slug));
        return registerTerminal(ref, slug, ownerId);
    }

    private TerminalEntry registerTerminal(TerminalRef ref, String slug,
                                           Optional<UUID> ownerId) {
        Objects.requireNonNull(ref, "ref");
        requireAvailable(ref);
        String validatedSlug = requireSlug(slug);
        if (slugByTerminal.containsValue(validatedSlug)) {
            throw new IllegalArgumentException("Duplicate terminal slug: " + validatedSlug);
        }
        TerminalEntry entry = new TerminalEntry(ref, ownerId);
        terminals.put(ref.deviceId(), entry);
        index(ref);
        cameraIdsByTerminal.put(ref.deviceId(), new LinkedHashSet<>());
        screenIdsByTerminal.put(ref.deviceId(), new LinkedHashSet<>());
        slugByTerminal.put(ref.deviceId(), validatedSlug);
        markDirty();
        return entry;
    }

    TerminalEntry registerMigratedTerminal(TerminalRef ref, String legacySlug) {
        return registerTerminal(ref, legacySlug, Optional.empty());
    }

    static GlobalDeviceRegistry copyOf(GlobalDeviceRegistry source,
                                       RegistryWrapper.WrapperLookup lookup) {
        return readNbt(source.writeNbt(new NbtCompound(), lookup), lookup);
    }

    static void install(MinecraftServer server, GlobalDeviceRegistry registry) {
        registry.markDirty();
        server.getOverworld().getPersistentStateManager().set(PERSISTENCE_KEY, registry);
    }

    public boolean isLegacyMigrationComplete() {
        return legacyMigrationComplete;
    }

    void markLegacyMigrationComplete() {
        legacyMigrationComplete = true;
        markDirty();
    }

    public CameraEntry registerCamera(CameraRef ref, UUID terminalId, String name) {
        Objects.requireNonNull(ref, "ref");
        requireTerminal(terminalId);
        requireAvailable(ref);
        CameraEntry entry = new CameraEntry(ref, terminalId, name);
        cameras.put(ref.deviceId(), entry);
        index(ref);
        cameraIdsByTerminal.get(terminalId).add(ref.deviceId());
        markDirty();
        return entry;
    }

    public ScreenEntry registerScreen(ScreenRef ref, UUID terminalId, String name,
                                      Optional<UUID> assignedCameraId) {
        Objects.requireNonNull(ref, "ref");
        requireTerminal(terminalId);
        requireAvailable(ref);
        validateAssignment(terminalId, assignedCameraId);
        ScreenEntry entry = new ScreenEntry(ref, terminalId, name, assignedCameraId);
        screens.put(ref.deviceId(), entry);
        index(ref);
        screenIdsByTerminal.get(terminalId).add(ref.deviceId());
        markDirty();
        return entry;
    }

    public Optional<TerminalEntry> terminal(UUID deviceId) {
        return Optional.ofNullable(terminals.get(deviceId));
    }

    public Optional<CameraEntry> camera(UUID deviceId) {
        return Optional.ofNullable(cameras.get(deviceId));
    }

    public Optional<ScreenEntry> screen(UUID deviceId) {
        return Optional.ofNullable(screens.get(deviceId));
    }

    public Optional<CameraTombstone> cameraTombstone(UUID cameraId) {
        return Optional.ofNullable(cameraTombstones.get(cameraId));
    }

    public Optional<DeviceRef> device(UUID deviceId) {
        return Optional.ofNullable(devicesById.get(deviceId));
    }

    public Optional<DeviceRef> deviceAt(DeviceLocation location) {
        UUID deviceId = deviceIdsByLocation.get(Objects.requireNonNull(location, "location"));
        return deviceId == null ? Optional.empty() : device(deviceId);
    }

    public List<CameraEntry> camerasFor(UUID terminalId) {
        return entriesFor(cameraIdsByTerminal.get(terminalId), cameras);
    }

    public List<ScreenEntry> screensFor(UUID terminalId) {
        return entriesFor(screenIdsByTerminal.get(terminalId), screens);
    }

    public Set<UUID> terminalIds() {
        return Set.copyOf(terminals.keySet());
    }

    public List<CameraTombstone> tombstonesFor(UUID terminalId) {
        requireTerminal(terminalId);
        return cameraTombstones.values().stream()
                .filter(tombstone -> tombstone.terminalId().equals(terminalId)).toList();
    }

    public boolean canManageTerminal(UUID terminalId, UUID playerId, boolean operator) {
        Objects.requireNonNull(playerId, "playerId");
        TerminalEntry terminal = terminals.get(terminalId);
        return terminal != null && (operator
                || terminal.ownerId().filter(playerId::equals).isPresent());
    }

    public boolean claimTerminal(UUID terminalId, UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        TerminalEntry terminal = terminals.get(terminalId);
        if (terminal == null || terminal.ownerId().isPresent()) {
            return false;
        }
        terminals.put(terminalId, new TerminalEntry(terminal.ref(), Optional.of(playerId)));
        markDirty();
        return true;
    }

    public void transferTerminal(UUID terminalId, UUID newOwnerId) {
        TerminalEntry terminal = terminals.get(terminalId);
        if (terminal == null) {
            throw new IllegalArgumentException("Unknown terminal UUID: " + terminalId);
        }
        terminals.put(terminalId, new TerminalEntry(terminal.ref(),
                Optional.of(Objects.requireNonNull(newOwnerId, "newOwnerId"))));
        markDirty();
    }

    public String slug(UUID terminalId) {
        requireTerminal(terminalId);
        return slugByTerminal.get(terminalId);
    }

    public Optional<TerminalEntry> terminalBySlug(String slug) {
        return slugByTerminal.entrySet().stream()
                .filter(entry -> entry.getValue().equals(slug))
                .findFirst()
                .flatMap(entry -> terminal(entry.getKey()));
    }

    public int deviceCount() {
        return devicesById.size();
    }

    public void assignCamera(UUID screenId, Optional<UUID> cameraId) {
        ScreenEntry current = requireScreen(screenId);
        validateAssignment(current.terminalId(), cameraId);
        screens.put(screenId, new ScreenEntry(current.ref(), current.terminalId(),
                current.name(), cameraId));
        markDirty();
    }

    public void relinkCamera(UUID cameraId, UUID terminalId) {
        requireTerminal(terminalId);
        CameraEntry current = cameras.get(cameraId);
        if (current == null) {
            throw new IllegalArgumentException("Unknown camera UUID: " + cameraId);
        }
        if (current.terminalId().equals(terminalId)) {
            return;
        }
        cameraIdsByTerminal.get(current.terminalId()).remove(cameraId);
        cameraIdsByTerminal.get(terminalId).add(cameraId);
        cameras.put(cameraId, new CameraEntry(current.ref(), terminalId, current.name()));
        for (Map.Entry<UUID, ScreenEntry> indexed : new ArrayList<>(screens.entrySet())) {
            ScreenEntry screen = indexed.getValue();
            if (screen.assignedCameraId().filter(cameraId::equals).isPresent()) {
                screens.put(indexed.getKey(), new ScreenEntry(screen.ref(), screen.terminalId(),
                        screen.name(), Optional.empty()));
            }
        }
        markDirty();
    }

    public void relinkScreen(UUID screenId, UUID terminalId) {
        requireTerminal(terminalId);
        ScreenEntry current = requireScreen(screenId);
        if (current.terminalId().equals(terminalId)) {
            return;
        }
        screenIdsByTerminal.get(current.terminalId()).remove(screenId);
        screenIdsByTerminal.get(terminalId).add(screenId);
        screens.put(screenId, new ScreenEntry(current.ref(), terminalId, current.name(),
                Optional.empty()));
        markDirty();
    }

    public void renameCamera(UUID cameraId, String name) {
        CameraEntry current = cameras.get(cameraId);
        if (current == null) {
            throw new IllegalArgumentException("Unknown camera UUID: " + cameraId);
        }
        cameras.put(cameraId, new CameraEntry(current.ref(), current.terminalId(), name));
        markDirty();
    }

    public void renameScreen(UUID screenId, String name) {
        ScreenEntry current = requireScreen(screenId);
        screens.put(screenId, new ScreenEntry(current.ref(), current.terminalId(), name,
                current.assignedCameraId()));
        markDirty();
    }

    public boolean removeCamera(UUID cameraId) {
        CameraEntry removed = cameras.remove(cameraId);
        if (removed == null) {
            return false;
        }
        unindex(removed.ref());
        cameraIdsByTerminal.get(removed.terminalId()).remove(cameraId);
        for (Map.Entry<UUID, ScreenEntry> indexed : new ArrayList<>(screens.entrySet())) {
            ScreenEntry screen = indexed.getValue();
            if (screen.assignedCameraId().filter(cameraId::equals).isPresent()) {
                screens.put(indexed.getKey(), new ScreenEntry(screen.ref(), screen.terminalId(),
                        screen.name(), Optional.empty()));
            }
        }
        markDirty();
        return true;
    }

    public boolean tombstoneCamera(UUID cameraId, Instant brokenAt) {
        Objects.requireNonNull(brokenAt, "brokenAt");
        CameraEntry removed = cameras.remove(cameraId);
        if (removed == null) {
            return false;
        }
        unindex(removed.ref());
        cameraIdsByTerminal.get(removed.terminalId()).remove(cameraId);
        cameraTombstones.put(cameraId, new CameraTombstone(
                removed.ref(), removed.terminalId(), removed.name(), brokenAt));
        markDirty();
        return true;
    }

    public CameraEntry restoreCamera(UUID cameraId, CameraRef replacement) {
        Objects.requireNonNull(replacement, "replacement");
        CameraTombstone tombstone = cameraTombstones.get(cameraId);
        if (tombstone == null) {
            throw new IllegalArgumentException("Unknown camera tombstone: " + cameraId);
        }
        if (!replacement.deviceId().equals(cameraId)) {
            throw new IllegalArgumentException("Replacement must retain the tombstoned camera UUID");
        }
        if (devicesById.containsKey(cameraId)) {
            throw new IllegalArgumentException("Duplicate device UUID: " + cameraId);
        }
        DeviceLocation replacementLocation = DeviceLocation.of(replacement);
        if (deviceIdsByLocation.containsKey(replacementLocation)) {
            throw new IllegalArgumentException(
                    "Device location is already registered: " + replacementLocation);
        }
        CameraEntry restored = new CameraEntry(
                replacement, tombstone.terminalId(), tombstone.name());
        cameraTombstones.remove(cameraId);
        cameras.put(cameraId, restored);
        index(replacement);
        cameraIdsByTerminal.get(tombstone.terminalId()).add(cameraId);
        markDirty();
        return restored;
    }

    public int purgeExpiredTombstones(Instant now) {
        Objects.requireNonNull(now, "now");
        Instant cutoff = now.minus(Duration.ofDays(
                CameraOverhaulContracts.TOMBSTONE_RETENTION_DAYS));
        Set<UUID> expired = cameraTombstones.values().stream()
                .filter(tombstone -> !tombstone.brokenAt().isAfter(cutoff))
                .map(CameraTombstone::cameraId).collect(java.util.stream.Collectors.toSet());
        if (expired.isEmpty()) {
            return 0;
        }
        expired.forEach(cameraTombstones::remove);
        for (Map.Entry<UUID, ScreenEntry> indexed : new ArrayList<>(screens.entrySet())) {
            ScreenEntry screen = indexed.getValue();
            if (screen.assignedCameraId().filter(expired::contains).isPresent()) {
                screens.put(indexed.getKey(), new ScreenEntry(screen.ref(), screen.terminalId(),
                        screen.name(), Optional.empty()));
            }
        }
        markDirty();
        return expired.size();
    }

    public boolean removeScreen(UUID screenId) {
        ScreenEntry removed = screens.remove(screenId);
        if (removed == null) {
            return false;
        }
        unindex(removed.ref());
        screenIdsByTerminal.get(removed.terminalId()).remove(screenId);
        markDirty();
        return true;
    }

    public boolean removeTerminal(UUID terminalId) {
        TerminalEntry terminal = terminals.get(terminalId);
        if (terminal == null) {
            return false;
        }
        if (!cameraIdsByTerminal.get(terminalId).isEmpty()
                || !screenIdsByTerminal.get(terminalId).isEmpty()
                || cameraTombstones.values().stream()
                .anyMatch(tombstone -> tombstone.terminalId().equals(terminalId))) {
            throw new IllegalStateException("Cannot remove a terminal with registered devices");
        }
        terminals.remove(terminalId);
        cameraIdsByTerminal.remove(terminalId);
        screenIdsByTerminal.remove(terminalId);
        slugByTerminal.remove(terminalId);
        unindex(terminal.ref());
        markDirty();
        return true;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        nbt.putInt(SCHEMA_VERSION_KEY, CameraOverhaulContracts.SAVE_SCHEMA_VERSION);
        nbt.putBoolean("legacyMigrationComplete", legacyMigrationComplete);

        NbtList terminalList = new NbtList();
        for (TerminalEntry entry : terminals.values()) {
            NbtCompound tag = new NbtCompound();
            tag.put("ref", entry.ref().toNbt());
            tag.putString("slug", slug(entry.ref().deviceId()));
            entry.ownerId().ifPresent(ownerId -> tag.putUuid("ownerId", ownerId));
            terminalList.add(tag);
        }
        nbt.put("terminals", terminalList);

        NbtList cameraList = new NbtList();
        for (CameraEntry entry : cameras.values()) {
            NbtCompound tag = new NbtCompound();
            tag.put("ref", entry.ref().toNbt());
            tag.putUuid("terminalId", entry.terminalId());
            tag.putString("name", entry.name());
            cameraList.add(tag);
        }
        nbt.put("cameras", cameraList);

        NbtList tombstoneList = new NbtList();
        for (CameraTombstone tombstone : cameraTombstones.values()) {
            NbtCompound tag = new NbtCompound();
            tag.put("lastRef", tombstone.lastRef().toNbt());
            tag.putUuid("terminalId", tombstone.terminalId());
            tag.putString("name", tombstone.name());
            tag.putLong("brokenAt", tombstone.brokenAt().toEpochMilli());
            tombstoneList.add(tag);
        }
        nbt.put("cameraTombstones", tombstoneList);

        NbtList screenList = new NbtList();
        for (ScreenEntry entry : screens.values()) {
            NbtCompound tag = new NbtCompound();
            tag.put("ref", entry.ref().toNbt());
            tag.putUuid("terminalId", entry.terminalId());
            tag.putString("name", entry.name());
            entry.assignedCameraId().ifPresent(id -> tag.putUuid("cameraId", id));
            screenList.add(tag);
        }
        nbt.put("screens", screenList);
        return nbt;
    }

    static GlobalDeviceRegistry readNbt(NbtCompound nbt,
                                        RegistryWrapper.WrapperLookup lookup) {
        Objects.requireNonNull(nbt, "nbt");
        int schemaVersion = nbt.getInt(SCHEMA_VERSION_KEY);
        if (schemaVersion != CameraOverhaulContracts.SAVE_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported global device registry schema: " + schemaVersion);
        }

        GlobalDeviceRegistry registry = new GlobalDeviceRegistry();
        registry.legacyMigrationComplete = nbt.getBoolean("legacyMigrationComplete");
        NbtList terminalList = nbt.getList("terminals", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < terminalList.size(); i++) {
            NbtCompound tag = terminalList.getCompound(i);
            Optional<UUID> ownerId = tag.containsUuid("ownerId")
                    ? Optional.of(tag.getUuid("ownerId")) : Optional.empty();
            registry.registerTerminal(TerminalRef.fromNbt(tag.getCompound("ref")),
                    tag.getString("slug"), ownerId);
        }

        NbtList cameraList = nbt.getList("cameras", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < cameraList.size(); i++) {
            NbtCompound tag = cameraList.getCompound(i);
            registry.registerCamera(CameraRef.fromNbt(tag.getCompound("ref")),
                    requiredUuid(tag, "terminalId"), tag.getString("name"));
        }

        NbtList tombstoneList = nbt.getList("cameraTombstones", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < tombstoneList.size(); i++) {
            NbtCompound tag = tombstoneList.getCompound(i);
            CameraRef lastRef = CameraRef.fromNbt(tag.getCompound("lastRef"));
            UUID terminalId = requiredUuid(tag, "terminalId");
            registry.requireTerminal(terminalId);
            if (registry.devicesById.containsKey(lastRef.deviceId())
                    || registry.cameraTombstones.containsKey(lastRef.deviceId())) {
                throw new IllegalArgumentException(
                        "Duplicate tombstoned camera UUID: " + lastRef.deviceId());
            }
            registry.cameraTombstones.put(lastRef.deviceId(), new CameraTombstone(
                    lastRef, terminalId, tag.getString("name"),
                    Instant.ofEpochMilli(tag.getLong("brokenAt"))));
        }

        NbtList screenList = nbt.getList("screens", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < screenList.size(); i++) {
            NbtCompound tag = screenList.getCompound(i);
            Optional<UUID> cameraId = tag.containsUuid("cameraId")
                    ? Optional.of(tag.getUuid("cameraId")) : Optional.empty();
            registry.registerScreen(ScreenRef.fromNbt(tag.getCompound("ref")),
                    requiredUuid(tag, "terminalId"), tag.getString("name"), cameraId);
        }
        registry.setDirty(false);
        return registry;
    }

    private void requireAvailable(DeviceRef ref) {
        if (devicesById.containsKey(ref.deviceId())
                || cameraTombstones.containsKey(ref.deviceId())) {
            throw new IllegalArgumentException("Duplicate device UUID: " + ref.deviceId());
        }
        DeviceLocation location = DeviceLocation.of(ref);
        if (deviceIdsByLocation.containsKey(location)) {
            throw new IllegalArgumentException("Device location is already registered: " + location);
        }
    }

    private void requireTerminal(UUID terminalId) {
        if (terminalId == null || !terminals.containsKey(terminalId)) {
            throw new IllegalArgumentException("Unknown terminal UUID: " + terminalId);
        }
    }

    private ScreenEntry requireScreen(UUID screenId) {
        ScreenEntry screen = screens.get(screenId);
        if (screen == null) {
            throw new IllegalArgumentException("Unknown screen UUID: " + screenId);
        }
        return screen;
    }

    private void validateAssignment(UUID terminalId, Optional<UUID> cameraId) {
        Objects.requireNonNull(cameraId, "cameraId");
        cameraId.ifPresent(id -> {
            CameraEntry camera = cameras.get(id);
            CameraTombstone tombstone = cameraTombstones.get(id);
            if (camera == null && tombstone == null) {
                throw new IllegalArgumentException("Unknown camera UUID: " + id);
            }
            UUID cameraTerminalId = camera != null ? camera.terminalId() : tombstone.terminalId();
            if (!cameraTerminalId.equals(terminalId)) {
                throw new IllegalArgumentException(
                        "Camera and screen must belong to the same terminal");
            }
        });
    }

    private void index(DeviceRef ref) {
        devicesById.put(ref.deviceId(), ref);
        deviceIdsByLocation.put(DeviceLocation.of(ref), ref.deviceId());
    }

    private void unindex(DeviceRef ref) {
        devicesById.remove(ref.deviceId());
        deviceIdsByLocation.remove(DeviceLocation.of(ref));
    }

    private static <T> List<T> entriesFor(Set<UUID> ids, Map<UUID, T> entries) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream().map(entries::get).toList();
    }

    private static UUID requiredUuid(NbtCompound nbt, String key) {
        if (!nbt.containsUuid(key)) {
            throw new IllegalArgumentException("Missing required UUID field: " + key);
        }
        return nbt.getUuid(key);
    }

    private static String validateName(String name) {
        String normalized = Objects.requireNonNull(name, "name").trim();
        if (normalized.isEmpty() || normalized.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "Device name must contain 1 to " + MAX_NAME_LENGTH + " characters");
        }
        return normalized;
    }

    private static String requireSlug(String slug) {
        String normalized = Objects.requireNonNull(slug, "slug").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Terminal slug must not be empty");
        }
        return normalized;
    }
}
