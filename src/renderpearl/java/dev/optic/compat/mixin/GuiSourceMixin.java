package dev.optic.compat.mixin;

import dev.optic.compat.CleanCapture;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiSourceMixin {
    @Shadow @Final private GuiRenderState guiRenderState;

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void optic$attach(DeltaTracker tracker, boolean renderLevel, boolean renderDebug, CallbackInfo ci) {
        CleanCapture.attach(guiRenderState);
    }
}
