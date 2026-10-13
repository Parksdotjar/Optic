package dev.optic.encoding;
import java.io.*;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class Processes {
    private Processes() {}
    public static boolean run(List<String> command, Path log, int timeoutSeconds) throws IOException, InterruptedException {
        return run(command, log, timeoutSeconds, new dev.optic.recording.Cancellation());
    }
    public static boolean run(List<String> command, Path log, int timeoutSeconds, dev.optic.recording.Cancellation cancellation) throws IOException, InterruptedException {
        cancellation.check();
        Process p = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log == null ? ProcessBuilder.Redirect.DISCARD : ProcessBuilder.Redirect.appendTo(log.toFile())).start();
        try {
            p.getOutputStream().close();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
            while (!p.waitFor(100, TimeUnit.MILLISECONDS)) {
                cancellation.check();
                if (System.nanoTime() >= deadline) return false;
            }
            cancellation.check(); return p.exitValue() == 0;
        }
        finally { if(p.isAlive()) p.destroyForcibly(); }
    }
}
