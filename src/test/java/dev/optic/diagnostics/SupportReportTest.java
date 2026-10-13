package dev.optic.diagnostics;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SupportReportTest {
    @TempDir Path directory;
    @Test void privateFieldsAndInjectedLogsCannotEnterReport() {
        String text=SupportReport.build(Map.of("gpu","GPU\nforged=x", "os","C:\\Users\\Secret", "world","PrivateWorld", "token","SecretToken", "microphoneDevice","PrivateMic"),"dropped=4 file=C:\\Users\\Secret world=PrivateWorld duplicated=2\nqueue=forged");
        for(String forbidden:List.of("Secret","PrivateWorld","PrivateMic","\nforged=","queue: forged")) assertFalse(text.contains(forbidden));
        assertTrue(text.contains("dropped: 4")); assertTrue(text.contains("duplicated: 2"));
    }
    @Test void exportsExactlyReviewedTextWithoutOverwriting() throws Exception {
        String preview=SupportReport.build(Map.of("minecraft","1.21.11"),"");
        Path a=SupportReport.export(directory,preview),b=SupportReport.export(directory,preview);
        assertNotEquals(a,b); assertEquals(preview,Files.readString(a)); assertEquals(preview,Files.readString(b));
    }
}
