package dev.optic.config;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class SettingsStoreTest {
    @TempDir Path directory;
    private JsonObject update() { var v=new JsonObject(); v.addProperty("fps",60); return v; }
    @Test void migrationPreservesOriginalAndUnknownFields() throws Exception {
        Path p=directory.resolve("settings.json"); String before="{\"schemaVersion\":3,\"futureExtension\":{\"enabled\":true},\"fps\":30}"; Files.writeString(p,before);
        SettingsStore.write(p,update(),4);
        assertEquals(before,Files.readString(directory.resolve("settings.json.before-v3.bak")));
        assertTrue(SettingsStore.read(p).getAsJsonObject("futureExtension").get("enabled").getAsBoolean());
        assertEquals(60,SettingsStore.read(p).get("fps").getAsInt());
    }
    @Test void futureSchemaIsNeverOverwritten() throws Exception {
        Path p=directory.resolve("settings.json"); String before="{\"schemaVersion\":999,\"fps\":30}"; Files.writeString(p,before);
        assertThrows(java.io.IOException.class,() -> SettingsStore.write(p,update(),4)); assertEquals(before,Files.readString(p));
    }
    @Test void corruptFileBackedUpBeforeRepair() throws Exception {
        Path p=directory.resolve("settings.json"); Files.writeString(p,"{broken"); SettingsStore.write(p,update(),4);
        assertEquals("{broken",Files.readString(directory.resolve("settings.json.before-invalid.bak")));
        assertEquals(4,SettingsStore.read(p).get("schemaVersion").getAsInt());
    }
    @Test void backupFailureRetainsOriginal() throws Exception {
        Path p=directory.resolve("settings.json"); Files.writeString(p,"{\"fps\":30}"); Files.createDirectory(directory.resolve("settings.json.before-v0.bak"));
        assertThrows(java.io.IOException.class,() -> SettingsStore.write(p,update(),4)); assertEquals("{\"fps\":30}",Files.readString(p));
    }
    @Test void oversizedSettingsRetained() throws Exception {
        Path p=directory.resolve("settings.json"); Files.writeString(p," ".repeat(1024*1024+1));
        assertThrows(java.io.IOException.class,() -> SettingsStore.write(p,update(),4)); assertEquals(1024*1024+1,Files.size(p));
    }
}
