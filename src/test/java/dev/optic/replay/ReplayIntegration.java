package dev.optic.replay;

import dev.optic.encoding.*;
import dev.optic.recording.*;
import dev.optic.config.*;
import java.nio.*;
import java.nio.file.*;

/** Synthetic real FFmpeg test; no Minecraft, microphone or render thread required. */
public final class ReplayIntegration {
    public static void main(String[] args) throws Exception {
        Path videos=Path.of(args[0]).resolve("run-"+System.nanoTime()); Files.createDirectories(videos);
        Path ffmpeg=new FFmpegManager(videos.resolve("tools")).obtain();
        Path partial=RecordingStorage.reserve(videos,"Full",RecordingStorage.Style.DEFAULT);
        var manifest=new SessionManifest(); manifest.outputDirectory=videos.toAbsolutePath().toString(); manifest.outputFile=partial.getParent().getFileName()+".mp4";
        manifest.width=64; manifest.height=64; manifest.fps=30; manifest.encoder="libx264"; manifest.save(partial);
        var history=new ReplayHistory(ffmpeg,partial,videos,manifest,15,true,true,System.out::println);
        try(var markers=new dev.optic.markers.MarkerJournal(partial,System.out::println)) {
            markers.offer(new dev.optic.markers.Marker(1,java.util.UUID.randomUUID().toString(),"source",30_000_000_000L,30,dev.optic.markers.Marker.Type.KEEP,"rebase me",null,true));
        }
        try {
            try(var game=history.audioStorage().open(partial,2); var mic=history.audioStorage().open(partial,1)) {
                byte[] a=new byte[48000*4],b=new byte[48000*2];
                for(int i=0;i<48000;i++) { short s=(short)(Math.sin(i*2*Math.PI*440/48000)*2000); for(int ch=0;ch<2;ch++) { a[i*4+ch*2]=(byte)s; a[i*4+ch*2+1]=(byte)(s>>8); } b[i*2]=(byte)s; b[i*2+1]=(byte)(s>>8); }
                for(int n=0;n<40;n++) { game.write(a); mic.write(b); }
            }
            try(var encoder=new FFmpegVideoEncoder(ffmpeg,"libx264",64,64,30,history.directory(),partial.resolveSibling("encoder.log"),VideoQuality.STANDARD,true)) {
                ByteBuffer pixels=ByteBuffer.allocate(64*64*4);
                for(int i=0;i<1200;i++) {
                    pixels.clear(); for(int p=0;p<64*64;p++) pixels.put((byte)(i/60*10)).put((byte)80).put((byte)120).put((byte)255);
                    encoder.write(pixels);
                }
            }
            long deadline=System.nanoTime()+10_000_000_000L;
            while(history.availableSeconds()<15 && history.failure()==null && System.nanoTime()<deadline) Thread.sleep(50);
            if(history.failure()!=null) throw history.failure();
            if(history.availableSeconds()<15) throw new AssertionError("History never became ready");
            if(Files.exists(ReplayExport.video(history.directory(),0))) throw new AssertionError("Old segment not evicted");
            history.promote(29.1);
            history.save(39); history.save(39); // second request must be refused, not another encoder/export
            while(history.saving() && System.nanoTime()<deadline+30_000_000_000L) Thread.sleep(50);
            if(history.saving() || history.retainedExports()) throw new AssertionError("Clip export did not succeed");
            try(var files=Files.list(videos)) { if(files.filter(p -> p.getFileName().toString().startsWith("Clip") && p.toString().endsWith(".mp4")).count()!=1) throw new AssertionError("Expected one clip"); }
            history.finish(true,new Cancellation());
            // Simulate a crash after clip admission and full concatenation, before either is published.
            Path interrupted=RecordingStorage.reserve(videos,"Interrupted",RecordingStorage.Style.DEFAULT);
            var pending=new SessionManifest(); pending.width=64; pending.height=64; pending.fps=30; pending.outputDirectory=videos.toAbsolutePath().toString(); pending.outputFile=interrupted.getParent().getFileName()+".mp4"; pending.save(interrupted);
            var plan=new java.util.Properties(); plan.setProperty("source",history.directory().toAbsolutePath().toString()); plan.setProperty("first","18"); plan.setProperty("last","19"); plan.setProperty("end","40"); plan.setProperty("game","true"); plan.setProperty("mic","true");
            try(var out=Files.newOutputStream(interrupted.resolveSibling("clip-plan.properties"))) { plan.store(out,""); }
            int recovered=ReplayRecovery.recover(ffmpeg,RecordingStorage.details(videos),System.out::println);
            if(recovered!=2) throw new AssertionError("Expected full recording and pending clip recovery: "+recovered);
            if(MediaValidation.validate(ffmpeg,RecordingStorage.target(partial),partial.resolveSibling("check.log"),true,new Cancellation())!=360) throw new AssertionError("Full recording length wrong");
            if(MediaValidation.validate(ffmpeg,RecordingStorage.target(interrupted),interrupted.resolveSibling("check.log"),true,new Cancellation())!=120) throw new AssertionError("Recovered clip length wrong");
            if(Files.exists(history.directory())) throw new AssertionError("Recovered cache was not cleaned");
            var timeline=dev.optic.markers.MarkerExport.write(ffmpeg,partial,partial,SessionManifest.read(partial));
            if(timeline.markers().size()!=1 || timeline.markers().getFirst().frame()!=60 || timeline.markers().getFirst().thumbnail()==null) throw new AssertionError("Marker rebase/thumbnail failed");
            System.out.println("PASS: encoded ring eviction, overlapping request refusal, clip stream-copy, shared full recording, two audio tracks: "+videos);
        } finally { history.discardPrepared(); }
    }
}
