package com.matissjurevics.icyou.remote;

import com.matissjurevics.icyou.camera.CameraBlock;
import com.matissjurevics.icyou.device.CameraRef;
import com.matissjurevics.icyou.device.DeviceLocation;
import com.matissjurevics.icyou.device.GlobalDeviceRegistry;
import com.matissjurevics.icyou.device.ScreenRef;
import com.matissjurevics.icyou.registry.ModDataComponentTypes;
import com.matissjurevics.icyou.screen.ScreenBlock;
import com.matissjurevics.icyou.screen.ScreenBlockEntity;
import com.matissjurevics.icyou.terminal.CameraTerminalBlock;
import com.matissjurevics.icyou.terminal.CameraTerminalBlockEntity;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Setup Remote: pick up a camera (use on camera) or a screen link (use on
 * screen), then deliver it to a camera terminal.
 */
public class SetupRemoteItem extends Item {

    public SetupRemoteItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        ItemStack stack = context.getStack();
        var player = context.getPlayer();

        if (!world.isClient) {
            upgradeLegacyLinks(stack, (ServerWorld) world);
        }

        // --- Pick up a camera link ---
        if (world.getBlockState(pos).getBlock() instanceof CameraBlock) {
            if (!world.isClient) {
                ServerWorld serverWorld = (ServerWorld) world;
                GlobalDeviceRegistry registry = GlobalDeviceRegistry.get(serverWorld.getServer());
                DeviceLocation location = new DeviceLocation(serverWorld.getRegistryKey(), pos);
                CameraRef carried = stack.get(ModDataComponentTypes.LINKED_CAMERA);
                CameraRef ref = registry.deviceAt(location)
                        .filter(CameraRef.class::isInstance).map(CameraRef.class::cast)
                        .orElseGet(() -> carried != null
                                && registry.cameraTombstone(carried.deviceId()).isPresent()
                                ? new CameraRef(carried.deviceId(),
                                        serverWorld.getRegistryKey(), pos)
                                : new CameraRef(UUID.randomUUID(),
                                        serverWorld.getRegistryKey(), pos));
                stack.set(ModDataComponentTypes.LINKED_CAMERA, ref);
                stack.remove(ModDataComponentTypes.LEGACY_LINKED_CAMERA);
                if (player != null) {
                    player.sendMessage(Text.literal("Camera link picked up at " + pos.toShortString()), true);
                }
            }
            return ActionResult.SUCCESS;
        }

        // --- Pick up a screen link ---
        if (world.getBlockState(pos).getBlock() instanceof ScreenBlock screenBlock) {
            if (!world.isClient) {
                BlockPos controllerPos = screenBlock.getControllerPos(
                        world.getBlockState(pos), pos);
                ServerWorld serverWorld = (ServerWorld) world;
                GlobalDeviceRegistry registry = GlobalDeviceRegistry.get(serverWorld.getServer());
                ScreenRef ref = registry.deviceAt(new DeviceLocation(
                                serverWorld.getRegistryKey(), controllerPos))
                        .filter(ScreenRef.class::isInstance).map(ScreenRef.class::cast)
                        .orElseGet(() -> new ScreenRef(UUID.randomUUID(),
                                serverWorld.getRegistryKey(), controllerPos));
                stack.set(ModDataComponentTypes.LINKED_SCREEN, ref);
                stack.remove(ModDataComponentTypes.LEGACY_LINKED_SCREEN);
                if (player != null) {
                    player.sendMessage(Text.literal("Screen link picked up at "
                            + controllerPos.toShortString()), true);
                }
            }
            return ActionResult.SUCCESS;
        }

        // --- Deliver to a terminal ---
        if (world.getBlockState(pos).getBlock() instanceof CameraTerminalBlock) {
            if (!world.isClient) {
                ServerWorld serverWorld = (ServerWorld) world;
                GlobalDeviceRegistry registry = GlobalDeviceRegistry.get(serverWorld.getServer());
                CameraRef cam = stack.get(ModDataComponentTypes.LINKED_CAMERA);
                ScreenRef scr = stack.get(ModDataComponentTypes.LINKED_SCREEN);
                if (player == null) {
                    return ActionResult.PASS;
                }
                if (cam != null) {
                    if (!(world.getBlockEntity(pos) instanceof CameraTerminalBlockEntity terminal)) {
                        return ActionResult.FAIL;
                    }
                    var terminalRef = terminal.initialize(serverWorld, player.getUuid());
                    if (!canManage(registry, terminalRef.deviceId(), player)) {
                        player.sendMessage(Text.literal(
                                "Only the terminal owner or an operator can link devices."), false);
                        return ActionResult.FAIL;
                    }
                    var existing = registry.camera(cam.deviceId());
                    if (existing.isPresent()) {
                        if (!existing.get().ref().equals(cam)) {
                            return ActionResult.FAIL;
                        }
                        registry.relinkCamera(cam.deviceId(), terminalRef.deviceId());
                    } else if (registry.cameraTombstone(cam.deviceId()).isPresent()) {
                        if (!registry.hasRegisteredCameraCapacity()) {
                            player.sendMessage(Text.literal(
                                    "This server's registered camera limit has been reached."),
                                    false);
                            return ActionResult.FAIL;
                        }
                        var tombstone = registry.cameraTombstone(cam.deviceId()).orElseThrow();
                        if (!tombstone.terminalId().equals(terminalRef.deviceId())) {
                            player.sendMessage(Text.literal(
                                    "Restore this camera through its original terminal."), false);
                            return ActionResult.FAIL;
                        }
                        registry.restoreCamera(cam.deviceId(), cam);
                    } else {
                        if (!registry.hasRegisteredCameraCapacity()) {
                            player.sendMessage(Text.literal(
                                    "This server's registered camera limit has been reached."),
                                    false);
                            return ActionResult.FAIL;
                        }
                        registry.registerCamera(cam, terminalRef.deviceId(), shortName("CAM", cam.deviceId()));
                    }
                    stack.remove(ModDataComponentTypes.LINKED_CAMERA);
                    player.sendMessage(Text.literal("Camera linked as "
                            + registry.camera(cam.deviceId()).orElseThrow().name()), false);
                } else if (scr != null) {
                    if (!(world.getBlockEntity(pos) instanceof CameraTerminalBlockEntity terminal)) {
                        return ActionResult.FAIL;
                    }
                    var terminalRef = terminal.initialize(serverWorld, player.getUuid());
                    if (!canManage(registry, terminalRef.deviceId(), player)) {
                        player.sendMessage(Text.literal(
                                "Only the terminal owner or an operator can link devices."), false);
                        return ActionResult.FAIL;
                    }
                    var existing = registry.screen(scr.deviceId());
                    if (existing.isPresent()) {
                        if (!existing.get().ref().equals(scr)) {
                            return ActionResult.FAIL;
                        }
                        registry.relinkScreen(scr.deviceId(), terminalRef.deviceId());
                    } else {
                        registry.registerScreen(scr, terminalRef.deviceId(),
                                shortName("SCR", scr.deviceId()), Optional.empty());
                    }
                    ServerWorld screenWorld = serverWorld.getServer().getWorld(scr.dimension());
                    if (screenWorld != null && screenWorld.getBlockEntity(
                            scr.position()) instanceof ScreenBlockEntity screen) {
                        screen.setLink(scr, terminalRef.deviceId());
                    }
                    stack.remove(ModDataComponentTypes.LINKED_SCREEN);
                    player.sendMessage(Text.literal("Screen linked as "
                            + registry.screen(scr.deviceId()).orElseThrow().name()), false);
                } else {
                    player.sendMessage(Text.literal(
                            "Nothing to link — use the remote on a camera or screen first."), false);
                    return ActionResult.FAIL;
                }
            }
            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }

    private static String shortName(String prefix, UUID id) {
        return prefix + "-" + id.toString().substring(0, 8).toUpperCase();
    }

    private static boolean canManage(GlobalDeviceRegistry registry, UUID terminalId,
                                     net.minecraft.entity.player.PlayerEntity player) {
        return registry.canManageTerminal(terminalId, player.getUuid(),
                player instanceof ServerPlayerEntity serverPlayer
                        && serverPlayer.hasPermissionLevel(2));
    }

    private static void upgradeLegacyLinks(ItemStack stack, ServerWorld world) {
        GlobalDeviceRegistry registry = GlobalDeviceRegistry.get(world.getServer());
        BlockPos cameraPos = stack.get(ModDataComponentTypes.LEGACY_LINKED_CAMERA);
        if (stack.get(ModDataComponentTypes.LINKED_CAMERA) == null && cameraPos != null) {
            CameraRef ref = registry.deviceAt(new DeviceLocation(world.getRegistryKey(), cameraPos))
                    .filter(CameraRef.class::isInstance).map(CameraRef.class::cast)
                    .orElseGet(() -> new CameraRef(UUID.randomUUID(),
                            world.getRegistryKey(), cameraPos));
            stack.set(ModDataComponentTypes.LINKED_CAMERA, ref);
            stack.remove(ModDataComponentTypes.LEGACY_LINKED_CAMERA);
        }
        BlockPos screenPos = stack.get(ModDataComponentTypes.LEGACY_LINKED_SCREEN);
        if (stack.get(ModDataComponentTypes.LINKED_SCREEN) == null && screenPos != null) {
            ScreenRef ref = registry.deviceAt(new DeviceLocation(world.getRegistryKey(), screenPos))
                    .filter(ScreenRef.class::isInstance).map(ScreenRef.class::cast)
                    .orElseGet(() -> new ScreenRef(UUID.randomUUID(),
                            world.getRegistryKey(), screenPos));
            stack.set(ModDataComponentTypes.LINKED_SCREEN, ref);
            stack.remove(ModDataComponentTypes.LEGACY_LINKED_SCREEN);
        }
    }
}
