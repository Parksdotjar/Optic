package dev.optic.capture;

import org.lwjgl.system.MemoryUtil;
import java.util.concurrent.ArrayBlockingQueue;

public final class DirectFramePool implements FrameBufferPool {
    private final Frame[] frames;
    private final ArrayBlockingQueue<Frame> free;
    public DirectFramePool(int count, int bytes) {
        frames = new Frame[count]; free = new ArrayBlockingQueue<>(Math.max(1,count));
        try { for (int i = 0; i < count; i++) { frames[i] = new Frame(MemoryUtil.memAlloc(bytes)); free.add(frames[i]); } }
        catch (Throwable failure) { close(); throw failure; }
    }
    public Frame acquire() { return free.poll(); }
    public void release(Frame frame) { frame.pixels.clear(); if(frame.externalRelease!=null) { frame.externalRelease.run(); return; } if (!free.offer(frame)) throw new IllegalStateException("Frame released twice"); }
    public void close() { for (Frame frame : frames) if (frame != null) MemoryUtil.memFree(frame.pixels); }
}
