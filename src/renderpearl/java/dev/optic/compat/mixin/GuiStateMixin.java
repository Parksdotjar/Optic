package dev.optic.compat.mixin;

import dev.optic.compat.CleanCapture;
import net.minecraft.client.renderer.state.gui.*;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderState.class)
public abstract class GuiStateMixin {
    @Unique private boolean optic$mirror() { return CleanCapture.mirror((GuiRenderState) (Object) this); }
    @Inject(method = "addGuiElement", at = @At("HEAD")) private void element(GuiElementRenderState state, CallbackInfo ci) { if (optic$mirror()) CleanCapture.CLEAN.addGuiElement(state); }
    @Inject(method = "addText", at = @At("HEAD")) private void text(GuiTextRenderState state, CallbackInfo ci) { if (optic$mirror()) CleanCapture.CLEAN.addText(state); }
    @Inject(method = "addItem", at = @At("HEAD")) private void item(GuiItemRenderState state, CallbackInfo ci) { if (optic$mirror()) CleanCapture.CLEAN.addItem(state); }
    @Inject(method = "addPicturesInPictureState", at = @At("HEAD")) private void picture(PictureInPictureRenderState state, CallbackInfo ci) { if (optic$mirror()) CleanCapture.CLEAN.addPicturesInPictureState(state); }
    @Inject(method = "nextStratum", at = @At("HEAD")) private void stratum(CallbackInfo ci) { if (optic$mirror()) CleanCapture.CLEAN.nextStratum(); }
    @Inject(method = "blurBeforeThisStratum", at = @At("HEAD")) private void blur(CallbackInfo ci) { if (optic$mirror()) CleanCapture.CLEAN.blurBeforeThisStratum(); }
}
