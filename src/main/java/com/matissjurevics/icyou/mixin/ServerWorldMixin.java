package com.matissjurevics.icyou.mixin;

import com.matissjurevics.icyou.tick.SupplementalRandomTickLifecycle;
import com.matissjurevics.icyou.render.scene.SceneChangeJournal;
import com.matissjurevics.icyou.render.audio.AudioSceneJournal;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
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

    @Inject(method = "playSound", at = @At("HEAD"))
    private void icyou$recordPositionedSound(PlayerEntity source, double x, double y,
                                             double z,
                                             RegistryEntry<SoundEvent> sound,
                                             SoundCategory category, float volume,
                                             float pitch, long seed,
                                             CallbackInfo callback) {
        AudioSceneJournal.record((ServerWorld) (Object) this,
                source == null ? null : source.getUuid(), x, y, z, sound,
                category, volume, pitch, seed);
    }

    @Inject(method = "playSoundFromEntity", at = @At("HEAD"))
    private void icyou$recordEntitySound(PlayerEntity excluded, Entity source,
                                         RegistryEntry<SoundEvent> sound,
                                         SoundCategory category, float volume,
                                         float pitch, long seed,
                                         CallbackInfo callback) {
        AudioSceneJournal.record((ServerWorld) (Object) this, source.getUuid(),
                source.getX(), source.getY(), source.getZ(), sound, category,
                volume, pitch, seed);
    }
}
