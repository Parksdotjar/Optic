//? if >=26.2 {
/*package dev.optic.compat.mixin;
import dev.optic.OpticClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
public abstract class MinecraftCaptureMixin {
    @Inject(method="renderFrame",at=@At("HEAD")) private void optic$begin(boolean renderLevel,CallbackInfo ci) { dev.optic.compat.CleanCapture.begin(); }
//? if >=26.3 {
    /^@Inject(method="renderFrame",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/GameRenderer;render()V",shift=At.Shift.AFTER))
^///?} else {
    @Inject(method="renderFrame",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V",shift=At.Shift.AFTER))
//?}
    private void optic$capture(boolean renderLevel,CallbackInfo ci) { dev.optic.compat.CleanCapture.finish(); dev.optic.compat.SmokeTest.frame(); }
    @Inject(method="stop",at=@At("HEAD"),cancellable=true)
    private void optic$stop(CallbackInfo ci) { if(OpticClient.manager!=null && OpticClient.manager.deferExit()) ci.cancel(); }
    @Inject(method="close",at=@At("HEAD"),cancellable=true)
    private void optic$close(CallbackInfo ci) { if(OpticClient.manager!=null && OpticClient.manager.deferExit()) ci.cancel(); }
}

*///?}
