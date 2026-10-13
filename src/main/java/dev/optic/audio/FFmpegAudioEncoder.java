package dev.optic.audio;

import dev.optic.encoding.Processes;
import java.nio.file.Path;
import java.util.List;

public final class FFmpegAudioEncoder implements AudioEncoder {
    public void muxTracks(Path ffmpeg,Path video,List<AudioTrack> tracks,Path output,Path log,dev.optic.recording.Cancellation cancellation) throws Exception {
        var args=new java.util.ArrayList<>(List.of(ffmpeg.toString(),"-hide_banner","-loglevel","warning","-nostdin","-n","-i",video.toString()));
        for(var track:tracks) args.addAll(List.of("-f","s16le","-ar","48000","-ac",Integer.toString(track.channels()),"-i",track.pcm().toString()));
        args.addAll(List.of("-map","0:v:0","-c:v","copy","-c:a","aac","-b:a","192k"));
        for(int i=0;i<tracks.size();i++) {
            var track=tracks.get(i);
            args.addAll(List.of("-map",(i+1)+":a:0","-filter:a:"+i,"adelay="+track.offsetMillis()+":all=1,apad","-metadata:s:a:"+i,"title="+track.title(),"-metadata:s:a:"+i,"handler_name="+track.title()));
        }
        args.addAll(List.of("-shortest","-movflags","+faststart",output.toString()));
        if(!Processes.run(args,log,600,cancellation)) throw new java.io.IOException("Audio mux failed; source tracks retained");
    }
    public void muxMicrophone(Path ffmpeg,Path video,Path pcm,long offsetMillis,Path output,Path log) throws Exception {
        muxMicrophone(ffmpeg,video,pcm,offsetMillis,output,log,new dev.optic.recording.Cancellation());
    }
    public void muxMicrophone(Path ffmpeg,Path video,Path pcm,long offsetMillis,Path output,Path log,dev.optic.recording.Cancellation cancellation) throws Exception {
        if(!Processes.run(List.of(ffmpeg.toString(),"-hide_banner","-loglevel","warning","-nostdin","-n","-i",video.toString(),
            "-f","s16le","-ar","48000","-ac","1","-i",pcm.toString(),"-map","0:v:0","-map","1:a:0","-c:v","copy","-c:a","aac","-b:a","128k",
            "-af","adelay="+Math.max(0,offsetMillis)+"|"+Math.max(0,offsetMillis)+",apad","-shortest","-metadata:s:a:0","title=Microphone","-movflags","+faststart",output.toString()),log,600,cancellation))
            throw new java.io.IOException("Microphone mux failed; video and PCM retained");
    }
}
