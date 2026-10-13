package dev.optic.config;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;

/** Worker-only, bounded JSON persistence. Unknown fields survive writes; future schemas are read-only. */
public final class SettingsStore {
    private static final long MAX_BYTES=1024*1024;
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    public static JsonObject read(Path file) throws IOException {
        if(Files.size(file)>MAX_BYTES) throw new IOException("Settings exceed the safe size limit");
        try(var input=Files.newInputStream(file)) {
            byte[] bytes=input.readNBytes((int)MAX_BYTES+1);
            if(bytes.length>MAX_BYTES) throw new IOException("Settings exceed the safe size limit");
            return JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
        catch(RuntimeException e) { throw new IOException("Invalid settings document",e); }
    }
    public static synchronized void write(Path file,JsonObject values,int schema) throws IOException {
        file=file.toAbsolutePath().normalize(); Files.createDirectories(file.getParent());
        JsonObject merged=new JsonObject();
        if(Files.exists(file)) {
            try { merged=read(file); }
            catch(IOException e) {
                // Do not replace unreadable/oversized files. A malformed, small JSON file can be preserved.
                if(Files.size(file)>MAX_BYTES || !(e.getCause() instanceof RuntimeException)) throw e;
                backup(file,"invalid");
            }
            int oldSchema;
            try { oldSchema=merged.has("schemaVersion")?merged.get("schemaVersion").getAsInt():0; }
            catch(RuntimeException e) { throw new IOException("Invalid settings schema; original retained",e); }
            if(oldSchema>schema) throw new IOException("Settings belong to a newer Optic version; original retained");
            if(oldSchema<schema) backup(file,"v"+oldSchema);
        }
        for(var entry:values.entrySet()) merged.add(entry.getKey(),entry.getValue().deepCopy());
        merged.addProperty("schemaVersion",schema);
        Path temp=Files.createTempFile(file.getParent(),"optic-settings-",".tmp");
        try {
            String text=JSON.toJson(merged);
            if(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_BYTES) throw new IOException("Settings exceed the safe size limit");
            Files.writeString(temp,text);
            try { Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException e) { Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
    private static void backup(Path file,String reason) throws IOException {
        // Never overwrite an earlier migration backup. Backup failure aborts the write.
        Path backup=file.resolveSibling(file.getFileName()+".before-"+reason+".bak");
        if(Files.exists(backup)) {
            if(Files.mismatch(file,backup)==-1) return;
            backup=file.resolveSibling(file.getFileName()+".before-"+reason+"-"+java.util.UUID.randomUUID()+".bak");
        }
        Files.copy(file,backup);
    }
    private SettingsStore() {}
}
