package dev.optic.compat;
import dev.optic.OpticClient;
import dev.optic.recording.RecordingManager;
import net.minecraft.client.Minecraft;
/** Isolated launch/recording test, enabled only by an explicit JVM flag. */
public final class SmokeTest {
    private static long ready,started; private static boolean requested,stopped,finished,exitQueued,pauseStarted,pauseFinished;
    private static long waiting; private static boolean waitingReported;
    public static void frame() {
        if(!Boolean.getBoolean("optic.smoke") || finished) return;
        var mc=Minecraft.getInstance(); long now=System.nanoTime();
        if(mc.gui.overlay()!=null) return;
        boolean readyScreen = Boolean.getBoolean("optic.worldSmoke") ? mc.level != null
            : mc.gui.screen() != null;
        if(!readyScreen && !requested) {
            if(waiting==0) waiting=now;
            if(!waitingReported && now-waiting>10_000_000_000L) {
                waitingReported=true;
                OpticClient.LOG.warn("OPTIC_SMOKE_WAITING screen={}",mc.gui.screen()==null?"none":mc.gui.screen().getClass().getName());
                net.minecraft.client.Screenshot.grab(mc.gameDirectory,"optic-smoke-waiting.png",mc.gameRenderer.mainRenderTarget(),1,message -> {});
            }
            if(now-waiting>90_000_000_000L) { finished=true; OpticClient.LOG.error("OPTIC_26_SMOKE_FAILED: world did not open"); queueExit(mc); }
        }
        if(!requested && readyScreen) {
            if(ready==0) ready=now;
            if(now-ready>3_000_000_000L) {
                requested=true; OpticClient.refreshMicrophones(); OpticClient.config.microphone=false; OpticClient.config.completionScreen=false;
                OpticClient.io.execute(() -> OpticClient.LOG.info("OPTIC_SMOKE_AUDIO_DEVICES choices={}",OpticClient.microphones.size()));
                if(Boolean.getBoolean("optic.worldSmoke")) { mc.options.pauseOnLostFocus=false; mc.gui.setScreen(null); }
                OpticClient.config.resolution=dev.optic.config.RecorderConfig.Resolution.P1080; OpticClient.config.fps=30;
                OpticClient.config.hud=true; OpticClient.config.captureHud=!Boolean.getBoolean("optic.smokeHideHud"); OpticClient.config.captureIndicator=false;
                OpticClient.manager.directory=mc.gameDirectory.toPath().resolve("smoke-videos"); OpticClient.manager.toggle();
            }
        }
        if(OpticClient.manager.recording()) {
            if(Boolean.getBoolean("optic.worldSmoke") && mc.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen) mc.gui.setScreen(null);
            if(started==0) started=now;
            if(Boolean.getBoolean("optic.pauseSmoke") && !pauseStarted && now-started>2_000_000_000L) { pauseStarted=true; OpticClient.manager.togglePause(); }
            if(pauseStarted && !pauseFinished && now-started>4_000_000_000L) { pauseFinished=true; OpticClient.manager.togglePause(); }
            long pauseAllowance=Boolean.getBoolean("optic.pauseSmoke")?2_000_000_000L:0;
            if(now-started>Long.getLong("optic.smokeSeconds",6)*1_000_000_000L+pauseAllowance) { stopped=true; OpticClient.manager.stop(); }
        }
        if((stopped || started!=0) && OpticClient.manager.state()==RecordingManager.State.IDLE) {
            finished=true; OpticClient.LOG.info("OPTIC_26_SMOKE_FINISHED saved={} {}",OpticClient.manager.lastRecording!=null,CleanCapture.diagnostics()); queueExit(mc);
        }
        if(requested && started==0 && now-ready>90_000_000_000L) { finished=true; OpticClient.LOG.error("OPTIC_26_SMOKE_FAILED"); queueExit(mc); }
    }
    private static void queueExit(Minecraft mc) {
        if(exitQueued) return;
        exitQueued=true;
        // stop() tears native render resources down. Queue it after this render callback has unwound.
        mc.execute(mc::stop);
    }
}
