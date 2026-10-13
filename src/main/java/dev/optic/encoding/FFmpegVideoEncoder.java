package dev.optic.encoding;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

public final class FFmpegVideoEncoder implements VideoEncoder {
    private final Process process;
    private final OutputStream pipe;
    private final byte[] chunk=new byte[256*1024];
    private volatile long lastProgress=System.nanoTime();
    private volatile boolean writing;
    private volatile long encodedFrames;
    private final Thread progressReader;
    private final java.util.concurrent.atomic.AtomicBoolean closed=new java.util.concurrent.atomic.AtomicBoolean();
    private final ScheduledExecutorService watchdog=Executors.newSingleThreadScheduledExecutor(r -> { var t=new Thread(r,"Optic encoder watchdog"); t.setDaemon(true); return t; });
    public FFmpegVideoEncoder(Path executable, String codec, int w, int h, int fps, Path output, Path log) throws IOException {
        this(executable,codec,w,h,fps,output,log,dev.optic.config.VideoQuality.STANDARD);
    }
    public FFmpegVideoEncoder(Path executable, String codec, int w, int h, int fps, Path output, Path log,dev.optic.config.VideoQuality quality) throws IOException {
        this(executable,codec,w,h,fps,output,log,quality,false);
    }
    public FFmpegVideoEncoder(Path executable, String codec, int w, int h, int fps, Path output, Path log,dev.optic.config.VideoQuality quality,boolean segments) throws IOException {
        this(executable,codec,w,h,fps,output,log,quality,segments,dev.optic.config.ColorMode.LEGACY);
    }
    public FFmpegVideoEncoder(Path executable, String codec, int w, int h, int fps, Path output, Path log,dev.optic.config.VideoQuality quality,boolean segments,dev.optic.config.ColorMode color) throws IOException {
        List<String> args=new ArrayList<>(List.of(executable.toString(),"-hide_banner","-loglevel","warning","-nostdin","-n","-f","rawvideo","-pixel_format","rgba","-video_size",w+"x"+h,"-framerate",Integer.toString(fps),"-i","pipe:0","-an"));
        args.addAll(EncoderDetector.options(codec,fps,quality));
        args.addAll(color.options());
        args.addAll(List.of("-progress","pipe:1","-stats_period","1"));
        if(segments) args.addAll(List.of("-force_key_frames","expr:gte(t,n_forced*2)","-f","segment","-segment_time","2","-segment_time_delta",Double.toString(.5/fps),"-reset_timestamps","1","-segment_format","matroska","-segment_list_type","csv","-segment_list_size","512","-segment_list",output.resolve("index.csv").toString(),output.resolve("video-%09d.mkv").toString()));
        else args.addAll(List.of("-movflags","+frag_keyframe+empty_moov+default_base_moof","-frag_duration","1000000","-flush_packets","1","-f","mp4",output.toString()));
        process=new ProcessBuilder(args).redirectError(log.toFile()).start();
        pipe=process.getOutputStream();
        progressReader=new Thread(() -> {
            try(var reader=new BufferedReader(new InputStreamReader(process.getInputStream(),java.nio.charset.StandardCharsets.UTF_8))) {
                String line; while((line=reader.readLine())!=null) if(line.startsWith("frame=")) {
                    try { encodedFrames=Long.parseLong(line.substring(6).trim()); } catch(NumberFormatException ignored) {}
                }
            } catch(IOException ignored) {}
        },"Optic encoder progress"); progressReader.setDaemon(true); progressReader.start();
        watchdog.scheduleAtFixedRate(() -> { if(writing && System.nanoTime()-lastProgress>TimeUnit.SECONDS.toNanos(10)) process.destroyForcibly(); },1,1,TimeUnit.SECONDS);
    }
    public void write(ByteBuffer pixels) throws IOException {
        checkHealth(); pixels.rewind(); lastProgress=System.nanoTime(); writing=true;
        try { while(pixels.hasRemaining()) { int count=Math.min(chunk.length,pixels.remaining()); pixels.get(chunk,0,count); pipe.write(chunk,0,count); lastProgress=System.nanoTime(); } }
        finally { writing=false; }
    }
    public void checkHealth() throws IOException { if(!process.isAlive()) throw new IOException("Video encoder exited unexpectedly; partial video retained"); }
    public long encodedFrames() { return encodedFrames; }
    public void abort() { process.destroyForcibly(); }
    public void close() throws IOException {
        if(!closed.compareAndSet(false,true)) return;
        try {
            pipe.close();
            if(!process.waitFor(15,TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IOException("FFmpeg shutdown timed out; partial recording retained"); }
            if(process.exitValue()!=0) throw new IOException("FFmpeg failed; partial recording and log retained");
            progressReader.join(1000);
        } catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
        finally { watchdog.shutdownNow(); if(process.isAlive()) process.destroyForcibly(); }
    }
}
