package com.matissjurevics.icyou.mixin;

import com.matissjurevics.icyou.render.scene.SceneChangeJournal;

import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.LightType;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerChunkManager.class)
public abstract class ServerChunkManagerMixin {

    @Inject(method = "onLightUpdate", at = @At("HEAD"))
    private void icyou$recordSceneLight(LightType type, ChunkSectionPos position,
                                        CallbackInfo callback) {
        ServerChunkManager manager = (ServerChunkManager) (Object) this;
        SceneChangeJournal.recordLight((ServerWorld) manager.getWorld(),
                position.toChunkPos());
    }
}
