package dev.optic.recording;
import java.nio.file.Path;
public record CompletedRecording(Path file, double durationSeconds, int width, int height, int fps,
                                 String encoder, long decodedFrames, boolean microphone, boolean gameAudio, String warning) {
    public String durationLabel() {
        long seconds=(long)durationSeconds;
        return seconds>=3600?String.format(java.util.Locale.ROOT,"%d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60)
            :String.format(java.util.Locale.ROOT,"%02d:%02d",seconds/60,seconds%60);
    }
}
