package com.matissjurevics.icyou.client.mixin;

import com.matissjurevics.icyou.client.render.RttFeedManager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.WorldRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps vanilla entity/transparency sub-passes inside the active camera FBO. */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/MinecraftClient;getFramebuffer()"
                    + "Lnet/minecraft/client/gl/Framebuffer;"))
    private Framebuffer icyou$useCameraFramebuffer(MinecraftClient client) {
        Framebuffer cameraTarget = RttFeedManager.currentRenderTarget();
        return cameraTarget != null ? cameraTarget : client.getFramebuffer();
    }
}
