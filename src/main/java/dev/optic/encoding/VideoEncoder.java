package dev.optic.encoding;
import java.nio.ByteBuffer;
import java.io.IOException;
public interface VideoEncoder extends AutoCloseable {
    void write(ByteBuffer rgba) throws IOException;
    default void checkHealth() throws IOException {}
    default long encodedFrames() { return -1; }
    /** Must be safe from a control worker while write is blocked. */
    default void abort() {}
    @Override void close() throws IOException;
}
