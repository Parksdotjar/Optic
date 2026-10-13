package dev.optic.capture;

import java.nio.ByteBuffer;

/** Ownership: acquire -> producer -> queue -> consumer -> release, exactly once. */
public interface FrameBufferPool extends AutoCloseable {
    final class Frame {
        public final ByteBuffer pixels;
        public long index;
        public final Runnable externalRelease;
        public Frame(ByteBuffer pixels) { this(pixels,null); }
        public Frame(ByteBuffer pixels,Runnable externalRelease) { this.pixels = pixels; this.externalRelease=externalRelease; }
    }
    Frame acquire();
    void release(Frame frame);
    @Override void close();
}
