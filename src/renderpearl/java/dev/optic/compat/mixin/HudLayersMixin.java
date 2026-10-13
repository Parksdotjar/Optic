package dev.optic.compat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.optic.compat.CaptureLayerAdapter.Vanilla;
import dev.optic.compat.CleanCapture;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Hud.class)
public abstract class HudLayersMixin {
    @WrapMethod(method = "extractRenderState") private void hud(GuiGraphicsExtractor g, DeltaTracker t, Operation<Void> original) { int s=Vanilla.HUD.enter(); try { original.call(g,t); } finally { CleanCapture.exit(s); } }
    @WrapMethod(method = "extractChat") private void chat(GuiGraphicsExtractor g, DeltaTracker t, Operation<Void> original) { int s=Vanilla.CHAT.enter(); try { original.call(g,t); } finally { CleanCapture.exit(s); } }
    @WrapMethod(method = "extractScoreboardSidebar") private void scoreboard(GuiGraphicsExtractor g, DeltaTracker t, Operation<Void> original) { int s=Vanilla.SCOREBOARD.enter(); try { original.call(g,t); } finally { CleanCapture.exit(s); } }
    @WrapMethod(method = "extractBossOverlay") private void bosses(GuiGraphicsExtractor g, DeltaTracker t, Operation<Void> original) { int s=Vanilla.BOSSES.enter(); try { original.call(g,t); } finally { CleanCapture.exit(s); } }
    @WrapMethod(method = "extractOverlayMessage") private void action(GuiGraphicsExtractor g, DeltaTracker t, Operation<Void> original) { int s=Vanilla.ACTION_BAR.enter(); try { original.call(g,t); } finally { CleanCapture.exit(s); } }
    @WrapMethod(method = "extractDebugOverlay") private void debug(GuiGraphicsExtractor g, Operation<Void> original) { int s=Vanilla.DEBUG.enter(); try { original.call(g); } finally { CleanCapture.exit(s); } }
}
