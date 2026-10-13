package dev.optic.compat.mixin;
import dev.optic.compat.CleanCapture;
import dev.optic.capture.CapturePolicy.Element;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.client.gui.components.SubtitleOverlay;
import net.minecraft.client.gui.GuiGraphics;
@Mixin(SubtitleOverlay.class)
public abstract class SubtitleMixin {
    @WrapMethod(method="render") private void subtitles(GuiGraphics g,Operation<Void> original) { int scope=CleanCapture.enter(Element.SUBTITLES); try { original.call(g); } finally { CleanCapture.exit(scope); } }
}
