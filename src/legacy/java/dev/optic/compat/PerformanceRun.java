package dev.optic.compat;

import dev.optic.OpticClient;
import dev.optic.recording.RecordingManager;
import net.minecraft.client.Minecraft;
import java.nio.file.*;

/** Opt-in bounded raw frame trace. Use a disposable world copy and fixed run conditions.
 * All trace IO happens on a worker after measurement. Never part of regular recording. */
public final class PerformanceRun {
    private static final String MODE=net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()?System.getProperty("optic.benchmark",""):"";
    private static final long[] intervals=MODE.isEmpty()?null:new long[240_000];
    private static long loaded,warm,measure,last;
    private static int count;
    private static boolean requested,finished,overflow,unfocused;
    private static float yaw,pitch;
    private static int oldWidth,oldHeight;
    private static boolean oldFullscreen,oldPause;
    private static net.minecraft.client.InactivityFpsLimit oldLimit;
    public static void frame() {
        if(MODE.isEmpty() || finished) return;
        var mc=Minecraft.getInstance(); long now=System.nanoTime();
        if(mc.level==null || mc.player==null || mc.getOverlay()!=null) return;
        if(loaded==0) {
            loaded=now; yaw=mc.player.getYRot(); pitch=mc.player.getXRot(); mc.setScreen(null);
            oldWidth=mc.getWindow().getWidth(); oldHeight=mc.getWindow().getHeight(); oldFullscreen=mc.getWindow().isFullscreen();
            oldPause=mc.options.pauseOnLostFocus; oldLimit=mc.options.inactivityFpsLimit().get();
            mc.getWindow().setWindowed(1280,720);
            org.lwjgl.glfw.GLFW.glfwRestoreWindow(mc.getWindow().handle());
            mc.options.pauseOnLostFocus=false;
            mc.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
            org.lwjgl.glfw.GLFW.glfwFocusWindow(mc.getWindow().handle());
        }
        if(now-loaded<15_000_000_000L) return; // world/chunk warmup
        if(!requested) {
            requested=true; warm=now;
            if(MODE.equals("record")) {
                var c=OpticClient.config; dev.optic.config.RecordingPreset.BALANCED.apply(c);
                c.microphone=false; c.gameAudio=false; c.hud=false; c.completionScreen=false;
                c.colorMode=dev.optic.config.ColorMode.LEGACY; c.stopOnSustainedDuplicates=false; c.replayEnabled=false;
                c.captureHud=c.captureChat=c.captureSubtitles=c.captureScoreboard=c.captureBosses=c.captureActionBar=c.captureDebug=c.captureIndicator=true;
                OpticClient.manager.directory=mc.gameDirectory.toPath().resolve("v5-benchmark-videos");
                OpticClient.manager.toggle();
            } else if(!MODE.equals("idle") && !MODE.equals("obs")) throw new IllegalArgumentException("Benchmark mode must be idle, record or obs");
        }
        if(MODE.equals("record") && !OpticClient.manager.recording()) {
            if(now-warm>30_000_000_000L) { finished=true; restore(mc); OpticClient.LOG.error("OPTIC_BENCHMARK_FAILED: recorder did not start"); mc.stop(); }
            return;
        }
        if(now-warm<5_000_000_000L) return; // encoder/startup toast warmup
        if(measure==0) { measure=now; last=now; }
        else {
            if(count<intervals.length) intervals[count++]=now-last; else overflow=true;
            last=now;
        }
        double seconds=(now-measure)/1e9;
        mc.player.setYRot(yaw+(float)(45*Math.sin(seconds*Math.PI/15))); mc.player.setXRot(pitch);
        if(!mc.isWindowActive()) unfocused=true;
        if(seconds>=Math.max(5,Math.min(300,Integer.getInteger("optic.benchmarkSeconds",30)))) {
            finished=true; mc.player.setYRot(yaw); mc.player.setXRot(pitch);
            String label=System.getProperty("optic.benchmarkLabel",MODE).replaceAll("[^a-zA-Z0-9_-]","_");
            Path directory=mc.gameDirectory.toPath().resolve("v5-benchmark").resolve(label+"-"+System.currentTimeMillis());
            String environment="mode="+MODE+"\ngpu="+org.lwjgl.opengl.GL33C.glGetString(org.lwjgl.opengl.GL33C.GL_RENDERER)+"\ndriver="+org.lwjgl.opengl.GL33C.glGetString(org.lwjgl.opengl.GL33C.GL_VERSION)+"\nwindow="+mc.getWindow().getWidth()+"x"+mc.getWindow().getHeight()+"\nvsync="+mc.options.enableVsync().get()+"\nfps_cap="+mc.options.framerateLimit().get()+"\nunfocused="+unfocused+"\noverflow="+overflow+"\nsamples="+count+"\njava="+System.getProperty("java.version")+"\nos="+System.getProperty("os.name")+"\n";
            Thread writer=new Thread(() -> {
                try {
                    Files.createDirectories(directory); Files.writeString(directory.resolve("environment.txt"),environment);
                    try(var output=Files.newBufferedWriter(directory.resolve("frames.csv"))) { output.write("frame,ns\n"); for(int i=0;i<count;i++) output.write(i+","+intervals[i]+"\n"); }
                    OpticClient.LOG.info("OPTIC_BENCHMARK_FINISHED {}",directory);
                } catch(Exception e) { OpticClient.LOG.error("Benchmark trace write failed",e); }
                finally { mc.execute(() -> { restore(mc); mc.stop(); }); }
            },"Optic benchmark trace"); writer.start();
            if(OpticClient.manager.recording()) OpticClient.manager.stop();
        }
    }
    private static void restore(Minecraft mc) {
        mc.options.pauseOnLostFocus=oldPause; mc.options.inactivityFpsLimit().set(oldLimit);
        mc.getWindow().setWindowed(oldWidth,oldHeight);
        if(oldFullscreen && !mc.getWindow().isFullscreen()) mc.getWindow().toggleFullScreen();
    }
}
