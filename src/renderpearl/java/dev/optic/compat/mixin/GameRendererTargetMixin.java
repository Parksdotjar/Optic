package dev.optic.compat.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.optic.compat.CleanCapture;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererTargetMixin {
    @Inject(method = "mainRenderTarget", at = @At("RETURN"), cancellable = true)
    private void optic$target(CallbackInfoReturnable<RenderTarget> cir) {
        cir.setReturnValue(CleanCapture.overrideTarget(cir.getReturnValue()));
    }
}
