package dev.optic.replay;

import dev.optic.recording.*;
import dev.optic.encoding.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/** One bounded encoded history. Metadata/eviction run off-thread; no UI method reads disk or waits. */
public final class ReplayHistory {
    public static final long BUDGET=2L*1024*1024*1024;
    private final Path root,ffmpeg,videos,partial;
    private final SessionManifest template;
    private final int seconds;
    private final long budget;
    private final boolean game,mic;
    private final Consumer<String> notice;
    private final ScheduledExecutorService monitor=Executors.newSingleThreadScheduledExecutor(r -> daemon(r,"Optic replay history"));
    private final ExecutorService exports=Executors.newSingleThreadExecutor(r -> daemon(r,"Optic clip export"));
    private final AtomicReference<Request> request=new AtomicReference<>();
    private final TreeMap<Long,Long> sizes=new TreeMap<>(); // only the bounded recent window, even during a long recording
    private long observed=-1,evicted=-1;
    private volatile long first,latest=-1,fullStart=-1;
    private volatile double end;
    private volatile boolean ended;
    private volatile IOException failure;
    public volatile long bytes,pinnedBytes;
    public volatile long totalVideoBytes;
    public volatile long maintenancePasses,maintenanceNanos;
    private long committedBytes;
    private static final class Request {
        final long first,last; final Cancellation cancel=new Cancellation();
        Request(long first,long last) { this.first=first; this.last=last; }
    }
    private static Thread daemon(Runnable r,String name) { var t=new Thread(r,name); t.setDaemon(true); return t; }
    public ReplayHistory(Path ffmpeg,Path partial,Path videos,SessionManifest template,int seconds,boolean game,boolean mic,Consumer<String> notice) throws IOException {
        this(ffmpeg,partial,videos,template,seconds,game,mic,notice,BUDGET);
    }
    ReplayHistory(Path ffmpeg,Path partial,Path videos,SessionManifest template,int seconds,boolean game,boolean mic,Consumer<String> notice,long budget) throws IOException {
        this.ffmpeg=ffmpeg; this.partial=partial; this.videos=videos; this.template=template; this.seconds=seconds; this.game=game; this.mic=mic; this.notice=notice;
        this.budget=budget; root=partial.getParent().resolve("replay"); Files.createDirectory(root); marker();
        monitor.scheduleWithFixedDelay(() -> { long start=System.nanoTime(); try { maintain(); } catch(Exception e) { failure=new IOException("Replay storage failed",e); } finally { maintenanceNanos+=System.nanoTime()-start; maintenancePasses++; } },250,250,TimeUnit.MILLISECONDS);
    }
    public Path directory() { return root; }
    public dev.optic.audio.PcmStorage audioStorage() { return new SegmentedPcm(root,index -> index>=first || (fullStart>=0 && index>=fullStart) || (index>=retainedFailure && index<=retainedLast)); }
    public IOException failure() { return failure; }
    public boolean saving() { return request.get()!=null; }
    public boolean retainedExports() { return retainedFailure!=Long.MAX_VALUE; }
    public double availableSeconds() { return Math.max(0,Math.min(seconds,end-first*2d)); }
    public String status() { return failure!=null?"Unavailable — stop and retry":saving()?"Saving clip…":latest<0?"Warming…":String.format(Locale.ROOT,"%s · %.0f sec available · %.0f MiB",availableSeconds()>=seconds?"Ready":"Warming",availableSeconds(),bytes/1048576d); }
    public void promote(double time) { fullStart=Math.max(first,(long)Math.floor(time/2)); monitor.execute(() -> { try { committedBytes=sizes.tailMap(fullStart).values().stream().mapToLong(Long::longValue).sum(); marker(); } catch(IOException e) { failure=e; } }); }
    public double fullStartSeconds() { return Math.max(0,fullStart*2d); }
    public double endSeconds() { return end; }
    public void save(double time) {
        if(retainedExports()) { notice.accept("A failed clip needs recovery. Restart Minecraft before saving another clip."); return; }
        if(ended || failure!=null || latest<first) { notice.accept("Replay is warming; try again after a few seconds."); return; }
        long start=Math.max(first,Math.max(0,(long)Math.floor((time-seconds)/2))),last=Math.max(start,(long)Math.ceil(time/2)-1);
        var r=new Request(start,last);
        if(!request.compareAndSet(null,r)) { notice.accept("A clip is already saving."); return; }
        notice.accept("Saving recent gameplay… up to 2 extra seconds at each boundary.");
        exports.execute(() -> export(r));
    }
    private void export(Request r) {
        Path clip=null; boolean success=false;
        try {
            long deadline=System.nanoTime()+12_000_000_000L;
            while(!ended && latest<r.last) { r.cancel.check(); if(System.nanoTime()>deadline) throw new IOException("Encoder has not reached the requested moment"); Thread.sleep(50); }
            long last=Math.min(latest,r.last); double stop=Math.min(end,(last+1)*2d);
            if(last<r.first) throw new IOException("No completed replay segments");
            long audioDeadline=System.nanoTime()+3_000_000_000L;
            while(!ended && System.nanoTime()<audioDeadline && ((game && !Files.exists(SegmentedPcm.path(root,"game",last,".pcm"))) || (mic && !Files.exists(SegmentedPcm.path(root,"mic",last,".pcm"))))) { r.cancel.check(); Thread.sleep(50); }
            long required=0;
            for(long i=r.first;i<=last;i++) required=Math.addExact(required,Files.size(ReplayExport.video(root,i)));
            required+=Math.round((stop-r.first*2)*48000*(game?4:0)+(stop-r.first*2)*48000*(mic?2:0));
            pinnedBytes=required;
            if(bytes+required*3>budget || Files.getFileStore(root).getUsableSpace()<required*3+256L*1024*1024) throw new IOException("Not enough clip-export space; lower replay duration");
            exportReserve=required*3;
            clip=RecordingStorage.reserve(videos,"Clip",RecordingStorage.Style.DEFAULT);
            try(var lock=SessionFiles.tryLock(clip)) {
                if(lock==null) throw new IOException("Clip reservation busy");
                SessionManifest manifest=new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(template),SessionManifest.class);
                manifest.sourceSessionId=template.sessionId; manifest.sessionId=UUID.randomUUID().toString(); manifest.sourceStartSeconds=r.first*2d; manifest.sourceEndSeconds=stop; manifest.durationSeconds=stop-r.first*2d;
                manifest.outputFile=clip.getParent().getFileName()+".mp4"; manifest.state="finalizing"; manifest.save(clip);
                Properties plan=new Properties(); plan.setProperty("source",root.toAbsolutePath().toString()); plan.setProperty("first",Long.toString(r.first)); plan.setProperty("last",Long.toString(last)); plan.setProperty("end",Double.toString(stop)); plan.setProperty("game",Boolean.toString(game)); plan.setProperty("mic",Boolean.toString(mic));
                try(var out=Files.newOutputStream(clip.resolveSibling("clip-plan.properties"))) { plan.store(out,"Optic interrupted clip recovery inputs"); }
                long padded=ReplayExport.assemble(ffmpeg,root,r.first,last,stop,clip,game,mic,r.cancel);
                var result=RecordingRecovery.finalizeOwned(ffmpeg,clip,r.cancel);
                manifest.state="complete"; manifest.save(clip); Files.deleteIfExists(clip.resolveSibling("clip-plan.properties"));
                dev.optic.markers.MarkerExport.schedule(ffmpeg,partial,clip,manifest);
                notice.accept("Clip saved — "+Math.round(stop-r.first*2)+" seconds. Open Recordings to view it.");
                if(padded>0) notice.accept("Some clip audio was unavailable; gaps were filled with silence.");
                if(!result.warning().isEmpty()) notice.accept(result.warning()); success=true;
            }
        } catch(Exception e) { org.slf4j.LoggerFactory.getLogger("Optic").warn("Clip export deferred; reserved sources retained",e); notice.accept("Clip could not be saved: "+e.getMessage()); }
        finally {
            // Keep failed prepared plans pinned until shutdown/recovery, never evict their source behind the plan.
            if(!success && clip!=null) { retainedFailure=r.first; retainedLast=r.last; retainedBytes=pinnedBytes; }
            pinnedBytes=0; exportReserve=0; request.compareAndSet(r,null);
        }
    }
    private volatile long retainedFailure=Long.MAX_VALUE;
    private volatile long retainedLast=-1,retainedBytes;
    private volatile long exportReserve;
    private void maintain() throws IOException {
        Path list=root.resolve("index.csv");
        if(Files.exists(list)) {
            if(Files.size(list)>128*1024) throw new IOException("Replay index exceeds bound");
            String contents=Files.readString(list); int complete=contents.lastIndexOf('\n');
            for(String line:(complete<0?"":contents.substring(0,complete)).split("\\R")) {
                String[] fields=line.split(","); if(fields.length!=3) continue;
                String name=fields[0].replace("\"",""); if(!name.matches("video-[0-9]{9}\\.mkv")) continue;
                try {
                    long n=Long.parseLong(name.substring(6,15)); double a=Double.parseDouble(fields[1]),b=Double.parseDouble(fields[2]);
                    if(!Double.isFinite(a) || !Double.isFinite(b) || Math.abs(a-n*2d)>.05 || b<=a || b-a>2.05) throw new IOException("Encoder segment timestamps do not match replay timeline");
                    if(n>observed) { long length=Files.size(root.resolve(name)); totalVideoBytes+=length; long total=length+(game?384000:0)+(mic?192000:0); sizes.put(n,total); if(fullStart>=0 && n>=fullStart) committedBytes+=total; observed=n; latest=n; end=b; }
                } catch(NumberFormatException ignored) { /* concurrent CSV rewrite: retry next poll */ }
            }
        }
        long oldest=Math.max(0,latest-(seconds+8)/2);
        bytes=0; for(long size:sizes.values()) bytes+=size;
        long ringLimit=Math.min(budget/2,budget-exportReserve-retainedBytes);
        while(!sizes.isEmpty() && (sizes.firstKey()<oldest || bytes>ringLimit)) {
            long n=sizes.firstKey(); var pin=request.get();
            if(pin!=null && n>=pin.first) {
                if(bytes>ringLimit && pin!=null) pin.cancel.cancel();
                if(bytes>budget) throw new IOException("Pinned replay history reached storage limit; source retained");
                break;
            }
            long size=sizes.remove(n); bytes-=size;
            if((fullStart<0 || n<fullStart) && !(n>=retainedFailure && n<=retainedLast)) delete(n);
            evicted=n; first=n+1;
        }
        // Disk monitoring also covers committed full recordings, PCM backlog and an encoder that never closes a segment.
        if(Files.getFileStore(root).getUsableSpace()<committedBytes*2+256L*1024*1024) throw new IOException("Disk reserve needed to finish this recording has been reached");
        Path active=ReplayExport.video(root,latest+1);
        if(Files.exists(active) && Files.size(active)>256L*1024*1024) throw new IOException("Replay segment exceeded 256 MiB safety limit");
    }
    private void delete(long n) throws IOException {
        Files.deleteIfExists(ReplayExport.video(root,n));
        Files.deleteIfExists(SegmentedPcm.path(root,"game",n,".pcm")); Files.deleteIfExists(SegmentedPcm.path(root,"mic",n,".pcm"));
    }
    private void marker() throws IOException {
        Properties state=new Properties(); state.setProperty("fullStart",Long.toString(fullStart)); state.setProperty("game",Boolean.toString(game)); state.setProperty("mic",Boolean.toString(mic));
        Path temp=root.resolve("history.tmp"); try(var out=Files.newOutputStream(temp)) { state.store(out,"Optic replay recovery"); }
        Files.move(temp,root.resolve("history.properties"),StandardCopyOption.REPLACE_EXISTING);
    }
    /** Called after video and audio producers close, on a worker. */
    public void finish(boolean full,Cancellation cancel) throws Exception {
        monitor.shutdown(); if(!monitor.awaitTermination(5,TimeUnit.SECONDS)) throw new IOException("Replay monitor did not stop");
        maintain(); ended=true; exports.shutdown();
        if(!exports.awaitTermination(180,TimeUnit.SECONDS)) throw new IOException("Clip export still active; history retained");
        if(full && ReplayExport.assemble(ffmpeg,root,Math.max(0,fullStart),latest,end,partial,game,mic,cancel)>0) notice.accept("Some recording audio was unavailable; gaps were filled with silence.");
    }
    public void discardPrepared() { ended=true; monitor.shutdownNow(); exports.shutdownNow(); }
    public void cancelExports() { var r=request.get(); if(r!=null) r.cancel.cancel(); }
    public void cleanup() throws IOException {
        if(retainedFailure!=Long.MAX_VALUE) return;
        // Only owned, flat filenames. Never recursively remove arbitrary paths or follow links.
        try(var files=Files.newDirectoryStream(root)) { for(Path p:files) if(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)) Files.delete(p); }
        Files.delete(root);
    }
}
