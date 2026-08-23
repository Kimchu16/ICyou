package com.matissjurevics.icyou.screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.matissjurevics.icyou.camera.CameraViews;
import com.matissjurevics.icyou.feed.FeedBlip;
import com.matissjurevics.icyou.network.FeedDataS2CPayload;
import com.matissjurevics.icyou.registry.ModBlockEntities;
import com.matissjurevics.icyou.terminal.CameraTerminalBlock;
import com.matissjurevics.icyou.terminal.CameraTerminalBlockEntity;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * A passive display panel. It pairs itself with the nearest camera terminal
 * (within {@link #PAIR_RANGE}) and renders that terminal's selected camera
 * feed. Screens hold no camera state of their own.
 */
public class ScreenBlockEntity extends BlockEntity {

    /** How far a screen will look for a terminal to pair with. */
    public static final int PAIR_RANGE = 8;
    /** How often (ticks) the screen re-scans for a terminal. */
    private static final int RESCAN_INTERVAL = 40;

    private BlockPos pairedTerminal;
    private int ticksUntilRescan;

    /** Server-side cadence counter for feed syncs. */
    private int syncCounter;
    /** Client-side cache of the latest feed snapshot received over the network. */
    private final List<FeedBlip> clientBlips = new ArrayList<>();
    private boolean receiving;
    private int lastFacingId;
    private int lastIndex;
    private int lastCount;

    public ScreenBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SCREEN, pos, state);
    }

    // --- Terminal pairing (server side) ---

    /** Finds the nearest terminal in range, or null. */
    private CameraTerminalBlockEntity findTerminal(World world) {
        for (BlockPos candidate : BlockPos.iterateOutwards(pos, PAIR_RANGE, PAIR_RANGE, PAIR_RANGE)) {
            BlockState state = world.getBlockState(candidate);
            if (state.getBlock() instanceof CameraTerminalBlock
                    && world.getBlockEntity(candidate) instanceof CameraTerminalBlockEntity terminal) {
                return terminal;
            }
        }
        return null;
    }

    private CameraTerminalBlockEntity resolveTerminal(World world) {
        if (pairedTerminal != null
                && world.getBlockState(pairedTerminal).getBlock() instanceof CameraTerminalBlock
                && world.getBlockEntity(pairedTerminal) instanceof CameraTerminalBlockEntity terminal) {
            return terminal;
        }
        pairedTerminal = null;
        return findTerminal(world);
    }

    public BlockPos getPairedTerminalPos() {
        return pairedTerminal;
    }

    // --- Feed syncing (server side) ---

    public static final int SYNC_INTERVAL_TICKS = 10;

    /**
     * Server ticker (wired up by {@code ScreenBlock.getTicker}): pushes a
     * snapshot of the paired terminal's selected camera to every player who
     * can see this screen.
     */
    public static void serverTick(World world, BlockPos pos, BlockState state,
                                  ScreenBlockEntity screen) {
        if (++screen.syncCounter < SYNC_INTERVAL_TICKS) {
            return;
        }
        screen.syncCounter = 0;
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }

        if (--screen.ticksUntilRescan <= 0) {
            screen.ticksUntilRescan = RESCAN_INTERVAL;
            CameraTerminalBlockEntity found = screen.resolveTerminal(world);
            if (found != null) {
                screen.pairedTerminal = found.getPos();
            }
        }
        if (screen.pairedTerminal == null
                || !(world.getBlockEntity(screen.pairedTerminal)
                        instanceof CameraTerminalBlockEntity terminal)) {
            return;
        }

        CameraTerminalBlockEntity.BoundCamera current = terminal.getSelected(world);
        List<FeedBlip> blips = CameraViews.scanBlips(world, current.pos(), current.facing());
        FeedDataS2CPayload payload = new FeedDataS2CPayload(
                pos, current.facing().getId(), current.index(), current.count(), blips);
        PlayerLookup.tracking(serverWorld, pos)
                .forEach(player -> ServerPlayNetworking.send(player, payload));
    }

    // --- Client-side cache ---

    /** Client side: called from the network receiver on the main thread. */
    public void updateClientFeed(List<FeedBlip> blips, int facingId, int index, int count) {
        clientBlips.clear();
        clientBlips.addAll(blips);
        receiving = true;
        lastFacingId = facingId;
        lastIndex = index;
        lastCount = count;
    }

    public List<FeedBlip> getClientBlips() {
        return Collections.unmodifiableList(clientBlips);
    }

    public boolean isReceiving() {
        return receiving;
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

    // --- Persistence ---

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        if (pairedTerminal != null) {
            nbt.putLong("terminal", pairedTerminal.asLong());
        }
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        if (nbt.contains("terminal")) {
            pairedTerminal = BlockPos.fromLong(nbt.getLong("terminal"));
        } else {
            pairedTerminal = null;
        }
    }
}
