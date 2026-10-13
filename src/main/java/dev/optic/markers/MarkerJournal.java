package dev.optic.markers;

import com.google.gson.Gson;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Bounded event writer. Acknowledgment follows append; video never depends on this worker. */
public final class MarkerJournal implements AutoCloseable {
    public static final int MAX_EVENTS=4096;
    public static final long MAX_BYTES=8*1024*1024;
    private static final Gson JSON=new Gson();
    public static Path path(Path partial) { return partial.resolveSibling("markers.jsonl"); }
    private final Path file;
    private final Consumer<String> feedback;
    private final AtomicInteger accepted=new AtomicInteger();
    private volatile boolean failed;
    public final dev.optic.recording.TimingHistogram appendLatency=new dev.optic.recording.TimingHistogram();
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(128),r -> { var t=new Thread(r,"Optic marker journal"); t.setDaemon(true); return t; },new ThreadPoolExecutor.AbortPolicy());
    public MarkerJournal(Path partial,Consumer<String> feedback) { file=path(partial); this.feedback=feedback; }
    public boolean offer(Marker marker) {
        long queued=System.nanoTime();
        if(failed) return false;
        if(accepted.incrementAndGet()>MAX_EVENTS) { accepted.decrementAndGet(); return false; }
        try { worker.execute(() -> {
            try {
                byte[] line=(JSON.toJson(marker)+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                if(Files.isSymbolicLink(file) || (Files.exists(file) && Files.size(file)+line.length>MAX_BYTES)) throw new java.io.IOException("Marker storage limit reached");
                Files.write(file,line,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
                appendLatency.add(System.nanoTime()-queued);
                feedback.accept("Marker saved: "+marker.type().label());
            } catch(Exception e) { failed=true; org.slf4j.LoggerFactory.getLogger("Optic").warn("Marker append failed",e); feedback.accept("Marker could not be saved; video continues."); }
        }); return true; }
        catch(RejectedExecutionException e) { accepted.decrementAndGet(); return false; }
    }
    @Override public void close() {
        worker.shutdown(); try { if(!worker.awaitTermination(10,TimeUnit.SECONDS)) feedback.accept("Marker writes are delayed; metadata may finish later."); }
        catch(InterruptedException e) { Thread.currentThread().interrupt(); }
        if(accepted.get()>0) org.slf4j.LoggerFactory.getLogger("Optic").info("Marker journal accepted={} append_p95_ms={} append_max_ms={}",accepted.get(),appendLatency.percentileMs(.95),appendLatency.maxMs());
    }
    /** Ignore a torn final line, malformed events and unsupported schema without touching video. */
    public static List<Marker> read(Path partial) throws java.io.IOException {
        Path file=path(partial); if(!Files.exists(file)) return List.of();
        if(Files.isSymbolicLink(file) || Files.size(file)>MAX_BYTES) throw new java.io.IOException("Invalid marker journal size/path");
        String text=new String(Files.readAllBytes(file),java.nio.charset.StandardCharsets.UTF_8); int end=text.lastIndexOf('\n'); if(end<0) return List.of();
        var result=new ArrayList<Marker>(); var ids=new HashSet<String>();
        for(String line:text.substring(0,end).split("\n")) {
            if(result.size()>=MAX_EVENTS) break;
            try {
                var m=JSON.fromJson(line,Marker.class);
                if(m==null || m.schemaVersion()!=1 || m.id()==null || !m.id().matches("[a-f0-9-]{36}") || m.sessionId()==null || m.elapsedNanos()<0 || m.type()==null || m.note()==null || m.note().length()>256 || (m.fps()!=30 && m.fps()!=60 && m.fps()!=120) || !ids.add(m.id())) continue;
                result.add(m);
            } catch(RuntimeException ignored) { /* isolate corrupt metadata records */ }
        }
        result.sort(Comparator.comparingLong(Marker::elapsedNanos)); return List.copyOf(result);
    }
}
