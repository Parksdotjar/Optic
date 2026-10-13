package dev.optic.compat;
import dev.optic.OpticClient;
import dev.optic.recording.RecordingManager;
import net.minecraft.client.Minecraft;

/** Opt-in V6 scenario on a copied world. Never persists the temporary recorder choices. */
public final class ReplaySmokeTest {
    private static long ready,started;
    private static int stage;
    private static boolean finished;
    private static int width,height;
    private static boolean fullscreen,pause;
    private static dev.optic.config.RecorderConfig original;
    private static long timelineShown;
    public static void frame() {
        if(!Boolean.getBoolean("optic.replaySmoke") || finished) return;
        var mc=Minecraft.getInstance(); long now=System.nanoTime();
        if(stage==0) {
            if(mc.level==null || mc.getOverlay()!=null) return;
            if(ready==0) ready=now; if(now-ready<3_000_000_000L) return;
            width=mc.getWindow().getWidth(); height=mc.getWindow().getHeight(); fullscreen=mc.getWindow().isFullscreen(); pause=mc.options.pauseOnLostFocus;
            mc.getWindow().setWindowed(1280,720); org.lwjgl.glfw.GLFW.glfwRestoreWindow(mc.getWindow().handle()); mc.options.pauseOnLostFocus=false;
            original=OpticClient.config.snapshot(); var c=OpticClient.config; c.replayEnabled=true; c.replaySeconds=15; c.encoder="auto"; c.fps=30; c.resolution=dev.optic.config.RecorderConfig.Resolution.P1080;
            c.microphone=false; c.gameAudio=true; c.completionScreen=false; c.captureChat=false; c.captureIndicator=false;
            if(Boolean.getBoolean("optic.markerSmoke")) { c.markersEnabled=true; c.markerContext=true; c.markerThumbnails=true; }
            OpticClient.manager.directory=mc.gameDirectory.toPath().resolve("v6-validation"); stage=1;
        }
        if(OpticClient.manager.recording() && started==0) started=now;
        if(started==0) { if(now-ready>40_000_000_000L) { OpticClient.LOG.error("OPTIC_REPLAY_SMOKE_FAILED setup"); finished=true; mc.stop(); } return; }
        double t=(now-started)/1e9;
        if(stage==1 && t>5) { if(Boolean.getBoolean("optic.markerSmoke")) OpticClient.manager.addMarker("Warm-up 日本語 marker"); OpticClient.manager.saveClip(); stage=2; }
        if(stage==2 && t>10) {
            mc.setScreen(new dev.optic.ui.SettingsScreen(null));
            for(var child:java.util.List.copyOf(mc.screen.children())) if(child instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getString().equals("Replay")) { b.onPress(null); break; }
            if(Boolean.getBoolean("optic.markerSmoke")) mc.setScreen(new dev.optic.ui.MarkersScreen(null));
            stage=3;
        }
        if(stage==3 && t>11) {
            net.minecraft.client.Screenshot.grab(mc.gameDirectory,"optic-v6-replay.png",mc.getMainRenderTarget(),1,m -> OpticClient.LOG.info(m.getString()));
            mc.setScreen(null); stage=4;
        }
        if(stage==4 && t>20) { OpticClient.manager.toggle(); if(!OpticClient.manager.fullRecording()) throw new IllegalStateException("Replay did not promote"); stage=5; }
        if(stage==5 && t>25) { if(Boolean.getBoolean("optic.markerSmoke")) { OpticClient.config.markerType=dev.optic.markers.Marker.Type.FUNNY; OpticClient.manager.addMarker("Full recording, clip rebase, \"quoted\" note"); } OpticClient.manager.saveClip(); OpticClient.manager.saveClip(); stage=6; }
        if(stage==6 && t>35) { OpticClient.config.replayEnabled=false; OpticClient.manager.stop(); stage=7; }
        if(stage==7 && OpticClient.manager.state()==RecordingManager.State.IDLE) {
            if(Boolean.getBoolean("optic.markerSmoke")) {
                if(timelineShown==0) { timelineShown=now; mc.setScreen(new dev.optic.ui.MarkerTimelineScreen(null,OpticClient.manager.markerPartial())); return; }
                if(now-timelineShown<3_000_000_000L) return;
                net.minecraft.client.Screenshot.grab(mc.gameDirectory,"optic-v7-timeline.png",mc.getMainRenderTarget(),1,m -> OpticClient.LOG.info(m.getString()));
                OpticClient.LOG.info("OPTIC_MARKER_SMOKE_FINISHED");
            }
            finished=true; mc.options.pauseOnLostFocus=pause; mc.getWindow().setWindowed(width,height); if(fullscreen && !mc.getWindow().isFullscreen()) mc.getWindow().toggleFullScreen();
            OpticClient.config=original; OpticClient.saveConfig();
            OpticClient.io.execute(() -> mc.execute(() -> { OpticClient.LOG.info("OPTIC_REPLAY_SMOKE_FINISHED"); mc.stop(); }));
        }
    }
}
