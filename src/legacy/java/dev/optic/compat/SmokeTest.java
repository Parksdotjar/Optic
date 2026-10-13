package dev.optic.compat;
import dev.optic.OpticClient;
import dev.optic.config.RecorderConfig;
import dev.optic.recording.RecordingManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

/** Explicit opt-in development harness. Does not run in normal installs. */
public final class SmokeTest {
    private static long shown,started;
    private static boolean requested,stopped,cleanScreenshot,finished,pauseStarted,pauseFinished;
    private static int originalWidth,originalHeight;
    private static boolean originalFullscreen;
    private static int cancellations;
    private static long scenarioStarted;
    public static void frame() {
        if(!Boolean.getBoolean("optic.smoke") || finished) return;
        var mc=Minecraft.getInstance(); long now=System.nanoTime();
        if(Boolean.getBoolean("optic.lifecycleSmoke") && cancellations<3) {
            if(mc.getOverlay()!=null || (!(mc.screen instanceof TitleScreen) && mc.level==null)) return;
            if(scenarioStarted==0) scenarioStarted=now;
            if(now-scenarioStarted>30_000_000_000L) { OpticClient.LOG.error("OPTIC_LIFECYCLE_SMOKE_FAILED: cancellation stuck"); mc.stop(); return; }
            if(OpticClient.manager.state()==RecordingManager.State.IDLE) {
                OpticClient.manager.toggle(); OpticClient.manager.toggle(); cancellations++;
                OpticClient.LOG.info("OPTIC_LIFECYCLE_CANCEL {}",cancellations);
            }
            return;
        }
        if(Boolean.getBoolean("optic.lifecycleSmoke") && OpticClient.manager.state()!=RecordingManager.State.IDLE && !requested) return;
        if(!requested && (mc.screen instanceof TitleScreen || mc.level!=null) && mc.getOverlay()==null) {
            if(shown==0) shown=now;
            if(now-shown>3_000_000_000L) {
                requested=true;
                OpticClient.refreshMicrophones();
                OpticClient.io.execute(() -> OpticClient.LOG.info("OPTIC_SMOKE_AUDIO_DEVICES choices={}",OpticClient.microphones.size()));
                OpticClient.config.resolution=switch(System.getProperty("optic.smokeResolution","native")) { case "1080" -> RecorderConfig.Resolution.P1080; case "1440" -> RecorderConfig.Resolution.P1440; default -> RecorderConfig.Resolution.NATIVE; };
                OpticClient.config.fps=Integer.getInteger("optic.smokeFps",30); OpticClient.config.encoder="auto";
                if(System.getProperty("optic.smokeCodec")!=null) { OpticClient.config.encoder=System.getProperty("optic.smokeCodec"); OpticClient.config.colorMode=dev.optic.config.ColorMode.SDR_SRGB; }
                OpticClient.config.microphone=false;
                if(Boolean.getBoolean("optic.workflowRecording")) { OpticClient.config.namingTemplate="V8-{date}-{time}-{server}"; OpticClient.config.namingServerLabel=false; }
                OpticClient.manager.directory=mc.gameDirectory.toPath().resolve("smoke-videos");
                OpticClient.config.quality=dev.optic.config.VideoQuality.valueOf(System.getProperty("optic.smokeQuality","STANDARD"));
                OpticClient.config.aspect=dev.optic.capture.ScaleLayout.Aspect.valueOf(System.getProperty("optic.smokeAspect","FIT"));
                if(Boolean.getBoolean("optic.cleanSmoke")) { OpticClient.config.captureChat=false; OpticClient.config.captureIndicator=false; }
                OpticClient.manager.toggle();
            }
        }
        if(OpticClient.manager.recording()) {
            if(Boolean.getBoolean("optic.pauseSmoke") && started!=0 && !pauseStarted && now-started>2_000_000_000L) { pauseStarted=true; OpticClient.manager.togglePause(); }
            if(pauseStarted && !pauseFinished && now-started>4_000_000_000L) { pauseFinished=true; OpticClient.manager.togglePause(); }
            if(Boolean.getBoolean("optic.cleanSmoke") && !cleanScreenshot && started!=0 && now-started>2_000_000_000L) {
                cleanScreenshot=true; net.minecraft.client.Screenshot.grab(mc.gameDirectory,"optic-clean-player.png",mc.getMainRenderTarget(),1,message -> OpticClient.LOG.info(message.getString()));
                if(Boolean.getBoolean("optic.smokeResize")) { mc.getWindow().setWindowed(1280,Boolean.getBoolean("optic.smokeWide")?540:720); OpticClient.LOG.info("OPTIC_SMOKE_RESIZE second size"); }
            }
            if(started==0) {
                started=now;
                if(Boolean.getBoolean("optic.smokeResize")) {
                    originalWidth=mc.getWindow().getWidth(); originalHeight=mc.getWindow().getHeight(); originalFullscreen=mc.getWindow().isFullscreen();
                    org.lwjgl.glfw.GLFW.glfwRestoreWindow(mc.getWindow().handle()); mc.getWindow().setWindowed(960,Boolean.getBoolean("optic.smokeWide")?720:540);
                    OpticClient.LOG.info("OPTIC_SMOKE_RESIZE first size");
                }
                if(Boolean.getBoolean("optic.audioSmoke")) {
                    Thread tone=new Thread(() -> {
                        try {
                            Thread.sleep(1500);
                            mc.execute(() -> mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK,1)));
                            var format=new javax.sound.sampled.AudioFormat(48000,16,2,true,false);
                            try(var line=javax.sound.sampled.AudioSystem.getSourceDataLine(format)) {
                                line.open(format); line.start(); byte[] pcm=new byte[48000*4];
                                for(int i=0;i<48000;i++) { short s=(short)(Math.sin(i*2*Math.PI*440/48000)*1200); for(int ch=0;ch<2;ch++) { pcm[i*4+ch*2]=(byte)s; pcm[i*4+ch*2+1]=(byte)(s>>8); } }
                                line.write(pcm,0,pcm.length); line.drain();
                            }
                        } catch(Exception e) { OpticClient.LOG.error("Audio smoke tone failed",e); }
                    },"Optic smoke tone"); tone.setDaemon(true); tone.start();
                }
            }
            long pauseAllowance=Boolean.getBoolean("optic.pauseSmoke")?2_000_000_000L:0;
            if(now-started>Long.getLong("optic.smokeSeconds",5)*1_000_000_000L+pauseAllowance) { if(Boolean.getBoolean("optic.exitSmoke")) mc.stop(); else OpticClient.manager.stop(); stopped=true; }
        }
        if((stopped || started!=0) && OpticClient.manager.state()==RecordingManager.State.IDLE) { finished=true; OpticClient.LOG.info("OPTIC_SMOKE_FINISHED clean_frames={} copies={} extra_gui={} clean_p95_ms={}",CleanCapture.cleanFrames,CleanCapture.gpuCopies,CleanCapture.extraGuiPasses,CleanCapture.cost.percentileMs(.95)); if(originalWidth!=0) { mc.getWindow().setWindowed(originalWidth,originalHeight); if(originalFullscreen && !mc.getWindow().isFullscreen()) mc.getWindow().toggleFullScreen(); originalWidth=0; } mc.stop(); }
    }
}
