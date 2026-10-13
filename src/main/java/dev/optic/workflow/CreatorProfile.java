package dev.optic.workflow;
import dev.optic.config.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

/** Portable intent only. Never copies paths, naming, device identities or privacy preferences. */
public final class CreatorProfile {
    public int schemaVersion=1;
    public String name="Custom";
    public JsonObject settings=new JsonObject();
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final List<String> FIELDS=List.of("resolution","fps","quality","aspect","encoder","colorMode","stopOnSustainedDuplicates","microphone","gameAudio","captureHud","captureChat","captureSubtitles","captureScoreboard","captureBosses","captureActionBar","captureDebug","captureIndicator");
    public static CreatorProfile capture(String name,RecorderConfig config) {
        var p=new CreatorProfile(); p.name=name; var all=JSON.toJsonTree(config).getAsJsonObject(); for(String field:FIELDS) p.settings.add(field,all.get(field)); return p;
    }
    public void apply(RecorderConfig target) {
        if(schemaVersion!=1 || settings==null) throw new IllegalArgumentException("Unsupported profile version");
        var all=JSON.toJsonTree(target).getAsJsonObject(); for(String field:FIELDS) if(settings.has(field)) all.add(field,settings.get(field));
        var resolved=JSON.fromJson(all,RecorderConfig.class); resolved.validate();
        try { for(String field:FIELDS) { var f=RecorderConfig.class.getField(field); f.set(target,f.get(resolved)); } } catch(ReflectiveOperationException e) { throw new IllegalStateException(e); }
        target.preset=RecordingPreset.CUSTOM;
    }
    public static CreatorProfile builtIn(String name,RecorderConfig current) {
        var c=current.snapshot();
        switch(name) {
            case "YouTube" -> RecordingPreset.CREATOR.apply(c);
            case "Cinematic" -> { RecordingPreset.CINEMATIC.apply(c); c.captureHud=false; c.captureIndicator=false; }
            case "Low-End PC" -> RecordingPreset.PERFORMANCE.apply(c);
            case "High FPS" -> { RecordingPreset.BALANCED.apply(c); c.fps=120; }
            default -> throw new IllegalArgumentException("Unknown built-in profile");
        }
        return capture(name,c);
    }
    public static void save(Path file,CreatorProfile profile) throws Exception { atomic(file,JSON.toJson(profile)); }
    public static CreatorProfile load(Path file) throws Exception {
        if(Files.isSymbolicLink(file) || Files.size(file)>16384) throw new java.io.IOException("Invalid profile file");
        var profile=JSON.fromJson(Files.readString(file),CreatorProfile.class);
        if(profile==null || profile.schemaVersion!=1) throw new java.io.IOException("Unsupported profile version"); return profile;
    }
    public static void atomic(Path file,String value) throws java.io.IOException {
        Files.createDirectories(file.getParent()); Path tmp=file.resolveSibling(file.getFileName()+".tmp");
        if(Files.isSymbolicLink(file) || Files.isSymbolicLink(tmp)) throw new java.io.IOException("Symbolic metadata path");
        Files.writeString(tmp,value); try { Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); } catch(AtomicMoveNotSupportedException e) { Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING); }
    }
}
