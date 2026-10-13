package dev.optic.audio;
import java.io.*;
import java.nio.file.*;
/** Worker-owned PCM destination. Replay can rotate files without changing capture/DSP. */
@FunctionalInterface public interface PcmStorage {
    OutputStream open(Path path,int channels) throws IOException;
    PcmStorage FILE=(path,channels) -> new BufferedOutputStream(Files.newOutputStream(path,StandardOpenOption.CREATE_NEW),32768);
}
