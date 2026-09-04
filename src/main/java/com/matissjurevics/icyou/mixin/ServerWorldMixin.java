package com.matissjurevics.icyou.mixin;

import com.matissjurevics.icyou.tick.SupplementalRandomTickLifecycle;
import com.matissjurevics.icyou.render.scene.SceneChangeJournal;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.WorldChunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin {

    @Inject(method = "tickChunk", at = @At("HEAD"))
    private void icyou$recordVanillaTick(WorldChunk chunk, int randomTickSpeed,
                                         CallbackInfo callback) {
        SupplementalRandomTickLifecycle.recordVanillaTick(
                (ServerWorld) (Object) this, chunk.getPos());
    }

    @Inject(method = "updateListeners", at = @At("HEAD"))
    private void icyou$recordSceneBlock(BlockPos position, BlockState oldState,
                                        BlockState newState, int flags,
                                        CallbackInfo callback) {
        SceneChangeJournal.recordBlock((ServerWorld) (Object) this, position);
    }
}
