package dev.optic.compat;

import dev.optic.OpticClient;
import dev.optic.capture.CapturePolicy;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.opengl.GlTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.state.GuiRenderState;
import static org.lwjgl.opengl.GL33C.*;
import com.mojang.blaze3d.opengl.GlStateManager;

/** Frame-local GUI command mirror. A clean GUI pass uses a private GPU target, never a second world render. */
public final class CleanCapture {
    public static final GuiRenderState CLEAN=new GuiRenderState();
    public static boolean collecting, rendering, handled;
    public static GuiRenderState source;
    private static CapturePolicy policy;
    private static boolean cleanFrame;
    private static int excluded;
    private static TextureTarget target;
    private static int readFbo,drawFbo;
    private static final long TEST_FAILURE_AFTER=net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()?Long.getLong("optic.cleanFailureAfter",Long.MAX_VALUE):Long.MAX_VALUE;
    public static long cleanFrames,gpuCopies,extraGuiPasses;
    public static final dev.optic.recording.TimingHistogram cost=new dev.optic.recording.TimingHistogram();
    public static void begin() {
        collecting=false; rendering=false; handled=false; source=null; excluded=0; cleanFrame=false;
        var manager=OpticClient.manager;
        if(manager!=null) manager.renderTick();
        if(manager==null || !manager.recording()) { release(); return; }
        policy=OpticClient.config.capturePolicy();
        cleanFrame=policy.clean();
        if(cleanFrame && manager.captureDue() && manager.captureReady()) { CLEAN.reset(); collecting=true; }
        if(!cleanFrame) release();
    }
    public static int enter(CapturePolicy.Element element) { int previous=excluded; if(collecting) { if(element==CapturePolicy.Element.INDICATOR) excluded=policy.includes(element)?0:1; else if(!policy.includes(element)) excluded++; } return previous; }
    public static void exit(int previous) { excluded=previous; }
    public static boolean mirror(GuiRenderState state) {
        return collecting && !rendering && excluded==0 && state==source;
    }
    public static void attach(GuiRenderState state) { if(collecting && !rendering && state!=CLEAN && source==null) source=state; }
    public static RenderTarget overrideTarget() { return rendering?target:null; }
    public static void prepareTarget() {
        if(cleanFrames>=TEST_FAILURE_AFTER) throw new IllegalStateException("Injected clean-capture test failure");
        var main=Minecraft.getInstance().getMainRenderTarget();
        if(!(main.getColorTexture() instanceof GlTexture color)) throw new IllegalStateException("Clean capture requires OpenGL");
        if((long)main.width*main.height*8>256L*1024*1024) throw new IllegalStateException("Clean capture exceeds its 256 MiB GPU target budget");
        if(target==null || target.width!=main.width || target.height!=main.height) {
            release(); target=new TextureTarget("Optic clean capture",main.width,main.height,true);
            readFbo=glGenFramebuffers(); drawFbo=glGenFramebuffers();
        }
        int read=glGetInteger(GL_READ_FRAMEBUFFER_BINDING),draw=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING); boolean scissor=glIsEnabled(GL_SCISSOR_TEST);
        try {
            GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER,readFbo); glFramebufferTexture2D(GL_READ_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,color.glId(),0); glReadBuffer(GL_COLOR_ATTACHMENT0);
            GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER,drawFbo); glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,((GlTexture)target.getColorTexture()).glId(),0); glDrawBuffer(GL_COLOR_ATTACHMENT0);
            if(glCheckFramebufferStatus(GL_READ_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE || glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("Clean capture framebuffer incomplete");
            GlStateManager._disableScissorTest(); glBlitFramebuffer(0,0,main.width,main.height,0,0,main.width,main.height,GL_COLOR_BUFFER_BIT,GL_NEAREST); gpuCopies++;
            com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(target.getDepthTexture(),1);
        } finally { GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER,read); GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER,draw); if(scissor) GlStateManager._enableScissorTest(); }
    }
    public static void capture() { OpticClient.manager.frame(); cleanFrames++; handled=true; }
    public static void failed(Throwable error) {
        handled=true; collecting=false;
        OpticClient.LOG.warn("Clean capture stopped rather than recording excluded content",error);
        OpticClient.manager.captureFailed(new java.io.IOException("Clean capture failed",error));
    }
    public static void finish() {
        if(OpticClient.manager!=null && OpticClient.manager.recording() && cleanFrame) {
            if(collecting && !handled) failed(new IllegalStateException("GUI composition hook did not execute"));
        } else if(OpticClient.manager!=null) OpticClient.manager.frame();
        if(source!=null) CLEAN.reset();
        collecting=false; source=null;
    }
    public static String diagnostics() { return "clean_frames="+cleanFrames+" gpu_copies="+gpuCopies+" extra_gui_passes="+extraGuiPasses+" clean_p95_ms="+cost.percentileMs(.95)+" clean_target_mib="+(target==null?0:(long)target.width*target.height*8/1048576); }
    public static void release() { if(target!=null) { target.destroyBuffers(); target=null; } if(readFbo!=0) glDeleteFramebuffers(readFbo); if(drawFbo!=0) glDeleteFramebuffers(drawFbo); readFbo=drawFbo=0; }
}
