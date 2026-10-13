package dev.optic.encoding;

import java.nio.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.util.*;

/** Explicit integration task; no real microphone is opened. Requires local or obtainable FFmpeg. */
public final class EncoderIntegration {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]); Files.createDirectories(root);
        Path ffmpeg=new FFmpegManager(root.resolve(".ffmpeg")).obtain();
        String codec=new EncoderDetector().detect(ffmpeg,"auto",320,180,30,root.resolve("probe.log"));
        Path partial=root.resolve("Optic-synthetic-"+System.nanoTime()+".partial.mp4");
        ByteBuffer pixels=ByteBuffer.allocate(320*180*4);
        for(int i=0;i<320*180;i++) pixels.put(new byte[]{(byte)210,40,20,(byte)255});
        pixels.flip();
        try(var encoder=new FFmpegVideoEncoder(ffmpeg,codec,320,180,30,partial,root.resolve("encode.log"))) { for(int i=0;i<90;i++) encoder.write(pixels); }
        Path damaged=root.resolve("Optic-truncated-"+System.nanoTime()+".partial.mp4");
        Files.copy(partial,damaged);
        try(var file=FileChannel.open(damaged,StandardOpenOption.WRITE)) { file.truncate(Files.size(damaged)-100); }
        Path recovered=RecordingRecovery.finalizeVideo(ffmpeg,damaged);
        if(!Processes.run(List.of(ffmpeg.toString(),"-v","error","-i",recovered.toString(),"-f","null","-"),root.resolve("decode-recovered.log"),30)) throw new AssertionError("Recovered file does not decode");
        Path pcm=partial.resolveSibling(partial.getFileName().toString().replace(".partial.mp4",".mic.pcm"));
        byte[] audio=new byte[48000*2*3]; ByteBuffer samples=ByteBuffer.wrap(audio).order(ByteOrder.LITTLE_ENDIAN);
        for(int i=0;i<48000*3;i++) samples.putShort((short)(Math.sin(i*2*Math.PI*440/48000)*5000));
        Files.write(pcm,audio); Files.writeString(partial.resolveSibling(partial.getFileName().toString().replace(".partial.mp4",".mic-offset.txt")),"100");
        Path finished=RecordingRecovery.finalizeVideo(ffmpeg,partial);
        Path cleanVideos=root.resolve("videos");
        Path named=dev.optic.recording.RecordingStorage.reserve(cleanVideos,"Bounty SMP",dev.optic.recording.RecordingStorage.Style.PART);
        var namedManifest=new dev.optic.recording.SessionManifest(); namedManifest.outputDirectory=cleanVideos.toString(); namedManifest.outputFile=named.getParent().getFileName()+".mp4"; namedManifest.save(named);
        Files.copy(finished,named);
        Path namedOutput=RecordingRecovery.finalizeVideo(ffmpeg,named);
        if(!namedOutput.equals(cleanVideos.resolve(namedManifest.outputFile))) throw new AssertionError("Named video published to wrong folder");
        try(var entries=Files.list(cleanVideos)) { if(entries.anyMatch(p -> !p.getFileName().toString().endsWith(".mp4"))) throw new AssertionError("Details leaked into videos folder"); }
        Path dualDirectory=root.resolve("Optic-dual-"+System.nanoTime()); Files.createDirectory(dualDirectory);
        Path dual=dualDirectory.resolve(dualDirectory.getFileName()+".partial.mp4"); Files.copy(finished,dual);
        Files.write(dev.optic.recording.SessionFiles.sibling(dual,".mic.pcm"),audio);
        Files.writeString(dev.optic.recording.SessionFiles.sibling(dual,".mic-offset.txt"),"0");
        Files.write(dev.optic.recording.SessionFiles.sibling(dual,".game.pcm"),new byte[48000*4*3]);
        Path dualOutput=RecordingRecovery.finalizeVideo(ffmpeg,dual);
        if(!dualOutput.getParent().equals(root)) throw new AssertionError("Video was not placed beside support folder");
        if(!Processes.run(List.of(ffmpeg.toString(),"-v","error","-i",dualOutput.toString(),"-map","0:a:0","-map","0:a:1","-f","null","-"),root.resolve("decode-dual.log"),30)) throw new AssertionError("Two audio tracks did not decode");
        if(!Processes.run(List.of(ffmpeg.toString(),"-v","error","-i",finished.toString(),"-map","0:a:0","-f","null","-"),root.resolve("decode-audio.log"),30)) throw new AssertionError("Audio missing or invalid");
        Path corrupt=root.resolve("Optic-corrupt-"+System.nanoTime()+".partial.mp4"); Files.writeString(corrupt,"not a video");
        try { RecordingRecovery.finalizeVideo(ffmpeg,corrupt); throw new AssertionError("Corrupt input accepted"); }
        catch(java.io.IOException expected) { if(!Files.exists(corrupt)) throw new AssertionError("Corrupt source was deleted"); }
        Path conflict=root.resolve("Optic-conflict-"+System.nanoTime()+".partial.mp4"); Files.copy(finished,conflict);
        Path existing=dev.optic.recording.SessionFiles.sibling(conflict,".mp4"); Files.writeString(existing,"user file");
        try { RecordingRecovery.finalizeVideo(ffmpeg,conflict); throw new AssertionError("Existing output overwritten"); }
        catch(java.io.IOException expected) { if(!Files.readString(existing).equals("user file") || !Files.exists(conflict)) throw new AssertionError("Conflict lost user data"); }
        Path locked=root.resolve("Optic-locked-"+System.nanoTime()+".partial.mp4"); Files.copy(finished,locked);
        try(var lock=dev.optic.recording.SessionFiles.tryLock(locked)) {
            try { RecordingRecovery.finalizeVideo(ffmpeg,locked); throw new AssertionError("Active source recovered"); }
            catch(java.io.IOException expected) { if(!Files.exists(locked)) throw new AssertionError("Active source deleted"); }
        }
        Path brokenAudio=root.resolve("Optic-bad-mic-"+System.nanoTime()+".partial.mp4"); Files.copy(finished,brokenAudio);
        Path brokenPcm=dev.optic.recording.SessionFiles.sibling(brokenAudio,".mic.pcm"); Files.write(brokenPcm,new byte[960]);
        Files.writeString(dev.optic.recording.SessionFiles.sibling(brokenAudio,".mic-offset.txt"),"bad offset");
        var fallback=RecordingRecovery.finalizeVideo(ffmpeg,brokenAudio);
        if(!Files.exists(fallback) || !Files.exists(brokenPcm)) throw new AssertionError("Microphone failure did not preserve video/audio source");
        Path killed=root.resolve("Optic-killed-"+System.nanoTime()+".partial.mp4");
        var interrupted=new FFmpegVideoEncoder(ffmpeg,codec,320,180,30,killed,root.resolve("killed-encoder.log"));
        for(int i=0;i<150;i++) interrupted.write(pixels);
        interrupted.abort(); Thread.sleep(200);
        try { interrupted.checkHealth(); throw new AssertionError("Dead encoder reported healthy"); } catch(java.io.IOException expected) {}
        try { interrupted.close(); } catch(java.io.IOException expected) {}
        Path repaired=RecordingRecovery.finalizeVideo(ffmpeg,killed);
        if(!Files.exists(repaired)) throw new AssertionError("Killed encoder fragments were not recovered");
        System.out.println("PASS: "+codec+", synthetic AAC track, decoded remux, truncated-fragment recovery: "+finished);
        System.out.println("PASS: corrupt input retained, output collision preserved, active session skipped, invalid audio falls back to video");
        System.out.println("PASS: forcibly terminated encoder detected and its completed fragments recovered");
    }
}
