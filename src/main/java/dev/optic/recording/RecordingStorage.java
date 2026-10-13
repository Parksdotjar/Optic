package dev.optic.recording;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import java.util.regex.Pattern;

/** All naming and allocation happens off-thread. Creating a details directory reserves a name. */
public final class RecordingStorage {
    public enum Style { DEFAULT, VERSION, PART, SCENE;
        public String label() { return switch(this) { case DEFAULT -> "Default"; case VERSION -> "V1"; case PART -> "Part 1"; case SCENE -> "Scene 1"; }; }
        String suffix() { return switch(this) { case VERSION -> " V"; case PART -> " Part "; case SCENE -> " Scene "; default -> ""; }; }
    }
    public static Path details(Path videos) { var path=videos.toAbsolutePath().normalize(); return path.resolveSibling(path.getFileName()+"-details"); }
    public static String cleanTitle(String title) {
        String name=(title==null?"":title).replaceAll("[\\p{Cntrl}<>:\"/\\\\|?*]","_").strip().replaceAll("[. ]+$","");
        if(name.length()>80) name=name.substring(0,80);
        if(name.isBlank() || name.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?")) name="Recording";
        return name;
    }
    public static Path reserve(Path videos,String title,Style style) throws IOException {
        Files.createDirectories(videos); Path details=details(videos); Files.createDirectories(details);
        String base=cleanTitle(title);
        if(style==Style.DEFAULT) {
            if(title==null || title.isBlank()) base="Optic";
            base+="-"+java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))+"-"+UUID.randomUUID().toString().substring(0,8);
        }
        long next=1;
        if(style!=Style.DEFAULT) {
            var pattern=Pattern.compile(Pattern.quote(base+style.suffix())+"([0-9]+)(?:\\.mp4)?",Pattern.CASE_INSENSITIVE);
            for(Path directory:new Path[]{videos,details}) try(var files=Files.list(directory)) {
                for(var file:files.toList()) { var match=pattern.matcher(file.getFileName().toString()); if(match.matches()) try { next=Math.max(next,Math.addExact(Long.parseLong(match.group(1)),1)); } catch(ArithmeticException | NumberFormatException e) { throw new IOException("Recording number is too large",e); } }
            }
        }
        for(int attempts=0;attempts<10000;attempts++,next++) {
            String stem=style==Style.DEFAULT?base+(attempts==0?"":"-"+attempts):base+style.suffix()+next;
            if(Files.exists(videos.resolve(stem+".mp4"))) continue;
            Path session=details.resolve(stem);
            try { Files.createDirectory(session); return session.resolve(stem+".partial.mp4"); }
            catch(FileAlreadyExistsException ignored) {}
        }
        throw new IOException("Could not reserve an unused recording name");
    }
    public static Path reserveTemplate(Path videos,String resolved) throws IOException {
        Files.createDirectories(videos); Path details=details(videos); Files.createDirectories(details);
        String base=cleanTitle(resolved);
        for(int n=0;n<10000;n++) {
            String stem=base+(n==0?"":"-"+(n+1)); if(Files.exists(videos.resolve(stem+".mp4"))) continue;
            Path dir=details.resolve(stem); try { Files.createDirectory(dir); return dir.resolve(stem+".partial.mp4"); } catch(FileAlreadyExistsException ignored) {}
        }
        throw new IOException("No available filename");
    }
    public static Path target(Path partial) throws IOException {
        var manifest=SessionManifest.read(partial);
        if(manifest!=null && manifest.schemaVersion>=4 && manifest.outputDirectory!=null && manifest.outputFile!=null) {
            String file=manifest.outputFile;
            if(!file.endsWith(".mp4") || file.contains("/") || file.contains("\\") || !Path.of(file).getFileName().toString().equals(file)) throw new IOException("Invalid recording destination filename");
            return Path.of(manifest.outputDirectory).toAbsolutePath().normalize().resolve(file);
        }
        return SessionFiles.sibling(partial,".mp4");
    }
}
