package com.matissjurevics.icyou.client.agent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;

class RemoteScenePacketPolicyTest {

    @Test
    void allowsOnlyPacketsThatMutateRemoteSceneState() {
        assertTrue(RemoteScenePacketPolicy.isAllowed(new EntitiesDestroyS2CPacket(4)));
        assertFalse(RemoteScenePacketPolicy.isAllowed(new KeepAliveS2CPacket(10)));
    }
}
