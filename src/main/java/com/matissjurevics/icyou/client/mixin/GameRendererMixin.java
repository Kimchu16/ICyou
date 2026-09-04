package com.matissjurevics.icyou.client.mixin;

import com.matissjurevics.icyou.client.render.RttFeedManager;
import com.matissjurevics.icyou.client.agent.RemoteOffscreenRenderer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs one off-screen camera pass before Minecraft restores the player frustum. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "renderWorld", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/WorldRenderer;setupFrustum("
                    + "Lnet/minecraft/util/math/Vec3d;Lorg/joml/Matrix4f;"
                    + "Lorg/joml/Matrix4f;)V", shift = At.Shift.BEFORE))
    private void icyou$renderCameraFeeds(RenderTickCounter tickCounter, CallbackInfo ci) {
        RttFeedManager.renderFrame(MinecraftClient.getInstance(),
                (GameRenderer) (Object) this, tickCounter);
        RemoteOffscreenRenderer.renderFrame(MinecraftClient.getInstance(),
                (GameRenderer) (Object) this, tickCounter);
    }
}
