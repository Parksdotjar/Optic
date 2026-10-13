package dev.optic.audio;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;

/** 250 ms bounded PCM queue. Capture callers copy into reused blocks; only this worker writes disk. */
public final class PcmTrackWriter implements AutoCloseable {
    private static final int BLOCK=480, CAPACITY=25;
    private static final class Block { final byte[] data; int frames; long index; Block(int channels) { data=new byte[BLOCK*channels*2]; } }
    private final ArrayBlockingQueue<Block> free, ready;
    private final int channels;
    private final long origin;
    private final AudioControls controls;
    private final Thread worker;
    private volatile boolean stopping;
    private volatile boolean paused;
    private long pauseStartedNanos, pausedNanos;
    private volatile IOException failure;
    public PcmTrackWriter(Path path,int channels,long origin,AudioControls controls) throws IOException {
        this(path,channels,origin,controls,CAPACITY);
    }
    public PcmTrackWriter(Path path,int channels,long origin,AudioControls controls,int capacity) throws IOException {
        this(path,channels,origin,controls,capacity,PcmStorage.FILE);
    }
    public PcmTrackWriter(Path path,int channels,long origin,AudioControls controls,int capacity,PcmStorage storage) throws IOException {
        this.channels=channels; this.origin=origin; this.controls=controls;
        free=new ArrayBlockingQueue<>(capacity); ready=new ArrayBlockingQueue<>(capacity);
        for(int i=0;i<capacity;i++) free.add(new Block(channels));
        OutputStream output=storage.open(path,channels);
        worker=new Thread(() -> write(output),"Optic PCM writer"); worker.setDaemon(true); worker.start();
    }
    public void offer(byte[] pcm,int frames,long timestamp) {
        long adjusted;
        synchronized(this) {
            if(stopping || failure!=null || paused) return;
            adjusted=timestamp-origin-pausedNanos;
        }
        long index=Math.max(0,Math.round(adjusted*48000d/1e9));
        for(int offset=0;offset<frames;offset+=BLOCK) {
            int n=Math.min(BLOCK,frames-offset); var b=free.poll();
            if(b==null) { controls.droppedFrames+=n; continue; }
            System.arraycopy(pcm,offset*channels*2,b.data,0,n*channels*2); b.frames=n; b.index=index+offset;
            if(!ready.offer(b)) { free.offer(b); controls.droppedFrames+=n; }
        }
    }
    private void write(OutputStream output) {
        long written=0; byte[] silence=new byte[BLOCK*channels*2];
        int clippedBlocks=0;
        try(output) {
            while(!stopping || !ready.isEmpty()) {
                var b=ready.poll(100,TimeUnit.MILLISECONDS); if(b==null) continue;
                try {
                    long gap=b.index-written;
                    // Timestamp gaps (device loss or queue pressure) remain silence, never collapse time.
                    while(gap>0) { int n=(int)Math.min(BLOCK,gap); output.write(silence,0,n*channels*2); written+=n; gap-=n; }
                    int skip=(int)Math.min(b.frames,Math.max(0,written-b.index));
                    double peak=0, gain=controls.muted?0:controls.gain; boolean clipped=false;
                    for(int i=skip*channels*2;i<b.frames*channels*2;i+=2) {
                        int sample=(short)((b.data[i]&255)|(b.data[i+1]<<8)); double scaled=sample*gain;
                        peak=Math.max(peak,Math.abs(scaled)/32768d); clipped|=Math.abs(scaled)>=32767;
                        int value=(int)Math.max(-32768,Math.min(32767,scaled)); b.data[i]=(byte)value; b.data[i+1]=(byte)(value>>8);
                    }
                    controls.peak=peak; clippedBlocks=clipped?clippedBlocks+1:0;
                    if(clippedBlocks>=3) controls.clippingUntil=System.nanoTime()+1_000_000_000L;
                    output.write(b.data,skip*channels*2,(b.frames-skip)*channels*2); written+=b.frames-skip;
                } finally { free.offer(b); }
            }
        } catch(Exception e) { failure=new IOException("Audio file writer failed",e); }
        finally { controls.peak=0; }
    }
    public IOException failure() { return failure; }
    public synchronized void setPaused(boolean value) {
        if(value==paused || stopping) return;
        setPaused(value,System.nanoTime());
    }
    synchronized void setPaused(boolean value,long now) {
        if(value==paused || stopping) return;
        if(value) { paused=true; pauseStartedNanos=now; controls.peak=0; }
        else { pausedNanos+=Math.max(0,now-pauseStartedNanos); pauseStartedNanos=0; paused=false; }
    }
    public void close() throws Exception { stopping=true; worker.join(3000); if(worker.isAlive()) throw new IOException("Audio writer is still finishing; source retained"); if(failure!=null) throw failure; }
}
