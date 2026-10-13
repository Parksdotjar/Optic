package dev.optic.recording;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class ManifestMigrationTest {
    @TempDir Path directory;
    @Test void savingOlderMetadataPreservesExtensionsAndBackup() throws Exception {
        Path partial=directory.resolve("example.partial.mp4"),json=SessionFiles.sibling(partial,".session.json");
        Files.writeString(json,"{\"schemaVersion\":3,\"fps\":30,\"extension\":{\"secret\":42}}");
        var m=SessionManifest.read(partial); m.state="complete"; m.save(partial);
        assertEquals(42,dev.optic.config.SettingsStore.read(json).getAsJsonObject("extension").get("secret").getAsInt());
        assertTrue(Files.exists(json.resolveSibling(json.getFileName()+".before-v3.bak")));
    }
    @Test void futureMetadataCannotBeReplacedByAnOldInstance() throws Exception {
        Path partial=directory.resolve("example.partial.mp4"),json=SessionFiles.sibling(partial,".session.json");
        String future="{\"schemaVersion\":999}"; Files.writeString(json,future);
        assertThrows(java.io.IOException.class,() -> new SessionManifest().save(partial)); assertEquals(future,Files.readString(json));
    }
}
