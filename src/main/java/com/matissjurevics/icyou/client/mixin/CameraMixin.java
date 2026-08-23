package com.matissjurevics.icyou.client.mixin;

import com.matissjurevics.icyou.client.CameraViewController;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While viewing a security feed, detaches the player's camera and anchors it
 * to the linked security camera's position and rotation.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void setPos(Vec3d pos);

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void icyou$overrideView(BlockView area, Entity focusedEntity, boolean thirdPerson,
                                    boolean inverseView, float tickDelta, CallbackInfo ci) {
        CameraViewController.View view = CameraViewController.current();
        if (view == null) {
            return;
        }
        this.setRotation(view.yaw(), view.pitch());
        this.setPos(view.pos());
        ci.cancel();
    }
}
