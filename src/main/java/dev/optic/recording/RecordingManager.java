package dev.optic.recording;

import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;
import dev.optic.OpticClient;
import dev.optic.capture.FrameCapture;
import dev.optic.compat.GlFrameCapture;
import dev.optic.encoding.*;
import net.minecraft.client.Minecraft;
//? if <26.2 {
import com.mojang.blaze3d.opengl.GlTexture;
//?} else {

//?}

/** Client-thread lifecycle; workers return results guarded by the originating session generation. */
public final class RecordingManager {
    public enum State { IDLE, PREPARING, RECORDING, STOPPING }
    private final SessionLifecycle lifecycle=new SessionLifecycle();
    private final Consumer<String> notice;
    private final ExecutorService control=Executors.newSingleThreadExecutor(r -> daemon(r,"Optic session control"));
    private final ExecutorService recovery=Executors.newSingleThreadExecutor(r -> daemon(r,"Optic recovery"));
    private final ScheduledExecutorService deadlines=Executors.newSingleThreadScheduledExecutor(r -> daemon(r,"Optic exit deadline"));
    public Path directory;
    public Path detailsDirectory() { return RecordingStorage.details(directory); }
    private final Path toolsDirectory=net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("optic-tools");
    public final dev.optic.audio.AudioControls microphoneControls=new dev.optic.audio.AudioControls();
    public final dev.optic.audio.AudioControls gameControls=new dev.optic.audio.AudioControls();
    private Run current;
    private boolean exitRequested;
    private boolean replayFault;
    private volatile boolean recovering=true;
    private long lastElapsedSecond=-1;
    private String elapsed="00:00";
    private long lastRender;
    public String detectedEncoder="Auto";
    public CompletedRecording lastRecording;
    private Path lastMarkerPartial;
    public String markerFeedback="";
    public long markerFeedbackUntil;
    public final TimingHistogram markerInputTime=new TimingHistogram();
    public Path markerPartial() { return current!=null?current.partial:lastMarkerPartial; }
    public void markerMessage(String text) { Minecraft.getInstance().execute(() -> { markerFeedback=text; markerFeedbackUntil=System.nanoTime()+2_000_000_000L; }); }
    public void addMarker(String note) {
        long inputStart=System.nanoTime();
        try { addMarkerEvent(note); } finally { markerInputTime.add(System.nanoTime()-inputStart); }
    }
    private void addMarkerEvent(String note) {
        if(!OpticClient.config.markersEnabled) { markerMessage("Markers are disabled in settings."); return; }
        Run run=current;
        if(!recording() || run==null || run.markers==null) { markerMessage("Start recording or enable replay first."); return; }
        long elapsed=run.session.activeNanos(System.nanoTime());
        var event=new dev.optic.markers.Marker(1,UUID.randomUUID().toString(),run.manifest.sessionId,elapsed,run.session.fps,OpticClient.config.markerType,dev.optic.markers.Marker.note(note),dev.optic.compat.MarkerContext.snapshot(OpticClient.config.markerContext),OpticClient.config.markerThumbnails);
        if(!run.markers.offer(event)) markerMessage("Marker not saved: metadata queue/limit reached.");
    }
    private static final class Run {
        final long generation;
        final Cancellation preparation=new Cancellation(), finalization=new Cancellation();
        volatile RecordingSession session;
        FrameCapture capture;
        Path ffmpeg, partial;
        SessionFiles lock;
        SessionManifest manifest;
        long lastIndex=-1;
        boolean raw;
        boolean replay,full=true;
        dev.optic.replay.ReplayHistory history;
        dev.optic.config.RecorderConfig profile;
        dev.optic.markers.MarkerJournal markers;
        dev.optic.capture.ScaleLayout.Aspect aspect;
        Run(long generation) { this.generation=generation; }
    }
    private static Thread daemon(Runnable work,String name) { var thread=new Thread(work,name); thread.setDaemon(true); return thread; }
    public RecordingManager(Path directory,Consumer<String> notice) { this.directory=directory; this.notice=notice; }
    public State state() { return State.valueOf(lifecycle.state().name()); }
    public boolean recording() { return lifecycle.state()==SessionLifecycle.State.RECORDING; }
    public boolean fullRecording() { return recording() && current!=null && current.full; }
    public boolean paused() { return fullRecording() && current.session!=null && current.session.paused(); }
    public void togglePause() {
        Run run=current;
        if(!fullRecording() || run==null || run.session==null) { notice.accept("Start a recording before pausing."); return; }
        boolean value=run.session.togglePaused();
        lastRender=System.nanoTime();
        notice.accept(value?"Recording paused.":"Recording resumed.");
    }
    public String replayStatus() { return !OpticClient.config.replayEnabled && current==null?"Disabled":replayFault?"Unavailable — switch replay OFF and ON to retry":current!=null && current.history!=null?current.history.status():"Waiting for a world / preparing"; }
    public void tickReplay() {
        var mc=Minecraft.getInstance();
        if(current!=null && current.replay && mc.level==null && !Boolean.getBoolean("optic.smoke")) { stop(); return; }
        if(OpticClient.config.replayEnabled && !replayFault && !recovering && !exitRequested && state()==State.IDLE && mc.level!=null) start(true,false);
    }
    public void setReplay(boolean enabled) {
        if(!enabled && fullRecording() && current.replay) { notice.accept("Stop the full recording before switching replay OFF."); return; }
        if(enabled && current!=null && !current.replay) { notice.accept("Stop the current recording before enabling replay."); return; }
        OpticClient.config.replayEnabled=enabled; replayFault=false; OpticClient.saveConfig();
        if(!enabled && current!=null && current.replay && !current.full) stop();
        if(enabled) notice.accept("Instant replay enabled. It continuously encodes while in a world.");
        if(enabled && OpticClient.clipKeyConflict()) notice.accept("Save Clip shares a key with another action. Change it in Minecraft Controls.");
    }
    public void saveClip() {
        if(!recording() || current.history==null) { notice.accept("Enable Instant Replay in Optic settings, then let it warm up."); return; }
        current.history.save(current.session.durationSeconds());
    }
    private boolean owns(Run run) { return current==run && lifecycle.current(run.generation); }
    public String elapsed() {
        long seconds=current==null || current.session==null?0:(long)Math.max(0,current.session.durationSeconds()-(current.history!=null && current.full?current.history.fullStartSeconds():0));
        if(seconds!=lastElapsedSecond) { lastElapsedSecond=seconds; elapsed=seconds>=3600?String.format(java.util.Locale.ROOT,"%d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60):String.format(java.util.Locale.ROOT,"%02d:%02d",seconds/60,seconds%60); }
        return elapsed;
    }
    public String details() {
        if(current==null || current.session==null) return "";
        var s=current.session; return s.width+"×"+s.height+" · "+s.fps+" FPS · "+EncoderDetector.label(detectedEncoder);
    }
    public String diagnostics() {
        return current==null || current.session==null?"No active recording":current.session.metrics.report(current.session.durationSeconds(),current.session.metrics.outputBytes);
    }
    /** Called from the client thread for an explicit support preview, never in the capture hot path. */
    public void supportFields(java.util.Map<String,String> fields) {
        Run run=current;
        if(run==null || run.manifest==null) { fields.put("capture_backend","inactive"); return; }
        var m=run.manifest;
        fields.put("capture_backend",m.captureBackend);
        fields.put("encoder",m.encoder); fields.put("resolution",m.width+"x"+m.height); fields.put("fps",Integer.toString(m.fps));
        fields.put("quality",m.quality); fields.put("color",m.colorContract);
        fields.put("microphone_enabled",Boolean.toString(m.microphoneRequested)); fields.put("game_audio_enabled",Boolean.toString(m.gameAudioAvailable));
    }
    public void toggle() {
        if(state()==State.PREPARING) { cancelPreparation(); return; }
        if(recording()) {
            if(current.replay && !current.full) {
                var c=OpticClient.config; var p=current.profile;
                if(c.colorMode!=p.colorMode || c.stopOnSustainedDuplicates!=p.stopOnSustainedDuplicates || c.resolution!=p.resolution || c.fps!=p.fps || c.quality!=p.quality || c.aspect!=p.aspect || !c.encoder.equals(p.encoder) || c.microphone!=p.microphone || c.gameAudio!=p.gameAudio || !c.microphoneDevice.equals(p.microphoneDevice)) { notice.accept("Video/audio settings changed. Switch replay OFF and ON before starting this recording."); return; }
                current.full=true; current.history.promote(current.session.durationSeconds()); notice.accept("Recording started using the replay stream (up to 2 seconds of pre-roll).");
            }
            else stop();
            return;
        }
        start(OpticClient.config.replayEnabled,true);
    }
    private void start(boolean replay,boolean full) {
        if(state()!=State.IDLE || exitRequested) return;
        if(replay && recovering) { notice.accept("Replay recovery is finishing; try again shortly."); return; }
        var mc=Minecraft.getInstance();
        if(mc.level==null && !Boolean.getBoolean("optic.smoke")) { notice.accept("Enter a world before recording."); return; }
        var c=OpticClient.config.snapshot(); c.validate();
        String namingLabel="Minecraft";
        if(c.namingServerLabel) { var context=dev.optic.compat.MarkerContext.snapshot(true); if(context!=null) namingLabel=context.worldLabel(); }
        final String templateLabel=namingLabel;
//? if <26.2 {
        int sourceWidth=mc.getMainRenderTarget().width,sourceHeight=mc.getMainRenderTarget().height;
        int width=switch(c.resolution) { case P1080 -> 1920; case P1440 -> 2560; case NATIVE -> mc.getMainRenderTarget().width & ~1; };
        int height=switch(c.resolution) { case P1080 -> 1080; case P1440 -> 1440; case NATIVE -> mc.getMainRenderTarget().height & ~1; };
//?} else {
        /*int sourceWidth=mc.gameRenderer.mainRenderTarget().width,sourceHeight=mc.gameRenderer.mainRenderTarget().height;
        int width=switch(c.resolution) { case P1080 -> 1920; case P1440 -> 2560; case NATIVE -> mc.gameRenderer.mainRenderTarget().width & ~1; };
        int height=switch(c.resolution) { case P1080 -> 1080; case P1440 -> 1440; case NATIVE -> mc.gameRenderer.mainRenderTarget().height & ~1; };
*///?}
        if(width<2 || height<2 || (long)width*height*4*10>512L*1024*1024) { if(replay) replayFault=true; notice.accept("Capture size exceeds the 512 MiB buffer budget."); return; }
        int fps=c.fps; String requested=c.encoder; boolean microphone=c.microphone; String device=c.microphoneDevice;
//? if <26.2 {
        boolean persistent=org.lwjgl.opengl.GL.getCapabilities().GL_ARB_buffer_storage && !Boolean.getBoolean("optic.forceLegacyCapture");
        String renderer=org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VENDOR);
//?} else {
        /*boolean persistent=false;
        String renderer=com.mojang.blaze3d.systems.RenderSystem.getDevice().getDeviceInfo().vendorName();
*///?}
        Run run=new Run(lifecycle.begin()); current=run; run.raw=Boolean.getBoolean("optic.raw"); run.aspect=c.aspect;
        run.replay=replay && !run.raw; run.full=full;
        run.profile=c; if(replay) replayFault=false;
        microphoneControls.reset(); gameControls.reset();
//? if <26.2 {
        notice.accept("Preparing recorder… press Record again to cancel.");
//?} else {
        /*if(c.capturePolicy().clean()) notice.accept("Clean capture active for supported HUD and GUI elements.");
*///?}
        control.execute(() -> {
            try {
                Files.createDirectories(directory);
                Files.createDirectories(toolsDirectory);
                if(run.replay && dev.optic.replay.ReplayRecovery.pendingClips(detailsDirectory())) throw new java.io.IOException("An interrupted clip needs recovery. Restart Minecraft; sources are in Details.");
                if(Files.getFileStore(directory).getUsableSpace()<256L*1024*1024) throw new java.io.IOException("Not enough free disk space to start recording");
                run.ffmpeg=new FFmpegManager(toolsDirectory.resolve("ffmpeg")).obtain(run.preparation);
                try { FFmpegManager.inspectVersion(run.ffmpeg,run.preparation); }
                catch(java.util.concurrent.CancellationException e) { throw e; }
                catch(Exception e) { OpticClient.LOG.debug("Optional encoder version inspection unavailable",e); }
                String codec;
                boolean advanced=AdvancedEncoders.isAdvanced(requested);
                if(advanced && !run.replay && AdvancedEncoders.probe(run.ffmpeg,requested,width,height,fps,c.quality,c.colorMode,toolsDirectory.resolve("advanced-probes"),run.preparation)) codec=requested;
                else {
                    if(advanced) notice.accept(run.replay?"Advanced codecs are not validated for replay yet; using H.264.":"Requested advanced codec unavailable; using H.264 fallback.");
                    codec=new EncoderDetector().detect(run.ffmpeg,advanced?"auto":requested,width,height,fps,toolsDirectory.resolve("encoder-probe.log"),renderer==null?"":renderer,run.preparation,c.quality);
                }
                if(c.colorMode!=dev.optic.config.ColorMode.LEGACY && !AdvancedEncoders.probe(run.ffmpeg,codec,width,height,fps,c.quality,c.colorMode,toolsDirectory.resolve("advanced-probes"),run.preparation)) throw new java.io.IOException("Requested SDR color conversion failed validation. Choose Existing conversion in Advanced.");
                run.preparation.check();
                String id=UUID.randomUUID().toString();
                run.partial=c.namingTemplate.isBlank()?RecordingStorage.reserve(directory,c.recordingTitle,c.namingStyle):RecordingStorage.reserveTemplate(directory,dev.optic.workflow.NamingTemplate.resolve(c.namingTemplate,c.recordingTitle,templateLabel,java.time.LocalDateTime.now()));
                Path support=run.partial.getParent(); String stem=support.getFileName().toString();
                run.lock=SessionFiles.tryLock(run.partial);
                if(run.lock==null) throw new java.io.IOException("Recording file is already in use");
                run.manifest=new SessionManifest(); var m=run.manifest;
                m.outputDirectory=directory.toAbsolutePath().normalize().toString(); m.outputFile=stem+".mp4";
                m.sessionId=id; m.createdUtc=java.time.Instant.now().toString(); m.width=width; m.height=height; m.fps=fps;
                m.preset=c.preset.name(); m.quality=c.quality.name(); m.aspect=c.aspect.name();
                m.initialSourceWidth=sourceWidth; m.initialSourceHeight=sourceHeight;
                m.videoOptions=EncoderDetector.options(codec,fps,c.quality).toArray(String[]::new);
                m.requestedEncoder=requested; m.colorContract=c.colorMode.name(); m.stopOnSustainedDuplicates=c.stopOnSustainedDuplicates;
                var videoOptions=new java.util.ArrayList<>(EncoderDetector.options(codec,fps,c.quality)); videoOptions.addAll(c.colorMode.options()); m.videoOptions=videoOptions.toArray(String[]::new);
//? if <26.2 {
                m.encoder=codec; m.captureBackend=persistent?"persistent-pbo":"fenced-pbo"; m.microphoneRequested=microphone; m.save(run.partial);
//?} else {
                /*m.encoder=codec; m.captureBackend="gpu-staging-ring"; m.microphoneRequested=microphone; m.save(run.partial);
*///?}
                run.markers=new dev.optic.markers.MarkerJournal(run.partial,this::markerMessage);
                if(run.replay) {
                    run.history=new dev.optic.replay.ReplayHistory(run.ffmpeg,run.partial,directory,m,c.replaySeconds,c.gameAudio,microphone,notice);
                    if(full) run.history.promote(0);
                }
                VideoEncoder encoder=run.raw?new RawVideoEncoder(SessionFiles.sibling(run.partial,".rgba"))
                    :new FFmpegVideoEncoder(run.ffmpeg,codec,width,height,fps,run.replay?run.history.directory():run.partial,support.resolve(stem+".ffmpeg.log"),c.quality,run.replay,c.colorMode);
                try { run.session=new RecordingSession(width,height,fps,run.partial,encoder,persistent); }
                catch(Exception | OutOfMemoryError e) { encoder.abort(); try { encoder.close(); } catch(Exception suppressed) { e.addSuppressed(suppressed); } throw e; }
                run.session.microphoneEnabled=microphone; run.session.microphoneDevice=device; run.session.notice=notice;
                run.session.stopOnSustainedDuplicates=c.stopOnSustainedDuplicates;
                if(run.history!=null) { run.session.audioStorage=run.history.audioStorage(); run.session.storageFailure=run.history::failure; run.session.externalBytes=() -> run.history.totalVideoBytes; }
                run.session.microphoneControls=microphoneControls; run.session.gameControls=gameControls;
                microphoneControls.configure(c.microphoneGain,c.microphoneMuted);
                run.session.microphoneReconnect=c.microphoneReconnect;
                run.session.gameAudioEnabled=c.gameAudio;
                run.preparation.check();
                mc.execute(() -> begin(run,requested));
            } catch(Exception | OutOfMemoryError e) { cleanupPreparation(run,e); }
        });
    }
    private void cancelPreparation() {
        if(current==null || state()!=State.PREPARING) return;
        if(current.replay) replayFault=true;
        lifecycle.stopping(current.generation); current.preparation.cancel(); notice.accept("Cancelling recorder setup…");
    }
    private void cleanupPreparation(Run run,Throwable failure) {
        if(run.markers!=null) run.markers.close();
        if(run.history!=null) run.history.discardPrepared();
        if(run.replay && !run.preparation.cancelled()) replayFault=true;
        if(run.session!=null) try { run.session.discardPrepared(); } catch(Exception e) { OpticClient.LOG.debug("Prepared encoder cleanup",e); }
        if(run.manifest!=null) { run.manifest.state=run.preparation.cancelled()?"cancelled":"failed"; run.manifest.failure=run.preparation.cancelled()?null:"Preparation failed"; saveManifest(run); }
        releaseLock(run);
        if(!run.preparation.cancelled()) OpticClient.LOG.warn("Recorder preparation failed",failure);
        Minecraft.getInstance().execute(() -> {
            if(!owns(run)) return;
            notice.accept(run.preparation.cancelled()?"Recording setup cancelled.":"Cannot start recording: "+failure.getMessage());
            finish(run);
        });
    }
    private void begin(Run run,String requested) {
        if(!owns(run) || state()!=State.PREPARING || run.preparation.cancelled()) {
            control.execute(() -> cleanupPreparation(run,new CancellationException())); return;
        }
        try {
//? if <26.2 {
            if(!(Minecraft.getInstance().getMainRenderTarget().getColorTexture() instanceof GlTexture)) throw new IllegalStateException("Recording requires Minecraft's OpenGL backend");
//?} else {
            
//?}
            var s=run.session;
            try { run.capture=new GlFrameCapture(s.width,s.height,s.pool,s.frames,s.metrics,s.persistentCapture,run.aspect); }
            catch(Exception | OutOfMemoryError failure) {
                if(!s.persistentCapture) throw failure;
                OpticClient.LOG.warn("Persistent capture initialization failed; preparing portable PBO fallback",failure);
                control.execute(() -> {
                    try {
                        run.preparation.check(); s.preparePortableCapture(); run.manifest.captureBackend="fenced-pbo";
                        Minecraft.getInstance().execute(() -> begin(run,requested));
                    } catch(Exception | OutOfMemoryError e) { cleanupPreparation(run,e); }
                });
                return;
            }
            s.start(); lastRender=System.nanoTime(); lastElapsedSecond=-1; detectedEncoder=run.manifest.encoder;
            lifecycle.prepared(run.generation);
            run.manifest.state="recording"; control.execute(() -> saveManifest(run));
            daemon(() -> encode(run),"Optic encoder").start();
            OpticClient.LOG.info("Capture path: {}",run.manifest.captureBackend);
            if(!requested.equals("auto") && !requested.equals(detectedEncoder)) notice.accept("Preferred encoder unavailable; using "+EncoderDetector.label(detectedEncoder)+".");
            if(requested.equals("auto") && detectedEncoder.equals("libx264")) notice.accept("Hardware encoder unavailable; using software. Lower FPS if capture cannot keep up.");
            notice.accept(run.replay && !run.full?"Replay warming. F10 saves a clip; F9 starts a full recording.":"Recording started.");
        } catch(Exception | OutOfMemoryError e) {
            if(run.capture!=null) { try { run.capture.close(); } catch(Exception ignored) {} run.capture=null; }
            lifecycle.stopping(run.generation); control.execute(() -> cleanupPreparation(run,e));
        }
    }
    public void frame() {
        Run run=current;
        if(!recording() || run==null || run.capture==null || run.session.paused()) return;
        long start=System.nanoTime(); var s=run.session;
        try {
            long index=s.frameIndex(start);
            if(index==run.lastIndex) return;
            if(run.lastIndex>=0 && index>run.lastIndex+1) s.metrics.dropped.add(index-run.lastIndex-1);
            run.lastIndex=index;
//? if <26.2 {
            var target=Minecraft.getInstance().getMainRenderTarget();
            if(target.getColorTexture() instanceof GlTexture texture && target.width>0 && target.height>0) run.capture.capture(texture.glId(),target.width,target.height,index);
//?} else {
            /*var target=Minecraft.getInstance().gameRenderer.mainRenderTarget();
            if(target.getColorTexture()!=null && target.width>0 && target.height>0) run.capture.capture(0,target.width,target.height,index);
*///?}
        } catch(Exception e) { OpticClient.LOG.warn("Capture failed",e); s.fail(e); stop(); notice.accept("Capture interrupted; partial video retained."); }
        finally { long elapsed=System.nanoTime()-start; s.metrics.hookNanos.add(elapsed); s.metrics.hooks.increment(); s.metrics.hookTime.add(elapsed); }
    }
    public boolean captureDue() { return recording() && current!=null && current.session!=null && !current.session.paused() && current.session.frameIndex(System.nanoTime())!=current.lastIndex; }
    /** Every rendered frame, even between recording deadlines. Keeps fence latency and frame metrics honest. */
    public void renderTick() {
        Run run=current; if(!recording() || run==null || run.capture==null) return;
        if(run.session.overloadStopRequested) { if(run.replay) replayFault=true; stop(); return; }
        long now=System.nanoTime();
        if(run.session.paused()) lastRender=now;
        else { run.session.metrics.frameIntervals.add(now-lastRender); lastRender=now; }
        try { run.capture.poll(); }
        catch(Exception e) { captureFailed(e); }
        finally { run.session.metrics.pollTime.add(System.nanoTime()-now); }
    }
    public boolean captureReady() { return current!=null && current.capture!=null && current.session!=null && !current.session.paused() && current.capture.ready(); }
    public void captureFailed(Exception error) { if(recording()) { current.session.fail(error); stop(); notice.accept("Clean capture interrupted; recording stopped to preserve exclusions."); } }
    public void stop() {
        if(state()==State.PREPARING) { cancelPreparation(); return; }
        if(!recording()) return;
        lifecycle.stopping(current.generation); current.session.stop();
        notice.accept("Saving and checking recording…");
    }
    private void encode(Run run) {
        Exception failure=null;
        try { run.session.consume(); } catch(Exception e) { failure=e; OpticClient.LOG.warn("Encoding failed",e); notice.accept("Recording stopped: "+e.getMessage()); }
        Exception result=failure;
        Minecraft.getInstance().execute(() -> {
            if(!owns(run)) return;
            lifecycle.stopping(run.generation); run.session.stop();
            // No consumer now owns a mapped pointer. Detach producer before draining late frames.
            Exception closeFailure=null;
            if(run.capture!=null) { try { run.capture.close(); } catch(Exception e) { closeFailure=e; } finally { run.capture=null; } }
            var frame=run.session.frames.poll(); while(frame!=null) { run.session.pool.release(frame); frame=run.session.frames.poll(); }
            Exception terminal=result!=null?result:closeFailure;
            control.execute(() -> finalizeSession(run,terminal));
        });
    }
    private void finalizeSession(Run run,Exception failure) {
        var s=run.session;
        if(s.overloadStopRequested) { run.manifest.stopReason="Sustained duplicates >10% for three five-second windows; no quality change"; if(run.replay) replayFault=true; }
        if(run.markers!=null) run.markers.close();
        try {
            run.manifest.durationSeconds=s.durationSeconds(); run.manifest.framesAccepted=s.metrics.encoded.sum(); run.manifest.encoderFrames=s.metrics.encoderFrames;
            if(run.history!=null) run.history.finish(run.full,run.finalization);
            if(failure!=null) throw failure;
            if(run.replay && !run.full) { run.manifest.state="replay-stopped"; run.history.cleanup(); return; }
            run.manifest.state="finalizing"; saveManifest(run);
            if(run.raw) { run.manifest.state="complete"; notice.accept("Raw diagnostic recording saved."); }
            else {
                var result=RecordingRecovery.finalizeOwned(run.ffmpeg,run.partial,run.finalization);
                run.manifest.state="complete"; run.manifest.outputFile=result.file().getFileName().toString();
                run.manifest.gameAudioAvailable=result.gameAudioIncluded();
                var tracks=new java.util.ArrayList<String>(); if(result.gameAudioIncluded()) tracks.add("minecraft-process"); if(result.microphoneIncluded()) tracks.add("microphone");
                run.manifest.audioTracks=tracks.toArray(String[]::new);
                double duration=result.decodedFrames()/(double)s.fps;
                run.manifest.durationSeconds=duration;
                if(run.history!=null) { run.manifest.sourceStartSeconds=run.history.fullStartSeconds(); run.manifest.sourceEndSeconds=run.history.endSeconds(); }
                dev.optic.markers.MarkerExport.schedule(run.ffmpeg,run.partial,run.partial,run.manifest);
                var completed=new CompletedRecording(result.file(),duration,s.width,s.height,s.fps,run.manifest.encoder,result.decodedFrames(),result.microphoneIncluded(),result.gameAudioIncluded(),result.warning());
                try { Files.writeString(SessionFiles.sibling(run.partial,".metrics.txt"),s.metrics.report(s.durationSeconds(),run.history==null?Files.size(result.file()):run.history.totalVideoBytes)+" decoded_frames="+result.decodedFrames()+" exported_seconds="+duration+" mic_dropped_samples="+microphoneControls.droppedFrames+" game_dropped_samples="+gameControls.droppedFrames); }
                catch(Exception e) { OpticClient.LOG.warn("Video saved but metrics could not be written",e); }
                Minecraft.getInstance().execute(() -> { if(owns(run)) {
                    lastRecording=completed; lastMarkerPartial=run.partial; notice.accept("Recording saved — "+completed.durationLabel()+". Details in Optic settings.");
                    var mc=Minecraft.getInstance();
//? if <26.2 {
                    if(OpticClient.config.completionScreen && !exitRequested && (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen || mc.screen instanceof dev.optic.ui.SettingsScreen))
                        mc.setScreen(new dev.optic.ui.RecordingSavedScreen(mc.screen,completed));
//?} else {
                    /*if(OpticClient.config.completionScreen && !exitRequested && (mc.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen || mc.gui.screen() instanceof dev.optic.ui.SettingsScreen))
                        mc.gui.setScreen(new dev.optic.ui.RecordingSavedScreen(mc.gui.screen(),completed));
*///?}
                } });
                if(!result.warning().isEmpty()) notice.accept(result.warning());
                if(run.history!=null) run.history.cleanup();
            }
        } catch(Exception e) {
            if(run.replay) { replayFault=true; if(run.history!=null) run.history.discardPrepared(); }
            run.manifest.state="recoverable"; run.manifest.failure=e instanceof CancellationException?"Finalization cancelled":"Recording interrupted; inspect local log";
            notice.accept("Recording interrupted; footage retained for recovery."); OpticClient.LOG.warn("Recording finalization deferred",e);
        } finally {
            saveManifest(run); releaseLock(run);
            try { s.pool.close(); }
            finally { Minecraft.getInstance().execute(() -> { if(owns(run)) finish(run); }); }
        }
    }
    private void saveManifest(Run run) { try { run.manifest.save(run.partial); } catch(Exception e) { OpticClient.LOG.warn("Session metadata write failed",e); } }
    private void releaseLock(Run run) { if(run.lock!=null) { try { run.lock.close(); } catch(Exception e) { OpticClient.LOG.warn("Session lock cleanup",e); } finally { run.lock=null; } } }
    private void finish(Run run) {
        if(!lifecycle.finished(run.generation)) return;
        if(run.history!=null && run.history.retainedExports()) { replayFault=true; notice.accept("Replay paused: failed clip sources are retained in Details for recovery."); }
        current=null;
        if(run.replay && run.full && !exitRequested && OpticClient.config.replayEnabled && !replayFault) notice.accept("Recording saved. Replay history is restarting.");
        if(exitRequested) Minecraft.getInstance().stop();
    }
    public boolean deferExit() {
        if(state()==State.IDLE) return false;
        if(!exitRequested) {
            exitRequested=true; Run run=current; stop();
            deadlines.schedule(() -> {
                run.preparation.cancel(); run.finalization.cancel();
                if(run.history!=null) run.history.cancelExports();
                var session=run.session; if(session!=null) session.abort();
            },20,TimeUnit.SECONDS);
        }
        return true;
    }
    public void recover() {
        recovering=true;
        recovery.execute(() -> {
            int restored=0, failed=0;
            try {
                Files.createDirectories(detailsDirectory());
                dev.optic.workflow.RecordingLibrary.reconcile(directory);
                boolean replaySources;
                try(var paths=Files.walk(detailsDirectory(),3)) { replaySources=paths.anyMatch(p -> p.getFileName().toString().equals("history.properties") || p.getFileName().toString().equals("clip-plan.properties")); }
                if(replaySources) {
                    Path replayFfmpeg=new FFmpegManager(toolsDirectory.resolve("ffmpeg")).obtain();
                    restored+=dev.optic.replay.ReplayRecovery.recover(replayFfmpeg,detailsDirectory(),notice);
                }
                var files=new java.util.ArrayList<Path>();
                for(Path root:new Path[]{directory,detailsDirectory()}) {
                    Files.createDirectories(root);
                    try(var paths=Files.walk(root,2)) { files.addAll(paths.filter(p -> p.getFileName().toString().endsWith(".partial.mp4")).toList()); }
                }
                Path ffmpeg=null;
                for(Path partial:files) {
                    // Replay recovery owns these sources; generic recovery must not publish an
                    // incompletely assembled clip after its plan failed (especially without audio).
                    if(Files.exists(partial.resolveSibling("clip-plan.properties")) || Files.exists(partial.getParent().resolve("replay/history.properties"))) continue;
                    try(var lock=SessionFiles.tryLock(partial)) {
                        if(lock==null) continue;
                        if(Files.exists(RecordingStorage.target(partial))) continue;
                        var manifest=SessionManifest.read(partial);
                        if(manifest!=null && "cancelled".equals(manifest.state)) continue;
                        if(ffmpeg==null) ffmpeg=new FFmpegManager(toolsDirectory.resolve("ffmpeg")).obtain();
                        var result=RecordingRecovery.finalizeOwned(ffmpeg,partial,new Cancellation()); restored++;
                        if(manifest!=null) { manifest.state="complete"; manifest.outputFile=result.file().getFileName().toString(); manifest.durationSeconds=result.decodedFrames()/(double)manifest.fps; manifest.save(partial); dev.optic.markers.MarkerExport.schedule(ffmpeg,partial,partial,manifest); }
                    } catch(Exception e) { failed++; OpticClient.LOG.warn("Recovery deferred for {}",partial.getFileName(),e); }
                }
                if(restored>0) notice.accept("Recovered "+restored+" recording(s). Open the recordings folder to view them.");
                if(failed>0) notice.accept(failed+" interrupted recording(s) need recovery; source files retained.");
            } catch(Exception e) { OpticClient.LOG.warn("Recovery scan failed",e); }
            finally { recovering=false; }
        });
    }
}
