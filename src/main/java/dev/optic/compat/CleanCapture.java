//? if >=26.2 {
/*package dev.optic.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import dev.optic.OpticClient;
import dev.optic.capture.CapturePolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.gui.GuiRenderState;

/^*
 * Builds the recorded GUI in a private RenderPearl target. The world is copied once on the GPU;
 * it is never rendered a second time and no pixels are read back synchronously here.
 ^/
public final class CleanCapture {
    public static final GuiRenderState CLEAN = new GuiRenderState();
    public static boolean collecting, rendering, handled;
    public static GuiRenderState source;
    private static CapturePolicy policy;
    private static boolean cleanFrame;
    private static int excluded;
    private static TextureTarget target;
    private static final long TEST_FAILURE_AFTER = net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()
        ? Long.getLong("optic.cleanFailureAfter", Long.MAX_VALUE) : Long.MAX_VALUE;
    public static long cleanFrames, gpuCopies, extraGuiPasses;
    public static final dev.optic.recording.TimingHistogram cost = new dev.optic.recording.TimingHistogram();

    private CleanCapture() {}

    public static void begin() {
        collecting = false;
        rendering = false;
        handled = false;
        source = null;
        excluded = 0;
        cleanFrame = false;
        var manager = OpticClient.manager;
        if (manager != null) manager.renderTick();
        if (manager == null || !manager.recording()) {
            release();
            return;
        }
        policy = OpticClient.config.capturePolicy();
        cleanFrame = policy.clean();
        if (cleanFrame && manager.captureDue() && manager.captureReady()) {
            CLEAN.reset();
            collecting = true;
        }
        if (!cleanFrame) release();
    }

    public static int enter(CapturePolicy.Element element) {
        int previous = excluded;
        if (collecting) {
            if (element == CapturePolicy.Element.INDICATOR) excluded = policy.includes(element) ? 0 : 1;
            else if (!policy.includes(element)) excluded++;
        }
        return previous;
    }

    public static void exit(int previous) { excluded = previous; }

    public static boolean mirror(GuiRenderState state) {
        return collecting && !rendering && excluded == 0 && state == source;
    }

    public static void attach(GuiRenderState state) {
        if (collecting && !rendering && state != CLEAN && source == null) source = state;
    }

    public static RenderTarget overrideTarget(RenderTarget original) {
        return rendering && target != null ? target : original;
    }

    public static void prepareTarget() {
        if (cleanFrames >= TEST_FAILURE_AFTER) throw new IllegalStateException("Injected clean-capture test failure");
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        long bytes = (long) main.width * main.height * 8;
        if (bytes > 256L * 1024 * 1024) throw new IllegalStateException("Clean capture exceeds its 256 MiB GPU target budget");
        if (target == null || target.width != main.width || target.height != main.height) {
            release();
            //? if >=26.3 {
            /^target = new TextureTarget("Optic clean capture", main.width, main.height,
                main.getColorTexture().getFormat(), main.hasDepth() ? main.getDepthTexture().getFormat() : null);
            ^///?} else {
            target = new TextureTarget("Optic clean capture",main.width,main.height,main.useDepth,main.getColorTexture().getFormat());
            //?}
        }
        //? if >=26.3 {
        /^target.copyColorFrom(main);
        if (main.hasDepth()) target.copyDepthFrom(main);
        ^///?} else {
        var encoder=com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(main.getColorTexture(),target.getColorTexture(),0,0,0,0,0,main.width,main.height);
        if(main.useDepth) encoder.copyTextureToTexture(main.getDepthTexture(),target.getDepthTexture(),0,0,0,0,0,main.width,main.height);
        encoder.submit();
        //?}
        gpuCopies++;
    }

    public static void capture() {
        OpticClient.manager.frame();
        cleanFrames++;
        handled = true;
    }

    public static void failed(Throwable error) {
        handled = true;
        collecting = false;
        OpticClient.LOG.warn("Clean capture stopped rather than recording excluded content", error);
        OpticClient.manager.captureFailed(new java.io.IOException("Clean capture failed", error));
    }

    public static void finish() {
        if (OpticClient.manager != null && OpticClient.manager.recording() && cleanFrame) {
            if (collecting && !handled) failed(new IllegalStateException("GUI composition hook did not execute"));
        } else if (OpticClient.manager != null) {
            OpticClient.manager.frame();
        }
        if (source != null) CLEAN.reset();
        collecting = false;
        source = null;
    }

    public static String diagnostics() {
        return "clean_frames=" + cleanFrames + " gpu_copies=" + gpuCopies + " extra_gui_passes=" + extraGuiPasses
            + " clean_p95_ms=" + cost.percentileMs(.95) + " clean_target_mib="
            + (target == null ? 0 : (long) target.width * target.height * 8 / 1048576);
    }

    public static void release() {
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
    }
}

*///?}
