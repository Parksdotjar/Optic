package dev.optic.encoding;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
/** Diagnostic backend only. Raw RGBA requires externally supplied dimensions and FPS. */
public final class RawVideoEncoder implements VideoEncoder {
    private final FileChannel file;
    public RawVideoEncoder(Path path) throws IOException { file=FileChannel.open(path,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE); }
    public void write(ByteBuffer pixels) throws IOException { pixels.rewind(); while(pixels.hasRemaining()) file.write(pixels); }
    public void close() throws IOException { file.close(); }
}
