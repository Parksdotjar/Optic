package dev.optic.audio;

import java.nio.file.Path;

/** Platform-neutral game-audio boundary. Windows uses process-scoped capture; never desktop loopback. */
public final class MinecraftAudioCapture implements AudioCapture {
    private final AudioCapture delegate;
    public MinecraftAudioCapture(long origin,AudioControls controls) { delegate=new WindowsProcessAudio(origin,controls); }
    public void start(Path destination) throws Exception { delegate.start(destination); }
    public void storage(PcmStorage storage) { delegate.storage(storage); }
    public long startedNanos() { return delegate.startedNanos(); }
    public java.io.IOException failure() { return delegate.failure(); }
    public void setPaused(boolean paused) { delegate.setPaused(paused); }
    public void requestStop() { delegate.requestStop(); }
    public void close() throws Exception { delegate.close(); }
}
