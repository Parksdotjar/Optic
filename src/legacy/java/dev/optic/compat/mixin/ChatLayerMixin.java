package dev.optic.compat.mixin;

import dev.optic.capture.CapturePolicy.Element;
import dev.optic.compat.CleanCapture;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;

/** Also covers chat history rendered by an open ChatScreen. The input field remains part of the screen. */
@Mixin(ChatComponent.class)
public abstract class ChatLayerMixin {
    @WrapMethod(method="render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V")
    private void optic$chat(GuiGraphics g,Font font,int tick,int mx,int my,boolean focused,boolean hidden,Operation<Void> original) {
        int scope=CleanCapture.enter(Element.CHAT);
        try { original.call(g,font,tick,mx,my,focused,hidden); } finally { CleanCapture.exit(scope); }
    }
}
