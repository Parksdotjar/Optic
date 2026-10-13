package dev.optic.replay;

import dev.optic.recording.*;
import dev.optic.encoding.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Recovers accepted clips and promoted recordings from owned closed segments before cache cleanup. */
public final class ReplayRecovery {
    public static int recover(Path ffmpeg,Path details,Consumer<String> notice) throws IOException {
        if(!Files.isDirectory(details)) return 0;
        int count=0;
        List<Path> plans;
        try(var walk=Files.walk(details,2)) { plans=walk.filter(p -> p.getFileName().toString().equals("clip-plan.properties")).toList(); }
        for(Path plan:plans) {
            try {
                Properties p=read(plan); Path root=source(details,p.getProperty("source")); Path owner=partial(root.getParent()),clip=partial(plan.getParent());
                try(var ownerLock=SessionFiles.tryLock(owner); var clipLock=SessionFiles.tryLock(clip)) {
                    if(ownerLock==null || clipLock==null) continue;
                    var m=SessionManifest.read(clip); if(m==null) throw new IOException("Clip manifest missing");
                    if("complete".equals(m.state) && Files.exists(RecordingStorage.target(clip))) { Files.delete(plan); continue; }
                    long first=Long.parseLong(p.getProperty("first")),last=Long.parseLong(p.getProperty("last")); double end=Double.parseDouble(p.getProperty("end"));
                    if(first<0 || last<first || last-first>151 || !Double.isFinite(end) || end<=last*2d || end>(last+1)*2d+.05) throw new IOException("Invalid clip range");
                    rebuild(ffmpeg,root,first,last,end,clip,Boolean.parseBoolean(p.getProperty("game")),Boolean.parseBoolean(p.getProperty("mic")),m);
                    Files.delete(plan); count++;
                }
            } catch(Exception e) { notice.accept("Clip recovery deferred: "+e.getMessage()); }
        }
        Set<Path> protectedRoots=new HashSet<>();
        try(var walk=Files.walk(details,2)) {
            for(Path plan:walk.filter(p -> p.getFileName().toString().equals("clip-plan.properties")).toList())
                try { protectedRoots.add(source(details,read(plan).getProperty("source"))); } catch(Exception e) { throw new IOException("Invalid replay plan; cache cleanup paused",e); }
        }
        try(var sessions=Files.newDirectoryStream(details)) {
            for(Path session:sessions) {
                Path root=session.resolve("replay"); if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(session)) continue;
                Path owner=partial(session);
                try(var lock=SessionFiles.tryLock(owner)) {
                    if(lock==null) continue;
                    Properties p=read(root.resolve("history.properties")); long first=Long.parseLong(p.getProperty("fullStart","-1"));
                    var m=SessionManifest.read(owner);
                    if(first>=0 && m!=null && !"complete".equals(m.state)) {
                        long last=-1; double end=0;
                        Path index=root.resolve("index.csv"); if(Files.size(index)>128*1024) throw new IOException("Replay index exceeds bound");
                        String indexText=Files.readString(index); int newline=indexText.lastIndexOf('\n');
                        for(String line:(newline<0?"":indexText.substring(0,newline)).split("\\R")) {
                            String[] row=line.split(","); if(row.length!=3) continue;
                            String name=row[0].replace("\"",""); if(!name.matches("video-[0-9]{9}\\.mkv")) continue;
                            long n=Long.parseLong(name.substring(6,15)); double stop=Double.parseDouble(row[2]);
                            if(n>last && Double.isFinite(stop) && stop>n*2d && stop<=(n+1)*2d+.05) { last=n; end=stop; }
                        }
                        if(last<first) { notice.accept("Interrupted replay recording has no closed segment yet; sources retained."); continue; }
                        rebuild(ffmpeg,root,first,last,end,owner,Boolean.parseBoolean(p.getProperty("game")),Boolean.parseBoolean(p.getProperty("mic")),m); count++;
                    }
                    if(!protectedRoots.contains(root.toAbsolutePath().normalize())) clean(root);
                } catch(Exception e) { notice.accept("Replay recording recovery deferred: "+e.getMessage()); }
            }
        }
        return count;
    }
    public static boolean pendingClips(Path details) throws IOException {
        if(!Files.isDirectory(details)) return false;
        try(var files=Files.walk(details,2)) { return files.anyMatch(p -> p.getFileName().toString().equals("clip-plan.properties")); }
    }
    private static Path partial(Path session) { return session.resolve(session.getFileName()+".partial.mp4"); }
    private static Properties read(Path file) throws IOException {
        if(Files.isSymbolicLink(file) || Files.size(file)>16384) throw new IOException("Invalid replay metadata");
        Properties p=new Properties(); try(var in=Files.newInputStream(file)) { p.load(in); } return p;
    }
    private static Path source(Path details,String value) throws IOException {
        if(value==null) throw new IOException("Missing replay source");
        Path root=Path.of(value).toAbsolutePath().normalize(),base=details.toAbsolutePath().normalize();
        if(!root.getFileName().toString().equals("replay") || root.getParent()==null || !base.equals(root.getParent().getParent()) || Files.isSymbolicLink(root) || Files.isSymbolicLink(root.getParent())) throw new IOException("Replay source outside owned details directory");
        return root;
    }
    private static void rebuild(Path ffmpeg,Path root,long first,long last,double end,Path partial,boolean game,boolean mic,SessionManifest m) throws Exception {
        if(Files.exists(RecordingStorage.target(partial))) { m.outputFile=partial.getParent().getFileName()+"-recovered-"+UUID.randomUUID().toString().substring(0,8)+".mp4"; m.save(partial); }
        long required=0;
        for(long n=first;n<=last;n++) { Path video=ReplayExport.video(root,n); if(Files.isSymbolicLink(video)) throw new IOException("Symbolic replay source"); required=Math.addExact(required,Files.size(video)); }
        if(Files.getFileStore(root).getUsableSpace()<required*3+256L*1024*1024) throw new IOException("More disk space is needed for recovery");
        backup(partial); backup(SessionFiles.sibling(partial,".game.pcm")); backup(SessionFiles.sibling(partial,".mic.pcm"));
        ReplayExport.assemble(ffmpeg,root,first,last,end,partial,game,mic,new Cancellation());
        RecordingRecovery.finalizeOwned(ffmpeg,partial,new Cancellation()); m.state="complete"; m.durationSeconds=end-first*2d; m.sourceStartSeconds=first*2d; m.sourceEndSeconds=end; m.save(partial);
        dev.optic.markers.MarkerExport.schedule(ffmpeg,partial(root.getParent()),partial,m);
    }
    private static void backup(Path file) throws IOException {
        if(!Files.exists(file)) return;
        if(Files.isSymbolicLink(file)) throw new IOException("Symbolic recovery input");
        Path backup=file.resolveSibling(file.getFileName()+".replay-backup");
        if(!Files.exists(backup)) Files.move(file,backup); else Files.delete(file); // retain the original once, not an unbounded chain of failed attempts
    }
    private static void clean(Path root) throws IOException {
        try(var files=Files.newDirectoryStream(root)) {
            for(Path file:files) if(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)) {
                String name=file.getFileName().toString();
                if(name.matches("(?:video-[0-9]{9}\\.mkv|(?:game|mic)-[0-9]{9}\\.(?:pcm|open)|index\\.csv|history\\.(?:properties|tmp))")) Files.delete(file);
            }
        }
        try { Files.delete(root); } catch(DirectoryNotEmptyException ignored) { /* preserve unknown user files */ }
    }
}
