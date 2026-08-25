package com.matissjurevics.icyou.client.mixin;

import com.matissjurevics.icyou.client.render.RttFeedManager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Routes every nested vanilla render layer to the active security-camera FBO. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Inject(method = "getFramebuffer", at = @At("HEAD"), cancellable = true)
    private void icyou$getCameraFramebuffer(CallbackInfoReturnable<Framebuffer> cir) {
        Framebuffer cameraTarget = RttFeedManager.currentRenderTarget();
        if (cameraTarget != null) {
            cir.setReturnValue(cameraTarget);
        }
    }
}
