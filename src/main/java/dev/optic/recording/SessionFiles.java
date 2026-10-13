package dev.optic.recording;

import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;

/** Stable sibling lock files coordinate recorder, recovery and another game instance. */
public final class SessionFiles implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;
    private SessionFiles(FileChannel channel, FileLock lock) { this.channel = channel; this.lock = lock; }
    public static Path sibling(Path partial, String suffix) {
        String name = partial.getFileName().toString();
        if (!name.endsWith(".partial.mp4")) throw new IllegalArgumentException("Not an Optic partial video");
        String stem=name.substring(0, name.length() - ".partial.mp4".length());
        if(suffix.equals(".mp4") && partial.getParent().getFileName().toString().equals(stem)) return partial.getParent().resolveSibling(stem+suffix);
        return partial.resolveSibling(stem + suffix);
    }
    public static SessionFiles tryLock(Path partial) throws IOException {
        Path path = sibling(partial, ".lock");
        if (Files.isSymbolicLink(path)) throw new IOException("Refusing symbolic session lock");
        FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) { channel.close(); return null; }
            return new SessionFiles(channel, lock);
        } catch (OverlappingFileLockException e) { channel.close(); return null; }
        catch (IOException | RuntimeException e) { channel.close(); throw e; }
    }
    public void close() throws IOException { try { lock.release(); } finally { channel.close(); } }
}
