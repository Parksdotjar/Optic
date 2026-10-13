package dev.optic.compat.mixin;
import dev.optic.compat.CleanCapture;
import dev.optic.compat.CaptureLayerAdapter.Vanilla;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;

@Mixin(Gui.class)
public abstract class GuiLayersMixin {
    @WrapMethod(method="render") private void hud(GuiGraphics g,DeltaTracker t,Operation<Void> original) { int scope=Vanilla.HUD.enter(); try { original.call(g,t); } finally { CleanCapture.exit(scope); } }
    @WrapMethod(method="renderChat") private void chat(GuiGraphics g,DeltaTracker t,Operation<Void> original) { int scope=Vanilla.CHAT.enter(); try { original.call(g,t); } finally { CleanCapture.exit(scope); } }
    @WrapMethod(method="renderScoreboardSidebar") private void scoreboard(GuiGraphics g,DeltaTracker t,Operation<Void> original) { int scope=Vanilla.SCOREBOARD.enter(); try { original.call(g,t); } finally { CleanCapture.exit(scope); } }
    @WrapMethod(method="renderBossOverlay") private void bosses(GuiGraphics g,DeltaTracker t,Operation<Void> original) { int scope=Vanilla.BOSSES.enter(); try { original.call(g,t); } finally { CleanCapture.exit(scope); } }
    @WrapMethod(method="renderOverlayMessage") private void action(GuiGraphics g,DeltaTracker t,Operation<Void> original) { int scope=Vanilla.ACTION_BAR.enter(); try { original.call(g,t); } finally { CleanCapture.exit(scope); } }
    @WrapMethod(method="renderDebugOverlay") private void debug(GuiGraphics g,Operation<Void> original) { int scope=Vanilla.DEBUG.enter(); try { original.call(g); } finally { CleanCapture.exit(scope); } }
}
