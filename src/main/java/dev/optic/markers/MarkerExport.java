package dev.optic.markers;

import com.google.gson.*;
import dev.optic.recording.*;
import dev.optic.encoding.Processes;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Portable exports and optional thumbnails from finished footage, never from the framebuffer. */
public final class MarkerExport {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final ThreadPoolExecutor WORK=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),r -> { var t=new Thread(r,"Optic marker export"); t.setDaemon(true); return t; },new ThreadPoolExecutor.AbortPolicy());
    public record Entry(String id,String type,String note,long sourceElapsedNanos,long frame,double seconds,Marker.Context context,boolean thumbnailRequested,String thumbnail) {}
    public record Timeline(int schemaVersion,String sessionId,String sourceSessionId,String video,int fps,double durationSeconds,double sourceStartSeconds,List<Entry> markers) {}
    public static List<Entry> map(List<Marker> markers,double start,double duration,int fps) {
        var entries=new ArrayList<Entry>();
        for(var m:markers) if(m.seconds()>=start && m.seconds()<start+duration) {
            long frame=(long)Math.floor((m.seconds()-start)*fps+1e-7);
            entries.add(new Entry(m.id(),m.type().label(),m.note(),m.elapsedNanos(),frame,frame/(double)fps,m.context(),m.thumbnail(),null));
        }
        return List.copyOf(entries);
    }
    public static void schedule(Path ffmpeg,Path source,Path destination,SessionManifest manifest) {
        // Snapshot because RecordingManager may still update its mutable manifest.
        var snapshot=JSON.fromJson(JSON.toJson(manifest),SessionManifest.class);
        try { WORK.execute(() -> { try { write(ffmpeg,source,destination,snapshot); } catch(Exception e) { org.slf4j.LoggerFactory.getLogger("Optic").warn("Marker export deferred; journal retained",e); } }); }
        catch(RejectedExecutionException e) { org.slf4j.LoggerFactory.getLogger("Optic").warn("Marker export busy; journal retained for manual retry"); }
    }
    public static synchronized Timeline write(Path ffmpeg,Path source,Path destination,SessionManifest m) throws Exception {
        var stored=SessionManifest.read(destination);
        if(stored!=null && stored.trashed) return new Timeline(1,stored.sessionId,stored.sourceSessionId,stored.outputFile,stored.fps,stored.durationSeconds,stored.sourceStartSeconds,List.of());
        if(stored!=null && "complete".equals(stored.state)) m=stored;
        var sourceMarkers=MarkerJournal.read(source);
        var entries=new ArrayList<>(map(sourceMarkers,m.sourceStartSeconds,m.durationSeconds,m.fps));
        if(!source.equals(destination)) {
            var journal=new StringBuilder(); var compact=new Gson();
            for(var marker:sourceMarkers) if(marker.seconds()>=m.sourceStartSeconds && marker.seconds()<m.sourceStartSeconds+m.durationSeconds) journal.append(compact.toJson(marker)).append('\n');
            atomic(MarkerJournal.path(destination),journal.toString());
        }
        Path video=RecordingStorage.target(destination),directory=destination.getParent();
        int thumbnails=0; long bytes=0;
        for(int n=0;n<entries.size();n++) {
            var e=entries.get(n); if(!e.thumbnailRequested() || thumbnails>=8 || bytes>=2*1024*1024) continue;
            String name="marker-"+e.id()+".jpg"; Path output=directory.resolve(name);
            if(Files.isSymbolicLink(output)) continue;
            thumbnails++;
            if(!Files.exists(output) && ffmpeg!=null && Files.isRegularFile(video)) {
                Path temp=directory.resolve(name+".tmp.jpg");
                try {
                    if(Processes.run(List.of(ffmpeg.toString(),"-hide_banner","-loglevel","error","-nostdin","-n","-ss",Double.toString(e.seconds()),"-i",video.toString(),"-frames:v","1","-vf","scale=320:-2","-q:v","5",temp.toString()),directory.resolve("marker-thumbnails.log"),15) && Files.size(temp)<=256*1024) Files.move(temp,output);
                } catch(Exception failure) { org.slf4j.LoggerFactory.getLogger("Optic").warn("Marker thumbnail omitted; text export continues",failure); }
                finally { Files.deleteIfExists(temp); }
            }
            if(Files.isRegularFile(output) && Files.size(output)<=256*1024) { bytes+=Files.size(output); entries.set(n,new Entry(e.id(),e.type(),e.note(),e.sourceElapsedNanos(),e.frame(),e.seconds(),e.context(),true,name)); }
        }
        var timeline=new Timeline(1,m.sessionId,m.sourceSessionId,m.outputFile,m.fps,m.durationSeconds,m.sourceStartSeconds,List.copyOf(entries));
        atomic(directory.resolve("markers.json"),JSON.toJson(timeline));
        var csv=new StringBuilder("id,type,note,frame,seconds,fps,source_elapsed_ns,x,y,z,dimension,world_label\r\n");
        for(var e:entries) { var c=e.context(); csv.append(String.join(",",cell(e.id()),cell(e.type()),cell(e.note()),Long.toString(e.frame()),Double.toString(e.seconds()),Integer.toString(m.fps),Long.toString(e.sourceElapsedNanos()),c==null?"":Integer.toString(c.x()),c==null?"":Integer.toString(c.y()),c==null?"":Integer.toString(c.z()),cell(c==null?"":c.dimension()),cell(c==null?"":c.worldLabel()))).append("\r\n"); }
        atomic(directory.resolve("markers.csv"),csv.toString()); return timeline;
    }
    /** Quote every text cell and neutralize spreadsheet formulas; JSON retains the original note. */
    public static String cell(String value) { String s=value==null?"":value; if(!s.isEmpty() && "=+-@\t\r\n".indexOf(s.charAt(0))>=0) s="'"+s; return "\""+s.replace("\"","\"\"")+"\""; }
    private static void atomic(Path file,String text) throws java.io.IOException {
        Path tmp=file.resolveSibling(file.getFileName()+".tmp"); if(Files.isSymbolicLink(file) || Files.isSymbolicLink(tmp)) throw new java.io.IOException("Symbolic marker export");
        Files.writeString(tmp,text); try { Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); } catch(AtomicMoveNotSupportedException e) { Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING); }
    }
}
