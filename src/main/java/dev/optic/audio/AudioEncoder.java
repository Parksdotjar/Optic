package dev.optic.audio;

import java.nio.file.Path;

public interface AudioEncoder {
    void muxTracks(Path ffmpeg,Path video,java.util.List<AudioTrack> tracks,Path output,Path log,dev.optic.recording.Cancellation cancellation) throws Exception;
    void muxMicrophone(Path ffmpeg,Path video,Path pcm,long offsetMillis,Path output,Path log) throws Exception;
}
