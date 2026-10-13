package dev.optic.recording;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class SessionFilesTest {
    @TempDir Path directory;
    @Test void activeRecordingCannotBeRecoveredUntilItsLockIsReleased() throws Exception {
        var partial=directory.resolve("Optic-test.partial.mp4");
        for(int i=0;i<100;i++) {
            try(var owner=SessionFiles.tryLock(partial)) { assertNotNull(owner); assertNull(SessionFiles.tryLock(partial)); }
        }
        try(var recovery=SessionFiles.tryLock(partial)) { assertNotNull(recovery); }
    }
    @Test void manifestsPreserveLegacyDimensionsAndRejectFutureSchemas() throws Exception {
        var partial=directory.resolve("Optic-test.partial.mp4"); var path=SessionFiles.sibling(partial,".session.json");
        Files.writeString(path,"{\"width\":1920,\"height\":1080,\"fps\":60}");
        assertEquals(1920,SessionManifest.read(partial).width);
        Files.writeString(path,"{\"schemaVersion\":999}");
        assertThrows(java.io.IOException.class,() -> SessionManifest.read(partial));
        assertEquals("{\"schemaVersion\":999}",Files.readString(path));
    }
}
