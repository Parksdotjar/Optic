package dev.optic.compat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.optic.capture.CapturePolicy.Element;
import dev.optic.compat.CleanCapture;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ChatComponent.class)
public abstract class ChatLayerMixin {
    @WrapMethod(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V")
    private void optic$chat(GuiGraphicsExtractor g, Font font, int tick, int mx, int my, ChatComponent.DisplayMode mode, boolean hidden, Operation<Void> original) {
        int scope = CleanCapture.enter(Element.CHAT);
        try { original.call(g, font, tick, mx, my, mode, hidden); } finally { CleanCapture.exit(scope); }
    }
}
