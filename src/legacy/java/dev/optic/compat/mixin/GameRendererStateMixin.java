package dev.optic.compat.mixin;

import dev.optic.compat.CleanCapture;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bind only the game's main GUI command stream, never an unrelated mod's temporary GUI. */
@Mixin(GameRenderer.class)
public abstract class GameRendererStateMixin {
    @Shadow @Final private GuiRenderState guiRenderState;
    @Inject(method="render",at=@At("HEAD"))
    private void optic$source(DeltaTracker tracker,boolean renderLevel,CallbackInfo ci) { CleanCapture.attach(guiRenderState); }
}
