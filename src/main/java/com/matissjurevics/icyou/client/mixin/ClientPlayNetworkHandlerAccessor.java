package com.matissjurevics.icyou.client.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Temporarily redirects vanilla scene packet handling into an isolated world. */
@Mixin(ClientPlayNetworkHandler.class)
public interface ClientPlayNetworkHandlerAccessor {

    @Accessor("world")
    ClientWorld icyou$getWorld();

    @Accessor("world")
    void icyou$setWorld(ClientWorld world);

    @Invoker("createEntity")
    Entity icyou$createEntity(EntitySpawnS2CPacket packet);
}
