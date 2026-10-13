package dev.optic.recording;

import dev.optic.capture.*;
import dev.optic.encoding.*;
import java.nio.file.*;
import java.util.concurrent.*;

/** No Minecraft dependency. Single encoding consumer; stop never waits on its caller. */
public final class RecordingSession {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("Optic");
    public java.util.function.Consumer<String> notice=message -> LOG.info(message);
    public final int width,height,fps;
    public FrameBufferPool pool;
    public final ArrayBlockingQueue<FrameBufferPool.Frame> frames=new ArrayBlockingQueue<>(3);
    public final RecordingMetrics metrics=new RecordingMetrics();
    public final Path partial;
    private final VideoEncoder encoder;
    private volatile boolean stopping;
    private volatile boolean paused;
    private long pauseStartedNanos, pausedNanos;
    private volatile long endIndex;
    public volatile long startNanos;
    public boolean stopOnSustainedDuplicates;
    public volatile boolean overloadStopRequested;
    private final OverloadGuard overloadGuard=new OverloadGuard();
    public volatile long stopNanos;
    private volatile Exception requestedFailure;
    private volatile dev.optic.audio.AudioCapture activeMicrophone, activeGame;
    public boolean microphoneEnabled;
    public boolean gameAudioEnabled, microphoneReconnect=true;
    public dev.optic.audio.AudioControls microphoneControls=new dev.optic.audio.AudioControls(), gameControls=new dev.optic.audio.AudioControls();
    public String microphoneDevice="Default Input";
    public boolean persistentCapture;
    public dev.optic.audio.PcmStorage audioStorage=dev.optic.audio.PcmStorage.FILE;
    public java.util.function.Supplier<java.io.IOException> storageFailure=() -> null;
    public java.util.function.LongSupplier externalBytes=() -> -1;
    java.util.function.Function<String,dev.optic.audio.AudioCapture> microphoneFactory=dev.optic.audio.MicrophoneCapture::new;
    public RecordingSession(int width,int height,int fps,Path partial,VideoEncoder encoder) {
        this(width,height,fps,partial,encoder,new DirectFramePool(6,Math.multiplyExact(Math.multiplyExact(width,height),4)));
    }
    public RecordingSession(int width,int height,int fps,Path partial,VideoEncoder encoder,boolean persistent) {
        this(width,height,fps,partial,encoder,new DirectFramePool(persistent?0:6,Math.multiplyExact(Math.multiplyExact(width,height),4)));
        this.persistentCapture=persistent;
    }
    RecordingSession(int width,int height,int fps,Path partial,VideoEncoder encoder,FrameBufferPool pool) {
        this.width=width; this.height=height; this.fps=fps; this.partial=partial; this.encoder=encoder;
        this.pool=pool;
    }
    public void start() { startNanos=System.nanoTime(); }
    /** Preparation worker only, before start/consume; caller has destroyed failed GL allocations. */
    public void preparePortableCapture() {
        if(startNanos!=0) throw new IllegalStateException("Cannot replace a running capture pool");
        var replacement=new DirectFramePool(6,Math.multiplyExact(Math.multiplyExact(width,height),4));
        pool.close(); pool=replacement; persistentCapture=false;
    }
    public synchronized long activeNanos(long now) {
        if(startNanos==0) return 0;
        long excluded=pausedNanos+(paused?Math.max(0,now-pauseStartedNanos):0);
        return Math.max(0,now-startNanos-excluded);
    }
    public long frameIndex(long now) { return activeNanos(now)*fps/1_000_000_000L; }
    public boolean paused() { return paused; }
    public synchronized boolean togglePaused() {
        if(stopping || startNanos==0) return paused;
        return togglePaused(System.nanoTime());
    }
    synchronized boolean togglePaused(long now) {
        if(stopping || startNanos==0) return paused;
        if(paused) { pausedNanos+=Math.max(0,now-pauseStartedNanos); pauseStartedNanos=0; paused=false; }
        else { paused=true; pauseStartedNanos=now; }
        if(activeMicrophone!=null) activeMicrophone.setPaused(paused);
        if(activeGame!=null) activeGame.setPaused(paused);
        return paused;
    }
    public void stop() { stopAt(frameIndex(System.nanoTime())); }
    void stopAt(long index) { if(!stopping) { endIndex=index; stopNanos=System.nanoTime(); stopping=true; if(activeMicrophone!=null) activeMicrophone.requestStop(); if(activeGame!=null) activeGame.requestStop(); } }
    public double durationSeconds() { return activeNanos(stopNanos==0?System.nanoTime():stopNanos)/1e9; }
    public void fail(Exception failure) { requestedFailure=failure; stop(); }
    public void abort() { fail(new java.io.IOException("Recording cancelled during shutdown; partial files retained")); encoder.abort(); }
    public void discardPrepared() throws Exception { encoder.abort(); try { encoder.close(); } finally { pool.close(); } }
    public void consume() throws Exception {
        CompletableFuture<dev.optic.audio.AudioCapture> game=new CompletableFuture<>();
        if(gameAudioEnabled) {
            Thread setup=new Thread(() -> {
                try {
                    var source=dev.optic.audio.AudioDevices.start(() -> { var capture=new dev.optic.audio.MinecraftAudioCapture(startNanos,gameControls); capture.storage(audioStorage); return capture; },SessionFiles.sibling(partial,".game.pcm"));
                    activeGame=source; source.setPaused(paused); if(stopping) source.requestStop();
                    game.complete(source);
                } catch(Exception e) { notice.accept("Game audio unavailable on this system; other tracks continue."); LOG.warn("Game audio setup failed",e); game.complete(null); }
            },"Optic game audio setup"); setup.setDaemon(true); setup.start();
        } else game.complete(null);
        dev.optic.audio.AudioCapture microphone=null;
        if(microphoneEnabled && !stopping) {
            try {
                microphone=dev.optic.audio.AudioDevices.start(() -> { var source=microphoneFactory.apply(microphoneDevice); source.configure(startNanos,microphoneControls,microphoneReconnect); source.storage(audioStorage); return source; },SessionFiles.sibling(partial,".mic.pcm"));
                activeMicrophone=microphone; microphone.setPaused(paused); if(stopping) microphone.requestStop();
                Files.writeString(partial.resolveSibling(partial.getFileName().toString().replace(".partial.mp4",".mic-offset.txt")),Long.toString(Math.max(0,(microphone.startedNanos()-startNanos)/1_000_000)));
            } catch(Exception e) { if(microphone!=null) try { dev.optic.audio.AudioDevices.close(microphone); } catch(Exception ignored) {} microphone=null; notice.accept("Microphone unavailable; recording video only."); LOG.warn("Microphone failed",e); }
        }
        FrameBufferPool.Frame last=null;
        FrameBufferPool.Frame pending=null;
        long next=0, lastLog=System.nanoTime(), lastFrame=lastLog, lastHealth=0, previousBytes=0;
        boolean microphoneWarned=false;
        boolean gameWarned=false;
        long lastClipWarning=0, reconnectCount=0;
        Exception primary=null;
        try {
            while(!stopping || !frames.isEmpty()) {
                if(requestedFailure!=null) throw requestedFailure;
                encoder.checkHealth();
                var storageError=storageFailure.get(); if(storageError!=null) throw storageError;
                long now=System.nanoTime();
                if(now-lastHealth>=1_000_000_000L) {
                    lastHealth=now;
                    if(microphoneControls.clippingUntil>now && now-lastClipWarning>10_000_000_000L) { lastClipWarning=now; notice.accept("Microphone clipping — lower input gain in Audio settings."); }
                    if(microphoneControls.reconnects>reconnectCount) { reconnectCount=microphoneControls.reconnects; notice.accept("Selected microphone reconnected."); }
                    var gameSource=game.getNow(null);
                    if(gameSource!=null && gameSource.failure()!=null && !gameWarned) { gameWarned=true; notice.accept("Game audio interrupted; video and microphone continue."); LOG.warn("Game audio failed",gameSource.failure()); }
                    if(Files.exists(partial) && Files.getFileStore(partial).getUsableSpace()<64L*1024*1024) throw new java.io.IOException("Disk is almost full; recording stopped to preserve existing footage");
                    if(microphone!=null && microphone.failure()!=null && !microphoneWarned) {
                        microphoneWarned=true; notice.accept("Microphone disconnected; video continues. Audio captured so far is retained."); LOG.warn("Microphone failed during recording",microphone.failure());
                    }
                }
                var incoming=frames.poll(100,TimeUnit.MILLISECONDS);
                if(incoming==null) {
                    if(paused) { lastFrame=System.nanoTime(); continue; }
                    if(!stopping && System.nanoTime()-lastFrame>10_000_000_000L) throw new java.io.IOException("No captured frames for 10 seconds; partial video retained");
                    continue;
                }
                lastFrame=System.nanoTime();
                if(incoming.index<next) { pool.release(incoming); continue; }
                if(incoming.index-next>fps*5L) { pool.release(incoming); throw new java.io.IOException("Encoder cannot keep up; stopped to protect game performance"); }
                pending=incoming;
                if(last==null) { last=incoming; pending=null; }
                while(next<incoming.index) { encoder.write(last.pixels); metrics.encoded.increment(); metrics.duplicated.increment(); next++; }
                if(last!=incoming) { pool.release(last); last=incoming; pending=null; }
                encoder.write(last.pixels); metrics.encoded.increment(); next++;
                metrics.queue(frames.size()); metrics.encoderFrames=encoder.encodedFrames();
                if(!overloadStopRequested && stopOnSustainedDuplicates && overloadGuard.sample(System.nanoTime(),metrics.encoded.sum(),metrics.duplicated.sum())) {
                    overloadStopRequested=true;
                    notice.accept("Recording stopping: duplicate frames exceeded 10% for three sustained windows. Quality was not changed.");
                    stop();
                }
                if(System.nanoTime()-lastLog>TimeUnit.SECONDS.toNanos(5)) {
                    long bytes=externalBytes.getAsLong(); if(bytes<0) bytes=Files.exists(partial)?Files.size(partial):0;
                    metrics.outputBytes=bytes;
                    metrics.writeBytesPerSecond=(bytes-previousBytes)/((System.nanoTime()-lastLog)/1e9); previousBytes=bytes;
                    LOG.info(metrics.report(durationSeconds(),bytes)); lastLog=System.nanoTime();
                }
            }
            if(requestedFailure!=null) throw requestedFailure;
            if(last==null) throw new java.io.IOException("No frames were captured");
            // Keep wall-clock duration when frames were skipped, including a short final gap.
            if(endIndex-next>fps*5L) throw new java.io.IOException("Capture stalled; partial recording retained");
            while(next<=endIndex) { encoder.write(last.pixels); metrics.encoded.increment(); metrics.duplicated.increment(); next++; }
        } catch(Exception e) { primary=e; throw e;
        } finally {
            try { var source=game.get(8,TimeUnit.SECONDS); if(source!=null) source.close(); }
            catch(Exception e) { LOG.warn("Game audio shutdown delayed",e); game.thenAccept(source -> { if(source!=null) try { source.close(); } catch(Exception ignored) {} }); }
            if(microphone!=null) try { dev.optic.audio.AudioDevices.close(microphone); } catch(Exception e) { LOG.warn("Microphone shutdown failed",e); notice.accept("Microphone interrupted; video retained."); }
            if(last!=null) pool.release(last);
            if(pending!=null) pool.release(pending);
            var f=frames.poll(); while(f!=null) { pool.release(f); f=frames.poll(); }
            try { encoder.close(); } catch(Exception closeFailure) { if(primary!=null) primary.addSuppressed(closeFailure); else throw closeFailure; }
            finally { metrics.encoderFrames=encoder.encodedFrames(); }
        }
    }
}
