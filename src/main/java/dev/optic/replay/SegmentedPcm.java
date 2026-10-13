package dev.optic.replay;

import dev.optic.audio.PcmStorage;
import java.io.*;
import java.nio.file.*;

/** PCM is indexed by session sample time. Only closed chunks get the final .pcm name. */
public final class SegmentedPcm implements PcmStorage {
    private final Path directory;
    private final java.util.function.LongPredicate retain;
    public SegmentedPcm(Path directory) { this(directory,() -> 0); }
    public SegmentedPcm(Path directory,java.util.function.LongSupplier floor) { this(directory,index -> index>=floor.getAsLong()); }
    public SegmentedPcm(Path directory,java.util.function.LongPredicate retain) { this.directory=directory; this.retain=retain; }
    public OutputStream open(Path ignored,int channels) throws IOException {
        if(channels!=1 && channels!=2) throw new IOException("Unsupported replay audio channels");
        return new OutputStream() {
            final String track=channels==1?"mic":"game";
            final int limit=48000*channels*2*2;
            int written; long index; OutputStream current;
            private void finish() throws IOException {
                if(current==null) return;
                current.close(); current=null;
                Path pending=path(directory,track,index,".open");
                if(!retain.test(index)) Files.deleteIfExists(pending);
                else if(Files.exists(pending)) Files.move(pending,path(directory,track,index,".pcm"));
                written=0; index++;
            }
            public void write(int value) throws IOException { byte[] b={(byte)value}; write(b,0,1); }
            public void write(byte[] data,int offset,int length) throws IOException {
                while(length>0) {
                    if(current==null) current=!retain.test(index)?OutputStream.nullOutputStream():new BufferedOutputStream(Files.newOutputStream(path(directory,track,index,".open"),StandardOpenOption.CREATE_NEW),32768);
                    int n=Math.min(length,limit-written); current.write(data,offset,n); written+=n; offset+=n; length-=n;
                    if(written==limit) finish();
                }
            }
            public void close() throws IOException { finish(); }
        };
    }
    public static Path path(Path directory,String track,long index,String suffix) { return directory.resolve(String.format(java.util.Locale.ROOT,"%s-%09d%s",track,index,suffix)); }
}
