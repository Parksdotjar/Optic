package dev.optic.encoding;
import dev.optic.config.*;
import dev.optic.recording.Cancellation;
import java.nio.file.*;
import java.util.*;

/** Probe the RGB conversion, requested encoder, fragmented MP4 mux and decoder together. */
public final class AdvancedEncoders {
    public static final List<String> CANDIDATES=List.of("hevc_nvenc","hevc_amf","hevc_qsv","av1_nvenc","av1_amf","av1_qsv");
    public static boolean isAdvanced(String name) { return name!=null && CANDIDATES.contains(name); }
    public static boolean probe(Path ffmpeg,String encoder,int w,int h,int fps,VideoQuality quality,ColorMode color,Path logs,Cancellation cancel) throws Exception {
        Files.createDirectories(logs); Path output=logs.resolve("probe-"+UUID.randomUUID()+".mp4"); Path log=logs.resolve(encoder+"-probe.log");
        try {
            var args=new ArrayList<>(List.of(ffmpeg.toString(),"-hide_banner","-loglevel","error","-nostdin","-n","-f","lavfi","-i","testsrc=size="+w+"x"+h+":rate="+fps+",format=rgba","-frames:v","6","-an"));
            args.addAll(EncoderDetector.options(encoder,fps,quality)); args.addAll(color.options());
            args.addAll(List.of("-movflags","+frag_keyframe+empty_moov+default_base_moof",output.toString()));
            if(!Processes.run(args,log,20,cancel)) return false;
            return MediaValidation.validate(ffmpeg,output,logs.resolve(encoder+"-decode.log"),false,cancel)==6;
        } catch(java.util.concurrent.CancellationException e) { throw e; }
        catch(Exception e) { return false; }
        finally { Files.deleteIfExists(output); }
    }
}
