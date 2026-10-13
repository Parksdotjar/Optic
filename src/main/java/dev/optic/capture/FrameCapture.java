package dev.optic.capture;

/** Methods are called on the context-owning render thread. No IO or waiting. */
public interface FrameCapture extends AutoCloseable {
    default boolean ready() { return true; }
    void capture(int textureId, int sourceWidth, int sourceHeight, long frameIndex);
    void poll();
    @Override void close();
}
