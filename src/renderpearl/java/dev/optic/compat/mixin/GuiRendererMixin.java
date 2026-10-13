package dev.optic.compat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.optic.compat.CleanCapture;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.*;

@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
    @Shadow @Final @Mutable private GuiRenderState renderState;

    @WrapMethod(method = "render")
    private void optic$compose(Operation<Void> original) {
        if (CleanCapture.collecting && !CleanCapture.rendering && renderState == CleanCapture.source) {
            long start = System.nanoTime();
            GuiRenderState normal = renderState;
            try {
                CleanCapture.prepareTarget();
                // These are root GUI state, not submitted elements; preserve menu panorama/clear behavior.
                CleanCapture.CLEAN.panoramaRenderState = normal.panoramaRenderState;
                CleanCapture.CLEAN.clearColorOverride = normal.clearColorOverride;
                CleanCapture.CLEAN.isHudHidden = normal.isHudHidden;
                CleanCapture.rendering = true;
                renderState = CleanCapture.CLEAN;
                original.call();
                CleanCapture.extraGuiPasses++;
                CleanCapture.capture();
            } catch (Throwable error) {
                CleanCapture.failed(error);
            } finally {
                renderState = normal;
                CleanCapture.rendering = false;
                CleanCapture.collecting = false;
                CleanCapture.cost.add(System.nanoTime() - start);
            }
        }
        original.call();
    }
}
