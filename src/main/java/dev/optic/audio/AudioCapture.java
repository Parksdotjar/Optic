package dev.optic.audio;

import java.nio.file.Path;

/** Implementations own their audio thread. Never called from rendering. PCM: 48kHz s16le; track declares channels. */
public interface AudioCapture extends AutoCloseable {
    default void configure(long origin,AudioControls controls,boolean reconnect) {}
    default void storage(PcmStorage storage) {}
    void start(Path destination) throws Exception;
    long startedNanos();
    default java.io.IOException failure() { return null; }
    /** Drops captured samples while paused and collapses their timestamps on resume. Non-blocking. */
    default void setPaused(boolean paused) {}
    /** Non-blocking intent only; driver shutdown is performed by close off-thread. */
    default void requestStop() {}
    @Override void close() throws Exception;
}
