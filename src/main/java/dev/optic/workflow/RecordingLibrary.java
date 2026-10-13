package dev.optic.workflow;
import dev.optic.recording.*;
import dev.optic.markers.MarkerExport;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.function.*;

/** Worker-only index and bundle operations. Stable details directory survives video renames. */
public final class RecordingLibrary {
    public record Item(Path partial,Path video,String id,String date,double duration,String notes,boolean exists,boolean trashed) {}
    public static List<Item> scan(Path videos,BooleanSupplier cancel,Consumer<List<Item>> progress) throws IOException {
        var items=new ArrayList<Item>(); Path details=RecordingStorage.details(videos); if(!Files.isDirectory(details)) return List.of();
        int visited=0;
        try(var dirs=Files.newDirectoryStream(details)) { for(Path dir:dirs) {
            if(cancel.getAsBoolean() || visited++>=20000) break;
            if(!Files.isDirectory(dir,LinkOption.NOFOLLOW_LINKS)) continue;
            Path partial=dir.resolve(dir.getFileName()+".partial.mp4");
            try {
                var m=SessionManifest.read(partial); if(m==null || !"complete".equals(m.state)) continue;
                Path video=RecordingStorage.target(partial); if(!video.getParent().equals(videos.toAbsolutePath().normalize()) || Files.isSymbolicLink(video)) continue;
                items.add(new Item(partial,video,m.sessionId,m.createdUtc==null?"Unknown date":m.createdUtc,m.durationSeconds,m.notes==null?"":m.notes,Files.isRegularFile(video),m.trashed));
                if(items.size()%256==0) progress.accept(sorted(items));
            } catch(Exception ignored) { /* corrupt/unknown metadata does not block other media */ }
        } }
        return sorted(items);
    }
    public static void restore(Item item) throws Exception {
        synchronized(MarkerExport.class) { try(var lock=SessionFiles.tryLock(item.partial())) {
            if(lock==null) throw new IOException("Recording is busy"); var m=editable(item);
            if(!m.trashed) throw new IOException("Recording is not in Trash");
            Path source=item.partial().getParent().resolve("Trash").resolve(item.video().getFileName());
            if(Files.exists(item.video()) || !Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(source.getParent())) throw new IOException("Restore destination occupied or Trash file missing");
            Path plan=item.partial().resolveSibling("library-move.properties"); var p=new Properties(); p.setProperty("old",item.video().getFileName().toString()); p.setProperty("new",item.video().getFileName().toString()); p.setProperty("restore","true");
            try(var out=Files.newOutputStream(plan,StandardOpenOption.CREATE_NEW)) { p.store(out,"Optic restore recovery"); }
            Files.move(source,item.video()); m.trashed=false; m.save(item.partial()); Files.delete(plan);
        } }
    }
    private static List<Item> sorted(List<Item> items) { var copy=new ArrayList<>(items); copy.sort(Comparator.comparing(Item::date).reversed().thenComparing(i -> i.video().toString())); return List.copyOf(copy); }
    private static SessionManifest editable(Item item) throws IOException {
        if(Files.isSymbolicLink(item.partial().getParent()) || Files.exists(item.partial().resolveSibling("clip-plan.properties")) || Files.exists(item.partial().getParent().resolve("replay"))) throw new IOException("Recording/replay sources are still active or need recovery");
        var m=SessionManifest.read(item.partial());
        if(m==null || !"complete".equals(m.state) || !Objects.equals(m.sessionId,item.id()) || !RecordingStorage.target(item.partial()).equals(item.video())) throw new IOException("Recording changed; refresh the library");
        return m;
    }
    public static void notes(Item item,String value) throws Exception {
        if(value.length()>2048) throw new IOException("Notes limit: 2048 characters");
        synchronized(MarkerExport.class) { try(var lock=SessionFiles.tryLock(item.partial())) {
            if(lock==null) throw new IOException("Recording is busy"); var m=editable(item); m.notes=value; m.save(item.partial());
        } }
    }
    /** Move only the video. Sidecars stay keyed by stable session directory, with a persisted move intent. */
    public static void move(Item item,String title,boolean trash) throws Exception {
        synchronized(MarkerExport.class) { try(var lock=SessionFiles.tryLock(item.partial())) {
            if(lock==null) throw new IOException("Recording is busy"); var m=editable(item);
            if(m.trashed) throw new IOException("Use the retained Trash file to restore manually");
            Path target=trash?item.partial().getParent().resolve("Trash").resolve(item.video().getFileName()):item.video().resolveSibling(RecordingStorage.cleanTitle(title)+".mp4");
            if(target.equals(item.video())) return;
            if(!Files.isRegularFile(item.video(),LinkOption.NOFOLLOW_LINKS) || Files.exists(target) || Files.isSymbolicLink(target.getParent())) throw new IOException("Video missing or destination already exists");
            Files.createDirectories(target.getParent());
            var intent=new Properties(); intent.setProperty("old",item.video().getFileName().toString()); intent.setProperty("new",target.getFileName().toString()); intent.setProperty("trash",Boolean.toString(trash));
            Path plan=item.partial().resolveSibling("library-move.properties"); try(var out=Files.newOutputStream(plan,StandardOpenOption.CREATE_NEW)) { intent.store(out,"Optic library move recovery"); }
            Files.move(item.video(),target); m.trashed=trash; if(!trash) m.outputFile=target.getFileName().toString(); m.save(item.partial()); Files.delete(plan);
            if(!trash) MarkerExport.write(null,item.partial(),item.partial(),m);
        } }
    }
    public static void reconcile(Path videos) throws IOException {
        Path details=RecordingStorage.details(videos); if(!Files.isDirectory(details)) return;
        try(var dirs=Files.newDirectoryStream(details)) { for(Path dir:dirs) {
            if(!Files.isDirectory(dir,LinkOption.NOFOLLOW_LINKS)) continue;
            Path partial=dir.resolve(dir.getFileName()+".partial.mp4");
            Path plan=dir.resolve("library-move.properties");
            if(!Files.isRegularFile(plan,LinkOption.NOFOLLOW_LINKS) || Files.size(plan)>4096) continue;
            try(var lock=SessionFiles.tryLock(partial)) {
                if(lock==null) continue; var p=new Properties(); try(var in=Files.newInputStream(plan)) { p.load(in); }
                String old=p.getProperty("old"),next=p.getProperty("new"); if(!safe(old) || !safe(next)) continue;
                boolean trash=Boolean.parseBoolean(p.getProperty("trash")); Path target=trash?dir.resolve("Trash").resolve(next):videos.resolve(next);
                if(Files.isSymbolicLink(target) || Files.isSymbolicLink(target.getParent())) continue;
                var m=SessionManifest.read(partial); if(m==null) continue;
                if(Boolean.parseBoolean(p.getProperty("restore"))) {
                    Path trashFile=dir.resolve("Trash").resolve(old);
                    if(Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS) && !Files.exists(trashFile)) { m.trashed=false; m.save(partial); Files.delete(plan); }
                    else if(!Files.exists(target) && Files.isRegularFile(trashFile,LinkOption.NOFOLLOW_LINKS)) Files.delete(plan);
                    continue;
                }
                if(!Files.exists(videos.resolve(old)) && Files.isRegularFile(target)) { m.trashed=trash; if(!trash) m.outputFile=next; m.save(partial); Files.delete(plan); }
                else if(Files.isRegularFile(videos.resolve(old)) && !Files.exists(target)) Files.delete(plan);
            } catch(Exception e) { org.slf4j.LoggerFactory.getLogger("Optic").warn("Library move needs recovery: {}",dir.getFileName(),e); }
        } }
    }
    private static boolean safe(String value) { return value!=null && value.endsWith(".mp4") && !value.contains("/") && !value.contains("\\") && !value.contains(":") && !value.equals("..mp4"); }
}
