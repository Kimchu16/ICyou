package com.matissjurevics.icyou.registry;

import com.matissjurevics.icyou.ICyouMod;
import com.matissjurevics.icyou.terminal.CameraTerminalBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** All block entity types. Must register after {@link ModBlocks}. */
public final class ModBlockEntities {

    private ModBlockEntities() {}

    public static final BlockEntityType<CameraTerminalBlockEntity> CAMERA_TERMINAL =
            FabricBlockEntityTypeBuilder.create(
                            CameraTerminalBlockEntity::new, ModBlocks.CAMERA_TERMINAL)
                    .build();

    public static void register() {
        Registry.register(Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(ICyouMod.MOD_ID, "camera_terminal"), CAMERA_TERMINAL);
    }
}
