package dev.optic.compat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.optic.capture.CapturePolicy.Element;
import dev.optic.compat.CleanCapture;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.SubtitleOverlay;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SubtitleOverlay.class)
public abstract class SubtitleMixin {
    @WrapMethod(method = "extractRenderState")
    private void optic$subtitles(GuiGraphicsExtractor g, Operation<Void> original) {
        int scope = CleanCapture.enter(Element.SUBTITLES);
        try { original.call(g); } finally { CleanCapture.exit(scope); }
    }
}
