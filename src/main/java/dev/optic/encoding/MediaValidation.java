package dev.optic.encoding;

import dev.optic.recording.Cancellation;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;

/** Decodes the candidate before recoverable sources are removed. Runs only off-thread. */
public final class MediaValidation {
    public static long validate(Path ffmpeg, Path video, Path progress, boolean expectAudio, Cancellation cancellation) throws Exception {
        Files.deleteIfExists(progress);
        var command = new java.util.ArrayList<>(List.of(ffmpeg.toString(), "-hide_banner", "-loglevel", "error", "-nostdin",
            "-xerror", "-threads", "2", "-i", video.toString(), "-map", "0:v:0", "-map", expectAudio ? "0:a" : "0:a?",
            "-progress", "pipe:1", "-stats_period", "5", "-f", "null", "-"));
        if (!Processes.run(command, progress, 600, cancellation)) throw new IOException("Output verification failed; source files retained");
        long frames = 0;
        try (var lines = Files.lines(progress)) {
            var iterator = lines.iterator();
            while (iterator.hasNext()) {
                String line = iterator.next();
                if (line.startsWith("frame=")) try { frames = Long.parseLong(line.substring(6).trim()); } catch (NumberFormatException ignored) {}
            }
        }
        if (frames < 1) throw new IOException("Output contains no decodable video frames");
        return frames;
    }
}
