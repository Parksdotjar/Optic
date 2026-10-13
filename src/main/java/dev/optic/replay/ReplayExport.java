package dev.optic.replay;

import dev.optic.recording.*;
import dev.optic.encoding.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;

/** Off-thread, video stream-copy only. Audio is rebased from the same two-second origin. */
public final class ReplayExport {
    public static Path video(Path root,long index) { return SegmentedPcm.path(root,"video",index,".mkv"); }
    public static long assemble(Path ffmpeg,Path root,long first,long last,double end,Path partial,boolean game,boolean mic,Cancellation cancel) throws Exception {
        if(first<0 || last<first || end<=first*2d) throw new IOException("Replay history is not ready");
        Path list=partial.resolveSibling("segments.ffconcat");
        try(var writer=Files.newBufferedWriter(list)) {
            writer.write("ffconcat version 1.0\n");
            for(long i=first;i<=last;i++) {
                cancel.check(); Path source=video(root,i);
                if(!Files.isRegularFile(source) || Files.isSymbolicLink(source)) throw new IOException("Replay segment expired or missing");
                writer.write("file '"+source.toAbsolutePath().toString().replace('\\','/').replace("'","'\\''")+"'\n");
                writer.write("duration "+Math.min(2d,end-i*2d)+"\n");
            }
        }
        if(!Processes.run(List.of(ffmpeg.toString(),"-hide_banner","-loglevel","warning","-nostdin","-n","-f","concat","-safe","0","-i",list.toString(),"-map","0:v:0","-c","copy","-t",Double.toString(end-first*2d),"-movflags","+frag_keyframe+empty_moov+default_base_moof",partial.toString()),SessionFiles.sibling(partial,".clip.log"),180,cancel)) throw new IOException("Clip remux failed; source segments retained");
        long padded=game?audio(root,"game",2,first,last,end,SessionFiles.sibling(partial,".game.pcm"),cancel):0;
        if(mic) { padded+=audio(root,"mic",1,first,last,end,SessionFiles.sibling(partial,".mic.pcm"),cancel); Files.writeString(SessionFiles.sibling(partial,".mic-offset.txt"),"0"); }
        if(padded>0) org.slf4j.LoggerFactory.getLogger("Optic").warn("Replay audio padded {} unavailable bytes with silence in {}",padded,partial.getFileName());
        return padded;
    }
    private static long audio(Path root,String track,int channels,long first,long last,double end,Path output,Cancellation cancel) throws Exception {
        byte[] buffer=new byte[32768],zero=new byte[32768];
        long padded=0;
        try(var out=new BufferedOutputStream(Files.newOutputStream(output,StandardOpenOption.CREATE_NEW),32768)) {
            for(long i=first;i<=last;i++) {
                long remaining=Math.round(Math.min(2d,end-i*2d)*48000)*channels*2;
                Path path=SegmentedPcm.path(root,track,i,".pcm");
                if(Files.isRegularFile(path) && !Files.isSymbolicLink(path)) try(var in=Files.newInputStream(path)) {
                    while(remaining>0) { cancel.check(); int n=in.read(buffer,0,(int)Math.min(remaining,buffer.length)); if(n<0) break; out.write(buffer,0,n); remaining-=n; }
                }
                padded+=remaining;
                while(remaining>0) { cancel.check(); int n=(int)Math.min(remaining,zero.length); out.write(zero,0,n); remaining-=n; }
            }
        }
        return padded;
    }
}
