package dev.optic.compat.mixin;
import dev.optic.compat.CleanCapture;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.render.state.*;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;

@Mixin(GuiRenderState.class)
public abstract class GuiStateMixin {
    @Unique private boolean optic$mirror() { return CleanCapture.mirror((GuiRenderState)(Object)this); }
    @Inject(method="submitGuiElement",at=@At("HEAD")) private void element(GuiElementRenderState state,CallbackInfo ci) { if(optic$mirror()) CleanCapture.CLEAN.submitGuiElement(state); }
    @Inject(method="submitText",at=@At("HEAD")) private void text(GuiTextRenderState state,CallbackInfo ci) { if(optic$mirror()) CleanCapture.CLEAN.submitText(state); }
    @Inject(method="submitItem",at=@At("HEAD")) private void item(GuiItemRenderState state,CallbackInfo ci) { if(optic$mirror()) CleanCapture.CLEAN.submitItem(state); }
    @Inject(method="submitPicturesInPictureState",at=@At("HEAD")) private void picture(PictureInPictureRenderState state,CallbackInfo ci) { if(optic$mirror()) CleanCapture.CLEAN.submitPicturesInPictureState(state); }
    @Inject(method="nextStratum",at=@At("HEAD")) private void stratum(CallbackInfo ci) { if(optic$mirror()) CleanCapture.CLEAN.nextStratum(); }
    @Inject(method="blurBeforeThisStratum",at=@At("HEAD")) private void blur(CallbackInfo ci) { if(optic$mirror()) CleanCapture.CLEAN.blurBeforeThisStratum(); }
}
