package dev.optic.encoding;

import dev.optic.recording.*;
import dev.optic.audio.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class RecordingRecovery {
    public record Result(Path file, long decodedFrames, boolean microphoneIncluded, boolean gameAudioIncluded, String warning) {}
    public static Path finalizeVideo(Path ffmpeg, Path partial) throws Exception {
        try (var lock = SessionFiles.tryLock(partial)) {
            if (lock == null) throw new IOException("Recording is active in another instance");
            return finalizeOwned(ffmpeg, partial, new Cancellation()).file();
        }
    }
    public static Result finalizeOwned(Path ffmpeg, Path partial, Cancellation cancellation) throws Exception {
        cancellation.check();
        if (Files.isSymbolicLink(partial)) throw new IOException("Refusing symbolic recording source");
        SessionManifest.read(partial);
        Path target = RecordingStorage.target(partial);
        if (Files.exists(target)) throw new IOException("Completed filename already exists; originals retained");
        Path temp = SessionFiles.sibling(partial, ".finalizing-" + UUID.randomUUID() + ".mp4");
        Path log = SessionFiles.sibling(partial, ".recovery.log");
        var tracks=new ArrayList<AudioTrack>(); String warning="";
        Path game=SessionFiles.sibling(partial,".game.pcm"), mic=SessionFiles.sibling(partial,".mic.pcm"), offset=SessionFiles.sibling(partial,".mic-offset.txt");
        if(Files.isRegularFile(game) && Files.size(game)>=4) tracks.add(new AudioTrack(game,2,0,"Minecraft (including mod audio)"));
        if(Files.isRegularFile(mic) && Files.size(mic)>=2) {
            try {
                long delay=Long.parseLong(Files.readString(offset).trim());
                if(delay<0 || delay>60000) throw new IOException("Invalid microphone offset");
                tracks.add(new AudioTrack(mic,1,delay,"Microphone"));
            } catch(Exception e) { warning="Microphone metadata invalid; audio source retained."; }
        }
        boolean microphone=false, gameAudio=false;
        try {
            if(!tracks.isEmpty()) {
                try { new FFmpegAudioEncoder().muxTracks(ffmpeg,partial,tracks,temp,log,cancellation); }
                catch(java.util.concurrent.CancellationException e) { throw e; }
                catch(Exception e) { cancellation.check(); Files.deleteIfExists(temp); tracks.clear(); warning="Audio could not be attached; source tracks retained."; }
            }
            if(tracks.isEmpty() && !Processes.run(List.of(ffmpeg.toString(),"-hide_banner","-loglevel","warning","-nostdin","-n","-i",partial.toString(),"-map","0:v:0","-c","copy","-movflags","+faststart",temp.toString()),log,180,cancellation))
                throw new IOException("Could not finalize; source retained");
            long decoded=MediaValidation.validate(ffmpeg,temp,SessionFiles.sibling(partial,".validation.log"),!tracks.isEmpty(),cancellation);
            microphone=tracks.stream().anyMatch(t -> t.pcm().equals(mic));
            gameAudio=tracks.stream().anyMatch(t -> t.pcm().equals(game));
            cancellation.check(); Files.move(temp,target);
            try {
                Files.delete(partial);
                for(var track:tracks) { Files.deleteIfExists(track.pcm()); if(track.pcm().equals(mic)) { microphone=true; Files.deleteIfExists(offset); } }
            } catch(IOException e) { warning="Video saved; some recovery sources could not be removed."; }
            return new Result(target,decoded,microphone,gameAudio,warning);
        } finally { try { Files.deleteIfExists(temp); } catch(IOException ignored) {} }
    }
}
