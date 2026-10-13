package dev.optic.compat.mixin;
import dev.optic.compat.CleanCapture;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.spongepowered.asm.mixin.*;

@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
    @Shadow @Final @Mutable private GuiRenderState renderState;
    @WrapMethod(method="render") private void optic$compose(GpuBufferSlice fog,Operation<Void> original) {
        if(CleanCapture.collecting && !CleanCapture.rendering && renderState==CleanCapture.source) {
            long start=System.nanoTime(); var normal=renderState;
            try {
                CleanCapture.prepareTarget(); CleanCapture.rendering=true; renderState=CleanCapture.CLEAN;
                original.call(fog); CleanCapture.extraGuiPasses++;
                CleanCapture.capture();
            } catch(Throwable error) { CleanCapture.failed(error); }
            finally { renderState=normal; CleanCapture.rendering=false; CleanCapture.collecting=false; CleanCapture.cost.add(System.nanoTime()-start); }
        }
        original.call(fog);
    }
}
