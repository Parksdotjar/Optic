package dev.optic.encoding;
import dev.optic.config.*;
import dev.optic.recording.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

public final class AdvancedIntegration {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]).resolve("run-"+System.nanoTime()); Files.createDirectories(root);
        Path ffmpeg=new FFmpegManager(root.resolve("tools")).obtain(); var cancel=new Cancellation();
        var codecs=new ArrayList<String>(); codecs.add("libx264");
        for(String encoder:AdvancedEncoders.CANDIDATES) {
            boolean usable=AdvancedEncoders.probe(ffmpeg,encoder,1920,1080,60,VideoQuality.STANDARD,ColorMode.SDR_SRGB,root,cancel);
            System.out.println(encoder+" actual RGB/mux/decode probe: "+usable); if(usable) codecs.add(encoder);
        }
        for(String codec:codecs) {
            Path partial=RecordingStorage.reserve(root,codec,RecordingStorage.Style.DEFAULT);
            var manifest=new SessionManifest(); manifest.width=384; manifest.height=256; manifest.fps=30; manifest.encoder=codec; manifest.outputDirectory=root.toAbsolutePath().toString(); manifest.outputFile=partial.getParent().getFileName()+".mp4"; manifest.save(partial);
            ByteBuffer frame=ByteBuffer.allocate(384*256*4);
            int[][] colors={{0,0,0},{255,255,255},{128,128,128},{255,0,0},{0,255,0},{0,0,255}}; for(int y=0;y<256;y++) for(int x=0;x<384;x++) { int[] c=colors[x/64]; frame.put((byte)c[0]).put((byte)c[1]).put((byte)c[2]).put((byte)255); }
            try(var encoder=new FFmpegVideoEncoder(ffmpeg,codec,384,256,30,partial,root.resolve(codec+"-encode.log"),VideoQuality.HIGH,false,ColorMode.SDR_SRGB)) { for(int i=0;i<60;i++) encoder.write(frame); }
            Files.write(SessionFiles.sibling(partial,".game.pcm"),new byte[48000*4*2]); Files.write(SessionFiles.sibling(partial,".mic.pcm"),new byte[48000*2*2]); Files.writeString(SessionFiles.sibling(partial,".mic-offset.txt"),"0");
            var result=RecordingRecovery.finalizeOwned(ffmpeg,partial,cancel); Path video=result.file();
            if(!result.gameAudioIncluded() || !result.microphoneIncluded()) throw new AssertionError("Advanced audio mux lost a track");
            Path colorLog=root.resolve(codec+"-color.log");
            if(!Processes.run(List.of(ffmpeg.toString(),"-hide_banner","-nostdin","-i",video.toString(),"-vf","showinfo","-frames:v","1","-an","-f","null","-"),colorLog,30)) throw new AssertionError("Color inspection failed");
            String tags=Files.readString(colorLog); if(!tags.contains("color_primaries:bt709") || !tags.contains("color_trc:iec61966-2-1") || !tags.contains("color_range:tv") || !tags.contains("color_space:bt709")) throw new AssertionError("Color metadata lost: "+codec);
            if(MediaValidation.validate(ffmpeg,video,root.resolve(codec+"-validation.log"),false,cancel)!=60) throw new AssertionError("Wrong duration: "+codec);
            Path raw=root.resolve(codec+".rgb");
            if(!Processes.run(List.of(ffmpeg.toString(),"-hide_banner","-loglevel","error","-nostdin","-n","-i",video.toString(),"-frames:v","1","-pix_fmt","rgb24","-f","rawvideo",raw.toString()),root.resolve(codec+"-rgb.log"),30)) throw new AssertionError("RGB decode failed");
            byte[] rgb=Files.readAllBytes(raw); int black=rgb[(128*384+32)*3]&255,white=rgb[(128*384+96)*3]&255;
            if(black>4 || white<251) throw new AssertionError("Range mismatch "+codec+": "+black+"/"+white);
            for(int patch=0;patch<6;patch++) for(int ch=0;ch<3;ch++) if(Math.abs((rgb[(128*384+patch*64+32)*3+ch]&255)-colors[patch][ch])>8) throw new AssertionError("Color patch mismatch: "+codec+" patch "+patch);
            System.out.println("PASS "+codec+" 60 frames SDR black/white "+black+"/"+white);
        }
        System.out.println("Advanced evidence: "+root);
    }
}
