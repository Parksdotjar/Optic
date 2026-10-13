package dev.optic.recording;

import dev.optic.capture.FrameBufferPool;
import dev.optic.encoding.VideoEncoder;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RecordingSessionTest {
    @org.junit.jupiter.api.io.TempDir Path directory;
    static class Pool implements FrameBufferPool {
        Set<Frame> returned=Collections.newSetFromMap(new IdentityHashMap<>());
        public Frame acquire() { return new Frame(ByteBuffer.allocate(4)); }
        public void release(Frame f) { assertTrue(returned.add(f),"double release"); }
        public void close() {}
    }
    static class Sink implements VideoEncoder {
        List<Integer> pixels=new ArrayList<>(); boolean closed;
        public void write(ByteBuffer b) { pixels.add((int)b.get(0)); }
        public void close() { closed=true; }
    }
    static FrameBufferPool.Frame frame(long index,int value) { var f=new FrameBufferPool.Frame(ByteBuffer.wrap(new byte[]{(byte)value,0,0,0})); f.index=index; return f; }
    @Test void missingFrameRepeatsPreviousInsteadOfSpeedingUpVideo() throws Exception {
        var sink=new Sink(); var pool=new Pool(); var s=new RecordingSession(1,1,30,Path.of("unused"),sink,pool);
        s.start(); s.frames.add(frame(0,10)); s.frames.add(frame(2,30)); s.stopAt(2); s.consume();
        assertEquals(List.of(10,10,30),sink.pixels); assertEquals(2,pool.returned.size()); assertTrue(sink.closed); assertEquals(1,s.metrics.duplicated.sum());
    }
    @Test void queueIsBounded() {
        var s=new RecordingSession(1,1,30,Path.of("unused"),new Sink(),new Pool());
        assertTrue(s.frames.offer(frame(0,0))); assertTrue(s.frames.offer(frame(1,0))); assertTrue(s.frames.offer(frame(2,0))); assertFalse(s.frames.offer(frame(3,0)));
    }
    @Test void pausedTimeIsRemovedFromFrameTimeline() {
        var s=new RecordingSession(1,1,30,Path.of("unused"),new Sink(),new Pool());
        s.startNanos=1_000_000_000L;
        assertTrue(s.togglePaused(2_000_000_000L));
        assertEquals(30,s.frameIndex(5_000_000_000L));
        assertFalse(s.togglePaused(5_000_000_000L));
        assertEquals(60,s.frameIndex(6_000_000_000L));
    }
    @Test void sustainedBacklogStopsAndReleasesFrames() {
        var sink=new Sink(); var pool=new Pool(); var s=new RecordingSession(1,1,30,Path.of("unused"),sink,pool);
        s.start(); s.frames.add(frame(0,10)); s.frames.add(frame(200,30)); s.stop();
        assertThrows(java.io.IOException.class,s::consume); assertTrue(sink.closed); assertEquals(2,pool.returned.size());
    }
    @Test void noFramesDoesNotReportSuccess() {
        var sink=new Sink(); var s=new RecordingSession(1,1,30,Path.of("unused"),sink,new Pool()); s.start(); s.stop();
        assertThrows(java.io.IOException.class,s::consume); assertTrue(sink.closed);
    }
    @Test void externalMappingLeaseIsRetainedThroughDuplicateWritesThenReleased() throws Exception {
        int[] releases={0};
        var pool=new dev.optic.capture.DirectFramePool(0,4);
        var sink=new Sink(); var s=new RecordingSession(1,1,30,Path.of("unused"),sink,pool);
        var a=new FrameBufferPool.Frame(ByteBuffer.wrap(new byte[]{10,0,0,0}),() -> releases[0]++); a.index=0;
        var b=new FrameBufferPool.Frame(ByteBuffer.wrap(new byte[]{30,0,0,0}),() -> releases[0]++); b.index=2;
        s.start(); s.frames.add(a); s.frames.add(b); s.stopAt(2); s.consume();
        assertEquals(List.of(10,10,30),sink.pixels); assertEquals(2,releases[0]); pool.close();
    }
    @Test void failureWhileDuplicatingReleasesBothPreviousAndPendingFrames() {
        var pool=new Pool();
        var encoder=new VideoEncoder() {
            int writes;
            public void write(ByteBuffer pixels) throws java.io.IOException { if(++writes==2) throw new java.io.IOException("pipe failure"); }
            public void close() throws java.io.IOException { throw new java.io.IOException("close failure"); }
        };
        var s=new RecordingSession(1,1,30,Path.of("unused"),encoder,pool); s.start();
        s.frames.add(frame(0,10)); s.frames.add(frame(3,30)); s.stopAt(3);
        var error=assertThrows(java.io.IOException.class,s::consume);
        assertEquals("pipe failure",error.getMessage()); assertEquals(1,error.getSuppressed().length); assertEquals(2,pool.returned.size());
    }
    @Test void hundredShortSessionsCloseTheirEncoderAndReturnEveryFrame() throws Exception {
        for(int i=0;i<100;i++) {
            var pool=new Pool(); var sink=new Sink(); var s=new RecordingSession(1,1,60,Path.of("unused"),sink,pool);
            s.start(); s.frames.add(frame(0,10)); s.frames.add(frame(1,20)); s.stopAt(1); s.consume();
            assertEquals(2,pool.returned.size()); assertTrue(sink.closed); assertEquals(List.of(10,20),sink.pixels);
        }
    }
    @Test void deniedMicrophoneDoesNotPreventVideoFromCompleting() throws Exception {
        var pool=new Pool(); var notices=new ArrayList<String>();
        var sessionRef=new java.util.concurrent.atomic.AtomicReference<RecordingSession>();
        var sink=new Sink() { @Override public void write(ByteBuffer pixels) { super.write(pixels); sessionRef.get().stopAt(0); } };
        var s=new RecordingSession(1,1,30,directory.resolve("Optic-audio.partial.mp4"),sink,pool); sessionRef.set(s);
        s.microphoneEnabled=true; s.notice=notices::add;
        s.microphoneFactory=name -> new dev.optic.audio.AudioCapture() {
            public void start(Path path) throws Exception { throw new javax.sound.sampled.LineUnavailableException("Test device denied"); }
            public long startedNanos() { return 0; }
            public void close() {}
        };
        s.start(); s.frames.add(frame(0,10)); s.consume();
        assertEquals(List.of(10),sink.pixels); assertTrue(sink.closed); assertTrue(notices.stream().anyMatch(n -> n.contains("Microphone unavailable")));
    }
    @Test void disconnectedMicrophoneIsReportedWhileVideoContinues() throws Exception {
        var ref=new java.util.concurrent.atomic.AtomicReference<RecordingSession>(); var notices=new ArrayList<String>();
        var sink=new Sink() { @Override public void write(ByteBuffer pixels) { super.write(pixels); ref.get().stopAt(0); } };
        var s=new RecordingSession(1,1,30,directory.resolve("Optic-disconnect.partial.mp4"),sink,new Pool()); ref.set(s);
        s.microphoneEnabled=true; s.notice=notices::add;
        s.microphoneFactory=name -> new dev.optic.audio.AudioCapture() {
            public void start(Path path) {}
            public long startedNanos() { return System.nanoTime(); }
            public java.io.IOException failure() { return new java.io.IOException("Test unplug"); }
            public void close() {}
        };
        s.start(); s.frames.add(frame(0,20)); s.consume();
        assertEquals(List.of(20),sink.pixels); assertTrue(notices.stream().anyMatch(n -> n.contains("disconnected")));
    }
}
