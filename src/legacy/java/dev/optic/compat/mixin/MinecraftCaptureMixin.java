package dev.optic.compat.mixin;
import dev.optic.compat.SmokeTest;
import dev.optic.OpticClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftCaptureMixin {
    @Inject(method="runTick",at=@At("HEAD")) private void optic$begin(boolean renderLevel,CallbackInfo ci) { dev.optic.compat.CleanCapture.begin(); }
    @Inject(method="getMainRenderTarget",at=@At("HEAD"),cancellable=true) private void optic$target(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<com.mojang.blaze3d.pipeline.RenderTarget> cir) { var target=dev.optic.compat.CleanCapture.overrideTarget(); if(target!=null) cir.setReturnValue(target); }
    @Inject(method="runTick",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/platform/Window;updateDisplay(Lcom/mojang/blaze3d/TracyFrameCapture;)V"))
    private void optic$capture(boolean renderLevel,CallbackInfo ci) { if(OpticClient.manager!=null) { dev.optic.compat.CleanCapture.finish(); SmokeTest.frame(); dev.optic.compat.SettingsSmokeTest.frame(); dev.optic.compat.CleanWorldSmokeTest.frame(); dev.optic.compat.PerformanceRun.frame(); dev.optic.compat.ReplaySmokeTest.frame(); dev.optic.compat.WorkflowSmokeTest.frame(); } }
    @Inject(method="stop",at=@At("HEAD"),cancellable=true)
    private void optic$deferExit(CallbackInfo ci) { if(OpticClient.manager!=null && OpticClient.manager.deferExit()) ci.cancel(); }
    @Inject(method="close",at=@At("HEAD"),cancellable=true)
    private void optic$abnormalClose(CallbackInfo ci) {
        // A crash can bypass stop(). Do not destroy a GL context while a worker reads
        // its mapping. Leave native cleanup to process teardown and recover fragments.
        if(OpticClient.manager!=null && OpticClient.manager.deferExit()) {
            OpticClient.LOG.warn("Abnormal shutdown during recording; retaining context until process teardown for mapped-buffer safety"); ci.cancel();
        }
    }
}
