package dev.optic.audio;
import java.nio.file.Path;
public record AudioTrack(Path pcm, int channels, long offsetMillis, String title) {}
