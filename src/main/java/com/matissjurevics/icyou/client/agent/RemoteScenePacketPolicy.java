package com.matissjurevics.icyou.client.agent;

import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityAttachS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityAttributesS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;

/** Restricts remotely supplied vanilla packets to scene-state mutations. */
public final class RemoteScenePacketPolicy {

    private RemoteScenePacketPolicy() {
    }

    public static boolean isAllowed(Packet<?> packet) {
        return packet instanceof ChunkDataS2CPacket
                || packet instanceof BlockUpdateS2CPacket
                || packet instanceof BlockEntityUpdateS2CPacket
                || packet instanceof LightUpdateS2CPacket
                || packet instanceof EntitySpawnS2CPacket
                || packet instanceof EntitiesDestroyS2CPacket
                || packet instanceof EntityTrackerUpdateS2CPacket
                || packet instanceof EntityVelocityUpdateS2CPacket
                || packet instanceof EntityAttributesS2CPacket
                || packet instanceof EntityEquipmentUpdateS2CPacket
                || packet instanceof EntityPassengersSetS2CPacket
                || packet instanceof EntityAttachS2CPacket;
    }
}
