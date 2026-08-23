package com.matissjurevics.icyou.item;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.matissjurevics.icyou.camera.CameraBlock;
import com.matissjurevics.icyou.network.EnterCameraViewS2CPayload;
import com.matissjurevics.icyou.registry.ModDataComponentTypes;
import com.matissjurevics.icyou.terminal.CameraTerminalBlock;
import com.matissjurevics.icyou.terminal.CameraTerminalBlockEntity;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Portable Screen: sneak-use on a camera terminal to pair with it, then
 * right-click in the air to view the terminal's cameras — one press advances
 * to the next feed. Viewing is a detached camera, so you see what it sees.
 */
public class PortableScreenItem extends Item {

    /** Per-player cursor: each use shows the next feed in the terminal list. */
    private static final Map<UUID, Integer> VIEW_CURSORS = new HashMap<>();

    public PortableScreenItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        ItemStack stack = context.getStack();
        PlayerEntity player = context.getPlayer();

        if (world.getBlockState(pos).getBlock() instanceof CameraTerminalBlock) {
            if (!world.isClient) {
                stack.set(ModDataComponentTypes.LINKED_TERMINAL, pos.toImmutable());
                if (player != null) {
                    player.sendMessage(Text.literal(
                            "Portable screen paired with terminal "
                                    + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), true);
                }
            }
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (!world.isClient) {
            BlockPos terminalPos = stack.get(ModDataComponentTypes.LINKED_TERMINAL);
            if (terminalPos == null) {
                user.sendMessage(Text.literal(
                        "Not paired — sneak-use this screen on a camera terminal first."), false);
                return TypedActionResult.fail(stack);
            }
            if (!(world.getBlockEntity(terminalPos) instanceof CameraTerminalBlockEntity terminal)) {
                user.sendMessage(Text.literal("Paired terminal is out of range or gone."), false);
                return TypedActionResult.fail(stack);
            }
            List<BlockPos> cameras = terminal.getCameras();
            if (cameras.isEmpty()) {
                user.sendMessage(Text.literal("That terminal has no cameras."), false);
                return TypedActionResult.fail(stack);
            }

            // Advance this player's personal channel cursor.
            int index = VIEW_CURSORS.merge(user.getUuid(), 0, (old, none) -> old + 1)
                    % cameras.size();
            BlockPos cam = cameras.get(index);
            Direction facing = Direction.NORTH;
            if (world.getBlockState(cam).getBlock() instanceof CameraBlock camera) {
                facing = world.getBlockState(cam).get(CameraBlock.FACING);
            }

            ServerPlayNetworking.send((ServerPlayerEntity) user,
                    new EnterCameraViewS2CPayload(List.of(
                            new EnterCameraViewS2CPayload.CamRef(cam, facing.getId()))));

            user.sendMessage(Text.literal(String.format("Viewing CAM %d/%d [%s]",
                    index + 1, cameras.size(), facing.asString())), true);
        }
        return TypedActionResult.success(stack, world.isClient);
    }
}
