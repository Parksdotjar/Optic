package dev.optic.encoding;

import java.nio.file.Path;
import java.util.*;

/** Availability is proven by encoding frames at requested size/FPS, not by listing compiled codecs. */
public final class EncoderDetector {
    public static String label(String codec) { if(AdvancedEncoders.isAdvanced(codec)) return (codec.startsWith("hevc")?"HEVC":"AV1")+" / "+(codec.endsWith("nvenc")?"NVENC":codec.endsWith("amf")?"AMF":"Quick Sync"); return switch(codec) { case "h264_nvenc" -> "NVIDIA NVENC"; case "h264_amf" -> "AMD AMF"; case "h264_qsv" -> "Intel Quick Sync"; case "libx264" -> "Software"; default -> "Auto"; }; }
    public String detect(Path ffmpeg, String requested, int w, int h, int fps, Path log) throws Exception {
        return detect(ffmpeg, requested, w, h, fps, log, "", new dev.optic.recording.Cancellation());
    }
    public String detect(Path ffmpeg, String requested, int w, int h, int fps, Path log, String renderer, dev.optic.recording.Cancellation cancellation) throws Exception {
        return detect(ffmpeg,requested,w,h,fps,log,renderer,cancellation,dev.optic.config.VideoQuality.STANDARD);
    }
    public String detect(Path ffmpeg, String requested, int w, int h, int fps, Path log, String renderer, dev.optic.recording.Cancellation cancellation,dev.optic.config.VideoQuality quality) throws Exception {
        List<String> candidates = new ArrayList<>();
        var supported = List.of("h264_nvenc","h264_amf","h264_qsv","libx264");
        if(supported.contains(requested)) candidates.add(requested);
        else if (renderer.toLowerCase(Locale.ROOT).contains("nvidia")) candidates.add("h264_nvenc");
        else if (renderer.toLowerCase(Locale.ROOT).contains("amd") || renderer.toLowerCase(Locale.ROOT).contains("radeon")) candidates.add("h264_amf");
        else if (renderer.toLowerCase(Locale.ROOT).contains("intel")) candidates.add("h264_qsv");
        for(String codec:List.of("h264_nvenc","h264_amf","h264_qsv","libx264")) if(!candidates.contains(codec)) candidates.add(codec);
        for(String codec:candidates) {
            List<String> args=new ArrayList<>(List.of(ffmpeg.toString(),"-hide_banner","-loglevel","error","-nostdin","-f","lavfi","-i","color=c=black:s="+w+"x"+h+":r="+fps,"-frames:v","3","-an"));
            args.addAll(options(codec,fps,quality)); args.addAll(List.of("-f","null","-"));
            cancellation.check();
            if(Processes.run(args,log,12,cancellation)) return codec;
        }
        throw new java.io.IOException("No working H.264 encoder. See encoder-probe.log.");
    }
    public static List<String> options(String codec,int fps) {
        return options(codec,fps,dev.optic.config.VideoQuality.STANDARD);
    }
    public static List<String> options(String codec,int fps,dev.optic.config.VideoQuality quality) {
        String q=Integer.toString(quality.quantizer);
        List<String> args=new ArrayList<>(List.of("-c:v",codec,"-pix_fmt","yuv420p","-g",Integer.toString(fps*2),"-bf","0"));
        args.addAll(switch(codec) {
            case "h264_nvenc","hevc_nvenc","av1_nvenc" -> List.of("-preset","p4","-rc","vbr","-cq",q,"-b:v","0");
            case "h264_amf","hevc_amf","av1_amf" -> List.of("-quality","speed","-rc","cqp","-qp_i",q,"-qp_p",q);
            case "h264_qsv","hevc_qsv","av1_qsv" -> List.of("-preset","veryfast","-global_quality",q);
            default -> List.of("-preset","ultrafast","-crf",q,"-threads","2");
        });
        if(codec.startsWith("hevc")) args.addAll(List.of("-tag:v","hvc1"));
        return args;
    }
}
