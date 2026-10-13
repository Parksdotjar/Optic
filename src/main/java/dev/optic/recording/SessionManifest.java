package dev.optic.recording;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;

/** Local recovery metadata. The chosen output directory is retained for recovery; do not include it in public diagnostics. */
public final class SessionManifest {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public int schemaVersion = 4;
    public String outputDirectory;
    public String sessionId, createdUtc, state = "preparing", encoder, captureBackend, outputFile, failure;
    public int width, height, fps;
    public String preset,quality,aspect;
    public String sourceSessionId;
    public String notes="";
    public boolean trashed=false;
    public String requestedEncoder,colorContract="legacy",timingPolicy="CFR: duplicate/drop; stop on >5-second capture gap";
    public boolean stopOnSustainedDuplicates;
    public String stopReason;
    public double sourceStartSeconds,sourceEndSeconds;
    public String[] videoOptions = {};
    public int initialSourceWidth,initialSourceHeight;
    public boolean microphoneRequested, gameAudioAvailable = false;
    public String[] audioTracks = {};
    public double durationSeconds;
    public long framesAccepted, encoderFrames = -1;
    public void save(Path partial) throws IOException {
        Path target = SessionFiles.sibling(partial, ".session.json");
        if(Files.exists(target)) {
            // Preserve extension fields and protect future metadata just as we protect settings.
            dev.optic.config.SettingsStore.write(target,JSON.toJsonTree(this).getAsJsonObject(),4);
            return;
        }
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, JSON.toJson(this));
        try { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
    }
    public static SessionManifest read(Path partial) throws IOException {
        Path path = SessionFiles.sibling(partial, ".session.json");
        if (!Files.isRegularFile(path)) return null;
        if (Files.size(path) > 65536) throw new IOException("Session metadata too large");
        try {
            JsonObject data = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            if (data.has("schemaVersion") && data.get("schemaVersion").getAsInt() > 4) throw new IOException("Session belongs to a newer Optic version");
            return JSON.fromJson(data, SessionManifest.class);
        } catch (JsonParseException | IllegalStateException e) { throw new IOException("Invalid session metadata", e); }
    }
}

